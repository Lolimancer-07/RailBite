package com.railbite.core.mesh

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MeshStatsSnapshot(
    val packetsSent: Int = 0,
    val packetsRelayed: Int = 0,
    val packetsReceived: Int = 0,
    val lastRssiDbm: Int? = null,
    val lastHopCount: Int? = null,
    val serviceRunning: Boolean = false,
    val lowLatencyScan: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val permissionsGranted: Boolean = false,
)

data class IncomingBleEvent(
    val packet: BlePacketCodec.BlePacket,
    val rssi: Int,
    val receivedAtMs: Long = System.currentTimeMillis(),
)

/**
 * Process-scoped state & radio coordination hub for the 23-byte BLE multi-hop flood mesh.
 * Each app (`com.railbite.customer` and `com.railbite.picker`) maintains its own [BleMeshHub]
 * singleton backed by [BleMeshService] and [BleTxScheduler].
 */
object BleMeshHub {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dedupCache = BleDedupCache()

    /** Orders originated by THIS process (never blindly cancelled by relays, etc.). */
    private val locallyOriginatedOrders = HashSet<UInt>()
    private val statusRank = mapOf(
        BlePacketCodec.STATUS_ACCEPTED to 1,
        BlePacketCodec.STATUS_PACKING to 2,
        BlePacketCodec.STATUS_OUT_FOR_DELIVERY to 3,
        BlePacketCodec.STATUS_DELIVERED to 4,
        BlePacketCodec.STATUS_REJECTED to 0,
    )

    @Volatile
    private var scheduler: BleTxScheduler? = null

    private val _stats = MutableStateFlow(MeshStatsSnapshot())
    val stats: StateFlow<MeshStatsSnapshot> = _stats.asStateFlow()

    private val _incoming = MutableSharedFlow<IncomingBleEvent>(extraBufferCapacity = 64)
    val incoming: SharedFlow<IncomingBleEvent> = _incoming.asSharedFlow()

    private val _latestStatuses = MutableStateFlow<Map<UInt, BlePacketCodec.StatusAck>>(emptyMap())
    val latestStatuses: StateFlow<Map<UInt, BlePacketCodec.StatusAck>> = _latestStatuses.asStateFlow()

    private val _quickMessages = MutableStateFlow<Map<UInt, List<BlePacketCodec.QuickMsg>>>(emptyMap())
    val quickMessages: StateFlow<Map<UInt, List<BlePacketCodec.QuickMsg>>> = _quickMessages.asStateFlow()

    private val _seenOrders = MutableStateFlow<Map<UInt, BlePacketCodec.OrderNew>>(emptyMap())
    val seenOrders: StateFlow<Map<UInt, BlePacketCodec.OrderNew>> = _seenOrders.asStateFlow()

    internal fun attachScheduler(txScheduler: BleTxScheduler) {
        synchronized(this) {
            scheduler?.shutdown()
            scheduler = txScheduler
        }
    }

    internal fun detachScheduler(txScheduler: BleTxScheduler) {
        synchronized(this) {
            if (scheduler === txScheduler) {
                scheduler = null
            }
        }
    }

    fun updateServiceState(
        running: Boolean,
        lowLatencyScan: Boolean,
        context: Context,
    ) {
        _stats.update {
            it.copy(
                serviceRunning = running,
                lowLatencyScan = lowLatencyScan,
                bluetoothEnabled = BlePermissions.isBluetoothEnabled(context),
                permissionsGranted = BlePermissions.requiredPermissions(context).isEmpty(),
            )
        }
    }

    fun refreshRadioState(context: Context) {
        _stats.update {
            it.copy(
                bluetoothEnabled = BlePermissions.isBluetoothEnabled(context),
                permissionsGranted = BlePermissions.requiredPermissions(context).isEmpty(),
            )
        }
    }

