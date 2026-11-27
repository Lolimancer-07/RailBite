package com.railbite.picker.ui.cash

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.model.LedgerEntry
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.util.Money
import com.railbite.picker.PickerGraph
import com.railbite.picker.ui.components.PickerBottomBar
import com.railbite.picker.ui.components.PickerErrorBanner
import com.railbite.picker.ui.components.PickerSeatPill
import com.railbite.picker.ui.components.PickerSectionCard
import com.railbite.picker.ui.components.PickerStatusTag
import com.railbite.picker.ui.components.PickerTab
import com.railbite.picker.ui.components.formatLocalTime
import com.railbite.picker.ui.components.shortOrderTag
import com.railbite.picker.ui.theme.PickerCardBorder
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.PickerSelectedBorder
import com.railbite.picker.ui.theme.RailBitePickerPalette

/**
 * Stitch Screen 6 (`64546e68`): Cash Ledger & Reconciliation.
 * Dark-slate 3-metric hero card (`Expected`, `Collected`, `Confirmed`), filter pills
 * (`All`, `Confirmed`, `Awaiting Passenger`), transaction list, and `Orders | Cash` bottom bar.
 */
@Composable
fun CashLedgerScreen(
    graph: PickerGraph,
    onBack: () -> Unit,
    onOpenOrders: () -> Unit,
) {
    val vm = viewModel(key = "cash") { CashSummaryViewModel(graph.container) }
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = RailBitePickerPalette.Canvas,
        topBar = {
            Surface(color = RailBitePickerPalette.SurfaceCard) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RailBitePickerPalette.TextPrimary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "SHIFT RECONCILIATION",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = RailBitePickerPalette.CyanPrimary,
                            )
                            Text(
                                text = "Cash Ledger",
                                style = MaterialTheme.typography.titleLarge,
                                color = RailBitePickerPalette.TextPrimary,
                            )
                        }
                        if (state.lastUpdatedLabel.isNotBlank()) {
                            Text(
                                text = state.lastUpdatedLabel,
                                style = PickerNumericType.MetaMono,
                                color = RailBitePickerPalette.TextMuted,
                            )
                        }
                        IconButton(onClick = vm::refresh) {
                            if (state.refreshing) {
                                CircularProgressIndicator(
                                    color = RailBitePickerPalette.CyanPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh Ledger",
                                    tint = RailBitePickerPalette.TextSecondary,
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)
                }
            }
        },
        bottomBar = {
            PickerBottomBar(
                selectedTab = PickerTab.CASH,
                onSelectTab = { tab ->
                    if (tab == PickerTab.ORDERS) onOpenOrders()
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = PickerMetrics.ScreenPadding,
                end = PickerMetrics.ScreenPadding,
                top = 14.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                item { LoadingSkeleton(rows = 4) }
                return@LazyColumn
            }

            state.error?.let { err ->
                item { PickerErrorBanner(message = err) }
            }

            val stats = state.stats
            val hasMismatch = state.myRow?.mismatch == true ||
                ((stats?.collectedPaise ?: 0) != (stats?.expectedPaise ?: 0))

            // 1. Dark Slate 3-Metric Hero Card
            item {
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Card),
                    color = RailBitePickerPalette.DarkSlate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Text(
                                    text = "TODAY'S COD SHIFT SUMMARY",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RailBitePickerPalette.CyanPinDigit,
                                )
                                Text(
                                    text = stats?.staffName?.substringBefore(" (")
                                        ?: state.staffId.uppercase().ifBlank { "Picker Shift" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                            PickerStatusTag(
                                text = "${stats?.ordersDelivered ?: 0} DELIVERIES",
                                bgColor = RailBitePickerPalette.DarkSlateCell,
                                textColor = Color.White,
                                borderColor = RailBitePickerPalette.DarkSlateBorder,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            HeroMetricCell(
                                label = "EXPECTED",
                                value = Money.rupees(stats?.expectedPaise ?: 0),
                                valueColor = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                            HeroMetricCell(
                                label = "COLLECTED",
                                value = Money.rupees(stats?.collectedPaise ?: 0),
                                valueColor = RailBitePickerPalette.CyanPinDigit,
                                modifier = Modifier.weight(1f),
                            )
                            HeroMetricCell(
                                label = "CONFIRMED",
                                value = Money.rupees(stats?.passengerConfirmedPaise ?: 0),
                                valueColor = RailBitePickerPalette.SemanticGreenBorder,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(PickerRadii.Button),
                            color = if (hasMismatch) Color(0xFF450A0A) else RailBitePickerPalette.DarkSlateCell,
                            border = BorderStroke(
                                1.dp,
                                if (hasMismatch) RailBitePickerPalette.SemanticRed else RailBitePickerPalette.DarkSlateBorder,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    imageVector = if (hasMismatch) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (hasMismatch) RailBitePickerPalette.SemanticRedBorder else RailBitePickerPalette.SemanticGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = if (hasMismatch) {
                                        "Mismatch flagged (${Money.rupees(stats?.collectedPaise ?: 0)} collected vs ${Money.rupees(stats?.expectedPaise ?: 0)} expected)"
                                    } else if (state.awaitingCount > 0) {
                                        "Float balanced • ${state.awaitingCount} passenger receipt${if (state.awaitingCount == 1) "" else "s"} pending confirmation"
                                    } else {
                                        "Shift float balanced • All recorded entries reconciled"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasMismatch) RailBitePickerPalette.SemanticRedBorder else RailBitePickerPalette.BorderSubtle,
                                )
                            }
                        }
                    }
                }
            }

            // 2. Concise Confirmation Explainer
            item {
                PickerSectionCard(containerColor = RailBitePickerPalette.CyanTint, border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder)) {
                    Text(
                        text = "Passengers confirm paid COD cash inside their app after delivery. Unconfirmed cash still counts in Collected until passenger confirmation arrives.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePickerPalette.CyanPressed,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            // 3. Filter Pills Row
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterPillChip(
                        label = "All (${state.entries.size})",
                        selected = state.selectedFilter == CashFilter.ALL,
                        onClick = { vm.selectFilter(CashFilter.ALL) },
                    )
                    FilterPillChip(
                        label = "Confirmed (${state.confirmedCount})",
                        selected = state.selectedFilter == CashFilter.CONFIRMED,
                        onClick = { vm.selectFilter(CashFilter.CONFIRMED) },
                    )
                    FilterPillChip(
                        label = "Awaiting (${state.awaitingCount})",
                        selected = state.selectedFilter == CashFilter.AWAITING,
                        onClick = { vm.selectFilter(CashFilter.AWAITING) },
                    )
                }
            }

            // 4. Ledger Entries List
            val filtered = state.filteredEntries
            if (filtered.isEmpty()) {
                item {
                    PickerSectionCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(PickerRadii.Card))
                                    .background(RailBitePickerPalette.ImageTile),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Payments,
                                    contentDescription = null,
                                    tint = RailBitePickerPalette.TextSecondary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Text(
                                text = when (state.selectedFilter) {
                                    CashFilter.ALL -> "No Deliveries Recorded Yet"
                                    CashFilter.CONFIRMED -> "No Confirmed Entries Yet"
                                    CashFilter.AWAITING -> "No Entries Awaiting Passenger"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RailBitePickerPalette.TextPrimary,
                            )
                            Text(
                                text = "Completed deliveries appear here automatically with cash tender and passenger confirmation status.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RailBitePickerPalette.TextSecondary,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filtered,
                    key = { it.orderId },
                ) { entry ->
                    LedgerTransactionCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun HeroMetricCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(PickerRadii.Button),
        color = RailBitePickerPalette.DarkSlateCell,
        border = BorderStroke(1.dp, RailBitePickerPalette.DarkSlateBorder),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = RailBitePickerPalette.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = PickerNumericType.PriceLarge,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilterPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(PickerRadii.Pill),
        color = if (selected) RailBitePickerPalette.CyanTint else RailBitePickerPalette.SurfaceCard,
        border = if (selected) PickerSelectedBorder else PickerCardBorder,
        modifier = Modifier
            .clip(RoundedCornerShape(PickerRadii.Pill))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) RailBitePickerPalette.CyanPressed else RailBitePickerPalette.TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun LedgerTransactionCard(entry: LedgerEntry) {
    val timeText = formatLocalTime(entry.deliveredAt)
    val confirmed = entry.passengerConfirmed
    val isUpi = entry.paymentMode == "UPI_QUEUED"

    PickerSectionCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PickerSeatPill(coach = entry.coach, berth = entry.berth)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = shortOrderTag(entry.orderId),
                        style = PickerNumericType.MetaMono,
                        color = RailBitePickerPalette.TextPrimary,
                    )
                    Text(
                        text = buildString {
                            if (timeText.isNotBlank()) append("$timeText • ")
                            append(if (isUpi) "UPI Digital" else "Cash COD")
                        },
                        style = PickerNumericType.MetaMono,
                        color = RailBitePickerPalette.TextSecondary,
                    )
                }
                Text(
                    text = Money.rupees(entry.collectedPaise),
                    style = PickerNumericType.PriceLarge,
                    color = RailBitePickerPalette.TextPrimary,
                )
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Order Total: ${Money.rupees(entry.totalPaise)}",
                    style = PickerNumericType.MetaMono,
                    color = RailBitePickerPalette.TextSecondary,
                )
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Pill),
                    color = if (confirmed) {
                        RailBitePickerPalette.SemanticGreenTint
                    } else {
                        RailBitePickerPalette.SemanticAmberTint
                    },
                    border = BorderStroke(
                        1.dp,
                        if (confirmed) {
                            RailBitePickerPalette.SemanticGreenBorder
                        } else {
                            RailBitePickerPalette.SemanticAmberBorder
                        },
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = if (confirmed) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = if (confirmed) {
                                RailBitePickerPalette.SemanticGreen
                            } else {
                                RailBitePickerPalette.SemanticAmberInk
                            },
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = if (confirmed) "Confirmed ✓" else "Awaiting Passenger",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (confirmed) {
                                RailBitePickerPalette.SemanticGreenInk
                            } else {
                                RailBitePickerPalette.SemanticAmberInk
                            },
                        )
                    }
                }
            }
        }
    }
}

