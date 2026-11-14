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
        try {
            val pnr = container.settings.pnr.first()
            if (pnr.isBlank()) {
                _state.update { it.copy(loading = false, active = emptyList(), past = emptyList()) }
                return
            }
            val orders = container.client.api().ordersByPnr(pnr)
            _state.update {
                it.copy(
                    loading = false,
                    active = orders.filter { o -> !Statuses.isTerminal(o.status) },
                    past = orders.filter { o -> Statuses.isTerminal(o.status) },
                    error = null,
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(loading = false, error = "Cannot reach server — retrying")
            }
        }
    }
}
