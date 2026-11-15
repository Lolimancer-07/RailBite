package com.railbite.core.mesh

/**
 * Relay engine, ported from `tools/relay-sim/src/relay.ts` (CoachNode). Every phone in
 * the rake runs one instance; the pantry phone constructs it with `isSink = true`.
 *
 * Pipeline per received packet (same order as the simulator):
 *  1. HMAC check — drop tampered/foreign packets before they touch any state
 *  2. Dedup — LRU cache keyed by orderId (max [MeshPacket.DEDUP_CACHE_MAX]); broadcast
 *     storms and repeated re-broadcasts die here
 *  3. Loop prevention — drop when this node originated the packet or already relayed it
 *  4. TTL — decrement once per relaying phone; drop at <= 0
 *  5. Sink check — the pantry phone consumes the packet and stops flooding
 *  6. Forward — re-broadcast with ttl-1 and this node appended to hops
 */
class MeshRelay(
    /** Stable id of this phone on the mesh (settings-generated UUID). */
    private val selfId: String,
    /** True for the pantry phone: consumes packets instead of relaying them. */
    private val isSink: () -> Boolean,
    /** Shared trip secret used for the hmac8 integrity tag. */
    private val hmacSecret: () -> String,
    /** Re-broadcast callback (transport responsibility: chunk + advertise). */
    private val forward: (MeshPacket) -> Unit,
    /** Sink callback: a packet reached the pantry phone. */
    private val onSink: (MeshPacket) -> Unit,
) {

    /** Insertion-ordered set — oldest eviction matches the simulator's LRU cache. */
    private val lruCache = LinkedHashSet<String>()

    /** Orders this node has relayed at least once (observability for tests/UI). */
    var relayedCount = 0
        private set

    /**
     * Handle one incoming packet. Returns false when the packet was dropped
     * (duplicate, loop, expired, tampered, invalid) — true when it was consumed
     * (delivered to sink or forwarded).
     */
    fun receive(packet: MeshPacket): Boolean {
        if (packet.v != 1 || !MeshCodec.verify(packet, hmacSecret())) return false

        // 2. Deduplication (LRU cache, max 500)
        if (lruCache.contains(packet.orderId)) return false
        if (lruCache.size >= MeshPacket.DEDUP_CACHE_MAX) {
            val oldest = lruCache.firstOrNull() ?: return false
            lruCache.remove(oldest)
        }
        lruCache.add(packet.orderId)

        // 3. Loop prevention
        if (packet.origin == selfId || packet.hops.contains(selfId)) return false

        // 4. TTL decrement
        val newTtl = packet.ttl - 1
        if (newTtl <= 0) return false

        // 5. Sink check — pantry phone records and stops
        if (isSink()) {
            onSink(packet)
            return true
        }

        // 6. Forward to adjacent peers (whoever hears the advertisement)
        relayedCount++
        forward(packet.copy(ttl = newTtl, hops = packet.hops + selfId))
        return true
    }
}
