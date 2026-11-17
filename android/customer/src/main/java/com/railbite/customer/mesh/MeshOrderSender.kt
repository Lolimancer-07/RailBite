package com.railbite.customer.mesh

import com.railbite.customer.AppContainer
import com.railbite.core.api.ApiClient
import com.railbite.core.mesh.MeshCodec
import com.railbite.core.mesh.MeshEntry
import com.railbite.core.mesh.MeshOrderPayload
import com.railbite.core.mesh.MeshOutbox
import com.railbite.core.mesh.MeshPacket
import com.railbite.core.mesh.MeshRelay
import com.railbite.core.mesh.MeshTrip
import com.railbite.core.model.OrderCreateRequest
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
import java.time.Instant
import java.util.UUID

/**
 * Passenger side of the Bluetooth relay (offline feature):
 *  - "send" persists the order in the outbox and bursts it over BLE advertisements —
 *    nearby phones relay it until the pantry phone (sink) consumes it
 *  - a background duty cycle ([startLoop]) keeps re-broadcasting pending orders until
 *    the pantry confirms, and listens for STATUS announcements — the pantry app is NOT
 *    guaranteed to be scanning at the exact second the order is sent, so a single
 *    burst loses orders (field-observed 2026-11-17: "the alert never rang")
 *  - "sync" runs whenever the cloud is reachable again and POSTs pending relay orders
 *    with their original client UUID (idempotent); the cloud then owns dispatch/status
 *
 * The relay never replaces the cloud path — it only exists when the cloud can't be
 * reached and the relay toggle is on (plus dual-path bursts for instant alerts, see
 * [burstOnly]).
 */
class MeshOrderSender(private val container: AppContainer) {

