package com.railbite.customer.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.railbite.core.ui.ProductEmoji
import com.railbite.core.ui.VegDot
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
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.placeOrder(onPlaced) },
                            enabled = !state.placing,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.placing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Placing…", color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(
                                    if (state.paymentMode == "UPI_QUEUED") {
                                        "Place Order · ${Money.rupees(grandTotal)}  (Pay via UPI)"
                                    } else {
                                        "Place Order · ${Money.rupees(grandTotal)}  (Cash on Delivery)"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (itemCount == 0) {
            Column(Modifier.padding(padding)) {
                EmptyState(
                    emoji = "🛒",
                    title = "Your cart is empty",
                    subtitle = "Add some chai or snacks from the menu",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        emoji = ProductEmoji.forItem(line.item.name, line.item.category),
                        isVeg = line.item.isVeg,
                        pricePaise = line.item.mrpPaise,
                        qty = line.qty,
                        onAdd = { vm.add(line.item.id) },
                        onDecrement = { vm.decrement(line.item.id) },
                    )
                }
                item { HorizontalDivider() }
                item {
                    BillSummary(itemTotal = itemTotal, deliveryFee = deliveryFee, grandTotal = grandTotal)
                }
                item {
                    Text(
                        "Payment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = state.paymentMode == "COD",
                            onClick = { vm.setPaymentMode("COD") },
                            label = { Text("Cash on delivery") },
                        )
                        FilterChip(
                            selected = state.paymentMode == "UPI_QUEUED",
                            onClick = { vm.setPaymentMode("UPI_QUEUED") },
                            label = { Text("UPI (pay in app)") },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.paymentMode == "UPI_QUEUED") {
                            "Pay ₹${Money.rupeesPlain(grandTotal)} with any UPI app from the order screen. " +
                                "The picker verifies the handover with your 4-digit code."
                        } else {
                            "Cash on Delivery — pay the picker at your seat."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CartRow(
    name: String,
    emoji: String,
    isVeg: Boolean,
    pricePaise: Int,
    qty: Int,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VegDot(isVeg = isVeg)
                    Spacer(Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.titleMedium)
                }
                Text(Money.rupees(pricePaise), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledIconButton(onClick = onDecrement) { Text("−") }
                Text("$qty", fontWeight = FontWeight.Bold)
                FilledIconButton(onClick = onAdd) { Text("+") }
            }
        }
    }
}

@Composable
private fun BillSummary(itemTotal: Int, deliveryFee: Int, grandTotal: Int) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryRow("Item total", Money.rupees(itemTotal))
            SummaryRow("Delivery fee", if (deliveryFee == 0) "FREE" else Money.rupees(deliveryFee))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Grand total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    Money.rupees(grandTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
