package com.railbite.customer.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.api.ApiClient
import com.railbite.core.model.Order
import com.railbite.core.model.OrderCreateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class CartUiState(
    val placing: Boolean = false,
    val error: String? = null,
)

class CartViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state.asStateFlow()

    /** Cart lines + diners combined for the summary. */
    val lines = container.cart.lines
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val seat = com.railbite.core.data.SeatSnapshot(container.settings)
        .seat.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val itemTotalPaise: Int get() = container.cart.totalPaise

    fun add(itemId: String) {
        container.cart.lines.value[itemId]?.let { container.cart.add(it.item) }
    }

    fun decrement(itemId: String) = container.cart.decrement(itemId)

    /**
     * Places the order:
     * - client generates the UUID (server uses it as an idempotency key)
     * - sends ids + quantities only — the server is authoritative on prices/stock
     */
    fun placeOrder(onPlaced: (String) -> Unit) {
        val linesMap = container.cart.lines.value
        if (linesMap.isEmpty()) return
        _state.update { it.copy(placing = true, error = null) }
        viewModelScope.launch {
            try {
                val seat = container.settings.let { s ->
                    com.railbite.core.data.SeatSnapshot.Seat(
                        pnr = s.pnr.first(),
                        coach = s.coach.first(),
                        berth = s.berth.first(),
                        diners = s.diners.first(),
                    )
                }
                val orderId = UUID.randomUUID().toString()
                val body = OrderCreateRequest(
                    id = orderId,
                    pnr = seat.pnr,
                    coach = seat.coach,
                    berth = seat.berth,
                    diners = seat.diners,
                    paymentMode = "COD",
                    items = linesMap.values.map { OrderCreateRequest.CreateItem(it.item.id, it.qty) },
                    createdAt = java.time.Instant.now().toString(),
                    source = "WIFI",
                )
                val order = container.client.api().createOrder(body)
                container.cart.clear()
                container.settings.setActiveOrderId(order.id)
                container.mqtt.watchOrder(order.id)
                onPlaced(order.id)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        placing = false,
                        error = friendly(e),
                    )
                }
            }
        }
    }

    private fun friendly(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("Insufficient stock", ignoreCase = true) ->
                "That item just sold out — adjust your cart and try again"
            msg.contains("PNR", ignoreCase = true) && msg.contains("manifest", ignoreCase = true) ->
                "PNR not on this train's manifest — check your seat details"
            msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ->
                "Cannot reach the pantry server — check Settings → server URL"
            else -> "Could not place order: $msg"
        }
    }
}
