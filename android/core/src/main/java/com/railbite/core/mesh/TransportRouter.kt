package com.railbite.core.mesh

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.railbite.core.api.ApiClient
import com.railbite.core.api.RailBiteClient
import com.railbite.core.data.RailBiteSettings
import com.railbite.core.model.OrderMessage
import com.railbite.core.model.OrderMessageCreateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class TransportMode {
    LAN_CONNECTED,
    BLE_MESH_ACTIVE,
    BLE_DISABLED,
}

data class TransportStatus(
    val mode: TransportMode = TransportMode.BLE_DISABLED,
    val lanReachable: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val permissionsMissing: Boolean = false,
    val lastCheckedMs: Long = 0L,
) {
    /** Exact human-readable pill labels shared across Customer & Picker screens. */
    val label: String
        get() = when (mode) {
            TransportMode.LAN_CONNECTED -> "LAN Connected"
            TransportMode.BLE_MESH_ACTIVE -> "BLE Mesh Offline (Active)"
            TransportMode.BLE_DISABLED -> "BLE Off — Enable Bluetooth"
        }
}

/**
 * Automatic Step A (LAN REST/MQTT, `1500ms` timeout) -> Step B (BLE Mesh) transport router.
 * Continuously tracks LAN reachability and Bluetooth adapter/permission readiness so UIs display
 * an accurate transport pill and fail over immediately with zero blocking queues.
 */
data class QuickChatSendResult(
    val message: OrderMessage,
    val transport: ChatTransport,
)

