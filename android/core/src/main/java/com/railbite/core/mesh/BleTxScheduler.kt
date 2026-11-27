package com.railbite.core.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Transport abstraction for advertising one 23-byte manufacturer payload at a time. */
interface BleFrameAdvertiser {
    fun startAdvertising(payload23: ByteArray): Boolean
    fun stopAdvertising()
}

/**
 * Time-sliced FIFO BLE advertiser queue (`1500ms` per slice).
 *
 * Android's [android.bluetooth.le.BluetoothLeAdvertiser] cleanly supports one legacy packet at a time.
 * This scheduler rotates active packets in FIFO order:
 * - Relayed packets enqueue with `remainingSlices = 4` (~`6s` total broadcast)
 * - Origin `STATUS_ACK` / `QUICK_MSG` packets enqueue with `10` slices (`15s`)
 * - Origin `ORDER_NEW` packets stay in rotation (with brief cooldown pauses when solo) up to `90s`
 *   until [cancelOrder] is called upon receiving `STATUS_ACK (ACCEPTED)` or user cancellation.
 */
class BleTxScheduler(
    private val advertiser: BleFrameAdvertiser,
    private val sliceMs: Long = DEFAULT_SLICE_MS,
    private val originRepeatPauseMs: Long = DEFAULT_ORIGIN_PAUSE_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onSliceTransmitted: (TxItem) -> Unit = {},
) {

    data class TxItem(
        val id: String,
        val payload23: ByteArray,
        val remainingSlices: Int,
        val orderShortId: UInt? = null,
        val isOriginOrder: Boolean = false,
        val expiresAtMs: Long? = null,
        /** Higher values are advertised before lower values; equal priorities stay FIFO. */
        val priority: Int = 0,
    )

    private val lock = Any()
    private val queue = ArrayDeque<TxItem>()
    private var workerJob: Job? = null

    fun enqueue(item: TxItem) {
        synchronized(lock) {
            queue.removeAll { it.id == item.id }
            // Priority queue: 0x13 QUICK_MSG (30) jumps ahead of STATUS_ACK (20), origin
            // orders (10), and relayed frames (0). Equal priorities keep insertion order.
            val insertAt = queue.indexOfFirst { it.priority < item.priority }
            if (insertAt < 0) {
                queue.addLast(item)
            } else {
                queue.add(insertAt, item)
            }
            if (workerJob?.isActive != true) {
                workerJob = scope.launch { runLoop() }
            }
        }
    }

    /** Stops origin broadcasting for [orderShortId] immediately (e.g., when ACCEPTED arrives). */
    fun cancelOrder(orderShortId: UInt) {
        synchronized(lock) {
            queue.removeAll { it.isOriginOrder && it.orderShortId == orderShortId }
        }
    }

    fun pendingCount(): Int = synchronized(lock) { queue.size }

    fun hasActiveOriginOrder(orderShortId: UInt): Boolean = synchronized(lock) {
        val now = nowMs()
        queue.any {
            it.isOriginOrder &&
                it.orderShortId == orderShortId &&
                (it.expiresAtMs == null || now < it.expiresAtMs)
        }
    }

    fun stopAll() {
        synchronized(lock) {
            queue.clear()
            workerJob?.cancel()
            workerJob = null
        }
        runCatching { advertiser.stopAdvertising() }
    }

    fun shutdown() {
        stopAll()
        scope.cancel()
    }

    private suspend fun runLoop() {
        while (scope.isActive) {
            val next: TxItem? = synchronized(lock) {
                val now = nowMs()
                while (queue.isNotEmpty()) {
                    val head = queue.removeFirst()
                    val expired = head.expiresAtMs != null && now >= head.expiresAtMs
                    val exhausted = !head.isOriginOrder && head.remainingSlices <= 0
                    if (!expired && !exhausted) {
                        return@synchronized head
                    }
                }
                null
            }

            if (next == null) {
                runCatching { advertiser.stopAdvertising() }
                break
            }

            val started = runCatching { advertiser.startAdvertising(next.payload23) }.getOrDefault(false)
            if (started) {
                delay(sliceMs)
                runCatching { advertiser.stopAdvertising() }
                onSliceTransmitted(next)
            } else {
                // Radio temporarily unavailable (e.g. BT toggled off); avoid tight spin.
                delay(500L)
            }

            val queueEmptyAfterPop: Boolean = synchronized(lock) {
                val now = nowMs()
                val shouldKeep = if (next.isOriginOrder) {
                    next.expiresAtMs == null || now < next.expiresAtMs
                } else {
                    (next.remainingSlices - 1) > 0
                }
                if (shouldKeep) {
                    val updated = if (next.isOriginOrder) next else next.copy(remainingSlices = next.remainingSlices - 1)
                    queue.addLast(updated)
                }
                queue.size == 1 && next.isOriginOrder && queue.firstOrNull()?.id == next.id
            }

            // When only the Customer origin ORDER_NEW packet is active, wait a short window
            // between slices so incoming STATUS_ACK scans and other phones' relays run smoothly.
            if (queueEmptyAfterPop && originRepeatPauseMs > 0L) {
                delay(originRepeatPauseMs)
            }
        }
        synchronized(lock) {
            workerJob = null
        }
    }

    companion object {
        const val DEFAULT_SLICE_MS = 1_500L
        const val DEFAULT_ORIGIN_PAUSE_MS = 2_000L
        const val RELAY_SLICES = 4
        const val STATUS_OR_MSG_SLICES = 10 // 10 * 1.5s = 15s
        /** 0x13 chat gets at least 6 slices (~9s) and a higher queue priority than status. */
        const val QUICK_MSG_SLICES = 6
        const val ORIGIN_ORDER_DURATION_MS = 90_000L

        const val PRIORITY_QUICK_MSG = 30
        const val PRIORITY_STATUS = 20
        const val PRIORITY_ORIGIN_ORDER = 10
        const val PRIORITY_RELAY = 0
    }
}

