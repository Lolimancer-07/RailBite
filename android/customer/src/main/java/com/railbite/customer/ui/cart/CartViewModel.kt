package com.railbite.customer.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.mesh.BlePacketCodec
import com.railbite.core.mesh.BlePermissions
import com.railbite.core.mesh.MeshOrderPayload
import com.railbite.core.mesh.TransportStatus
import com.railbite.core.model.OrderCreateRequest
import com.railbite.customer.AppContainer
import com.railbite.customer.CartLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class CartUiState(
    val placing: Boolean = false,
    val sendingRelay: Boolean = false,
    val needsBleAction: Boolean = false,
    val error: String? = null,
    val paymentMode: String = "COD",
    val relayedCode: String? = null,
)

class CartViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state.asStateFlow()

    val lines = container.cart.lines
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val seat = com.railbite.core.data.SeatSnapshot(container.settings)
        .seat.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val transportStatus: StateFlow<TransportStatus> = container.transportRouter.status

    val itemTotalPaise: Int get() = container.cart.totalPaise

    fun add(itemId: String) {
        container.cart.lines.value[itemId]?.let { container.cart.add(it.item) }
    }

    fun decrement(itemId: String) = container.cart.decrement(itemId)

    fun setPaymentMode(mode: String) {
        _state.update { it.copy(paymentMode = mode) }
    }

    fun clearError() {
        _state.update { it.copy(error = null, needsBleAction = false) }
    }

    /**
     * Step A: Check if `edge-server` LAN is reachable within `1500ms`. If reachable -> REST/MQTT.
     * Step B: Immediately route through 23-byte BLE Multi-Hop Mesh with zero blocking queues.
     */
    fun placeOrder(onPlaced: (String) -> Unit) {
        val linesMap = container.cart.lines.value
        if (linesMap.isEmpty() || _state.value.placing) return
        val paymentMode = _state.value.paymentMode

        _state.update { it.copy(placing = true, error = null, needsBleAction = false) }
        viewModelScope.launch {
            val seatSnapshot = com.railbite.core.data.SeatSnapshot(container.settings).seat.first()
            if (seatSnapshot == null || !com.railbite.core.util.Validators.isValidTrainNumber(seatSnapshot.trainNumber)) {
                _state.update {
                    it.copy(
                        placing = false,
                        error = "Set your 5-digit Train Number, Coach, and Seat before placing an order",
                    )
                }
                return@launch
            }

            // Step A: Fast 1500ms LAN probe
            val lanReachable = container.transportRouter.checkLanReachable()
            if (lanReachable) {
                try {
                    val orderId = UUID.randomUUID().toString()
                    val body = OrderCreateRequest(
                        id = orderId,
                        trainNumber = seatSnapshot.trainNumber,
                        pnr = seatSnapshot.pnr,
                        coach = seatSnapshot.coach,
                        berth = seatSnapshot.berth,
                        diners = seatSnapshot.diners,
                        paymentMode = paymentMode,
                        items = linesMap.values.map { OrderCreateRequest.CreateItem(it.item.id, it.qty) },
                        createdAt = java.time.Instant.now().toString(),
                        source = "WIFI",
                    )
                    val order = container.client.api().createOrder(body)
                    container.cart.clear()
                    container.settings.setActiveOrderId(order.id)
                    container.mqtt.watchOrder(order.id, order.trainNumber)
                    _state.update { it.copy(placing = false, sendingRelay = false, error = null) }
                    onPlaced(order.id)
                    return@launch
                } catch (e: Exception) {
                    if (!isUnreachable(e) || paymentMode != "COD") {
                        _state.update { it.copy(placing = false, error = friendly(e)) }
                        return@launch
                    }
                    // Transient network failure mid-call: seamlessly fail over to Step B (BLE Mesh)
                }
            }

            // Step B: 100% Offline BLE Multi-Hop Flood Mesh
            if (linesMap.size > BlePacketCodec.MAX_ORDER_ITEMS) {
                _state.update {
                    it.copy(
                        placing = false,
                        sendingRelay = false,
                        error = "Offline BLE Mesh fits up to ${BlePacketCodec.MAX_ORDER_ITEMS} distinct items per order — remove extra items or place as two separate orders.",
                    )
                }
                return@launch
            }

            val missingPerms = BlePermissions.requiredPermissions(container.app)
            if (missingPerms.isNotEmpty()) {
                _state.update {
                    it.copy(
                        placing = false,
                        needsBleAction = true,
                        error = "Allow Nearby Devices (Bluetooth) to broadcast your order over the BLE mesh.",
                    )
                }
                return@launch
            }

            if (!BlePermissions.isBluetoothEnabled(container.app)) {
                _state.update {
                    it.copy(
                        placing = false,
                        needsBleAction = true,
                        error = "Bluetooth is OFF — tap Enable Bluetooth to send your order offline.",
                    )
                }
                return@launch
            }

            _state.update { it.copy(sendingRelay = true) }
            val relayed = container.meshSender.send(
                trainNumber = seatSnapshot.trainNumber,
                pnr = seatSnapshot.pnr,
                coach = seatSnapshot.coach,
                berth = seatSnapshot.berth,
                diners = seatSnapshot.diners,
                items = meshItems(linesMap),
                totalPaise = container.cart.totalPaise,
            )

            if (relayed != null) {
                container.cart.clear()
                container.settings.setActiveOrderId(relayed.orderId)
                _state.update {
                    it.copy(
                        placing = false,
                        sendingRelay = false,
                        error = null,
                        relayedCode = relayed.deliveryCode,
                    )
                }
                onPlaced(relayed.orderId)
            } else {
                _state.update {
                    it.copy(
                        placing = false,
                        sendingRelay = false,
                        needsBleAction = true,
                        error = "Could not start BLE advertiser — ensure Bluetooth is ON cleanly.",
                    )
                }
            }
        }
    }

    private fun meshItems(linesMap: Map<String, CartLine>): List<MeshOrderPayload.Item> =
        linesMap.values.map {
            MeshOrderPayload.Item(
                itemId = it.item.id,
                name = it.item.name,
                qty = it.qty,
                unitPricePaise = it.item.mrpPaise,
            )
        }

    private fun isUnreachable(e: Exception): Boolean {
        val msg = e.message ?: ""
        return msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("Failed to connect", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("Network is unreachable", ignoreCase = true) ||
            msg.contains("ECONNREFUSED", ignoreCase = true) ||
            e is java.io.IOException
    }

    private fun friendly(e: Exception): String {
        val code = (e as? retrofit2.HttpException)?.code()
        val msg = e.message ?: ""
        return when {
            code == 401 || msg.contains("401") ->
                "Session expired — open Settings → Sign out and sign in again"
            msg.contains("Insufficient stock", ignoreCase = true) ->
                "That item just sold out — adjust your cart and try again"
            msg.contains("PNR", ignoreCase = true) && msg.contains("manifest", ignoreCase = true) ->
                "PNR not on this train's manifest — check your seat details"
            else -> "Could not place order: $msg"
        }
    }
}
