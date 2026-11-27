package com.railbite.core.mesh

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class BleDedupAndSchedulerTest {

    @Test
    fun `dedup cache enforces 500-entry LRU capacity and 10-minute TTL expiry`() {
        var fakeClock = 1_000_000L
        val cache = BleDedupCache(maxEntries = 500, ttlMs = 10 * 60_000L, nowMs = { fakeClock })

        assertTrue(cache.offer("0x11:100"))
        assertFalse(cache.offer("0x11:100")) // immediate duplicate dropped

        // Different packet type or status uses separate key cleanly
        assertTrue(cache.offer("0x12:100:1"))
        assertTrue(cache.offer("0x12:100:2"))
        assertTrue(cache.offer("0x13:100:1:1"))

        // Advance just before 10m -> still deduped
        fakeClock += 9 * 60_000L
        assertFalse(cache.offer("0x11:100"))

        // Advance past 10m -> expired and accepted again
        fakeClock += 11 * 60_000L
        assertTrue(cache.offer("0x11:100"))

        // Fill beyond 500 -> LRU evicts oldest
        cache.clear()
        for (i in 1..505) {
            assertTrue(cache.offer("0x11:$i"))
        }
        assertEquals(500, cache.size())
        // First 5 were evicted; offering 1 returns true again
        assertTrue(cache.offer("0x11:1"))
    }

    @Test
    fun `tx scheduler limits relayed frames to 4 slices and cancels origin order on ACCEPT`() = runBlocking {
        val frames = CopyOnWriteArrayList<Pair<Int, Boolean>>() // size, stopped
        val fakeAdvertiser = object : BleFrameAdvertiser {
            override fun startAdvertising(payload23: ByteArray): Boolean {
                frames.add(payload23.size to false)
                return true
            }

            override fun stopAdvertising() {
                frames.add(0 to true)
            }
        }

        val scheduler = BleTxScheduler(
            advertiser = fakeAdvertiser,
            sliceMs = 20L,
            originRepeatPauseMs = 10L,
        )

        val relayBytes = BlePacketCodec.encode(
            BlePacketCodec.OrderNew(
                orderId = 123u,
                hopCount = 1,
                ttl = 5,
                coach = "B1",
                seat = 2,
                pnrLast4 = 1,
                handoverPin = 1234,
                items = listOf(BlePacketCodec.ItemSlot(1, 1)),
            ),
        )
        scheduler.enqueue(
            BleTxScheduler.TxItem(
                id = "relay:0x11:123",
                payload23 = relayBytes,
                remainingSlices = BleTxScheduler.RELAY_SLICES, // 4 slices
                orderShortId = 123u,
                isOriginOrder = false,
            ),
        )

        delay(220L)
        val startedSlices = frames.count { it.first == 23 && !it.second }
        assertEquals(4, startedSlices)
        assertEquals(0, scheduler.pendingCount())

        // Origin order stays queued until cancelOrder
        scheduler.enqueue(
            BleTxScheduler.TxItem(
                id = "order:456",
                payload23 = relayBytes,
                remainingSlices = 1,
                orderShortId = 456u,
                isOriginOrder = true,
                expiresAtMs = System.currentTimeMillis() + 5_000L,
            ),
        )
        assertTrue(scheduler.hasActiveOriginOrder(456u))
        scheduler.cancelOrder(456u)
        assertFalse(scheduler.hasActiveOriginOrder(456u))
        scheduler.shutdown()
    }
}

