package com.railbite.picker.mesh

import android.util.Log
import com.railbite.core.api.ApiClient
import com.railbite.core.model.CaptainActionRequest
import com.railbite.core.model.DeliverRequest
import com.railbite.core.mesh.BleMeshTransport
import com.railbite.core.mesh.MeshCodec
import com.railbite.core.mesh.MeshEntry
import com.railbite.core.mesh.MeshLedger
import com.railbite.core.mesh.MeshOrderPayload
import com.railbite.core.mesh.MeshPacket
import com.railbite.core.mesh.MeshRelay
import com.railbite.core.mesh.MeshTrip
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.model.OrderCreateRequest
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
import java.time.Instant

/**
 * Pantry side of the Bluetooth relay. While the picker is ONLINE (and the sink is on)
 * this scans for mesh packets; every completed packet is verified + relayed by
 * [MeshRelay] — but the sink consumes orders instead of forwarding them:
 *
 *  1. map the payload to an [MqttOrderAlert] and post it to [AlertBus] — the existing
 *     loud full-screen alert path lights up unchanged (marked "via Bluetooth relay")
 *  2. persist the payload in the ledger as PENDING
 *
 * Outcomes recorded offline (ACCEPTED / DELIVERED + code) replay to the cloud as a
 * create → accept → deliver sequence whenever the server is reachable again; the cloud
 * then owns the record and the customer's status timeline flows over MQTT as usual.
 */
class MeshGateway(
    private val app: RailBitePickerApp,
    private val transport: BleMeshTransport,
    private val ledger: MeshLedger,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var relay: MeshRelay? = null
    private var replayJob: Job? = null

    fun start() {
        if (relay != null) return
        scope.launch {
            val selfId = app.container.settings.ensureMeshNodeId()
            relay = MeshRelay(
                selfId = selfId,
                // The pantry phone consumes ORDER packets; STATUS packets (pantry
                // outcomes for passengers) are forwarded to help them flood the rake.
                isSink = { it.type == MeshPacket.TYPE_ORDER },
                hmacSecret = { MeshTrip.SECRET },
                forward = { pkt ->
                    // Not a sink for this packet type — re-broadcast it with the
                    // transport's burst (MeshRelay already decremented TTL/hops).
                    transport.advertiseBurst(pkt, seconds = 5)
                },
                onSink = { packet -> handleSink(packet) },
            )
            transport.startScanning { packet ->
                // BLE binder thread → hop into our scope
                scope.launch { relay?.receive(packet) }
            }
            startReplayLoop()
        }
    }

    fun stop() {
        transport.stopScanning()
        relay = null
        replayJob?.cancel()
        replayJob = null
    }

    private fun handleSink(packet: MeshPacket) {
        val bytes = MeshCodec.b64Decode(packet.payloadB64) ?: return
        val order = MeshOrderPayload.decode(String(bytes, Charsets.UTF_8)) ?: return
        scope.launch {
            // Dual-path dedupe: when the passenger had internet, the order already
            // exists in the cloud and its FCM alert already fired — don't ring twice.
            if (cloudHasOrder(order.orderId)) {
                Log.i(TAG, "mesh order ${order.orderId} already in cloud — duplicate alert skipped")
                return@launch
            }
            ledger.add(MeshEntry(payload = order.encode(), createdAt = order.createdAt))
            val alert = order.toAlert()
            AlertBus.post(alert, source = AlertBus.SOURCE_MESH)
            Notifications.notifyAlert(app, alert)
        }
    }

    /**
     * True when the cloud already knows this order. A ~1.5 s reachability probe
     * first, so an offline sink alerts immediately instead of stalling on a
     * doomed request.
     */
    private suspend fun cloudHasOrder(orderId: String): Boolean {
        val serverUrl = app.container.settings.serverUrl.first()
        if (serverUrl.isBlank()) return false
        if (!ApiClient.reachable(serverUrl, timeoutMs = 1_500)) return false
        return ApiClient.orderExists(serverUrl, orderId)
    }

    /**
     * Broadcasts a pantry outcome over the mesh so the passenger's timeline
     * advances fully offline ([MeshPacket.TYPE_STATUS] floods back through relay
     * phones; the passenger's app consumes them and maps them onto the timeline).
     * Called on accept / handover open / delivery.
     */
    fun broadcastStatus(orderId: String, state: String) {
        scope.launch {
            val packet = MeshCodec.sign(
                MeshPacket(
                    type = MeshPacket.TYPE_STATUS,
                    orderId = orderId,
                    ttl = MeshPacket.INITIAL_TTL,
                    origin = app.container.settings.ensureMeshNodeId(),
                    payloadB64 = MeshCodec.b64Encode(state.toByteArray()),
                    tag = state,
                    hmac8 = "",
                ),
                MeshTrip.SECRET,
            )
            // Two spaced bursts: BLE delivery is opportunistic, hearing it once is enough.
            repeat(2) { i ->
                transport.advertiseBurst(packet, seconds = 6)
                if (i == 0) delay(10_000)
            }
        }
    }

    private fun MeshOrderPayload.toAlert() = MqttOrderAlert(
        orderId = orderId,
        coach = coach,
        berth = berth,
        items = items.map { MqttOrderAlert.AlertItem(it.name, it.qty, it.unitPricePaise) },
        total = totalPaise,
        track = "READY",
        attempt = 1,
        expiresAt = Instant.now().plusSeconds(60).toString(),
        pnr = pnr,
        diners = diners,
        verified = 1,
    )

    /** Periodically replays ledger outcomes to the cloud while the service runs. */
    private fun startReplayLoop() {
        replayJob?.cancel()
        replayJob = scope.launch {
            while (isActive) {
                runCatching { replayPending() }
                delay(120_000) // every 2 min
            }
        }
    }

    /** One replay pass; returns the number of ledger entries synced. */
    suspend fun replayPending(): Int {
        val container = app.container
        val serverUrl = container.settings.serverUrl.first()
        val staffId = container.settings.staffId.first()
        if (serverUrl.isBlank() || staffId.isBlank()) return 0
        // Reachability probe — treat "offline" as nothing to do this round.
        if (runCatching { ApiClient.testConnection(serverUrl) }.isFailure) return 0

        val api = container.client.api()
        var synced = 0
        // REJECTED relay orders are dropped entirely — the cloud never learns about them.
        val pending = ledger.all().filter { it.state != "SYNCED" && it.state != "REJECTED" }
        for (entry in pending) {
            val order = MeshOrderPayload.decode(entry.payload) ?: continue
            try {
                // create (idempotent by client UUID) → accept → deliver (embedded code)
                api.createOrder(order.toRequest())
                runCatching { api.acceptOrder(order.orderId, CaptainActionRequest(staffId)) }
                if (entry.state == "DELIVERED") {
                    api.deliverOrder(
                        order.orderId,
                        DeliverRequest(captainId = staffId, deliveryCode = order.deliveryCode),
                    )
                }
                ledger.markState(order.orderId, "SYNCED")
                synced++
            } catch (e: Exception) {
                Log.w(TAG, "mesh replay failed for ${order.orderId}: ${e.message}")
            }
        }
        return synced
    }

    private fun MeshOrderPayload.toRequest() = OrderCreateRequest(
        id = orderId,
        pnr = pnr,
        coach = coach,
        berth = berth,
        diners = diners,
        paymentMode = "COD",
        items = items.map { OrderCreateRequest.CreateItem(it.itemId, it.qty) },
        createdAt = createdAt,
        source = "RELAY", // matches the shared schema enum: WIFI | SMS | RELAY
    )

    companion object {
        private const val TAG = "MeshGateway"
    }
}
