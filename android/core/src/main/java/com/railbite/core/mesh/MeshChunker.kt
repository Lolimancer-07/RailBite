package com.railbite.core.mesh

import java.security.MessageDigest

/**
 * Splits a serialized [MeshPacket] into BLE legacy-advertisement frames and reassembles
 * them on the receiving phone. Pure logic — unit tested; [com.railbite.core.mesh.BleMeshTransport]
 * only wraps these frames in advertiser/scanner calls.
 *
 * Frame layout (the data portion of a Bluetooth manufacturer-specific AD structure):
 * ```
 * [ msgHash: 4 bytes ][ chunkIdx: 1 ][ chunkTotal: 1 ][ data: <= MAX_CHUNK-6 bytes ]
 * ```
 * `msgHash` = first 4 bytes of SHA-256 over the whole packet, so a phone hearing any
 * frame of the same packet can bucket them together; `chunkTotal` lets it know when
 * reassembly is possible.
 */
object MeshChunker {

    const val MAX_CHUNK = 20 // conservative budget for legacy adv payload data

    /** Re-broadcast amplification guard: never chunk more than this per packet. */
    const val MAX_TOTAL_CHUNKS = 64

    fun msgHash(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes).copyOfRange(0, 4)

    /** Splits [bytes] into frames; empty input yields a single empty-data frame. */
    fun chunk(bytes: ByteArray): List<ByteArray> {
        val hash = msgHash(bytes)
        val dataPerChunk = MAX_CHUNK - 6
        val total = maxOf(1, (bytes.size + dataPerChunk - 1) / dataPerChunk).coerceAtMost(MAX_TOTAL_CHUNKS)
        val frames = ArrayList<ByteArray>(total)
        for (idx in 0 until total) {
            val from = idx * dataPerChunk
            val to = minOf(bytes.size, from + dataPerChunk)
            val data = if (from >= bytes.size) ByteArray(0) else bytes.copyOfRange(from, to)
            val frame = ByteArray(6 + data.size)
            hash.copyInto(frame, 0)
            frame[4] = idx.toByte()
            frame[5] = total.toByte()
            data.copyInto(frame, 6)
            frames.add(frame)
        }
        return frames
    }

    /** Accumulates frames of one packet; emits the full bytes when every chunk arrived. */
    class Reassembler {

        private val buffers = HashMap<String, Slot>()

        /** Offer one received frame; returns the complete payload once (then forgets it). */
        fun offer(frame: ByteArray): ByteArray? {
            if (frame.size < 6) return null
            val total = frame[5].toInt() and 0xFF
            if (total < 1 || total > MAX_TOTAL_CHUNKS) return null
            val idx = frame[4].toInt() and 0xFF
            if (idx >= total) return null
            val key = frame.copyOfRange(0, 4).joinToString("") { "%02x".format(it) }

            val slot = buffers.getOrPut(key) { Slot(total) }
            if (slot.total != total) return null
            if (idx >= slot.chunks.size) return null
            if (slot.chunks[idx] != null) return null // duplicate chunk
            slot.chunks[idx] = frame.copyOfRange(6, frame.size)
            slot.held++

            if (slot.held < slot.total) return null
            val complete = slot.chunks.fold(ByteArray(0)) { acc, chunk -> acc + (chunk ?: ByteArray(0)) }
            buffers.remove(key)
            return complete
        }

        fun pendingCount(): Int = buffers.size

        private class Slot(val total: Int) {
            val chunks = arrayOfNulls<ByteArray>(total)
            var held = 0
        }
    }
}
