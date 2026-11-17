package com.railbite.customer.mesh

import com.railbite.customer.AppContainer
import com.railbite.core.api.ApiClient
import com.railbite.core.mesh.MeshCodec
import com.railbite.core.mesh.MeshEntry
import com.railbite.core.mesh.MeshOrderPayload
import com.railbite.core.mesh.MeshOutbox
import com.railbite.core.mesh.MeshPacket
import com.railbite.core.mesh.MeshTrip
import com.railbite.core.model.OrderCreateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID

/**
 * Passenger side of the Bluetooth relay (offline feature):
 *  - "send" persists the order in the outbox and bursts it over BLE advertisements —
 *    nearby phones relay it until the pantry phone (sink) consumes it
 *  - "sync" runs whenever the cloud is reachable again and POSTs pending relay orders
 *    with their original client UUID (idempotent); the cloud then owns dispatch/status
 *
 * The relay never replaces the cloud path — it only exists when the cloud can't be
 * reached and the relay toggle is on.
 */
class MeshOrderSender(private val container: AppContainer) {

    private val outbox = MeshOutbox(container.app)

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
        val origin = container.settings.ensureMeshNodeId()
        val packet = MeshCodec.sign(
            MeshPacket(
                orderId = payload.orderId,
                ttl = MeshPacket.INITIAL_TTL,
                origin = origin,
                payloadB64 = MeshCodec.b64Encode(payload.encode().toByteArray()),
                hmac8 = "",
            ),
            MeshTrip.SECRET,
        )
        outbox.add(MeshEntry(payload = payload.encode(), createdAt = payload.createdAt))
        refreshPending()
        container.meshTransport.advertiseBurst(packet)
        return payload
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
