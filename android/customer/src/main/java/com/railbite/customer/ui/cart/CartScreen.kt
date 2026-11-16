package com.railbite.customer.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.ProductThumb
import com.railbite.core.ui.VegDot
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteFilterChip
import com.railbite.core.ui.components.RailBiteStepper
import com.railbite.core.ui.theme.Space
import com.railbite.core.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    graph: com.railbite.customer.CustomerGraph,
    onPlaced: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = "cart") { CartViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val lines by vm.lines.collectAsState()
    val seat by vm.seat.collectAsState()

    val itemCount = lines.values.sumOf { it.qty }
    val itemTotal = lines.values.sumOf { it.qty * it.item.mrpPaise }
    val deliveryFee = 0
    val grandTotal = itemTotal + deliveryFee

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cart") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (itemCount > 0) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.screen, vertical = Space.m),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        RailBiteButton(
                            text = if (state.paymentMode == "UPI_QUEUED") {
                                "Place order · ${Money.rupees(grandTotal)} (Pay via UPI)"
                            } else {
                                "Place order · ${Money.rupees(grandTotal)} (Cash on delivery)"
                            },
                            onClick = { vm.placeOrder(onPlaced) },
                            busy = state.placing,
                        )
                        state.error?.let { ErrorBar(it) }
                    }
                }
            }
        },
    ) { padding ->
        if (itemCount == 0) {
            Column(Modifier.padding(padding)) {
                EmptyState(
                    title = "Your cart is empty",
                    subtitle = "Add some chai or snacks from the menu",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Space.screen,
                    end = Space.screen,
                    top = Space.s,
                    bottom = Space.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                item {
                    Text(
                        "Delivering to ${seat?.chip ?: "your seat"} · ${seat?.diners ?: 1} diner${if ((seat?.diners ?: 1) == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(lines.values.sortedBy { it.item.name }, key = { it.item.id }) { line ->
                    CartRow(
                        name = line.item.name,
                        isVeg = line.item.isVeg,
                        pricePaise = line.item.mrpPaise,
                        qty = line.qty,
                        onAdd = { vm.add(line.item.id) },
                        onDecrement = { vm.decrement(line.item.id) },
                    )
                }
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                item {
                    BillSummary(itemTotal = itemTotal, deliveryFee = deliveryFee, grandTotal = grandTotal)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text("Payment", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            RailBiteFilterChip(
                                label = "Cash on delivery",
                                selected = state.paymentMode == "COD",
                                onClick = { vm.setPaymentMode("COD") },
                            )
                            RailBiteFilterChip(
                                label = "UPI (pay in app)",
                                selected = state.paymentMode == "UPI_QUEUED",
                                onClick = { vm.setPaymentMode("UPI_QUEUED") },
                            )
                        }
                        Text(
                            if (state.paymentMode == "UPI_QUEUED") {
                                "Pay ${Money.rupees(grandTotal)} with any UPI app from the order screen. " +
                                    "The picker verifies the handover with your 4-digit code."
                            } else {
                                "Cash on delivery — pay the picker at your seat."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartRow(
    name: String,
    isVeg: Boolean,
    pricePaise: Int,
    qty: Int,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium, // 10 dp card
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductThumb(name, Modifier.size(48.dp))
                Spacer(Modifier.width(Space.s))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VegDot(isVeg = isVeg)
                        Spacer(Modifier.width(6.dp))
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                    Text(
                        Money.rupees(pricePaise),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RailBiteStepper(
                quantity = qty,
                itemName = name,
                onAdd = onAdd,
                onRemove = onDecrement,
            )
        }
    }
}

@Composable
private fun BillSummary(itemTotal: Int, deliveryFee: Int, grandTotal: Int) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            SummaryRow("Item total", Money.rupees(itemTotal))
            SummaryRow("Delivery fee", if (deliveryFee == 0) "FREE" else Money.rupees(deliveryFee))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Grand total", style = MaterialTheme.typography.titleMedium)
                Text(
                    Money.rupees(grandTotal),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
