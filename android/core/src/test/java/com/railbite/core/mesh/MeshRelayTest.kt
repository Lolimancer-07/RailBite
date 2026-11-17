package com.railbite.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports `tools/relay-sim/src/__tests__/relay.test.ts` onto the Kotlin engine.
 * The "rake" is a line of phones; range-1 forwarding mimics coach-to-coach BLE.
 */
class MeshRelayTest {

    private val secret = "trip-secret"

    /** One phone on the rake: relay engine + captured forwards/deliveries. */
    private inner class Phone(val id: String, val sink: Boolean = false) {
        val forwarded = mutableListOf<MeshPacket>()
        val delivered = mutableListOf<MeshPacket>()
        lateinit var network: Rake
        var online = true

        val relay = MeshRelay(
            selfId = id,
            isSink = { sink },
            hmacSecret = { secret },
            forward = { p -> forwarded.add(p); network.spread(this, p) },
            onSink = { p -> delivered.add(p) },
        )
    }

    /** Linear 24-coach rake; `spread` = broadcast to phones within [range] positions. */
    private inner class Rake(val size: Int = 24, val pantryIndex: Int = 12, var range: Int = 1) {
        val phones = (1..size).map { i -> Phone(if (i == pantryIndex) "PC" else "P$i", sink = i == pantryIndex) }

        init {
            phones.forEach { it.network = this }
        }

        fun node(index: Int): Phone = phones[index - 1]

        fun spread(from: Phone, packet: MeshPacket) {
            val i = phones.indexOfFirst { it === from }
            for (j in phones.indices) {
                if (j == i) continue
                if (Math.abs(j - i) <= range && phones[j].online) phones[j].relay.receive(packet)
            }
        }

        /** The origin phone does not run receive() — it builds and broadcasts directly. */
        fun originate(originIndex: Int, orderId: String, ttl: Int = MeshPacket.INITIAL_TTL): MeshPacket {
            val origin = node(originIndex)
            val packet = MeshPacket(
                orderId = orderId, ttl = ttl, origin = origin.id,
                payloadB64 = "dGVzdA==", hmac8 = "", hops = listOf(origin.id),
            )
            val signed = MeshCodec.sign(packet, secret)
            spread(origin, signed)
            return signed
        }
    }

    @Test
    fun `scenario A - all online, order crosses 12 coaches to the pantry`() {
        val rake = Rake()
        rake.originate(24, "ord-test-001", ttl = 16)

        val pantry = rake.node(12)
        assertEquals(1, pantry.delivered.size)
        // hops: origin + 11 relays = 12 entries, matching the simulator
        assertEquals(12, pantry.delivered[0].hops.size)
        // 11 phones relay (P23..P13); the pantry is a sink and never forwards
        assertEquals(11, rake.phones.count { it.forwarded.isNotEmpty() })
    }

    @Test
    fun `scenario B - two dead phones partition a range-1 phone mesh`() {
        val rake = Rake()
        rake.node(18).online = false
        rake.node(19).online = false

        rake.originate(24, "ord-test-partition", ttl = 16)

        assertEquals(0, rake.node(12).delivered.size)
    }

    @Test
    fun `dedup - identical orderId is dropped on the second arrival`() {
        val phone = Rake().node(5)
        val packet = MeshCodec.sign(
            MeshPacket(
                orderId = "dedup-order-123", ttl = 5, origin = "OTHER",
                payloadB64 = "dGVzdA==", hmac8 = "", hops = listOf("NODE_1", "NODE_2"),
            ),
            secret,
        )

        assertTrue(phone.relay.receive(packet))
        assertFalse(phone.relay.receive(packet)) // duplicate dropped
    }

    @Test
    fun `ttl - packet dies before reaching a far pantry`() {
        val rake = Rake()
        rake.originate(24, "ord-expired", ttl = 5)
        assertEquals(0, rake.node(12).delivered.size)
    }

