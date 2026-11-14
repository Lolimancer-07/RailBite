package com.railbite.picker.ui.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.model.LedgerSummaryRow
import com.railbite.core.model.PickerStats
import com.railbite.picker.PickerContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CashUiState(
    val loading: Boolean = true,
    val stats: PickerStats? = null,
    val myRow: LedgerSummaryRow? = null,
    val error: String? = null,
)

/** Expected vs collected vs passenger-confirmed cash for today. */
class CashSummaryViewModel(private val container: PickerContainer) : ViewModel() {

    private val _state = MutableStateFlow(CashUiState())
    val state: StateFlow<CashUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = it.stats == null) }
        viewModelScope.launch {
            try {
                val api = container.client.api()
                val staffId = container.settings.staffId.first()
                val stats = api.staffStats(staffId)
                val myRow = api.ledgerSummary().firstOrNull { it.captainId == staffId }
                _state.update {
                    it.copy(loading = false, stats = stats, myRow = myRow, error = null)
                }
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, error = "Cannot reach server") }
            }
        }
    }
}
