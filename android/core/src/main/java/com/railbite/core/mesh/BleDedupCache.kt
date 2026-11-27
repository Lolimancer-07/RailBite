package com.railbite.core.mesh

/**
 * Thread-safe LRU deduplication cache (`maxEntries = 500`, `ttlMs = 10 minutes`)
 * for decoded binary BLE mesh packets.
 */
class BleDedupCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val entries = LinkedHashMap<String, Long>(maxEntries, 0.75f, true)

    /**
     * Records [key] if unseen or expired and returns `true`.
     * Returns `false` immediately when [key] was seen recently within [ttlMs].
     */
    @Synchronized
    fun offer(key: String): Boolean {
        val now = nowMs()
        pruneExpiredLocked(now)
        val seenAt = entries[key]
        if (seenAt != null && now - seenAt < ttlMs) {
            return false
        }
        while (entries.size >= maxEntries) {
            val eldestKey = entries.entries.firstOrNull()?.key ?: break
            entries.remove(eldestKey)
        }
        entries[key] = now
        return true
    }

    /** Marks a locally originated packet key as seen so this node will not relay its own flood. */
    @Synchronized
    fun markSeen(key: String) {
        val now = nowMs()
        pruneExpiredLocked(now)
        while (entries.size >= maxEntries) {
            val eldestKey = entries.entries.firstOrNull()?.key ?: break
            entries.remove(eldestKey)
        }
        entries[key] = now
    }

    @Synchronized
    fun contains(key: String): Boolean {
        val now = nowMs()
        pruneExpiredLocked(now)
        val seenAt = entries[key] ?: return false
        return now - seenAt < ttlMs
    }

    @Synchronized
    fun size(): Int {
        pruneExpiredLocked(nowMs())
        return entries.size
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun pruneExpiredLocked(now: Long) {
        val iter = entries.entries.iterator()
        while (iter.hasNext()) {
            val item = iter.next()
            if (now - item.value >= ttlMs) {
                iter.remove()
            } else {
                // LinkedHashMap in access-order keeps older/stale accesses ahead when idle,
                // continue scanning briefly is O(n <= 500) which is negligible.
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 500
        const val DEFAULT_TTL_MS = 10 * 60_000L
    }
}

