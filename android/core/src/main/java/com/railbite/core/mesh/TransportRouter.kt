package com.railbite.core.mesh

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.railbite.core.api.ApiClient
import com.railbite.core.data.RailBiteSettings
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
class TransportRouter(
    private val context: Context,
    private val settings: RailBiteSettings,
    private val probeTimeoutMs: Long = DEFAULT_LAN_PROBE_TIMEOUT_MS,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
    suspend fun checkLanReachable(): Boolean {
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
        return reachable
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
        const val DEFAULT_LAN_PROBE_TIMEOUT_MS = 1_500L
        const val MONITOR_INTERVAL_MS = 5_000L
    }
}
