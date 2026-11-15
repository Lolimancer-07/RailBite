package com.railbite.core.mesh

import kotlinx.serialization.Serializable

/**
 * Wire envelope for the Bluetooth-mesh relay, ported from `tools/relay-sim/src/relay.ts`
 * (RelayPacket). One order travels as one packet; every phone that hears it relays it
 * towards the pantry phone (the "sink").
 *
 * Field semantics match the simulator:
 *  - [v]        envelope version (1)
 *  - [orderId]  client order UUID — also the dedup key on every node
 *  - [ttl]      hop budget; decremented once per relaying phone, dropped at <= 0
 *  - [origin]   stable node id of the phone that created the packet
 *  - [payloadB64] base64 of the compact order JSON (OrderCreateRequest, items include
 *                 name/unit price from the passenger's cached menu so the pantry can
 *                 alert without a server)
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
) {
    companion object {
        const val INITIAL_TTL = 16 // sim default: crosses a 24-coach rake with margin
        const val DEDUP_CACHE_MAX = 500
    }
}
