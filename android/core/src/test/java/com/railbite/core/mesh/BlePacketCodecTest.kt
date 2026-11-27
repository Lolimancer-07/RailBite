package com.railbite.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePacketCodecTest {

    private val testKey = BlePacketCodec.RAILBITE_MESH_KEY

    @Test
    fun `ORDER_NEW round-trip preserves all fields and fits 23 bytes`() {
        val orderId = 0x1A2B3C4Du
        val pkt = BlePacketCodec.OrderNew(
            orderId = orderId,
            hopCount = 2,
            ttl = 4,
            coach = "B2",
            seat = 14,
            pnrLast4 = 4410,
            handoverPin = 7391,
            items = listOf(
                BlePacketCodec.ItemSlot(itemId = 1, qty = 2),
                BlePacketCodec.ItemSlot(itemId = 8, qty = 1),
                BlePacketCodec.ItemSlot(itemId = 5, qty = 3),
            ),
        )

        val encoded = BlePacketCodec.encode(pkt, testKey)
        assertEquals(BlePacketCodec.PACKET_SIZE, encoded.size)
        assertTrue(encoded.size <= 23)
        assertEquals(BlePacketCodec.VER_ORDER_NEW, encoded[0])

        val decoded = BlePacketCodec.decode(encoded, testKey) as? BlePacketCodec.OrderNew
        assertNotNull(decoded)
        assertEquals(orderId, decoded!!.orderId)
        assertEquals(2, decoded.hopCount)
        assertEquals(4, decoded.ttl)
        assertEquals("B2", decoded.coach)
        assertEquals(14, decoded.seat)
        assertEquals(4410, decoded.pnrLast4)
        assertEquals(7391, decoded.handoverPin)
        assertEquals(3, decoded.items.size)
        assertEquals(BlePacketCodec.ItemSlot(1, 2), decoded.items[0])
        assertEquals(BlePacketCodec.ItemSlot(8, 1), decoded.items[1])
        assertEquals(BlePacketCodec.ItemSlot(5, 3), decoded.items[2])
    }

    @Test
    fun `STATUS_ACK round-trip preserves status, pickerId, etaMins, cashRs, and fits 23 bytes`() {
        val pkt = BlePacketCodec.StatusAck(
            orderId = 0xCAFEBABEu,
            hopCount = 1,
            ttl = 5,
            coach = "S1",
            seat = 63,
            pnrLast4 = 12,
            handoverPin = 4821,
            statusCode = BlePacketCodec.STATUS_OUT_FOR_DELIVERY,
            pickerId = "c3",
            etaMins = 4,
            cashRs = 185,
        )

        val encoded = BlePacketCodec.encode(pkt, testKey)
        assertEquals(BlePacketCodec.PACKET_SIZE, encoded.size)
        assertEquals(BlePacketCodec.VER_STATUS_ACK, encoded[0])

        val decoded = BlePacketCodec.decode(encoded, testKey) as? BlePacketCodec.StatusAck
        assertNotNull(decoded)
        assertEquals(0xCAFEBABEu, decoded!!.orderId)
        assertEquals(1, decoded.hopCount)
        assertEquals(5, decoded.ttl)
        assertEquals("S1", decoded.coach)
        assertEquals(63, decoded.seat)
        assertEquals(BlePacketCodec.STATUS_OUT_FOR_DELIVERY, decoded.statusCode)
        assertEquals("c3", decoded.pickerId)
        assertEquals(4, decoded.etaMins)
        assertEquals(185, decoded.cashRs)
    }

    @Test
    fun `QUICK_MSG round-trip preserves role, presetCode, msgSeq and fits 23 bytes`() {
        val pkt = BlePacketCodec.QuickMsg(
            orderId = 0x00F01234u,
            hopCount = 0,
            ttl = 6,
            coach = "A1",
            seat = 7,
            pnrLast4 = 9999,
            handoverPin = 1004,
            senderRole = BlePacketCodec.ROLE_CUSTOMER,
            presetCode = 1, // WHERE_ARE_YOU
            msgSeq = 42,
        )

        val encoded = BlePacketCodec.encode(pkt, testKey)
        assertEquals(BlePacketCodec.PACKET_SIZE, encoded.size)
        assertEquals(BlePacketCodec.VER_QUICK_MSG, encoded[0])

        val decoded = BlePacketCodec.decode(encoded, testKey) as? BlePacketCodec.QuickMsg
        assertNotNull(decoded)
        assertEquals(0x00F01234u, decoded!!.orderId)
        assertEquals("A1", decoded.coach)
        assertEquals(7, decoded.seat)
        assertEquals(BlePacketCodec.ROLE_CUSTOMER, decoded.senderRole)
        assertEquals(1, decoded.presetCode)
        assertEquals(42, decoded.msgSeq)
    }

    @Test
    fun `tampered HMAC bytes or tampered payload or wrong key are rejected`() {
        val original = BlePacketCodec.OrderNew(
            orderId = 0x11223344u,
            coach = "B1",
            seat = 1,
            pnrLast4 = 1,
            handoverPin = 2345,
            items = listOf(BlePacketCodec.ItemSlot(1, 1)),
        )
        val encoded = BlePacketCodec.encode(original, testKey)

        // Flip HMAC byte 21
        val tamperedHmac1 = encoded.copyOf().apply { this[21] = (this[21].toInt() xor 0xFF).toByte() }
        assertNull(BlePacketCodec.decode(tamperedHmac1, testKey))

        // Flip HMAC byte 22
        val tamperedHmac2 = encoded.copyOf().apply { this[22] = (this[22].toInt() xor 0x01).toByte() }
        assertNull(BlePacketCodec.decode(tamperedHmac2, testKey))

        // Flip seat byte 8 without recomputing HMAC
        val tamperedSeat = encoded.copyOf().apply { this[8] = 99 }
        assertNull(BlePacketCodec.decode(tamperedSeat, testKey))

        // Wrong key
        assertNull(BlePacketCodec.decode(encoded, "wrong-train-key"))

        // Wrong byte length
        assertNull(BlePacketCodec.decode(encoded.copyOf(22), testKey))
    }

    @Test
    fun `forRelay increments hopCount, decrements ttl, and stops at ttl 0`() {
        val start = BlePacketCodec.OrderNew(
            orderId = 0xABCDEF01u,
            hopCount = 0,
            ttl = 2,
            coach = "B3",
            seat = 22,
            pnrLast4 = 123,
            handoverPin = 5678,
            items = listOf(BlePacketCodec.ItemSlot(2, 1)),
        )
        val hop1 = BlePacketCodec.forRelay(start)
        assertNotNull(hop1)
        assertEquals(1, hop1!!.hopCount)
        assertEquals(1, hop1.ttl)

        // Re-encoding hop1 produces valid packet with new HMAC
        val hop1Bytes = BlePacketCodec.encode(hop1, testKey)
        assertNotNull(BlePacketCodec.decode(hop1Bytes, testKey))

        val hop2 = BlePacketCodec.forRelay(hop1)
        assertNotNull(hop2)
        assertEquals(2, hop2!!.hopCount)
        assertEquals(0, hop2.ttl)

        // Next relay must stop because ttl == 0
        assertNull(BlePacketCodec.forRelay(hop2))
    }

    @Test
    fun `shortId and UUID conversion is deterministic and reversible`() {
        val shortId = 0x98F012A4u
        val uuid = BlePacketCodec.shortIdToUuid(shortId)
        assertEquals(36, uuid.length)
        assertEquals(shortId, BlePacketCodec.uuidToShortId(uuid))
    }

    @Test
    fun `train-scoped HMAC isolates trains without increasing 23-byte payload size`() {
        assertEquals("secret:12345", BlePacketCodec.scopedKey("secret", " 12345 "))

        val order = BlePacketCodec.OrderNew(
            orderId = 0x55667788u,
            coach = "B2",
            seat = 14,
            pnrLast4 = 1234,
            handoverPin = 4321,
            items = listOf(BlePacketCodec.ItemSlot(itemId = 1, qty = 2)),
        )
        val status = BlePacketCodec.StatusAck(
            orderId = 0x55667788u,
            coach = "B2",
            seat = 14,
            pnrLast4 = 1234,
            handoverPin = 4321,
            statusCode = BlePacketCodec.STATUS_ACCEPTED,
            pickerId = "c1",
            etaMins = 7,
            cashRs = 180,
        )
        val chat = BlePacketCodec.QuickMsg(
            orderId = 0x55667788u,
            coach = "B2",
            seat = 14,
            senderRole = BlePacketCodec.ROLE_CUSTOMER,
            presetCode = 1,
            msgSeq = 1,
        )

        val orderTrainA = BlePacketCodec.encode(order, testKey, trainNumber = "12345")
        val statusTrainA = BlePacketCodec.encode(status, testKey, trainNumber = "12345")
        val chatTrainA = BlePacketCodec.encode(chat, testKey, trainNumber = "12345")

        assertEquals(23, orderTrainA.size)
        assertEquals(23, statusTrainA.size)
        assertEquals(23, chatTrainA.size)

        // Exact train succeeds
        assertEquals(order, BlePacketCodec.decode(orderTrainA, testKey, trainNumber = "12345"))
        assertEquals(status, BlePacketCodec.decode(statusTrainA, testKey, trainNumber = "12345"))
        assertEquals(chat, BlePacketCodec.decode(chatTrainA, testKey, trainNumber = "12345"))

        // Cross-train fails HMAC verification immediately at decode time
        assertNull(BlePacketCodec.decode(orderTrainA, testKey, trainNumber = "12423"))
        assertNull(BlePacketCodec.decode(statusTrainA, testKey, trainNumber = "12423"))
        assertNull(BlePacketCodec.decode(chatTrainA, testKey, trainNumber = "12423"))

        // Same packet encoded for 12423 decodes on 12423 and fails on 12345
        val orderTrainB = BlePacketCodec.encode(order, testKey, trainNumber = "12423")
        assertEquals(23, orderTrainB.size)
        assertEquals(order, BlePacketCodec.decode(orderTrainB, testKey, trainNumber = "12423"))
        assertNull(BlePacketCodec.decode(orderTrainB, testKey, trainNumber = "12345"))
    }
}

