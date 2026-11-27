package com.railbite.core.mesh

import com.railbite.core.model.ChatPreset
import com.railbite.core.model.ChatPresets
import com.railbite.core.util.Statuses
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 23-byte binary BLE Legacy Advertisement Codec (`Company ID 0x5242`).
 *
 * Frame budget: `3B Flags + 4B Manufacturer Header (Len, 0xFF, 0x5242) + 23B Payload = 30B <= 31B`.
 *
 * Byte layout (big-endian integers):
 * - Byte `0`: `verType` (`0x11`=ORDER_NEW, `0x12`=STATUS_ACK, `0x13`=QUICK_MSG)
 * - Bytes `1..4`: `orderId` (`UInt32`)
 * - Byte `5`: `hopTtl` — upper 4 bits `hopCount` (`0..15`), lower 4 bits `ttl` (`0..15`, default `6`)
 * - Bytes `6..7`: `coach` (2 ASCII chars, space-padded)
 * - Byte `8`: `seat` (`UInt8`)
 * - Bytes `9..10`: `pnrLast4` (`UInt16`)
 * - Bytes `11..12`: `handoverPin` (`UInt16`)
 * - Bytes `13..20`: 8-byte type-specific payload
 * - Bytes `21..22`: `hmac16` — first 2 bytes of HMAC-SHA256 over bytes `0..20`
 */
object BlePacketCodec {

    const val COMPANY_ID = 0x5242
    const val PACKET_SIZE = 23
    const val HEADER_AND_PAYLOAD_END = 21 // bytes 0..20 covered by HMAC
    const val DEFAULT_TTL = 6
    const val MAX_ORDER_ITEMS = 4

    const val RAILBITE_MESH_KEY = MeshTrip.SECRET
    const val DEFAULT_TRAIN_NUMBER = "12345"

    /**
     * Derives the train-scoped HMAC secret (`"<baseKey>:<trainNumber>"`) so BLE frames
     * never cross-bleed across trains while keeping the payload strictly 23 bytes.
     */
    fun scopedKey(
        key: String = RAILBITE_MESH_KEY,
        trainNumber: String? = null,
    ): String {
        val base = key.ifBlank { RAILBITE_MESH_KEY }
        val cleanTrain = trainNumber?.trim()?.takeIf { it.isNotEmpty() } ?: return base
        return "$base:$cleanTrain"
    }

    const val VERSION_NIBBLE = 0x1
    const val TYPE_ORDER_NEW = 0x1
    const val TYPE_STATUS_ACK = 0x2
    const val TYPE_QUICK_MSG = 0x3

    const val VER_ORDER_NEW: Byte = 0x11
    const val VER_STATUS_ACK: Byte = 0x12
    const val VER_QUICK_MSG: Byte = 0x13

    const val STATUS_ACCEPTED = 1
    const val STATUS_PACKING = 2
    const val STATUS_OUT_FOR_DELIVERY = 3
    const val STATUS_DELIVERED = 4
    const val STATUS_REJECTED = 5

    const val ROLE_CUSTOMER = 1
    const val ROLE_PICKER = 2

    private val UUID_BLE_PREFIX = "52420000-0000-4000-8000-0000"
    private val BLE_UUID_REGEX = Regex("^52420000-0000-4000-8000-0000([0-9a-fA-F]{8})$")

    data class ItemSlot(
        val itemId: Int,
        val qty: Int,
    )

    sealed interface BlePacket {
        val verType: Byte
        val orderId: UInt
        val hopCount: Int
        val ttl: Int
        val coach: String
        val seat: Int
        val pnrLast4: Int
        val handoverPin: Int
        val orderUuid: String
        val dedupKey: String

        fun withHop(newHopCount: Int, newTtl: Int): BlePacket
    }

    data class OrderNew(
        override val orderId: UInt,
        override val hopCount: Int = 0,
        override val ttl: Int = DEFAULT_TTL,
        override val coach: String,
        override val seat: Int,
        override val pnrLast4: Int,
        override val handoverPin: Int,
        val items: List<ItemSlot>,
    ) : BlePacket {
        override val verType: Byte = VER_ORDER_NEW
        override val orderUuid: String get() = shortIdToUuid(orderId)
        override val dedupKey: String get() = "0x11:$orderId"

        override fun withHop(newHopCount: Int, newTtl: Int): BlePacket =
            copy(hopCount = newHopCount, ttl = newTtl)
    }

