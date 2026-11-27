package com.railbite.picker.ui.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.model.LedgerEntry
import com.railbite.core.model.LedgerSummaryRow
import com.railbite.core.model.PickerStats
import com.railbite.picker.PickerContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class CashFilter {
    ALL,
    CONFIRMED,
    AWAITING,
}

data class CashUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val staffId: String = "",
    val stats: PickerStats? = null,
    val myRow: LedgerSummaryRow? = null,
    val entries: List<LedgerEntry> = emptyList(),
    val selectedFilter: CashFilter = CashFilter.ALL,
    val lastUpdatedLabel: String = "",
    val error: String? = null,
) {
    val filteredEntries: List<LedgerEntry>
        get() = when (selectedFilter) {
            CashFilter.ALL -> entries
            CashFilter.CONFIRMED -> entries.filter { it.passengerConfirmed }
            CashFilter.AWAITING -> entries.filter { !it.passengerConfirmed }
        }

    val confirmedCount: Int get() = entries.count { it.passengerConfirmed }
    val awaitingCount: Int get() = entries.count { !it.passengerConfirmed }
}

/** Shift Cash Ledger & Reconciliation for this picker. */
class CashSummaryViewModel(private val container: PickerContainer) : ViewModel() {

    private val _state = MutableStateFlow(CashUiState())
    val state: StateFlow<CashUiState> = _state.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        refresh()
    }

    fun selectFilter(filter: CashFilter) {
        _state.update { it.copy(selectedFilter = filter) }
    }

    fun refresh() {
        _state.update {
            it.copy(
                loading = it.stats == null,
                refreshing = it.stats != null,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val api = container.client.api()
                val staffId = container.settings.staffId.first()
                val ledgerRes = runCatching { api.staffLedger(staffId) }.getOrNull()
                val summaryRows = runCatching { api.ledgerSummary() }.getOrNull()
                val myRow = summaryRows?.firstOrNull { it.captainId == staffId }

                if (ledgerRes != null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            staffId = staffId,
                            stats = ledgerRes.summary,
                            myRow = myRow,
                            entries = ledgerRes.entries,
                            lastUpdatedLabel = LocalTime.now().format(timeFormatter),
                            error = null,
                        )
                    }
                } else {
                    val stats = api.staffStats(staffId)
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            staffId = staffId,
                            stats = stats,
                            myRow = myRow,
                            entries = emptyList(),
                            lastUpdatedLabel = LocalTime.now().format(timeFormatter),
                            error = null,
                        )
                    }
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = "Cannot reach server — check Wi-Fi",
                    )
                }
            }
        }
    }
}
