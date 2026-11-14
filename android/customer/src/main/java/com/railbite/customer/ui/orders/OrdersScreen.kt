@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.railbite.customer.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.ui.StatusChip
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    graph: com.railbite.customer.CustomerGraph,
    modifier: Modifier = Modifier,
    onOpenOrder: (String) -> Unit,
) {
    val vm = viewModel(key = "orders") { OrdersViewModel(graph.container) }
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Orders", fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.loading) {
                LoadingSkeleton(rows = 4, modifier = Modifier.padding(16.dp))
            } else if (state.active.isEmpty() && state.past.isEmpty()) {
                EmptyState(
                    emoji = "📋",
                    title = "No orders yet",
                    subtitle = "Your orders will show up here after you place one",
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.active.isNotEmpty()) {
                        item { SectionHeader("Active") }
                        items(state.active, key = { it.id }) { OrderCard(it, onOpenOrder) }
                    }
                    if (state.past.isNotEmpty()) {
                        item { SectionHeader("Past") }
                        items(state.past, key = { it.id }) { OrderCard(it, onOpenOrder) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OrderCard(order: com.railbite.core.model.Order, onOpenOrder: (String) -> Unit) {
    Card(
        onClick = { onOpenOrder(order.id) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "#${order.id.take(8)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        formatTime(order.createdAt) + " · " + order.coach + " · Seat " + order.berth,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(
                    Statuses.label(order.status),
                    color = when {
                        order.status == Statuses.DELIVERED -> MaterialTheme.colorScheme.primary
                        Statuses.isFailed(order.status) -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    },
                )
            }
            Row {
                Text(
                    "${order.items?.sumOf { it.qty } ?: order.diners} item(s) · COD",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Money.rupees(order.totalPaise),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatTime(iso: String): String = try {
    DateTimeFormatter.ofPattern("d MMM, h:mm a").format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
} catch (_: Exception) {
    iso.take(16)
}