    data class StatusAck(
        override val orderId: UInt,
        override val hopCount: Int = 0,
        override val ttl: Int = DEFAULT_TTL,
        override val coach: String,
        override val seat: Int,
        override val pnrLast4: Int = 0,
        override val handoverPin: Int = 0,
        val statusCode: Int,
        val pickerId: String,
        val etaMins: Int = 8,
        val cashRs: Int = 0,
        val reserved: Int = 0,
    ) : BlePacket {
        override val verType: Byte = VER_STATUS_ACK
        override val orderUuid: String get() = shortIdToUuid(orderId)
        override val dedupKey: String get() = "0x12:$orderId:$statusCode"

        override fun withHop(newHopCount: Int, newTtl: Int): BlePacket =
            copy(hopCount = newHopCount, ttl = newTtl)
    }

    data class QuickMsg(
        override val orderId: UInt,
        override val hopCount: Int = 0,
        override val ttl: Int = DEFAULT_TTL,
        override val coach: String,
        override val seat: Int,
        override val pnrLast4: Int = 0,
        override val handoverPin: Int = 0,
        val senderRole: Int,
        val presetCode: Int,
        val msgSeq: Int,
        val reserved: Int = 0,
    ) : BlePacket {
        override val verType: Byte = VER_QUICK_MSG
        override val orderUuid: String get() = shortIdToUuid(orderId)
        override val dedupKey: String get() = "0x13:$orderId:$senderRole:$msgSeq"

        override fun withHop(newHopCount: Int, newTtl: Int): BlePacket =
            copy(hopCount = newHopCount, ttl = newTtl)
    }

    /** Encodes a [BlePacket] into an exact 23-byte payload with 16-bit HMAC trailer. */
    fun encode(
        packet: BlePacket,
        key: String = RAILBITE_MESH_KEY,
        trainNumber: String? = null,
    ): ByteArray {
        val out = ByteArray(PACKET_SIZE)
        out[0] = packet.verType

        writeUInt32BE(out, 1, packet.orderId)

        val clampedHop = packet.hopCount.coerceIn(0, 15)
        val clampedTtl = packet.ttl.coerceIn(0, 15)
        out[5] = ((clampedHop shl 4) or clampedTtl).toByte()

        val coachNorm = normalizeCoach2(packet.coach)
        out[6] = coachNorm[0].code.toByte()
        out[7] = coachNorm[1].code.toByte()
        out[8] = packet.seat.coerceIn(0, 255).toByte()

        writeUInt16BE(out, 9, packet.pnrLast4.coerceIn(0, 65535))
        writeUInt16BE(out, 11, packet.handoverPin.coerceIn(0, 65535))

        when (packet) {
            is OrderNew -> {
                val slots = packet.items.take(MAX_ORDER_ITEMS)
                for (i in 0 until MAX_ORDER_ITEMS) {
                    val slot = slots.getOrNull(i)
                    val offset = 13 + i * 2
                    if (slot != null && slot.itemId in 1..255 && slot.qty > 0) {
                        out[offset] = slot.itemId.toByte()
                        out[offset + 1] = slot.qty.coerceIn(1, 255).toByte()
                    } else {
                        out[offset] = 0
                        out[offset + 1] = 0
                    }
                }
            }
            is StatusAck -> {
                out[13] = packet.statusCode.coerceIn(1, 255).toByte()
                val p = normalizePicker2(packet.pickerId)
                out[14] = p[0].code.toByte()
                out[15] = p[1].code.toByte()
                out[16] = packet.etaMins.coerceIn(0, 255).toByte()
                writeUInt16BE(out, 17, packet.cashRs.coerceIn(0, 65535))
                writeUInt16BE(out, 19, packet.reserved.coerceIn(0, 65535))
            }
            is QuickMsg -> {
                out[13] = packet.senderRole.coerceIn(1, 255).toByte()
                out[14] = packet.presetCode.coerceIn(1, 255).toByte()
                writeUInt16BE(out, 15, packet.msgSeq.coerceIn(0, 65535))
                writeUInt32BE(out, 17, packet.reserved.coerceAtLeast(0).toUInt())
            }
        }

        val tag = computeHmac16(out, key, trainNumber)
        out[21] = tag[0]
        out[22] = tag[1]
        return out
    }

