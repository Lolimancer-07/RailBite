package com.railbite.picker.mesh

import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.railbite.core.api.ApiClient
import com.railbite.core.data.OfflineMenuCatalog
import com.railbite.core.mesh.BleMeshHub
import com.railbite.core.mesh.BleMeshService
import com.railbite.core.mesh.BleNearestArbitrator
import com.railbite.core.mesh.BlePacketCodec
import com.railbite.core.mesh.BlePermissions
import com.railbite.core.mesh.MeshEntry
import com.railbite.core.mesh.MeshLedger
import com.railbite.core.mesh.MeshOrderPayload
import com.railbite.core.mesh.TransportRouter
import com.railbite.core.model.MeshOrderSyncRequest
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.model.OrderCreateRequest
import com.railbite.core.util.Statuses
import com.railbite.picker.RailBitePickerApp
import com.railbite.picker.alert.AlertBus
import com.railbite.picker.alert.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Pantry-side coordinator for the 23-byte BLE multi-hop flood mesh:
 * - Listens for `ORDER_NEW (0x11)` frames via [BleMeshHub] while shift is ONLINE
 * - Runs Nearest-Picker Arbitration ([BleNearestArbitrator]) before ringing [AlertBus] / [Notifications]
 * - Auto-dismisses pending/ringing alerts when another picker claims the order (`0x12 ACCEPTED+`)
 * - Broadcasts `STATUS_ACK (0x12)` & preset `QUICK_MSG (0x13)` frames (`ttl = 6`, `15s`)
 * - Syncs offline-worked orders idempotently via `POST /orders/mesh-sync` when LAN returns.
 */
