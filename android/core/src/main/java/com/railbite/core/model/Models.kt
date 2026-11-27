package com.railbite.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the edge-server /menu response (packages/shared MenuItem). */
@Serializable
data class MenuItem(
    val id: String,
    val name: String,
    val category: String,
    val track: String, // READY | COOK
    val mrpPaise: Int,
    val stock: Int,
    val prepMinutes: Int,
    val isVeg: Boolean = true,
    val active: Boolean = true,
)

/** Mirrors GET /health. */
@Serializable
data class Health(
    val ok: Boolean,
    val tripId: String,
    val time: String,
    val version: String,
)

@Serializable
data class OrderItem(
    val itemId: String,
    val qty: Int,
    val unitPricePaise: Int,
    val name: String? = null,
    val category: String? = null,
)

/** Passenger feedback stored on the order ("Did you receive this order?" + stars). */
@Serializable
data class FeedbackRead(
    val received: Boolean,
    val rating: Int? = null,
    val reasons: List<String>? = null,
)

/** Mirrors the edge-server Order JSON (packages/shared OrderSchema). Money is integer paise. */
@Serializable
data class Order(
    val id: String,
    val pnr: String,
    val coach: String,
    val berth: Int,
    val diners: Int,
    val track: String,
    val status: String,
    val paymentMode: String,
    val totalPaise: Int,
    val source: String,
    val verified: Int,
    val createdAt: String,
    val updatedAt: String,
    /** 4-digit handover code; only present in the passenger view (?role defaults to passenger). */
    val deliveryCode: String? = null,
    val feedback: FeedbackRead? = null,
    val items: List<OrderItem>? = null,
)

/** POST /orders body — ids and quantities only; the server is authoritative on prices. */
@Serializable
data class OrderCreateRequest(
    val id: String, // client-generated UUID, used as idempotency key
    val pnr: String,
    val coach: String,
    val berth: Int,
    val diners: Int,
    val paymentMode: String = "COD",
    val items: List<CreateItem>,
    val createdAt: String,
    val source: String = "WIFI",
) {
    @Serializable
    data class CreateItem(val itemId: String, val qty: Int)
}

@Serializable
data class MeshOrderSyncRequest(
    val id: String,
    val pnr: String,
    val coach: String,
    val berth: Int,
    val diners: Int = 1,
    val paymentMode: String = "COD",
    val items: List<OrderCreateRequest.CreateItem>,
    val createdAt: String,
    val status: String = "PLACED",
    val captainId: String? = null,
    val deliveryCode: String? = null,
    val cashPaise: Int? = null,
    val passengerConfirmed: Boolean? = null,
)

@Serializable
data class CaptainActionRequest(
    val captainId: String,
)

@Serializable
data class DeliverRequest(
    val captainId: String,
    val cashPaise: Int? = null,
    val deliveryCode: String? = null,
)

/** POST /staff/login request — password is the production credential; pin is legacy. */
@Serializable
data class StaffLoginRequest(
    val staffId: String,
    val password: String? = null,
    val pin: String? = null,
)

@Serializable
data class StaffLoginResponse(
    val staffId: String,
    val name: String,
    val role: String,
    val zoneCoaches: List<String> = emptyList(),
    /** Legacy alias for the access token (pre-auth pantry APKs). */
    val token: String = "",
    val expiresAt: String = "",
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: String? = null,
)

// ---------- Auth (Firebase Phone Auth / staff password) ----------

@Serializable
data class FirebaseLoginRequest(val idToken: String, val name: String? = null)

@Serializable
data class DevLoginRequest(val phone: String, val name: String? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class MeResponse(val ok: Boolean = true, val user: com.railbite.core.auth.AuthUser)

@Serializable
data class SimpleAck(val ok: Boolean = true)

/** GET /staff/:id/stats — today's picker stats. Money is integer paise. */
@Serializable
data class PickerStats(
    val staffId: String,
    val staffName: String,
    val date: String,
    val ordersDelivered: Int,
    val expectedPaise: Int,
    val collectedPaise: Int,
    val passengerConfirmedPaise: Int,
    val avgRating: Double? = null,
    val feedbackCount: Int = 0,
)

/** GET /ledger/summary rows. */
@Serializable
data class LedgerSummaryRow(
    val captainId: String,
    val captainName: String,
    val expectedPaise: Int,
    val collectedPaise: Int,
    val passengerConfirmedPaise: Int,
    val mismatch: Boolean,
)

/** Per-order entry returned by GET /staff/{id}/ledger. */
@Serializable
data class LedgerEntry(
    val orderId: String,
    val coach: String,
    val berth: Int,
    val paymentMode: String,
    val totalPaise: Int,
    val collectedPaise: Int,
    val passengerConfirmed: Boolean,
    val deliveredAt: String,
)

/** GET /staff/{id}/ledger response payload. */
@Serializable
data class StaffLedgerResponse(
    val staffId: String,
    val summary: PickerStats,
    val entries: List<LedgerEntry> = emptyList(),
)

// ---------- MQTT payloads (packages/shared/src/schemas/mqtt.ts) ----------

/** Retained payload on rb/{tripId}/orders/{orderId}/status. */
@Serializable
data class MqttOrderStatus(
    val status: String,
    val updatedAt: String,
    val captainName: String? = null,
    val etaMin: Int? = null,
)

/** Payload on rb/{tripId}/captain/{captainId}/alerts. */
@Serializable
data class MqttOrderAlert(
    val orderId: String,
    val coach: String,
    val berth: Int,
    val items: List<AlertItem>,
    val total: Int,
    val track: String,
    val attempt: Int,
    val expiresAt: String,
    val pnr: String,
    val diners: Int,
    val verified: Int,
    /** COD | UPI_QUEUED — drives the collect-cash vs digital badge. */
    val paymentMode: String = "COD",
) {
    @Serializable
    data class AlertItem(
        val name: String,
        val qty: Int,
        @SerialName("unitPricePaise") val unitPricePaise: Int,
    )
}

/** POST /complaints body (Tier 1 only in the prototype apps). */
@Serializable
data class ComplaintCreateRequest(
    val id: String,
    val orderId: String,
    val type: String,
    val text: String? = null,
    val createdAt: String,
)

/** Quick message between customer and picker (REST + MQTT `rb/{trip}/orders/{orderId}/chat`). */
@Serializable
data class OrderMessage(
    val id: String,
    val orderId: String,
    val senderRole: String,
    val senderId: String? = null,
    val presetCode: String? = null,
    val body: String,
    val createdAt: Long,
)

/** POST /orders/{id}/messages body — id is client UUID for idempotency. */
@Serializable
data class OrderMessageCreateRequest(
    val id: String,
    val senderRole: String? = null,
    val senderId: String? = null,
    val presetCode: String? = null,
    val body: String,
)

data class ChatPreset(
    val code: String,
    val body: String,
)

object ChatPresets {
    const val MAX_BODY_LENGTH = 120

    val CUSTOMER = listOf(
        ChatPreset("WHERE_ARE_YOU", "Where are you?"),
        ChatPreset("AT_SEAT", "I'm at my seat"),
        ChatPreset("NEAR_DOOR", "Near coach door"),
        ChatPreset("NEED_CHANGE", "Please bring change"),
    )

    val PICKER = listOf(
        ChatPreset("IM_HERE", "I'm here at your seat"),
        ChatPreset("ENTERING_COACH", "Entering your coach"),
        ChatPreset("TWO_MINS", "2 mins away"),
        ChatPreset("PACKING", "Packing your order"),
    )
}