    /**
     * Decodes a 23-byte payload and validates [hmac16].
     * Returns null when size != 23, version != 1, unknown type, or HMAC mismatch.
     */
    fun decode(
        bytes: ByteArray,
        key: String = RAILBITE_MESH_KEY,
        trainNumber: String? = null,
    ): BlePacket? {
        if (bytes.size != PACKET_SIZE) return null

        val verTypeByte = bytes[0].toInt() and 0xFF
        val version = (verTypeByte ushr 4) and 0x0F
        if (version != VERSION_NIBBLE) return null

        val expectedTag = computeHmac16(bytes, key, trainNumber)
        val actualTag = byteArrayOf(bytes[21], bytes[22])
        if (!MessageDigest.isEqual(expectedTag, actualTag)) return null

        val orderId = readUInt32BE(bytes, 1)
        val hopTtl = bytes[5].toInt() and 0xFF
        val hopCount = (hopTtl ushr 4) and 0x0F
        val ttl = hopTtl and 0x0F

        val coach = String(byteArrayOf(bytes[6], bytes[7]), StandardCharsets.US_ASCII).trim()
        val seat = bytes[8].toInt() and 0xFF
        val pnrLast4 = readUInt16BE(bytes, 9)
        val handoverPin = readUInt16BE(bytes, 11)

        return when (verTypeByte and 0x0F) {
            TYPE_ORDER_NEW -> {
                val items = buildList {
                    for (i in 0 until MAX_ORDER_ITEMS) {
                        val offset = 13 + i * 2
                        val itemId = bytes[offset].toInt() and 0xFF
                        val qty = bytes[offset + 1].toInt() and 0xFF
                        if (itemId > 0 && qty > 0) {
                            add(ItemSlot(itemId = itemId, qty = qty))
                        }
                    }
                }
                if (items.isEmpty()) return null
                OrderNew(
                    orderId = orderId,
                    hopCount = hopCount,
                    ttl = ttl,
                    coach = coach,
                    seat = seat,
                    pnrLast4 = pnrLast4,
                    handoverPin = handoverPin,
                    items = items,
                )
            }
            TYPE_STATUS_ACK -> {
                val statusCode = bytes[13].toInt() and 0xFF
                if (statusCode !in STATUS_ACCEPTED..STATUS_REJECTED) return null
                val pickerId = decodeAscii2(bytes[14], bytes[15])
                val etaMins = bytes[16].toInt() and 0xFF
                val cashRs = readUInt16BE(bytes, 17)
                val reserved = readUInt16BE(bytes, 19)
                StatusAck(
                    orderId = orderId,
                    hopCount = hopCount,
                    ttl = ttl,
                    coach = coach,
                    seat = seat,
                    pnrLast4 = pnrLast4,
                    handoverPin = handoverPin,
                    statusCode = statusCode,
                    pickerId = pickerId,
                    etaMins = etaMins,
                    cashRs = cashRs,
                    reserved = reserved,
                )
            }
            TYPE_QUICK_MSG -> {
                val senderRole = bytes[13].toInt() and 0xFF
                val presetCode = bytes[14].toInt() and 0xFF
                if (senderRole !in ROLE_CUSTOMER..ROLE_PICKER) return null
                if (presetCode !in 1..8) return null
                val msgSeq = readUInt16BE(bytes, 15)
                val reserved = readUInt32BE(bytes, 17).toInt()
                QuickMsg(
                    orderId = orderId,
                    hopCount = hopCount,
                    ttl = ttl,
                    coach = coach,
                    seat = seat,
                    pnrLast4 = pnrLast4,
                    handoverPin = handoverPin,
                    senderRole = senderRole,
                    presetCode = presetCode,
                    msgSeq = msgSeq,
                    reserved = reserved,
                )
            }
            else -> null
        }
    }

    /**
     * Prepares a decoded packet for forwarding: returns null if `ttl <= 0`,
     * otherwise increments `hopCount` (clamped at 15) and decrements `ttl`.
     */
    fun forRelay(packet: BlePacket): BlePacket? {
        if (packet.ttl <= 0) return null
        val nextHop = (packet.hopCount + 1).coerceAtMost(15)
        val nextTtl = (packet.ttl - 1).coerceAtLeast(0)
        return packet.withHop(newHopCount = nextHop, newTtl = nextTtl)
    }

