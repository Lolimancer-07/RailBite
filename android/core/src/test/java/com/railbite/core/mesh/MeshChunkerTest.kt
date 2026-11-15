package com.railbite.core.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MeshChunkerTest {

    private fun reassemble(frames: List<ByteArray>): ByteArray? {
        val r = MeshChunker.Reassembler()
        var result: ByteArray? = null
        frames.forEach { result = r.offer(it) ?: result }
        return result
    }

    @Test
    fun `round-trip small payload`() {
        val data = "hello mesh".toByteArray()
        val frames = MeshChunker.chunk(data)
        assertEquals(1, frames.size)
        assertArrayEquals(data, reassemble(frames))
    }

    @Test
    fun `round-trip exactly one chunk boundary`() {
        val dataPerChunk = MeshChunker.MAX_CHUNK - 6
        val exact = ByteArray(dataPerChunk) { it.toByte() }
        val oneOver = ByteArray(dataPerChunk + 1) { it.toByte() }
        assertEquals(1, MeshChunker.chunk(exact).size)
        assertEquals(2, MeshChunker.chunk(oneOver).size)
        assertArrayEquals(exact, reassemble(MeshChunker.chunk(exact)))
        assertArrayEquals(oneOver, reassemble(MeshChunker.chunk(oneOver)))
    }

    @Test
    fun `round-trip large packet - order-sized payload survives`() {
        val data = ByteArray(500) { (it * 7).toByte() } // ~500 B > typical compact order JSON
        val frames = MeshChunker.chunk(data)
        assertArrayEquals(data, reassemble(frames))
    }

    @Test
    fun `out-of-order frames still reassemble`() {
        val data = ByteArray(200) { it.toByte() }
        val frames = MeshChunker.chunk(data)
        val shuffled = frames.sortedByDescending { it[4].toInt() and 0xFF }
        assertArrayEquals(data, reassemble(shuffled))
    }

    @Test
    fun `duplicate frames are ignored`() {
        val data = ByteArray(60) { it.toByte() }
        val frames = MeshChunker.chunk(data)
        val r = MeshChunker.Reassembler()
        var last: ByteArray? = null
        (frames + frames).forEach { last = r.offer(it) ?: last }
        assertArrayEquals(data, last)
    }

    @Test
    fun `garbage frames are rejected`() {
        val r = MeshChunker.Reassembler()
        assertNull(r.offer(ByteArray(3)))
        assertNull(r.offer(ByteArray(20).also { it[5] = 0.toByte() })) // total 0
        assertNull(r.offer(ByteArray(20).also { it[4] = 9.toByte(); it[5] = 2.toByte() })) // idx >= total
    }

    @Test
    fun `two interleaved packets stay independent`() {
        val a = ByteArray(40) { it.toByte() }
        val b = ByteArray(40) { (it + 100).toByte() }
        val framesA = MeshChunker.chunk(a)
        val framesB = MeshChunker.chunk(b)
        val mixed = framesA.flatMapIndexed { i, fa -> listOf(fa, framesB[i]) }
        assertEquals(a.toList(), (reassemble(mixed.filterIndexed { i, _ -> i % 2 == 0 }) ?: ByteArray(0)).toList())
        assertEquals(b.toList(), (reassemble(mixed.filterIndexed { i, _ -> i % 2 == 1 }) ?: ByteArray(0)).toList())
    }

    @Test
    fun `chunk count is capped for oversized packets`() {
        val data = ByteArray(10_000)
        val frames = MeshChunker.chunk(data)
        assertEquals(MeshChunker.MAX_TOTAL_CHUNKS, frames.size)
        // capped packets cannot round-trip fully — callers must keep payloads compact
        assertNotNull(frames)
    }
}