class MeshGateway(
    private val app: RailBitePickerApp,
    private val ledger: MeshLedger,
    private val transportRouter: TransportRouter,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectorJob: Job? = null
    private var replayJob: Job? = null

    @Volatile
    private var dispatchActive = false

    private val pendingArbitration = ConcurrentHashMap<UInt, Job>()
    private val seenPackets = ConcurrentHashMap<UInt, BlePacketCodec.OrderNew>()

    fun start(dispatchAlerts: Boolean = true) {
        dispatchActive = dispatchAlerts
        if (BlePermissions.isReadyForMesh(app)) {
            BleMeshService.start(app, lowLatency = dispatchAlerts)
        }
        ensureCollector()
        startReplayLoop()
    }

    fun stop() {
        dispatchActive = false
        pendingArbitration.values.forEach { it.cancel() }
        pendingArbitration.clear()
        replayJob?.cancel()
        replayJob = null
        // Downgrade scanning to balanced relay or stop gracefully
        if (BlePermissions.isReadyForMesh(app)) {
            scope.launch {
                val relayEnabled = runCatching { app.container.settings.meshRelayEnabled.first() }.getOrDefault(false)
                if (relayEnabled) {
                    BleMeshService.start(app, lowLatency = false)
                } else {
                    BleMeshService.stop(app)
                }
            }
        }
    }

    private fun ensureCollector() {
        if (collectorJob?.isActive == true) return
        collectorJob = scope.launch {
            BleMeshHub.incoming.collect { event ->
                when (val pkt = event.packet) {
                    is BlePacketCodec.OrderNew -> handleIncomingOrder(pkt, event.rssi)
                    is BlePacketCodec.StatusAck -> handleIncomingStatusAck(pkt)
                    is BlePacketCodec.QuickMsg -> Unit // Observed live by HomeViewModel / OrderProgressViewModel
                }
            }
        }
    }

    private fun handleIncomingOrder(pkt: BlePacketCodec.OrderNew, rssi: Int) {
        if (!dispatchActive) return
        seenPackets[pkt.orderId] = pkt

        // Cancel if another picker already broadcast ACCEPTED+ for this order
        val knownStatus = BleMeshHub.latestStatuses.value[pkt.orderId]
        if (knownStatus != null &&
            knownStatus.statusCode in BlePacketCodec.STATUS_ACCEPTED..BlePacketCodec.STATUS_DELIVERED
        ) {
            return
        }

        // Replace any older arbitration wait for this order ID
        pendingArbitration[pkt.orderId]?.let { if (it.isActive) return }

        val job = scope.launch {
            try {
                val existing = ledger.entryOf(pkt.orderUuid)
                if (existing != null && existing.state != "PENDING") {
                    // Already accepted, delivered, rejected locally, or synced
                    return@launch
                }

                val staffZone = app.container.settings.staffZone.first()
                val delayMs = BleNearestArbitrator.delayMillis(
                    orderCoach = pkt.coach,
                    staffZone = staffZone,
                    hopCount = pkt.hopCount,
                    rssiDbm = rssi,
                )
                if (delayMs > 0L) {
                    delay(delayMs)
                }

                // Re-check after arbitration backoff: did another picker claim it or did we go OFFLINE?
                if (!dispatchActive) return@launch
                val postDelayStatus = BleMeshHub.latestStatuses.value[pkt.orderId]
                if (postDelayStatus != null &&
                    postDelayStatus.statusCode in BlePacketCodec.STATUS_ACCEPTED..BlePacketCodec.STATUS_DELIVERED
                ) {
                    return@launch
                }
                val rechecked = ledger.entryOf(pkt.orderUuid)
                if (rechecked != null && rechecked.state != "PENDING") {
                    return@launch
                }

                // Dual-path dedupe: if LAN is fast-reachable and server already owns it, skip double ringing.
                if (cloudAlreadyHasOrder(pkt.orderUuid)) {
                    Log.i(TAG, "mesh order ${pkt.orderUuid} already exists on LAN server — skipping BLE ring")
                    return@launch
                }

                val orderPayload = decodeToPayload(pkt)
                ledger.addIfAbsent(
                    orderId = orderPayload.orderId,
                    entry = MeshEntry(
                        payload = orderPayload.encode(),
                        createdAt = orderPayload.createdAt,
                        state = "PENDING",
                        deliveryCode = orderPayload.deliveryCode,
                        cashPaise = orderPayload.totalPaise,
                        hopCount = pkt.hopCount,
                        lastRssi = rssi,
                    ),
                )

                val alert = orderPayload.toDisplayAlert(pkt.pnrLast4)
                val normTrain = com.railbite.core.util.Validators.normalizeTrainNumber(alert.trainNumber)
                Log.i(
                    "RailBiteTrainMatch",
                    "Incoming orderId=${alert.orderId} incomingTrain=$normTrain activeTrain=$normTrain MATCH",
                )
                AlertBus.post(alert, source = AlertBus.SOURCE_MESH, hopCount = pkt.hopCount)
                Notifications.notifyAlert(app, alert, hopCount = pkt.hopCount)
                Notifications.launchIncomingActivity(app, alert.orderId)
            } finally {
                pendingArbitration.remove(pkt.orderId)
            }
        }
        pendingArbitration[pkt.orderId] = job
    }

    private suspend fun handleIncomingStatusAck(pkt: BlePacketCodec.StatusAck) {
        if (pkt.statusCode !in BlePacketCodec.STATUS_ACCEPTED..BlePacketCodec.STATUS_DELIVERED) return

        val existing = ledger.entryOf(pkt.orderUuid)
        val ownedByMe = existing != null && existing.state in OWNED_STATES
        if (ownedByMe) return

        // Another picker won arbitration — cancel local pending wait and dismiss any active ring!
        pendingArbitration.remove(pkt.orderId)?.cancel()
        if (existing != null && existing.state == "PENDING") {
            ledger.markState(pkt.orderUuid, "REJECTED")
        }
        if (AlertBus.dismissIfOrder(pkt.orderUuid)) {
            runCatching {
                NotificationManagerCompat.from(app).cancel(Notifications.ALERT_NOTIFICATION_ID)
            }
        }
    }

    private suspend fun cloudAlreadyHasOrder(orderId: String): Boolean {
        if (!transportRouter.checkLanReachable()) return false
        val serverUrl = app.container.settings.serverUrl.first()
        if (serverUrl.isBlank()) return false
        return ApiClient.orderExists(serverUrl, orderId, app.container.authStore.accessToken())
    }

    private suspend fun decodeToPayload(pkt: BlePacketCodec.OrderNew): MeshOrderPayload {
        val decodedItems = pkt.items.map { slot ->
            val resolved = OfflineMenuCatalog.resolveByNumericId(app, app.container.settings, slot.itemId)
            MeshOrderPayload.Item(
                itemId = resolved.id,
                name = resolved.name,
                qty = slot.qty,
                unitPricePaise = resolved.mrpPaise,
            )
        }
        val total = decodedItems.sumOf { it.unitPricePaise * it.qty }
        val pinStr = BlePacketCodec.formatPin4(pkt.handoverPin)
        val syntheticPnr10 = "000000${pkt.pnrLast4.toString().padStart(4, '0')}"
        val activeTrain = com.railbite.core.util.Validators.normalizeTrainNumber(
            app.container.settings.activeTrainNumber.first(),
        ).ifBlank { com.railbite.core.mesh.BlePacketCodec.DEFAULT_TRAIN_NUMBER }
        return MeshOrderPayload(
            orderId = pkt.orderUuid,
            trainNumber = activeTrain,
            pnr = syntheticPnr10,
            coach = pkt.coach.trim().uppercase(),
            berth = pkt.seat,
            diners = 1,
            createdAt = Instant.now().toString(),
            deliveryCode = pinStr,
            items = decodedItems,
            totalPaise = total,
        )
    }

    private fun MeshOrderPayload.toDisplayAlert(pnrLast4: Int) = MqttOrderAlert(
        orderId = orderId,
        trainNumber = trainNumber,
        coach = coach,
        berth = berth,
        items = items.map { MqttOrderAlert.AlertItem(it.name, it.qty, it.unitPricePaise) },
        total = totalPaise,
        track = "READY",
        attempt = 1,
        expiresAt = Instant.now().plusSeconds(60).toString(),
        pnr = "••${pnrLast4.toString().padStart(4, '0')}",
        diners = diners,
        verified = 1,
    )

    /**
     * Broadcasts `STATUS_ACK (0x12)` into the 23-byte BLE mesh (`ttl = 6`, `15s`) so the passenger's
     * timeline advances in real time (`ACCEPTED`, `PACKING`, `OUT_FOR_DELIVERY`, `DELIVERED`, or `REJECTED`).
     */
    fun broadcastStatus(
        orderId: String,
        state: String,
        etaMins: Int = 8,
        cashPaise: Int? = null,
    ) {
        val statusCode = BlePacketCodec.codeFromStatus(state) ?: return
        scope.launch {
            val shortId = BlePacketCodec.uuidToShortId(orderId)
            val cachedPkt = seenPackets[shortId]
            val ledgerEntry = ledger.entryOf(orderId)
            val payload = ledgerEntry?.let { MeshOrderPayload.decode(it.payload) }
            val staffId = app.container.settings.staffId.first().ifBlank { "c1" }
            val meshKey = app.container.settings.meshKey.first()
            val trainNumber = payload?.trainNumber
                ?.let { com.railbite.core.util.Validators.normalizeTrainNumber(it) }
                ?.takeIf { com.railbite.core.util.Validators.isValidTrainNumber(it) }
                ?: com.railbite.core.util.Validators
                    .normalizeTrainNumber(app.container.settings.activeTrainNumber.first())
                    .takeIf { com.railbite.core.util.Validators.isValidTrainNumber(it) }
                ?: BlePacketCodec.DEFAULT_TRAIN_NUMBER

            val packet = BlePacketCodec.StatusAck(
                orderId = shortId,
                hopCount = 0,
                ttl = BlePacketCodec.DEFAULT_TTL,
                coach = payload?.coach ?: cachedPkt?.coach ?: "B1",
                seat = payload?.berth ?: cachedPkt?.seat ?: 1,
                pnrLast4 = payload?.let { BlePacketCodec.parsePnrLast4(it.pnr) } ?: cachedPkt?.pnrLast4 ?: 0,
                handoverPin = payload?.let { BlePacketCodec.parsePinInt(it.deliveryCode) } ?: cachedPkt?.handoverPin ?: 0,
                statusCode = statusCode,
                pickerId = staffId,
                etaMins = etaMins,
                cashRs = ((cashPaise ?: payload?.totalPaise ?: 0) / 100).coerceAtLeast(0),
            )
            BleMeshHub.sendStatusAck(app, packet, meshKey, trainNumber = trainNumber)
        }
    }

    private fun startReplayLoop() {
        replayJob?.cancel()
        replayJob = scope.launch {
            while (isActive) {
                runCatching { replayPending() }
                delay(120_000)
            }
        }
    }

    /** Idempotent LAN replay for all BLE orders accepted/packed/delivered by THIS picker. */
    suspend fun replayPending(): Int {
        val staffId = app.container.settings.staffId.first()
        if (staffId.isBlank()) return 0
        if (!transportRouter.checkLanReachable()) return 0

        val api = app.container.client.api()
        var synced = 0
        val pending = ledger.all().filter { it.state in OWNED_STATES }
        for (entry in pending) {
            val order = MeshOrderPayload.decode(entry.payload) ?: continue
            val ok = runCatching {
                api.syncMeshOrder(
                    MeshOrderSyncRequest(
                        id = order.orderId,
                        trainNumber = order.trainNumber,
                        pnr = order.pnr,
                        coach = order.coach,
                        berth = order.berth,
                        diners = order.diners,
                        paymentMode = "COD",
                        items = order.items.map { OrderCreateRequest.CreateItem(it.itemId, it.qty) },
                        createdAt = order.createdAt,
                        status = entry.state,
                        captainId = entry.captainId ?: staffId,
                        deliveryCode = entry.deliveryCode ?: order.deliveryCode,
                        cashPaise = entry.cashPaise ?: order.totalPaise,
                        passengerConfirmed = entry.passengerConfirmed,
                    ),
                )
                true
            }.getOrElse { err ->
                val code = (err as? HttpException)?.code()
                if (code == 404 || code == 405) {
                    runCatching {
                        api.createOrder(
                            OrderCreateRequest(
                                id = order.orderId,
                                trainNumber = order.trainNumber,
                                pnr = order.pnr,
                                coach = order.coach,
                                berth = order.berth,
                                diners = order.diners,
                                paymentMode = "COD",
                                items = order.items.map { OrderCreateRequest.CreateItem(it.itemId, it.qty) },
                                createdAt = order.createdAt,
                                source = "RELAY",
                            ),
                        )
                        true
                    }.getOrDefault(false)
                } else {
                    Log.w(TAG, "mesh sync failed for ${order.orderId}: ${err.message}")
                    false
                }
            }
            if (ok) {
                ledger.markState(order.orderId, "SYNCED")
                synced++
            }
        }
        return synced
    }

    companion object {
        private const val TAG = "MeshGateway"
        private val OWNED_STATES = setOf(
            Statuses.ACCEPTED,
            Statuses.PACKING,
            Statuses.OUT_FOR_DELIVERY,
            Statuses.DELIVERED,
        )
    }
}
