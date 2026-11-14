package com.railbite.customer.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.api.RailBiteApi
import com.railbite.core.model.MqttOrderStatus
import com.railbite.core.model.Order
import com.railbite.core.util.Statuses
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TrackingUiState(
    val loading: Boolean = true,
    val order: Order? = null,
    val status: String = "PLACED",
    val captainName: String? = null,
    val etaMin: Int? = null,
    val error: String? = null,
    val cashConfirming: Boolean = false,
    val cashConfirmed: Boolean = false,
    val cancelled: Boolean = false,
) {
    val delivered: Boolean get() = status == Statuses.DELIVERED
    val failed: Boolean get() = Statuses.isFailed(status)
}

class TrackingViewModel(
    private val container: AppContainer,
    private val orderId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        // MQTT live updates (retained message gives instant current status)
        viewModelScope.launch {
            runCatching { container.mqtt.watchOrder(orderId) }
        }
        // REST fallback poll every 3 s while the order is active
        pollJob = viewModelScope.launch {
            while (isActive) {
                loadOnce()
                val st = _state.value.status
                if (Statuses.isTerminal(st)) break
                delay(3_000)
            }
        }
    }

    fun mqttStatuses(orderId: String): StateFlow<Map<String, MqttOrderStatus>> = container.mqtt.orderStatuses

    /** Latest merged snapshot (REST + MQTT) used by the UI. */
    fun refreshMerged(mqtt: Map<String, MqttOrderStatus>) {
        val mqttStatus = mqtt[orderId]
        val order = _state.value.order
        _state.update { s ->
            s.copy(
                status = mqttStatus?.status ?: s.status,
                captainName = mqttStatus?.captainName ?: s.captainName,
                etaMin = mqttStatus?.etaMin ?: s.etaMin,
            )
        }
        // keep the persisted "active order" pointer in sync
        viewModelScope.launch {
            if (order != null && (order.status != _state.value.status) && Statuses.isTerminal(_state.value.status)) {
                container.settings.setActiveOrderId("")
            }
        }
    }

    private suspend fun loadOnce() {
        try {
            val api = container.client.api()
            val order = api.order(orderId)
            _state.update { s ->
                s.copy(
                    loading = false,
                    order = order,
                    status = order.status,
                    error = null,
                )
            }
            if (Statuses.isTerminal(order.status)) {
                container.settings.setActiveOrderId("")
            }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = friendly(e)) }
        }
    }

    fun confirmCash() {
        _state.update { it.copy(cashConfirming = true) }
        viewModelScope.launch {
            try {
                container.client.api().confirmCash(orderId)
                _state.update { it.copy(cashConfirming = false, cashConfirmed = true) }
            } catch (e: Exception) {
                val notFound = e.message?.contains("404") == true || e.message?.contains("record", true) == true
                _state.update {
                    it.copy(
                        cashConfirming = false,
                        cashConfirmed = notFound || it.cashConfirmed,
                        error = if (notFound) null else "Could not confirm payment — try again",
                    )
                }
            }
        }
    }

    fun cancelOrder() {
        viewModelScope.launch {
            try {
                val order = container.client.api().cancelOrder(orderId)
                _state.update { it.copy(order = order, status = order.status, cancelled = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Cannot cancel: ${e.message}") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        pollJob?.cancel()
        // Keep the MQTT subscription alive briefly for the active-order pill; unwatch on terminal.
        val st = _state.value.status
        if (Statuses.isTerminal(st)) container.mqtt.unwatchOrder(orderId)
        super.onCleared()
    }

    private fun friendly(e: Exception): String {
        val msg = e.message ?: "unknown"
        return if (msg.contains("Unable to resolve", true) || msg.contains("Failed to connect", true) ||
            msg.contains("timeout", true)
        ) {
            "Reconnecting to the pantry server…"
        } else {
            "Order updates may be delayed ($msg)"
        }
    }
}
