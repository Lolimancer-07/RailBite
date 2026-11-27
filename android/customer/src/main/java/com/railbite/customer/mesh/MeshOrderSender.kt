package com.railbite.customer.mesh

import com.railbite.core.data.OfflineMenuCatalog
import com.railbite.core.mesh.BleMeshHub
import com.railbite.core.mesh.BleMeshService
import com.railbite.core.mesh.BlePacketCodec
import com.railbite.core.mesh.BlePermissions
import com.railbite.core.mesh.MeshEntry
import com.railbite.core.mesh.MeshOrderPayload
import com.railbite.core.mesh.MeshOutbox
import com.railbite.core.model.MeshOrderSyncRequest
import com.railbite.core.model.OrderCreateRequest
import com.railbite.core.util.Statuses
import com.railbite.customer.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant

/**
 * Customer-side coordinator for the 23-byte BLE multi-hop flood mesh:
 * - Originates binary `ORDER_NEW (0x11)` broadcasts via [BleMeshHub] & [BleMeshService]
 * - Listens continuously for incoming `STATUS_ACK (0x12)` & `QUICK_MSG (0x13)` frames
 *   even after screen transitions
 * - Broadcasts preset `QUICK_MSG (0x13)` replies when LAN is unreachable
 * - Syncs completed & in-flight offline BLE orders idempotently when LAN connectivity returns.
 */
class MeshOrderSender(private val container: AppContainer) {

