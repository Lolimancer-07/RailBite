package com.railbite.core.mesh

import kotlinx.serialization.Serializable

/**
 * Compact order carried inside a [MeshPacket]'s payload. Richer than the REST
 * OrderCreateRequest on purpose: offline there is no server to be authoritative, so
 * the passenger's cart snapshot includes item names/prices (for the pantry alert)
 * and the passenger's own 4-digit handover code (delivery verification).
 *
 * When connectivity returns, the pantry phone replays this payload to the cloud as a
 * normal create → accept → deliver sequence (cloud then owns the record).
 */
@Serializable
data class MeshOrderPayload(
    val orderId: String,
    val pnr: String,
    val coach: String,
    val berth: Int,
    val diners: Int,
    val createdAt: String,
    /** Passenger-generated 4-digit code (server generates its own for cloud orders). */
    val deliveryCode: String,
    val items: List<Item>,
    val totalPaise: Int,
) {
    @Serializable
    data class Item(
        val itemId: String,
        val name: String,
        val qty: Int,
        val unitPricePaise: Int,
    )

    fun encode(): String = MeshStore.json.encodeToString(serializer(), this)

    companion object {
        fun decode(json: String): MeshOrderPayload? = try {
            MeshStore.json.decodeFromString(serializer(), json)
        } catch (_: Exception) {
            null
        }
    }
}

/** Shared mesh integrity secret — must match the edge server's HMAC_SECRET. */
object MeshTrip {
    const val SECRET = "railbite-trip-secret-key-xyz"
}
