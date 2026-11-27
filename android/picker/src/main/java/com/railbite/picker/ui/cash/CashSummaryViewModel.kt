package com.railbite.picker.ui.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.mesh.MeshOrderPayload
import com.railbite.core.model.LedgerEntry
import com.railbite.core.model.LedgerSummaryRow
import com.railbite.core.model.PickerStats
import com.railbite.core.util.Statuses
import com.railbite.picker.PickerContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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

/** Shift Cash Ledger & Reconciliation (LAN + offline BLE delivered entries). */
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
            val staffId = container.settings.staffId.first()
            val staffName = container.settings.staffName.first()
            val localDelivered = runCatching {
                container.meshLedger.all()
                    .filter { it.state == Statuses.DELIVERED }
                    .mapNotNull { entry ->
                        val payload = MeshOrderPayload.decode(entry.payload) ?: return@mapNotNull null
                        val collected = entry.cashPaise ?: payload.totalPaise
                        LedgerEntry(
                            orderId = payload.orderId,
                            coach = payload.coach,
                            berth = payload.berth,
                            paymentMode = "COD",
                            totalPaise = payload.totalPaise,
                            collectedPaise = collected,
                            passengerConfirmed = entry.passengerConfirmed,
                            deliveredAt = entry.createdAt,
                        )
                    }
            }.getOrDefault(emptyList())

            if (!container.transportRouter.checkLanReachable()) {
                val expected = localDelivered.sumOf { it.totalPaise }
                val collected = localDelivered.sumOf { it.collectedPaise }
                val confirmed = localDelivered.filter { it.passengerConfirmed }.sumOf { it.collectedPaise }
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        staffId = staffId,
                        stats = PickerStats(
                            staffId = staffId,
                            staffName = staffName.ifBlank { staffId.uppercase() },
                            date = LocalDate.now().toString(),
                            ordersDelivered = localDelivered.size,
                            expectedPaise = expected,
                            collectedPaise = collected,
                            passengerConfirmedPaise = confirmed,
                        ),
                        myRow = null,
                        entries = localDelivered,
                        lastUpdatedLabel = LocalTime.now().format(timeFormatter),
                        error = null,
                    )
                }
                return@launch
            }

            try {
                runCatching { container.meshGateway.replayPending() }
                val api = container.client.api()
                val ledgerRes = runCatching { api.staffLedger(staffId) }.getOrNull()
                val summaryRows = runCatching { api.ledgerSummary() }.getOrNull()
                val myRow = summaryRows?.firstOrNull { it.captainId == staffId }

                val serverEntries = ledgerRes?.entries.orEmpty()
                val serverIds = serverEntries.map { it.orderId }.toSet()
                val extraOffline = runCatching {
                    container.meshLedger.all()
                        .filter { it.state == Statuses.DELIVERED }
                        .mapNotNull { entry ->
                            val payload = MeshOrderPayload.decode(entry.payload) ?: return@mapNotNull null
                            if (payload.orderId in serverIds) return@mapNotNull null
                            val collected = entry.cashPaise ?: payload.totalPaise
                            LedgerEntry(
                                orderId = payload.orderId,
                                coach = payload.coach,
                                berth = payload.berth,
                                paymentMode = "COD",
                                totalPaise = payload.totalPaise,
                                collectedPaise = collected,
                                passengerConfirmed = entry.passengerConfirmed,
                                deliveredAt = entry.createdAt,
                            )
                        }
                }.getOrDefault(emptyList())

                val baseSummary = ledgerRes?.summary ?: api.staffStats(staffId)
                val extraExpected = extraOffline.sumOf { it.totalPaise }
                val extraCollected = extraOffline.sumOf { it.collectedPaise }
                val extraConfirmed = extraOffline.filter { it.passengerConfirmed }.sumOf { it.collectedPaise }

                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        staffId = staffId,
                        stats = baseSummary.copy(
                            ordersDelivered = baseSummary.ordersDelivered + extraOffline.size,
                            expectedPaise = baseSummary.expectedPaise + extraExpected,
                            collectedPaise = baseSummary.collectedPaise + extraCollected,
                            passengerConfirmedPaise = baseSummary.passengerConfirmedPaise + extraConfirmed,
                        ),
                        myRow = myRow,
                        entries = extraOffline + serverEntries,
                        lastUpdatedLabel = LocalTime.now().format(timeFormatter),
                        error = null,
                    )
                }
            } catch (_: Exception) {
                val expected = localDelivered.sumOf { it.totalPaise }
                val collected = localDelivered.sumOf { it.collectedPaise }
                val confirmed = localDelivered.filter { it.passengerConfirmed }.sumOf { it.collectedPaise }
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        staffId = staffId,
                        stats = PickerStats(
                            staffId = staffId,
                            staffName = staffName.ifBlank { staffId.uppercase() },
                            date = LocalDate.now().toString(),
                            ordersDelivered = localDelivered.size,
                            expectedPaise = expected,
                            collectedPaise = collected,
                            passengerConfirmedPaise = confirmed,
                        ),
                        entries = localDelivered,
                        lastUpdatedLabel = LocalTime.now().format(timeFormatter),
                        error = null,
                    )
                }
            }
        }
    }
}
