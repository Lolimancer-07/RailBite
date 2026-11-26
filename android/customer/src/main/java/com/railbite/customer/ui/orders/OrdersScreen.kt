package com.railbite.customer.ui.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    graph: com.railbite.customer.CustomerGraph,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenOrder: (String) -> Unit,
) {
    val vm = viewModel(key = "orders") { OrdersViewModel(graph.container) }
    val state by vm.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RailBitePalette.Canvas,
        topBar = {
            Column(Modifier.background(RailBitePalette.SurfaceCard)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RailBitePalette.SurfaceCard,
                        titleContentColor = RailBitePalette.TextPrimary,
                    ),
                    title = {
                        Text(
                            text = "My Orders",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RailBitePalette.TextPrimary,
                            )
                        }
                    },
                )
                HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            if (state.loading) {
                LoadingSkeleton(rows = 4, modifier = Modifier.padding(RailBiteMetrics.ScreenPadding))
            } else if (state.active.isEmpty() && state.past.isEmpty() && state.relayPending.isEmpty()) {
                EmptyState(
                    title = "No orders yet",
                    subtitle = "Your active and past train orders will appear here",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = RailBiteMetrics.ScreenPadding,
                        end = RailBiteMetrics.ScreenPadding,
                        top = 14.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.relayPending.isNotEmpty()) {
                        item { SectionHeader("Sent via Bluetooth Relay") }
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

@Composable
private fun RelayOrderCard(p: com.railbite.core.mesh.MeshOrderPayload) {
    Card(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Coach ${p.coach} • Seat ${p.berth}",
                    style = MaterialTheme.typography.titleSmall,
                    color = RailBitePalette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Pill),
                    color = RailBitePalette.CyanTint,
                    border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                ) {
                    Text(
                        text = "BLE Relay",
                        style = MaterialTheme.typography.labelSmall,
                        color = RailBitePalette.CyanPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = "Bluetooth relay — syncs to the cloud automatically when online",
                style = MaterialTheme.typography.bodySmall,
                color = RailBitePalette.TextSecondary,
            )
            p.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${item.qty} × ${item.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                    Text(
                        text = Money.rupees(item.unitPricePaise * item.qty),
                        style = RailBiteNumericType.PriceMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "HANDOVER PIN",
                        style = MaterialTheme.typography.labelSmall,
                        color = RailBitePalette.TextSecondary,
                    )
                    Text(
                        text = p.deliveryCode,
                        style = RailBiteNumericType.PinDigit,
                        color = RailBitePalette.DarkSlate,
                    )
                }
                Text(
                    text = Money.rupees(p.totalPaise),
                    style = RailBiteNumericType.PriceLarge,
                    color = RailBitePalette.CyanPrimary,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = RailBitePalette.TextSecondary,
    )
}

@Composable
private fun OrderCard(
    order: com.railbite.core.model.Order,
    onOpenOrder: (String) -> Unit,
) {
    val delivered = order.status == Statuses.DELIVERED
    val failed = Statuses.isFailed(order.status)

    Card(
        onClick = { onOpenOrder(order.id) },
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "#${order.id.take(8).uppercase()}",
                        style = RailBiteNumericType.PriceMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                    Text(
                        text = "${formatTime(order.createdAt)} • ${order.coach} • Seat ${order.berth}",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                    )
                }

                val pillBg = when {
                    delivered -> RailBitePalette.SemanticGreenTint
                    failed -> RailBitePalette.SemanticRedTint
                    else -> RailBitePalette.CyanTint
                }
                val pillBorder = when {
                    delivered -> RailBitePalette.SemanticGreenBorder
                    failed -> RailBitePalette.SemanticRedBorder
                    else -> RailBitePalette.CyanBorder
                }
                val pillText = when {
                    delivered -> RailBitePalette.SemanticGreen
                    failed -> RailBitePalette.SemanticRed
                    else -> RailBitePalette.CyanPrimary
                }

                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Pill),
                    color = pillBg,
                    border = BorderStroke(1.dp, pillBorder),
                ) {
                    Text(
                        text = Statuses.label(order.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = pillText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${order.items?.sumOf { it.qty } ?: order.diners} item(s) • " +
                        (if (order.paymentMode == "UPI_QUEUED") "UPI" else "COD"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RailBitePalette.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Money.rupees(order.totalPaise),
                    style = RailBiteNumericType.PriceMedium,
                    color = RailBitePalette.CyanPrimary,
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
