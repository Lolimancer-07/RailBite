package com.railbite.core.mesh

import kotlinx.serialization.Serializable

/**
 * Wire envelope for the Bluetooth-mesh relay, ported from `tools/relay-sim/src/relay.ts`
 * (RelayPacket). Every phone that hears a packet relays it through the rake.
 *
 * Two packet types share the envelope:
 *  - [TYPE_ORDER]: a passenger's order travelling towards the pantry phone (the "sink").
 *    [payloadB64] carries the compact order JSON ([MeshOrderPayload]).
 *  - [TYPE_STATUS]: a pantry outcome travelling back towards the passenger so their
 *    timeline advances fully offline. [tag] carries the state; [payloadB64] mirrors it.
 *
 * Field semantics match the simulator:
 *  - [v]        envelope version (1)
 *  - [orderId]  client order UUID — the dedup key for ORDER packets on every node
 *  - [ttl]      hop budget; decremented once per relaying phone, dropped at <= 0
 *  - [origin]   stable node id of the phone that created the packet
 *  - [payloadB64] base64 of the packet payload (order JSON / status word)
 *  - [hmac8]    first 8 hex chars of HMAC-SHA256("orderId|origin|payloadB64") under the
 *               shared trip secret — cheap anti-spoofing for the demo
 *  - [hops]     node ids of the phones that relayed so far (loop prevention)
 */
@Serializable
data class MeshPacket(
    val v: Int = 1,
    val orderId: String,
    val ttl: Int,
    val origin: String,
    val payloadB64: String,
    val hmac8: String,
    val hops: List<String> = emptyList(),
    /** [TYPE_ORDER] or [TYPE_STATUS]; defaults to ORDER for pre-1.2.2 peers. */
    val type: String = TYPE_ORDER,
    /** For [TYPE_STATUS]: the ledger state being announced. Empty for order packets. */
    val tag: String = "",
) {
    companion object {
        const val INITIAL_TTL = 16 // sim default: crosses a 24-coach rake with margin
        const val DEDUP_CACHE_MAX = 500

        const val TYPE_ORDER = "ORDER"
        const val TYPE_STATUS = "STATUS"

        /** Pantry outcomes announced back to the passenger (mesh ledger states). */
        const val STATUS_ACCEPTED = "ACCEPTED"
        const val STATUS_OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY"
        const val STATUS_DELIVERED = "DELIVERED"
        const val STATUS_REJECTED = "REJECTED"
    }

    /**
     * Dedup key on every node. Orders dedupe by orderId; statuses must dedupe per
     * state — otherwise the first ACCEPTED announcement would swallow the later
     * DELIVERED one on every relay phone.
     */
    val dedupKey: String
        get() = if (type == TYPE_STATUS) "$type:$orderId:$tag" else orderId
}