    /** First 2 bytes of HMAC-SHA256 over bytes `0..20` using [key] (optionally train-scoped). */
    fun computeHmac16(
        bytes: ByteArray,
        key: String = RAILBITE_MESH_KEY,
        trainNumber: String? = null,
    ): ByteArray {
        require(bytes.size >= HEADER_AND_PAYLOAD_END)
        val effectiveKey = scopedKey(key, trainNumber)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(effectiveKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        mac.update(bytes, 0, HEADER_AND_PAYLOAD_END)
        val digest = mac.doFinal()
        return byteArrayOf(digest[0], digest[1])
    }

    fun generateShortOrderId(): UInt = Random.nextInt(1, Int.MAX_VALUE).toUInt()

    fun shortIdToUuid(shortId: UInt): String =
        UUID_BLE_PREFIX + "%08x".format(shortId.toInt())

    fun uuidToShortId(orderUuid: String): UInt {
        BLE_UUID_REGEX.matchEntire(orderUuid.trim())?.let { match ->
            return match.groupValues[1].toLong(16).toUInt()
        }
        val crc = CRC32().apply {
            update(orderUuid.toByteArray(StandardCharsets.UTF_8))
        }.value
        return (if (crc == 0L) 1L else crc).toUInt()
    }

    fun parsePnrLast4(pnr: String): Int =
        pnr.filter(Char::isDigit).takeLast(4).toIntOrNull()?.coerceIn(0, 9999) ?: 0

    fun parsePinInt(pin: String): Int =
        pin.filter(Char::isDigit).take(4).toIntOrNull()?.coerceIn(0, 9999) ?: 0

    fun formatPin4(pin: Int): String = pin.coerceIn(0, 9999).toString().padStart(4, '0')

    fun statusFromCode(code: Int): String = when (code) {
        STATUS_ACCEPTED -> Statuses.ACCEPTED
        STATUS_PACKING -> Statuses.PACKING
        STATUS_OUT_FOR_DELIVERY -> Statuses.OUT_FOR_DELIVERY
        STATUS_DELIVERED -> Statuses.DELIVERED
        STATUS_REJECTED -> "REJECTED"
        else -> Statuses.PLACED
    }

    fun codeFromStatus(status: String): Int? = when (status.uppercase()) {
        Statuses.ACCEPTED -> STATUS_ACCEPTED
        Statuses.PACKING -> STATUS_PACKING
        Statuses.OUT_FOR_DELIVERY -> STATUS_OUT_FOR_DELIVERY
        Statuses.DELIVERED -> STATUS_DELIVERED
        "REJECTED" -> STATUS_REJECTED
        else -> null
    }

    fun presetForCode(code: Int): ChatPreset? = when (code) {
        in 1..4 -> ChatPresets.CUSTOMER.getOrNull(code - 1)
        in 5..8 -> ChatPresets.PICKER.getOrNull(code - 5)
        else -> null
    }

    fun codeForPreset(presetCode: String?): Int? {
        if (presetCode.isNullOrBlank()) return null
        val cIdx = ChatPresets.CUSTOMER.indexOfFirst { it.code == presetCode }
        if (cIdx >= 0) return cIdx + 1
        val pIdx = ChatPresets.PICKER.indexOfFirst { it.code == presetCode }
        if (pIdx >= 0) return pIdx + 5
        return null
    }

    fun roleString(roleByte: Int): String =
        if (roleByte == ROLE_PICKER) "PICKER" else "CUSTOMER"

    private fun normalizeCoach2(raw: String): String {
        val clean = raw.trim().uppercase().filter { it.code in 0x20..0x7E }
        return when {
            clean.isEmpty() -> "  "
            clean.length == 1 -> "$clean "
            else -> clean.take(2)
        }
    }

    private fun normalizePicker2(raw: String): String {
        val clean = raw.trim().filter { it.code in 0x20..0x7E }
        return when {
            clean.isEmpty() -> "c1"
            clean.length == 1 -> "${clean[0]}\u0000"
            else -> clean.take(2)
        }
    }

    private fun decodeAscii2(b0: Byte, b1: Byte): String = buildString {
        val c0 = (b0.toInt() and 0xFF).toChar()
        val c1 = (b1.toInt() and 0xFF).toChar()
        if (c0 != '\u0000' && c0 != ' ') append(c0)
        if (c1 != '\u0000' && c1 != ' ') append(c1)
    }

    private fun writeUInt32BE(buf: ByteArray, offset: Int, v: UInt) {
        val x = v.toLong()
        buf[offset] = ((x ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((x ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((x ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (x and 0xFF).toByte()
    }

    private fun readUInt32BE(buf: ByteArray, offset: Int): UInt {
        val b0 = (buf[offset].toLong() and 0xFF) shl 24
        val b1 = (buf[offset + 1].toLong() and 0xFF) shl 16
        val b2 = (buf[offset + 2].toLong() and 0xFF) shl 8
        val b3 = buf[offset + 3].toLong() and 0xFF
        return (b0 or b1 or b2 or b3).toUInt()
    }

    private fun writeUInt16BE(buf: ByteArray, offset: Int, v: Int) {
        buf[offset] = ((v ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (v and 0xFF).toByte()
    }

    private fun readUInt16BE(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
}

