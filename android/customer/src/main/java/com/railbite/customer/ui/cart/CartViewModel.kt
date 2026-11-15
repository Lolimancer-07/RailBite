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
    /** COD (default) or UPI_QUEUED — picker still verifies handover by code either way. */
    val paymentMode: String = "COD",
    /** Set when the order went out over the Bluetooth relay instead of the cloud. */
    val relayedCode: String? = null,
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
    /** COD or UPI_QUEUED; relayed (offline) orders are always COD — cash at handover. */
    fun setPaymentMode(mode: String) {
        _state.update { it.copy(paymentMode = mode) }
    }

    fun placeOrder(onPlaced: (String) -> Unit) {
        val linesMap = container.cart.lines.value
        if (linesMap.isEmpty()) return
        val paymentMode = _state.value.paymentMode
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
                    paymentMode = paymentMode,
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
                // Cloud path failed: fall back to the Bluetooth relay when enabled
                // (offline feature — the cloud remains the default transport). Relay
                // orders settle in cash at handover, so UPI selections stay online-only.
                if (paymentMode == "COD" && isUnreachable(e) && container.settings.meshRelayEnabled.first()) {
                    val relayed = relayViaMesh(linesMap)
                    if (relayed != null) {
                        container.cart.clear()
                        _state.update {
                            it.copy(placing = false, error = null, relayedCode = relayed.deliveryCode)
                        }
                        onPlaced(relayed.orderId)
                        return@launch
                    }
                }
                _state.update {
                    it.copy(
                        placing = false,
                        error = friendly(e),
                    )
                }
            }
        }
    }

    /** Broadcasts the cart as a mesh packet and queues it for cloud sync. */
    private suspend fun relayViaMesh(
        linesMap: Map<String, com.railbite.customer.CartLine>,
    ): com.railbite.core.mesh.MeshOrderPayload? {
        val seat = com.railbite.core.data.SeatSnapshot(container.settings).seat.first() ?: return null
        return try {
            val orderId = UUID.randomUUID().toString()
            val items = linesMap.values.map {
                com.railbite.core.mesh.MeshOrderPayload.Item(
                    itemId = it.item.id,
                    name = it.item.name,
                    qty = it.qty,
                    unitPricePaise = it.item.mrpPaise,
                )
            }
            container.meshSender.send(
                orderId = orderId,
                pnr = seat.pnr,
                coach = seat.coach,
                berth = seat.berth,
                diners = seat.diners,
                items = items,
                totalPaise = container.cart.totalPaise,
            )
        } catch (_: Exception) {
            null // no BT hardware/permission — surface the normal cloud error instead
        }
    }

    private fun isUnreachable(e: Exception): Boolean {
        val msg = e.message ?: ""
        return msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("Failed to connect", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("Network is unreachable", ignoreCase = true) ||
            msg.contains("ECONNREFUSED", ignoreCase = true)
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
