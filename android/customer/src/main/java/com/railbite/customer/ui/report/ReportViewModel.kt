package com.railbite.customer.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.model.ComplaintCreateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Tier-1 operational complaint types (Tier 2 is out of scope for the prototype apps). */
val TIER1_TYPES = listOf(
    "COLD_FOOD" to "Food arrived cold",
    "MISSING_ITEM" to "Item missing",
    "BAD_TASTE" to "Bad taste",
    "WRONG_ITEM" to "Wrong item",
)

data class ReportUiState(
    val type: String? = null,
    val text: String = "",
    val sending: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

class ReportViewModel(private val container: AppContainer, private val orderId: String) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun setType(v: String) = _state.update { it.copy(type = v, error = null) }
    fun setText(v: String) = _state.update { it.copy(text = v.take(500)) }

    fun send() {
        val s = _state.value
        val type = s.type ?: run {
            _state.update { it.copy(error = "Pick what went wrong") }
            return
        }
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                container.client.api().createComplaint(
                    ComplaintCreateRequest(
                        id = UUID.randomUUID().toString(),
                        orderId = orderId,
                        type = type,
                        text = s.text.ifBlank { null },
                        createdAt = java.time.Instant.now().toString(),
                    ),
                )
                _state.update { it.copy(sending = false, done = true) }
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, error = "Could not send report — try again") }
            }
        }
    }
}