    /**
     * Customer origin broadcast (`0x11`): registers in dedup cache, starts [BleMeshService]
     * in LOW_LATENCY mode, and re-broadcasts up to `90s` until `ACCEPTED` or cancelled.
     */
    fun sendOrderNew(
        context: Context,
        packet: BlePacketCodec.OrderNew,
        meshKey: String = BlePacketCodec.RAILBITE_MESH_KEY,
    ): Boolean {
        if (!BlePermissions.isReadyForMesh(context)) {
            refreshRadioState(context)
            return false
        }
        synchronized(locallyOriginatedOrders) {
            locallyOriginatedOrders.add(packet.orderId)
        }
        dedupCache.markSeen(packet.dedupKey)
        _seenOrders.update { it + (packet.orderId to packet) }

        val encoded = BlePacketCodec.encode(packet, meshKey)
        BleMeshService.start(context, lowLatency = true)

        scope.launch {
            val tx = ensureTx(context) ?: return@launch
            tx.enqueue(
                BleTxScheduler.TxItem(
                    id = "order:${packet.orderId}",
                    payload23 = encoded,
                    remainingSlices = 1,
                    orderShortId = packet.orderId,
                    isOriginOrder = true,
                    expiresAtMs = System.currentTimeMillis() + BleTxScheduler.ORIGIN_ORDER_DURATION_MS,
                ),
            )
            _stats.update { it.copy(packetsSent = it.packetsSent + 1) }
        }
        return true
    }

    /** Cancels origin re-broadcasting for [shortOrderId] (e.g. on ACCEPTED or cancel). */
    fun cancelOrderBroadcast(shortOrderId: UInt) {
        scheduler?.cancelOrder(shortOrderId)
    }

    /**
     * Broadcasts a `STATUS_ACK (0x12)` packet (`ttl = 6`, `15s`) into the BLE mesh.
     */
    fun sendStatusAck(
        context: Context,
        packet: BlePacketCodec.StatusAck,
        meshKey: String = BlePacketCodec.RAILBITE_MESH_KEY,
        slices: Int = BleTxScheduler.STATUS_OR_MSG_SLICES,
    ): Boolean {
        if (!BlePermissions.isReadyForMesh(context)) {
            refreshRadioState(context)
            return false
        }
        dedupCache.markSeen(packet.dedupKey)
        recordStatusMonotonic(packet)
        if (packet.statusCode in BlePacketCodec.STATUS_ACCEPTED..BlePacketCodec.STATUS_DELIVERED) {
            scheduler?.cancelOrder(packet.orderId)
        }

        val encoded = BlePacketCodec.encode(packet, meshKey)
        BleMeshService.start(context, lowLatency = true)

        scope.launch {
            val tx = ensureTx(context) ?: return@launch
            tx.enqueue(
                BleTxScheduler.TxItem(
                    id = "status:${packet.orderId}:${packet.statusCode}",
                    payload23 = encoded,
                    remainingSlices = slices,
                    orderShortId = packet.orderId,
                    isOriginOrder = false,
                ),
            )
            _stats.update { it.copy(packetsSent = it.packetsSent + 1) }
        }
        return true
    }

    /**
     * Broadcasts a `QUICK_MSG (0x13)` preset packet (`ttl = 6`, `15s`) into the BLE mesh.
     */
    fun sendQuickMsg(
        context: Context,
        packet: BlePacketCodec.QuickMsg,
        meshKey: String = BlePacketCodec.RAILBITE_MESH_KEY,
        slices: Int = BleTxScheduler.STATUS_OR_MSG_SLICES,
    ): Boolean {
        if (!BlePermissions.isReadyForMesh(context)) {
            refreshRadioState(context)
            return false
        }
        dedupCache.markSeen(packet.dedupKey)
        recordQuickMessage(packet)

        val encoded = BlePacketCodec.encode(packet, meshKey)
        BleMeshService.start(context, lowLatency = true)

        scope.launch {
            val tx = ensureTx(context) ?: return@launch
            tx.enqueue(
                BleTxScheduler.TxItem(
                    id = "msg:${packet.orderId}:${packet.senderRole}:${packet.msgSeq}",
                    payload23 = encoded,
                    remainingSlices = slices,
                    orderShortId = packet.orderId,
                    isOriginOrder = false,
                ),
            )
            _stats.update { it.copy(packetsSent = it.packetsSent + 1) }
        }
        return true
    }

