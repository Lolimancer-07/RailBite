@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.railbite.picker.ui.cash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.util.Money

/** Today's cash ledger for this picker: expected vs collected vs passenger-confirmed. */
@Composable
fun CashSummaryScreen(
    graph: com.railbite.picker.PickerGraph,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = "cash") { CashSummaryViewModel(graph.container) }
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cash summary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = vm::refresh) { Text("Refresh") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> LoadingSkeleton(rows = 4)
                state.error != null -> EmptyState(
                    emoji = "📡",
                    title = "No connection",
                    subtitle = state.error!!,
                )
                else -> {
                    val stats = state.stats
                    Text(
                        "Today" + (stats?.let { " · ${it.staffName} (${it.staffId})" } ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            "${stats?.ordersDelivered ?: 0}",
                            "orders delivered",
                            Modifier.weight(1f),
                        )
                        StatTile(
                            stats?.avgRating?.let { "★ %.1f".format(it) } ?: "—",
                            "rating",
                            Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            Money.rupees(stats?.expectedPaise ?: 0),
                            "expected",
                            Modifier.weight(1f),
                        )
                        StatTile(
                            Money.rupees(stats?.collectedPaise ?: 0),
                            "collected",
                            Modifier.weight(1f),
                        )
                        StatTile(
                            Money.rupees(stats?.passengerConfirmedPaise ?: 0),
                            "confirmed",
                            Modifier.weight(1f),
                        )
                    }

                    val row = state.myRow
                    if (row != null && row.mismatch) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    "⚠ Cash mismatch flagged",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    "Collected ${Money.rupees(row.collectedPaise)} vs expected " +
                                        "${Money.rupees(row.expectedPaise)}; passengers confirmed " +
                                        "${Money.rupees(row.passengerConfirmedPaise)}. " +
                                        "Re-count your float with the pantry manager.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }

                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "How confirmation works",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "After every delivery the passenger is asked \"Did you pay ₹X?\" " +
                                    "in their app. Once they confirm, the amount moves into the " +
                                    "confirmed column above. Unconfirmed cash still counts as " +
                                    "collected today.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 14.dp, horizontal = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
