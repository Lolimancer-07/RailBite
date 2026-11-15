package com.railbite.customer.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.model.Order
import com.railbite.core.util.Statuses
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OrdersUiState(
    val loading: Boolean = true,
    val active: List<Order> = emptyList(),
    val past: List<Order> = emptyList(),
    val error: String? = null,
    /** Bluetooth-relay orders still waiting to reach the cloud (offline feature). */
    val relayPending: List<com.railbite.core.mesh.MeshOrderPayload> = emptyList(),
)

class OrdersViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.active.isEmpty() && it.past.isEmpty()) }
            while (isActive) {
                loadOnce()
                val anyActive = _state.value.active.any { !Statuses.isTerminal(it.status) }
                if (!anyActive) break
                delay(3_000)
            }
        }
    }

    private suspend fun loadOnce() {
        // Relay orders first (offline feature): pending outbox entries shown as local cards.
        val relayPending = runCatching {
            com.railbite.core.mesh.MeshOutbox(container.app).all()
                .filter { it.state != "SYNCED" }
                .mapNotNull { com.railbite.core.mesh.MeshOrderPayload.decode(it.payload) }
        }.getOrDefault(emptyList())
        try {
            val pnr = container.settings.pnr.first()
            if (pnr.isBlank()) {
                _state.update { it.copy(loading = false, active = emptyList(), past = emptyList(), relayPending = relayPending) }
                return
            }
            val orders = container.client.api().ordersByPnr(pnr)
            _state.update {
                it.copy(
                    loading = false,
                    active = orders.filter { o -> !Statuses.isTerminal(o.status) },
                    past = orders.filter { o -> Statuses.isTerminal(o.status) },
                    error = null,
                    relayPending = relayPending,
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    loading = false,
                    error = if (relayPending.isEmpty()) "Cannot reach server — retrying" else null,
                    relayPending = relayPending,
                )
            }
        }
    }
}