    /**
     * Pipeline run for every scanned 23-byte Manufacturer Data `0x5242` frame:
     * 1. Validate `hmac16` and structure via [BlePacketCodec.decode]
     * 2. Filter duplicates via [dedupCache] (`500` entries, `10` min TTL)
     * 3. Update local state & notify observers
     * 4. If [relayEnabled] and `ttl > 0`: increment `hopCount`, decrement `ttl`, recompute `hmac16`,
     *    wait random `150..600ms` jitter, and enqueue for `4` transmit slices (`~6s`).
     */
    fun onFrameScanned(
        context: Context,
        rawBytes: ByteArray,
        rssi: Int,
        meshKey: String = BlePacketCodec.RAILBITE_MESH_KEY,
        relayEnabled: Boolean = true,
    ): Boolean {
        val packet = BlePacketCodec.decode(rawBytes, meshKey) ?: return false
        if (!dedupCache.offer(packet.dedupKey)) return false

        _stats.update {
            it.copy(
                packetsReceived = it.packetsReceived + 1,
                lastRssiDbm = rssi,
                lastHopCount = packet.hopCount,
            )
        }

        when (packet) {
            is BlePacketCodec.OrderNew -> {
                _seenOrders.update { it + (packet.orderId to packet) }
            }
            is BlePacketCodec.StatusAck -> {
                recordStatusMonotonic(packet)
                if (packet.statusCode in BlePacketCodec.STATUS_ACCEPTED..BlePacketCodec.STATUS_DELIVERED) {
                    scheduler?.cancelOrder(packet.orderId)
                }
            }
            is BlePacketCodec.QuickMsg -> {
                recordQuickMessage(packet)
            }
        }

        _incoming.tryEmit(IncomingBleEvent(packet = packet, rssi = rssi))

        val isLocalOriginOrder = packet is BlePacketCodec.OrderNew &&
            synchronized(locallyOriginatedOrders) { packet.orderId in locallyOriginatedOrders }

        if (relayEnabled && !isLocalOriginOrder) {
            val relayed = BlePacketCodec.forRelay(packet)
            if (relayed != null) {
                val relayedBytes = BlePacketCodec.encode(relayed, meshKey)
                val jitterMs = Random.nextLong(150L, 601L)
                scope.launch {
                    delay(jitterMs)
                    val tx = scheduler ?: ensureTx(context) ?: return@launch
                    tx.enqueue(
                        BleTxScheduler.TxItem(
                            id = "relay:${packet.dedupKey}",
                            payload23 = relayedBytes,
                            remainingSlices = BleTxScheduler.RELAY_SLICES,
                            orderShortId = packet.orderId,
                            isOriginOrder = false,
                        ),
                    )
                    _stats.update { it.copy(packetsRelayed = it.packetsRelayed + 1) }
                }
            }
        }
        return true
    }

    /** Clears active status history tracking when resetting test state. */
    internal fun resetForTest() {
        dedupCache.clear()
        synchronized(locallyOriginatedOrders) { locallyOriginatedOrders.clear() }
        _latestStatuses.value = emptyMap()
        _quickMessages.value = emptyMap()
        _seenOrders.value = emptyMap()
        _stats.value = MeshStatsSnapshot()
    }

    private fun recordStatusMonotonic(packet: BlePacketCodec.StatusAck) {
        _latestStatuses.update { map ->
            val current = map[packet.orderId]
            val curRank = current?.let { statusRank[it.statusCode] ?: 0 } ?: -1
            val newRank = statusRank[packet.statusCode] ?: 0
            if (current == null || newRank >= curRank) {
                map + (packet.orderId to packet)
            } else {
                map
            }
        }
    }

    private fun recordQuickMessage(packet: BlePacketCodec.QuickMsg) {
        _quickMessages.update { map ->
            val current = map[packet.orderId].orEmpty()
            val exists = current.any {
                it.senderRole == packet.senderRole && it.msgSeq == packet.msgSeq
            }
            if (exists) map else map + (packet.orderId to (current + packet).sortedBy { it.msgSeq })
        }
    }

    private suspend fun ensureTx(context: Context): BleTxScheduler? {
        scheduler?.let { return it }
        // Wait briefly for BleMeshService.onCreate() after start()
        repeat(15) {
            delay(100)
            scheduler?.let { return it }
        }
        return scheduler
    }
}