    private val outbox = MeshOutbox(container.app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    suspend fun refreshPending() {
        _pending.value = outbox.pendingCount()
    }

    /** Tracking's offline view: the queued relay order, if any, with its live state. */
    suspend fun relayEntry(orderId: String): MeshEntry? = outbox.entryOf(orderId)

    /** Builds the payload, queues it, and bursts it over the mesh. Returns the code. */
    suspend fun send(
        orderId: String,
        pnr: String,
        coach: String,
        berth: Int,
        diners: Int,
        items: List<MeshOrderPayload.Item>,
        totalPaise: Int,
    ): MeshOrderPayload {
        val payload = MeshOrderPayload(
            orderId = orderId,
            pnr = pnr,
            coach = coach,
            berth = berth,
            diners = diners,
            createdAt = Instant.now().toString(),
            deliveryCode = randomCode(),
            items = items,
            totalPaise = totalPaise,
        )
        outbox.add(MeshEntry(payload = payload.encode(), createdAt = payload.createdAt))
        refreshPending()
        container.meshTransport.advertiseBurst(packetFor(payload))
        startLoop() // keep re-broadcasting until the pantry confirms — timing can't lose orders
        return payload
    }

    /**
     * Dual-path delivery: the order was ALSO created in the cloud. Burst it over the
     * mesh so a nearby pantry phone rings instantly instead of waiting on FCM. Not
     * queued in the outbox (the cloud owns the record) — the pantry phone dedupes the
     * mesh copy against the cloud before alerting, so it never rings twice.
     */
    fun burstOnly(
        orderId: String,
        pnr: String,
        coach: String,
        berth: Int,
        diners: Int,
        items: List<MeshOrderPayload.Item>,
        totalPaise: Int,
    ) {
        scope.launch {
            val payload = MeshOrderPayload(
                orderId = orderId,
                pnr = pnr,
                coach = coach,
                berth = berth,
                diners = diners,
                createdAt = Instant.now().toString(),
                deliveryCode = randomCode(),
                items = items,
                totalPaise = totalPaise,
            )
            val packet = packetFor(payload)
            repeat(3) { i ->
                container.meshTransport.advertiseBurst(packet, seconds = 6)
                if (i < 2) delay(12_000)
            }
        }
    }

    /**
     * Background mesh duty cycle, started on app open and whenever a relay order is
     * on screen:
     *  - re-broadcasts pending orders every cycle until the pantry confirms (ack) or
     *    the cloud sync lands — a closed/locked pantry app at the wrong second no
     *    longer loses the order
     *  - listens for STATUS announcements (accepted / out for delivery / delivered)
     *    so the passenger's timeline advances fully offline
     * Listening is skipped while nothing is in flight (battery).
     */
    fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            val selfId = container.settings.ensureMeshNodeId()
            val relay = MeshRelay(
                selfId = selfId,
                isSink = { it.type == MeshPacket.TYPE_STATUS }, // passenger consumes pantry outcomes
                hmacSecret = { MeshTrip.SECRET },
                forward = { pkt -> container.meshTransport.advertiseBurst(pkt, seconds = 5) },
                onSink = { pkt -> scope.launch { handleStatusPacket(pkt) } },
            )
            while (isActive) {
                val inFlight = outbox.all().filter {
                    it.state == "PENDING" &&
                        it.ack != MeshPacket.STATUS_DELIVERED &&
                        it.ack != MeshPacket.STATUS_REJECTED
                }
                if (container.settings.meshRelayEnabled.first() && inFlight.isNotEmpty()) {
                    for ((i, entry) in inFlight.take(2).withIndex()) {
                        val payload = MeshOrderPayload.decode(entry.payload) ?: continue
                        // Bursts run asynchronously on the transport and cancel the previous
                        // one — wait out the burst before starting the next order's.
                        if (i > 0) delay(7_000)
                        container.meshTransport.advertiseBurst(packetFor(payload), seconds = 6)
                    }
                    listen(relay, seconds = 12)
                    delay(3_000)
                } else {
                    delay(20_000)
                }
            }
        }
    }

    /** Scans for [seconds] so STATUS announcements (and mesh hops) can arrive. */
    private suspend fun listen(relay: MeshRelay, seconds: Long) {
        if (container.meshTransport.requiredPermissions().isNotEmpty()) return
        container.meshTransport.startScanning { pkt ->
            scope.launch { relay.receive(pkt) }
        }
        delay(seconds * 1000)
        container.meshTransport.stopScanning()
    }

    /** STATUS announcement from the pantry phone: record it so tracking advances offline. */
    private suspend fun handleStatusPacket(packet: MeshPacket) {
        val state = packet.tag.ifBlank {
            MeshCodec.b64Decode(packet.payloadB64)
                ?.let { String(it, Charsets.UTF_8) }
                ?: return
        }
        outbox.markAck(packet.orderId, state)
    }

    /** Called on app start / Home resume / manual refresh: POSTs everything pending. */
    suspend fun syncPending(): Int {
        if (_syncing.value) return 0
        _syncing.value = true
        var synced = 0
        try {
            val serverUrl = container.settings.serverUrl.first()
            if (serverUrl.isBlank()) return 0
            // Cheap reachability probe — throws when offline, which we treat as "nothing synced".
            runCatching { ApiClient.testConnection(serverUrl) }.getOrElse { return 0 }
            val api = container.client.api()
            val pending = outbox.all().filter { it.state != "SYNCED" }
            for (entry in pending) {
                val payload = MeshOrderPayload.decode(entry.payload) ?: continue
                try {
                    api.createOrder(payload.toRequest())
                    outbox.markState(payload.orderId, "SYNCED")
                    synced++
                } catch (_: Exception) {
                    break // server unreachable again — resume next time
                }
            }
            refreshPending()
        } finally {
            _syncing.value = false
        }
        return synced
    }

    private suspend fun packetFor(payload: MeshOrderPayload): MeshPacket = MeshCodec.sign(
        MeshPacket(
            orderId = payload.orderId,
            ttl = MeshPacket.INITIAL_TTL,
            origin = container.settings.ensureMeshNodeId(),
            payloadB64 = MeshCodec.b64Encode(payload.encode().toByteArray()),
            hmac8 = "",
        ),
        MeshTrip.SECRET,
    )

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

    private fun randomCode(): String = (1000..9999).random().toString()
}
