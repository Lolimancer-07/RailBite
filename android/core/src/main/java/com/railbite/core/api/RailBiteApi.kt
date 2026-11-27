package com.railbite.core.api

import com.railbite.core.model.CaptainActionRequest
import com.railbite.core.model.ComplaintCreateRequest
import com.railbite.core.model.DeliverRequest
import com.railbite.core.model.DevLoginRequest
import com.railbite.core.model.FirebaseLoginRequest
import com.railbite.core.model.Health
import com.railbite.core.model.LedgerSummaryRow
import com.railbite.core.model.LogoutRequest
import com.railbite.core.model.ManifestLookupResponse
import com.railbite.core.model.MeResponse
import com.railbite.core.model.MenuItem
import com.railbite.core.model.MeshOrderSyncRequest
import com.railbite.core.model.Order
import com.railbite.core.model.OrderCreateRequest
import com.railbite.core.model.OrderMessage
import com.railbite.core.model.OrderMessageCreateRequest
import com.railbite.core.model.PickerStats
import com.railbite.core.model.RefreshRequest
import com.railbite.core.model.SimpleAck
import com.railbite.core.model.StaffLedgerResponse
import com.railbite.core.model.StaffLoginRequest
import com.railbite.core.model.StaffLoginResponse
import com.railbite.core.auth.AuthResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * REST surface of the RailBite edge server (services/edge-server).
 * All money fields are integer paise. Errors: 400 validation, 404, 409 illegal transition,
 * 403 wrong delivery code, 422 out of stock.
 */
interface RailBiteApi {

    @GET("health")
    suspend fun health(): Health

    @GET("manifest/{pnr}")
    suspend fun manifestByPnr(@Path("pnr") pnr: String): ManifestLookupResponse

    @GET("menu")
    suspend fun menu(): List<MenuItem>

    @GET("menu/trending")
    suspend fun trendingMenu(@Query("limit") limit: Int = 6): List<MenuItem>

    @POST("orders")
    suspend fun createOrder(@Body body: OrderCreateRequest): Order

    @POST("orders/mesh-sync")
    suspend fun syncMeshOrder(@Body body: MeshOrderSyncRequest): Order

    @GET("orders")
    suspend fun ordersByPnr(@Query("pnr") pnr: String): List<Order>

    @GET("orders/{id}")
    suspend fun order(
        @Path("id") id: String,
        @Query("role") role: String? = null,
    ): Order

    @POST("orders/{id}/cancel")
    suspend fun cancelOrder(@Path("id") id: String): Order

    @POST("orders/{id}/accept")
    suspend fun acceptOrder(@Path("id") id: String, @Body body: CaptainActionRequest): Order

    @POST("orders/{id}/reject")
    suspend fun rejectOrder(@Path("id") id: String, @Body body: CaptainActionRequest)

    @POST("orders/{id}/packing")
    suspend fun startPacking(@Path("id") id: String, @Body body: CaptainActionRequest): Order

    @POST("orders/{id}/pickup")
    suspend fun pickupOrder(@Path("id") id: String, @Body body: CaptainActionRequest): Order

    @POST("orders/{id}/deliver")
    suspend fun deliverOrder(@Path("id") id: String, @Body body: DeliverRequest): Order

    @POST("orders/{id}/confirm-cash")
    suspend fun confirmCash(@Path("id") id: String): ConfirmCashResponse

    @POST("orders/{id}/feedback")
    suspend fun submitFeedback(@Path("id") id: String, @Body body: FeedbackRequest): FeedbackAck

    @GET("orders/{id}/messages")
    suspend fun orderMessages(@Path("id") id: String): List<OrderMessage>

    @POST("orders/{id}/messages")
    suspend fun sendOrderMessage(
        @Path("id") id: String,
        @Body body: OrderMessageCreateRequest,
    ): OrderMessage

    @POST("staff/login")
    suspend fun staffLogin(@Body body: StaffLoginRequest): StaffLoginResponse

    // --- auth ---

    /** Exchanges a Firebase Phone Auth ID token for a RailBite session. */
    @POST("auth/otp/verify")
    suspend fun verifyFirebase(@Body body: FirebaseLoginRequest): AuthResponse

    /** Local demo sign-in; the server rejects this when NODE_ENV=production. */
    @POST("auth/dev/login")
    suspend fun devLogin(@Body body: DevLoginRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest): SimpleAck

    @GET("auth/me")
    suspend fun me(): MeResponse

    @POST("pickers/{id}/status")
    suspend fun setPickerStatus(@Path("id") id: String, @Body body: PickerStatusRequest): PickerStatusResponse

    @POST("staff/{id}/online")
    suspend fun setStaffOnline(@Path("id") id: String, @Body body: OnlineRequest): OnlineResponse

    @POST("staff/device-token")
    suspend fun registerDeviceToken(@Body body: DeviceTokenRequest): DeviceTokenResponse

    @GET("staff/{id}/stats")
    suspend fun staffStats(@Path("id") id: String): PickerStats

    @GET("staff/{id}/orders")
    suspend fun staffOrders(@Path("id") id: String): List<Order>

    @GET("staff/{id}/ledger")
    suspend fun staffLedger(@Path("id") id: String): StaffLedgerResponse

    @GET("ledger/summary")
    suspend fun ledgerSummary(): List<LedgerSummaryRow>

    @POST("complaints")
    suspend fun createComplaint(@Body body: ComplaintCreateRequest): ComplaintAck

    // --- response shells ---

    @kotlinx.serialization.Serializable
    data class ConfirmCashResponse(val ok: Boolean, val orderId: String, val passengerConfirmed: Boolean)

    @kotlinx.serialization.Serializable
    data class FeedbackRequest(val received: Boolean, val rating: Int? = null, val reasons: List<String>? = null)

    @kotlinx.serialization.Serializable
    data class FeedbackAck(val ok: Boolean, val orderId: String)

    @kotlinx.serialization.Serializable
    data class PickerStatusRequest(val online: Boolean, val trainNumber: String)

    @kotlinx.serialization.Serializable
    data class PickerStatusResponse(
        val ok: Boolean = true,
        val staffId: String,
        val online: Boolean,
        val trainNumber: String,
    )

    @kotlinx.serialization.Serializable
    data class OnlineRequest(val online: Boolean, val trainNumber: String? = null)

    @kotlinx.serialization.Serializable
    data class OnlineResponse(
        val ok: Boolean,
        val staffId: String,
        val online: Boolean,
        val trainNumber: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class DeviceTokenRequest(val staffId: String, val deviceToken: String)

    @kotlinx.serialization.Serializable
    data class DeviceTokenResponse(val ok: Boolean = true, val staffId: String? = null)

    @kotlinx.serialization.Serializable
    data class ComplaintAck(val id: String, val tier: Int, val status: String)
}