    private val outbox = MeshOutbox(container.app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectorJob: Job? = null

    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    val entriesFlow: Flow<List<MeshEntry>> = outbox.entries

    suspend fun refreshPending() {
        _pending.value = outbox.pendingCount()
    }

    suspend fun relayEntry(orderId: String): MeshEntry? = outbox.entryOf(orderId)

    /** Observe live status/ack updates persisted for a specific offline order. */
    fun observeEntry(orderId: String): Flow<MeshEntry?> =
        outbox.entries.map { list -> list.firstOrNull { it.payload.contains("\"orderId\":\"$orderId\"") } }

    /** Creates an offline BLE order, saves to [MeshOutbox], and starts `0x11` mesh broadcast. */
    suspend fun send(
        trainNumber: String,
        pnr: String,
        coach: String,
        berth: Int,
        diners: Int,
        items: List<MeshOrderPayload.Item>,
        totalPaise: Int,
    ): MeshOrderPayload? {
        val slots = items.take(BlePacketCodec.MAX_ORDER_ITEMS).mapNotNull { item ->
            val numericId = OfflineMenuCatalog.numericIdFor(container.app, item.itemId, item.name)
                ?: return@mapNotNull null
            BlePacketCodec.ItemSlot(itemId = numericId, qty = item.qty)
        }
        if (slots.isEmpty()) return null

        val shortOrderId = BlePacketCodec.generateShortOrderId()
        val orderId = BlePacketCodec.shortIdToUuid(shortOrderId)
        val handoverPin = (1000..9999).random()
        val pinString = BlePacketCodec.formatPin4(handoverPin)

        val payload = MeshOrderPayload(
            orderId = orderId,
            trainNumber = trainNumber.trim(),
            pnr = pnr,
            coach = coach.trim().uppercase(),
            berth = berth,
            diners = diners,
            createdAt = Instant.now().toString(),
            deliveryCode = pinString,
            items = items.take(BlePacketCodec.MAX_ORDER_ITEMS),
            totalPaise = totalPaise,
        )

        val packet = BlePacketCodec.OrderNew(
            orderId = shortOrderId,
            hopCount = 0,
            ttl = BlePacketCodec.DEFAULT_TTL,
            coach = coach.trim().uppercase(),
            seat = berth,
            pnrLast4 = BlePacketCodec.parsePnrLast4(pnr),
            handoverPin = handoverPin,
            items = slots,
        )

        val meshKey = container.settings.meshKey.first()
        val sent = BleMeshHub.sendOrderNew(container.app, packet, meshKey)
        if (!sent) return null

        outbox.add(
            MeshEntry(
                payload = payload.encode(),
                createdAt = payload.createdAt,
                state = "PENDING",
                ack = Statuses.BROADCASTING,
            ),
        )
        refreshPending()
        startLoop()
        return payload
    }

    /** Persists passenger's offline cash confirmation so LAN sync sends `passengerConfirmed=true`. */
    suspend fun markCashConfirmed(orderId: String) {
        outbox.markPassengerConfirmed(orderId)
    }

    /** Re-enqueues `0x11` broadcast for an existing offline order still in `BROADCASTING`. */
    suspend fun retryBroadcast(orderId: String): Boolean {
        val entry = outbox.entryOf(orderId) ?: return false
        val payload = MeshOrderPayload.decode(entry.payload) ?: return false
        val slots = payload.items.take(BlePacketCodec.MAX_ORDER_ITEMS).mapNotNull { item ->
            val numericId = OfflineMenuCatalog.numericIdFor(container.app, item.itemId, item.name)
                ?: return@mapNotNull null
            BlePacketCodec.ItemSlot(itemId = numericId, qty = item.qty)
        }
        if (slots.isEmpty()) return false
        val shortId = BlePacketCodec.uuidToShortId(payload.orderId)
        val packet = BlePacketCodec.OrderNew(
            orderId = shortId,
            hopCount = 0,
            ttl = BlePacketCodec.DEFAULT_TTL,
            coach = payload.coach,
            seat = payload.berth,
            pnrLast4 = BlePacketCodec.parsePnrLast4(payload.pnr),
            handoverPin = BlePacketCodec.parsePinInt(payload.deliveryCode),
            items = slots,
        )
        val meshKey = container.settings.meshKey.first()
        val ok = BleMeshHub.sendOrderNew(container.app, packet, meshKey)
        if (ok) outbox.markAck(orderId, Statuses.BROADCASTING)
        return ok
    }

    /** Stops origin BLE broadcast when user cancels an unanswered offline order. */
    suspend fun cancelOfflineOrder(orderId: String) {
        BleMeshHub.cancelOrderBroadcast(BlePacketCodec.uuidToShortId(orderId))
        outbox.markState(orderId, "CANCELLED")
        refreshPending()
    }

    /** Observes incoming BLE `STATUS_ACK` packets to keep [MeshOutbox] up-to-date in the background. */
    fun startLoop() {
        if (collectorJob?.isActive == true) return
        collectorJob = scope.launch {
            // Re-arm BLE foreground listener cleanly whenever relay is enabled & radio is ready
            if (container.settings.meshRelayEnabled.first() && BlePermissions.isReadyForMesh(container.app)) {
                val activeId = container.settings.activeOrderId.first()
                BleMeshService.start(container.app, lowLatency = activeId.isNotBlank())
            }
            BleMeshHub.incoming.collect { event ->
                val pkt = event.packet
                if (pkt is BlePacketCodec.StatusAck) {
                    val status = BlePacketCodec.statusFromCode(pkt.statusCode)
                    outbox.markAck(
                        orderId = pkt.orderUuid,
                        ack = status,
                        captainId = pkt.pickerId,
                        etaMin = pkt.etaMins.takeIf { it > 0 },
                    )
                    if (pkt.statusCode in BlePacketCodec.STATUS_ACCEPTED..BlePacketCodec.STATUS_DELIVERED) {
                        BleMeshHub.cancelOrderBroadcast(pkt.orderId)
                    }
                    if (status == Statuses.DELIVERED) {
                        container.settings.setActiveOrderId("")
                        BleMeshService.start(container.app, lowLatency = false)
                    }
                    refreshPending()
                }
            }
        }
    }

    /** Replays pending / BLE-completed orders idempotently when LAN returns. */
    suspend fun syncPending(): Int {
        if (_syncing.value) return 0
        _syncing.value = true
        var synced = 0
        try {
            if (!container.transportRouter.checkLanReachable()) return 0
            val api = container.client.api()
            val pendingList = outbox.all().filter {
                it.state != "SYNCED" && it.state != "CANCELLED" && it.state != "REJECTED"
            }
            for (entry in pendingList) {
                val payload = MeshOrderPayload.decode(entry.payload) ?: continue
                val syncStatus = when (entry.ack) {
                    Statuses.ACCEPTED,
                    Statuses.PACKING,
                    Statuses.OUT_FOR_DELIVERY,
                    Statuses.DELIVERED -> entry.ack!!
                    else -> Statuses.PLACED
                }
                val ok = runCatching {
                    api.syncMeshOrder(
                        MeshOrderSyncRequest(
                            id = payload.orderId,
                            trainNumber = payload.trainNumber,
                            pnr = payload.pnr,
                            coach = payload.coach,
                            berth = payload.berth,
                            diners = payload.diners,
                            paymentMode = "COD",
                            items = payload.items.map { OrderCreateRequest.CreateItem(it.itemId, it.qty) },
                            createdAt = payload.createdAt,
                            status = syncStatus,
                            captainId = entry.captainId,
                            deliveryCode = payload.deliveryCode,
                            cashPaise = payload.totalPaise,
                            passengerConfirmed = entry.passengerConfirmed,
                        ),
                    )
                    true
                }.getOrElse { err ->
                    val code = (err as? HttpException)?.code()
                    if (code == 404 || code == 405) {
                        // Backwards-compatible fallback for older servers without /orders/mesh-sync
                        runCatching {
                            api.createOrder(
                                OrderCreateRequest(
                                    id = payload.orderId,
                                    trainNumber = payload.trainNumber,
                                    pnr = payload.pnr,
                                    coach = payload.coach,
                                    berth = payload.berth,
                                    diners = payload.diners,
                                    paymentMode = "COD",
                                    items = payload.items.map { OrderCreateRequest.CreateItem(it.itemId, it.qty) },
                                    createdAt = payload.createdAt,
                                    source = "RELAY",
                                ),
                            )
                            true
                        }.getOrDefault(false)
                    } else {
                        false
                    }
                }
                if (ok) {
                    outbox.markState(payload.orderId, "SYNCED")
                    synced++
                }
            }
            refreshPending()
        } finally {
            _syncing.value = false
        }
        return synced
    }
}