    @Test
    fun `status - announcements dedupe per state, not per order`() {
        val phone = Rake().node(5)
        fun statusPacket(orderId: String, state: String) = MeshCodec.sign(
            MeshPacket(
                type = MeshPacket.TYPE_STATUS, tag = state,
                orderId = orderId, ttl = 5, origin = "OTHER",
                payloadB64 = MeshCodec.b64Encode(state.toByteArray()),
                hmac8 = "", hops = listOf("NODE_1"),
            ),
            secret,
        )

        assertTrue(phone.relay.receive(statusPacket("st-1", MeshPacket.STATUS_ACCEPTED)))
        // Same announcement repeated (pantry re-bursts) → deduped…
        assertFalse(phone.relay.receive(statusPacket("st-1", MeshPacket.STATUS_ACCEPTED)))
        // …but the next state for the SAME order must still travel, or the passenger's
        // timeline would freeze on ACCEPTED forever.
        assertTrue(phone.relay.receive(statusPacket("st-1", MeshPacket.STATUS_DELIVERED)))
    }

    @Test
    fun `loop - phone drops packets it originated or already relayed`() {
        val rake = Rake()
        val phone = rake.node(7)
        val viaPhone = MeshCodec.sign(
            MeshPacket(
                orderId = "loop-1", ttl = 5, origin = "OTHER",
                payloadB64 = "dA==", hmac8 = "", hops = listOf("P7"),
            ),
            secret,
        )
        assertFalse(phone.relay.receive(viaPhone))

        val fromSelf = MeshCodec.sign(
            MeshPacket(
                orderId = "loop-2", ttl = 5, origin = "P7",
                payloadB64 = "dA==", hmac8 = "", hops = listOf("P7"),
            ),
            secret,
        )
        assertFalse(phone.relay.receive(fromSelf))
    }

    @Test
    fun `hmac - tampered or foreign-trip packets are dropped`() {
        val phone = Rake().node(5)
        val tampered = MeshPacket(
            orderId = "evil-1", ttl = 5, origin = "OTHER",
            payloadB64 = "cGF5bG9hZA==", hmac8 = "deadbeef", hops = emptyList(),
        )
        assertFalse(phone.relay.receive(tampered))
    }

    @Test
    fun `lru - eviction frees a slot for a NEW order, evicted orders re-accept`() {
        val phone = Rake().node(5)
        repeat(500) { i ->
            phone.relay.receive(
                MeshCodec.sign(
                    MeshPacket(
                        orderId = "flood-$i", ttl = 3, origin = "OTHER",
                        payloadB64 = "dA==", hmac8 = "", hops = emptyList(),
                    ),
                    secret,
                ),
            )
        }
        // Cache is full: flood-0 is still deduped…
        assertFalse(
            phone.relay.receive(
                MeshCodec.sign(
                    MeshPacket(
                        orderId = "flood-0", ttl = 3, origin = "OTHER",
                        payloadB64 = "dA==", hmac8 = "", hops = emptyList(),
                    ),
                    secret,
                ),
            )
        )
        // …but a new order evicts the oldest entry (flood-0)…
        assertTrue(
            phone.relay.receive(
                MeshCodec.sign(
                    MeshPacket(
                        orderId = "flood-500", ttl = 3, origin = "OTHER",
                        payloadB64 = "dA==", hmac8 = "", hops = emptyList(),
                    ),
                    secret,
                ),
            )
        )
        // …so flood-0 passes again (LRU semantics, as in the simulator).
        assertTrue(
            phone.relay.receive(
                MeshCodec.sign(
                    MeshPacket(
                        orderId = "flood-0", ttl = 3, origin = "OTHER",
                        payloadB64 = "dA==", hmac8 = "", hops = emptyList(),
                    ),
                    secret,
                ),
            )
        )
    }

    @Test
    fun `flood - 100 concurrent orders all reach the pantry`() {
        val rake = Rake()
        for (i in 1..100) {
            val originCoach = (i % 23) + 1
            if (originCoach == 12) continue
            rake.originate(originCoach, "flood-order-$i", ttl = 16)
        }
        // Origins (i%23)+1 hit the pantry coach 4 times (i=11,34,57,80) and are skipped:
        // 96 attempts, all delivered.
        assertEquals(96, rake.node(12).delivered.size)
    }
}
