@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.railbite.customer.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.ui.StatusChip
import com.railbite.core.ui.theme.Space
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
            TopAppBar(title = { Text("My orders") })
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.loading) {
                LoadingSkeleton(rows = 4, modifier = Modifier.padding(Space.screen))
            } else if (state.active.isEmpty() && state.past.isEmpty() && state.relayPending.isEmpty()) {
                EmptyState(
                    title = "No orders yet",
                    subtitle = "Your orders will show up here after you place one",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Space.screen,
                        end = Space.screen,
                        top = Space.s,
                        bottom = Space.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    if (state.relayPending.isNotEmpty()) {
                        item { SectionHeader("Sent via Bluetooth relay") }
                        items(state.relayPending, key = { it.orderId }) { RelayOrderCard(it) }
                    }
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

/** Offline relay order waiting for the cloud — shows the handover code. */
@Composable
private fun RelayOrderCard(p: com.railbite.core.mesh.MeshOrderPayload) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Column {
                Text(
                    "Coach ${p.coach} · Seat ${p.berth}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Bluetooth relay — syncs to the cloud automatically when you have internet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            p.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.qty} × ${item.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(Money.rupees(item.unitPricePaise * item.qty), style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Delivery code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        p.deliveryCode,
                        style = MaterialTheme.typography.headlineSmall,
                        letterSpacing = 4.sp,
                    )
                }
                Text(
                    Money.rupees(p.totalPaise),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OrderCard(order: com.railbite.core.model.Order, onOpenOrder: (String) -> Unit) {
    Card(
        onClick = { onOpenOrder(order.id) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "#${order.id.take(8)}",
                        style = MaterialTheme.typography.titleMedium,
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
                    "${order.items?.sumOf { it.qty } ?: order.diners} item(s) · " +
                        (if (order.paymentMode == "UPI_QUEUED") "UPI" else "COD"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Money.rupees(order.totalPaise),
                    style = MaterialTheme.typography.titleSmall,
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
