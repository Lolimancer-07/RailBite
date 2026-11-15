package com.railbite.core.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshCodecTest {

    private val secret = "trip-secret"

    private fun sample(): MeshPacket {
        val orderJson =
            """{"id":"6f0a2c1e-1111-2222-3333-444455556666","pnr":"8492000001","coach":"B1",""" +
                """"berth":1,"diners":1,"paymentMode":"COD","items":[{"itemId":"m1","qty":2}],""" +
                """"createdAt":"2026-11-15T10:00:00Z","source":"BLE"}"""
        return MeshPacket(
            orderId = "6f0a2c1e-1111-2222-3333-444455556666",
            ttl = 15,
            origin = "a1b2c3d4",
            payloadB64 = MeshCodec.b64Encode(orderJson.toByteArray()),
            hmac8 = "",
            hops = listOf("a1b2c3d4"),
        )
    }

    @Test
    fun `envelope survives encode-decode`() {
        val packet = MeshCodec.sign(sample(), secret)
        val bytes = MeshCodec.encode(packet)
        val decoded = MeshCodec.decode(bytes)
        assertEquals(packet, decoded)
    }

    @Test
    fun `garbage bytes decode to null`() {
        assertNull(MeshCodec.decode(byteArrayOf(1, 2, 3, 0, -1)))
        assertNull(MeshCodec.decode("{}".toByteArray())) // missing required fields
        assertNull(MeshCodec.decode("""{"v":2}""".toByteArray())) // wrong version
    }

    @Test
    fun `hmac8 is 8 hex chars and deterministic`() {
        val p = sample()
        val tag = MeshCodec.hmac8(secret, p.orderId, p.origin, p.payloadB64)
        assertEquals(8, tag.length)
        assertTrue(tag.all { it in "0123456789abcdef" })
        assertEquals(tag, MeshCodec.hmac8(secret, p.orderId, p.origin, p.payloadB64))
    }

    @Test
    fun `verify accepts signed and rejects tampered packets`() {
        val signed = MeshCodec.sign(sample(), secret)
        assertTrue(MeshCodec.verify(signed, secret))

        val tamperedPayload = signed.copy(payloadB64 = MeshCodec.b64Encode("evil".toByteArray()))
        assertFalse(MeshCodec.verify(tamperedPayload, secret))

        val tamperedTag = signed.copy(hmac8 = "00000000")
        assertFalse(MeshCodec.verify(tamperedTag, secret))

        // Different trip secret -> different tag -> rejected (foreign traffic)
        assertFalse(MeshCodec.verify(signed, "other-secret"))
    }

    @Test
    fun `base64 helpers round-trip`() {
        val data = byteArrayOf(0, 1, -1, 100, 5)
        val restored = MeshCodec.b64Decode(MeshCodec.b64Encode(data))
        assertArrayEquals(data, restored)
        assertNull(MeshCodec.b64Decode("not base64 !!"))
    }

    @Test
    fun `relay chain uses codec end to end`() {
        // origin signs -> chunk -> reassemble -> decode -> verify -> equals original
        val signed = MeshCodec.sign(sample(), secret)
        val restored = MeshChunker.Reassembler().let { r ->
            var out: ByteArray? = null
            MeshChunker.chunk(MeshCodec.encode(signed)).forEach { out = r.offer(it) ?: out }
            out
        }?.let { MeshCodec.decode(it) }
        assertEquals(signed, restored)
    }
}