class TransportRouter(
    private val context: Context,
    private val settings: RailBiteSettings,
    private val probeTimeoutMs: Long = DEFAULT_LAN_PROBE_TIMEOUT_MS,
    private val client: RailBiteClient? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val quickMsgSeq = ConcurrentHashMap<UInt, AtomicInteger>()
    private val _status = MutableStateFlow(computeStatus(lanReachable = false))
    val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private var monitorJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring() {
        registerConnectivityCallbacks()
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            BleMeshHub.stats.collect {
                // Reflect immediate BT / permission changes without waiting for next timer tick.
                _status.updateIfChanged(computeStatus(lanReachable = _status.value.lanReachable))
            }
        }
        scope.launch {
            while (isActive) {
                probeNow()
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    fun refreshRadioOnly() {
        BleMeshHub.refreshRadioState(context)
        _status.updateIfChanged(computeStatus(lanReachable = _status.value.lanReachable))
    }

    /** Fast `<= 1500ms` LAN probe; updates [status] immediately with the resolved transport mode. */
    suspend fun checkLanReachable(): Boolean = withContext(Dispatchers.IO) {
        val url = runCatching { settings.serverUrl.first() }.getOrDefault("")
        val reachable = if (url.isBlank()) {
            false
        } else {
            runCatching { ApiClient.reachable(url, timeoutMs = probeTimeoutMs) }.getOrDefault(false)
        }
        val next = computeStatus(lanReachable = reachable)
        _status.value = next
        if (!reachable && next.mode == TransportMode.BLE_MESH_ACTIVE && settings.meshRelayEnabled.first()) {
            BleMeshService.start(context, lowLatency = false)
        }
        reachable
    }

    /**
     * Single entry point for order quick-chat: try LAN (`POST /orders/:id/messages`, 1500ms
     * timeout, idempotent client UUID) first and fall through to a high-priority 23-byte
     * `0x13 QUICK_MSG` BLE broadcast when the HTTP request fails.
     *
     * Always runs on [Dispatchers.IO]; callers get [QuickChatSendResult] or
     * [QuickChatUnavailableException] when both transports are unavailable. A 409 means the
     * order left the chat-allowed state machine and is surfaced unchanged.
     */
    suspend fun sendQuickMessage(
        orderId: String,
        senderRole: String,
        senderId: String? = null,
        presetCode: String? = null,
        body: String,
        coach: String,
        berth: Int,
        meshKey: String = BlePacketCodec.RAILBITE_MESH_KEY,
        trainNumber: String? = null,
    ): QuickChatSendResult = withContext(Dispatchers.IO) {
        val cleanBody = body.trim()
        if (orderId.isBlank() || cleanBody.isBlank()) {
            throw QuickChatUnavailableException("Message is empty")
        }
        val clientMsgId = UUID.randomUUID().toString()
        val lanReachable = runCatching { checkLanReachable() }.getOrDefault(false)
        if (lanReachable) {
            val api = client?.api()
            if (api != null) {
                try {
                    val created = withTimeoutOrNull(LAN_SEND_TIMEOUT_MS) {
                        api.sendOrderMessage(
                            orderId,
                            OrderMessageCreateRequest(
                                id = clientMsgId,
                                senderRole = senderRole,
                                senderId = senderId,
                                presetCode = presetCode,
                                body = cleanBody,
                            ),
                        )
                    }
                    if (created != null) {
                        OrderChatRepository.ingest(orderId, created, ChatTransport.LAN)
                        return@withContext QuickChatSendResult(created, ChatTransport.LAN)
                    }
                    Log.w(TAG, "LAN quick-chat timed out after ${LAN_SEND_TIMEOUT_MS}ms — BLE fallback")
                } catch (e: HttpException) {
                    if (e.code() == 409) throw e
                    Log.w(TAG, "LAN quick-chat HTTP ${e.code()} — BLE fallback", e)
                } catch (e: Exception) {
                    Log.w(TAG, "LAN quick-chat failed (${e.javaClass.simpleName}: ${e.message}) — BLE fallback", e)
                }
            } else {
                Log.w(TAG, "LAN quick-chat skipped: API client unavailable — BLE fallback")
            }
        } else {
            Log.d(TAG, "LAN unreachable — sending quick-chat over BLE mesh")
        }

        // BLE fallback. Only the 8 preset codes fit the 23-byte frame; custom notes need LAN.
        val presetCodeByte = presetCode?.let { BlePacketCodec.codeForPreset(it) }
            ?: throw QuickChatUnavailableException(
                if (presetCode == null) {
                    "Offline BLE mode supports 1-tap quick presets only"
                } else {
                    "Unknown quick preset"
                },
            )
        val preset = BlePacketCodec.presetForCode(presetCodeByte)
            ?: throw QuickChatUnavailableException("Unknown quick preset")
        val roleByte = when (senderRole.uppercase()) {
            "CUSTOMER" -> BlePacketCodec.ROLE_CUSTOMER
            "PICKER" -> BlePacketCodec.ROLE_PICKER
            else -> throw QuickChatUnavailableException("Unsupported chat sender")
        }
        val shortId = BlePacketCodec.uuidToShortId(orderId)
        val seq = quickMsgSeq.getOrPut(shortId) { AtomicInteger(1) }
            .getAndIncrement()
            .coerceIn(1, 65535)
        val packet = BlePacketCodec.QuickMsg(
            orderId = shortId,
            hopCount = 0,
            ttl = BlePacketCodec.DEFAULT_TTL,
            coach = coach,
            seat = berth,
            senderRole = roleByte,
            presetCode = presetCodeByte,
            msgSeq = seq,
        )
        val activeTrain = trainNumber
            ?.trim()
            ?.takeIf { com.railbite.core.util.Validators.isValidTrainNumber(it) }
            ?: runCatching { settings.bleTrainNumber.first() }
                .getOrDefault(BlePacketCodec.DEFAULT_TRAIN_NUMBER)
        val sent = BleMeshHub.sendQuickMsg(
            context = context,
            packet = packet,
            meshKey = meshKey,
            slices = BleTxScheduler.QUICK_MSG_SLICES,
            trainNumber = activeTrain,
        )
        if (!sent) {
            Log.w(TAG, "BLE quick-chat rejected: radio/permissions unavailable")
            throw QuickChatUnavailableException(
                "Bluetooth off — enable Bluetooth to message over BLE mesh",
            )
        }
        val local = OrderMessage(
            id = "ble-${packet.senderRole}-${packet.msgSeq}",
            orderId = orderId,
            senderRole = senderRole.uppercase(),
            senderId = senderId,
            presetCode = preset.code,
            body = preset.body,
            createdAt = System.currentTimeMillis(),
        )
        OrderChatRepository.ingest(orderId, local, ChatTransport.BLE)
        QuickChatSendResult(local, ChatTransport.BLE)
    }

    suspend fun probeNow(): TransportStatus {
        checkLanReachable()
        return _status.value
    }

    private fun computeStatus(lanReachable: Boolean): TransportStatus {
        val btOn = BlePermissions.isBluetoothEnabled(context)
        val permsMissing = BlePermissions.requiredPermissions(context).isNotEmpty()
        val mode = when {
            lanReachable -> TransportMode.LAN_CONNECTED
            btOn && !permsMissing -> TransportMode.BLE_MESH_ACTIVE
            else -> TransportMode.BLE_DISABLED
        }
        return TransportStatus(
            mode = mode,
            lanReachable = lanReachable,
            bluetoothEnabled = btOn,
            permissionsMissing = permsMissing,
            lastCheckedMs = System.currentTimeMillis(),
        )
    }

    private fun registerConnectivityCallbacks() {
        if (networkCallback != null) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { probeNow() }
            }

            override fun onLost(network: Network) {
                scope.launch { probeNow() }
            }
        }
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                cb,
            )
            networkCallback = cb
        }
    }

    private fun MutableStateFlow<TransportStatus>.updateIfChanged(next: TransportStatus) {
        val cur = value
        if (cur.mode != next.mode ||
            cur.lanReachable != next.lanReachable ||
            cur.bluetoothEnabled != next.bluetoothEnabled ||
            cur.permissionsMissing != next.permissionsMissing
        ) {
            value = next
        }
    }

    companion object {
        private const val TAG = "RailBiteChat"
        const val DEFAULT_LAN_PROBE_TIMEOUT_MS = 1_500L
        const val LAN_SEND_TIMEOUT_MS = 1_500L
        const val MONITOR_INTERVAL_MS = 5_000L
    }
}

