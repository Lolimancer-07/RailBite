package com.railbite.picker.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.model.CaptainActionRequest
import com.railbite.core.model.DeliverRequest
import com.railbite.core.model.Order
import com.railbite.core.util.Statuses
import com.railbite.picker.PickerContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.math.BigDecimal
import java.math.RoundingMode

data class ProgressUiState(
    val order: Order? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** itemId -> packed tick; local only, the server only sees the pickup call. */
    val checklist: Map<String, Boolean> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null,
)

class OrderProgressViewModel(
    private val container: PickerContainer,
    private val orderId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var autoPacked = false

    init {
        load()
    }

    /** Loads the order and keeps polling every 3 s until it reaches a terminal state. */
    fun load() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                val o = _state.value.order
                if (o != null && Statuses.isTerminal(o.status)) break
                delay(3_000)
            }
        }
    }

    private suspend fun refreshOnce() {
        try {
            val api = container.client.api()
            var order = api.order(orderId, role = "picker")
            // A fresh accept (from the alert) is immediately followed by the packing step
            if (order.status == Statuses.ACCEPTED && !autoPacked) {
                autoPacked = true
                runCatching {
                    api.startPacking(orderId, CaptainActionRequest(container.settings.staffId.first()))
                }
                order = api.order(orderId, role = "picker")
            }
            _state.update { st ->
                val incoming = order.items
                st.copy(
                    order = order,
                    loading = false,
                    error = null,
                    checklist = if (st.checklist.isEmpty() && incoming != null) {
                        incoming.associate { it.itemId to false }
                    } else {
                        st.checklist
                    },
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = friendly(e)) }
        }
    }

    fun tick(itemId: String) {
        _state.update { st ->
            st.copy(checklist = st.checklist + (itemId to !(st.checklist[itemId] ?: false)))
        }
    }

    /** All items picked -> pick the basket up and head to the coach. */
    fun startPickup() {
        val s = _state.value
        if (s.busy || s.order == null) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val order = container.client.api().pickupOrder(
                    orderId,
                    CaptainActionRequest(container.settings.staffId.first()),
                )
                _state.update { it.copy(order = order, busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    /**
     * Final handover. The picker must repeat the passenger's 4-digit code (403 on
     * mismatch) and reports the cash actually collected (server defaults to the total).
     */
    fun deliver(code: String, cashText: String) {
        val s = _state.value
        if (s.busy || s.order == null) return
        if (code.trim().length != 4) {
            _state.update { it.copy(message = "Enter the 4-digit code shown in the passenger's app") }
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val order = container.client.api().deliverOrder(
                    orderId,
                    DeliverRequest(
                        captainId = container.settings.staffId.first(),
                        cashPaise = parseCash(cashText),
                        deliveryCode = code.trim(),
                    ),
                )
                _state.update { it.copy(order = order, busy = false, message = null) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    /** Clears the current-order pointer and leaves the screen. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            container.settings.setPickerOrderId("")
            onDone()
        }
    }

    private fun parseCash(text: String): Int? = runCatching {
        val cleaned = text.replace("₹", "").replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact()
    }.getOrNull()

    private fun friendly(e: Exception): String {
        val code = (e as? HttpException)?.code()
        return when {
            code == 403 || e.message?.contains("403") == true ->
                "Wrong delivery code — ask the passenger to show it again"
            code == 409 || e.message?.contains("409") == true ->
                "The order is already in that state"
            code == 404 || e.message?.contains("404") == true ->
                "Order no longer exists"
            else -> "Network error — check the train Wi-Fi"
        }
    }
}
