package com.railbite.core.mesh

import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Codec for [MeshPacket]s: JSON envelope <-> bytes, plus the hmac8 integrity tag
 * (see [MeshPacket.hmac8]). Kept in `core` so both apps share one wire format.
 */
object MeshCodec {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(packet: MeshPacket): ByteArray =
        json.encodeToString(MeshPacket.serializer(), packet).toByteArray(StandardCharsets.UTF_8)

    /** Returns null when the bytes are not a parseable v1 packet (corrupt/garbage frame). */
    fun decode(bytes: ByteArray): MeshPacket? = try {
        val s = String(bytes, StandardCharsets.UTF_8)
        val p = json.decodeFromString(MeshPacket.serializer(), s)
        if (p.v == 1 && p.orderId.isNotBlank() && p.origin.isNotBlank()) p else null
    } catch (_: Exception) {
        null
    }

    fun b64Encode(bytes: ByteArray): String =
        Base64.getEncoder().withoutPadding().encodeToString(bytes)

    fun b64Decode(text: String): ByteArray? = try {
        Base64.getDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }

    /** First 8 hex chars of HMAC-SHA256 over "orderId|origin|payloadB64". */
    fun hmac8(secret: String, orderId: String, origin: String, payloadB64: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val data = "$orderId|$origin|$payloadB64".toByteArray(StandardCharsets.UTF_8)
        return mac.doFinal(data).take(4).joinToString("") { "%02x".format(it) }
    }

    /** Fills in [MeshPacket.hmac8] before first broadcast. */
    fun sign(packet: MeshPacket, secret: String): MeshPacket = packet.copy(
        hmac8 = hmac8(secret, packet.orderId, packet.origin, packet.payloadB64),
    )

    /** Drop packets whose tag does not match (tampered or foreign-trip traffic). */
    fun verify(packet: MeshPacket, secret: String): Boolean =
        packet.hmac8 == hmac8(secret, packet.orderId, packet.origin, packet.payloadB64)
}
