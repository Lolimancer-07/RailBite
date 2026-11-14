package com.railbite.picker.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.model.Order
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.ui.StatusChip
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses
import java.util.Locale

/**
 * Picker workflow for one order:
 * ACCEPTED (auto -> packing) -> PACKING: tick every item -> pickup -> OUT_FOR_DELIVERY:
 * repeat the passenger's 4-digit code + collected cash -> DELIVERED.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderProgressScreen(
    graph: com.railbite.picker.PickerGraph,
    orderId: String,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = "progress-$orderId") {
        OrderProgressViewModel(graph.container, orderId)
    }
    val state by vm.state.collectAsState()

    var code by remember { mutableStateOf("") }
    var cashText by remember { mutableStateOf("") }
    var cashTouched by remember { mutableStateOf(false) }

    // Pre-fill the collected-cash field with the expected total
    LaunchedEffect(state.order?.id, state.order?.status, state.order?.totalPaise) {
        val o = state.order ?: return@LaunchedEffect
        if (!cashTouched && o.status == Statuses.OUT_FOR_DELIVERY) {
            cashText = String.format(Locale.US, "%.2f", o.totalPaise / 100.0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order #${orderId.take(8)}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
            val order = state.order
            if (state.loading && order == null) {
                LoadingSkeleton(rows = 4)
                return@Column
            }
            if (order == null) {
                EmptyState(
                    emoji = "📭",
                    title = "Order unavailable",
                    subtitle = state.error ?: "Try again from the home screen",
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Back")
                }
                return@Column
            }

            StepsRow(order.status)
            HeaderCard(order)

            state.message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (state.error != null && !Statuses.isTerminal(order.status)) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            when (order.status) {
                Statuses.PACKING -> PackingCard(state, vm)
                Statuses.OUT_FOR_DELIVERY -> DeliveryCard(
                    code = code,
                    onCodeChange = { code = it.filter(Char::isDigit).take(4) },
                    cashText = cashText,
                    onCashChange = {
                        cashTouched = true
                        cashText = it
                    },
                    expectedPaise = order.totalPaise,
                    busy = state.busy,
                    onDeliver = { vm.deliver(code, cashText) },
                )
                Statuses.DELIVERED -> DeliveredCard(order, busy = state.busy, onDone = { vm.finish(onBack) })
                Statuses.CANCELLED, Statuses.EXPIRED -> EmptyState(
                    emoji = "🚫",
                    title = Statuses.label(order.status),
                    subtitle = "Nothing to do — this order left the flow",
                )
                else -> InfoCard(
                    "Waiting — order is ${Statuses.label(order.status).lowercase()}. " +
                        "It appears here once it is accepted by you.",
                )
            }
        }
    }
}

@Composable
private fun HeaderCard(order: Order) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Coach ${order.coach} · Seat ${order.berth}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    Statuses.label(order.status),
                    color = when {
                        order.status == Statuses.DELIVERED -> MaterialTheme.colorScheme.primary
                        Statuses.isFailed(order.status) -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    },
                )
            }
            Text(
                "PNR ${order.pnr} · ${order.diners} diner${if (order.diners == 1) "" else "s"}" +
                    if (order.verified == 0) " · ⚠ unverified" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            order.items?.let { items ->
                Text(
                    items.sumOf { it.qty }.toString() + " item(s) · " + Money.rupees(order.totalPaise) + " COD",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StepsRow(status: String) {
    val step = when (status) {
        Statuses.ACCEPTED -> 0
        Statuses.PACKING -> 1
        Statuses.OUT_FOR_DELIVERY -> 2
        Statuses.DELIVERED -> 3
        else -> 0
    }
    val labels = listOf("Accepted", "Packing", "On the way", "Delivered")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val done = index < step
            val active = index == step
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done || active -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                    } else if (active) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary),
                        )
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (done || active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
            if (index < labels.lastIndex) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 4.dp)
                        .background(
                            if (index < step) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun PackingCard(state: ProgressUiState, vm: OrderProgressViewModel) {
    val order = state.order ?: return
    val items = order.items.orEmpty()
    val packed = state.checklist.values.count { it }
    val allPacked = items.isNotEmpty() && packed == items.size

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Picking checklist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$packed / ${items.size} picked",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.checklist[item.itemId] ?: false,
                        onCheckedChange = { vm.tick(item.itemId) },
                    )
                    Text(
                        "${item.qty} × ${item.name ?: item.itemId}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Money.rupees(item.unitPricePaise * item.qty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.isEmpty()) {
                Text(
                    "No item list on this order — pick per the printed slip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = vm::startPickup,
                enabled = allPacked && !state.busy,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("All picked — start delivery", fontWeight = FontWeight.Bold)
                }
            }
            if (!allPacked) {
                Text(
                    "Tick every item to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeliveryCard(
    code: String,
    onCodeChange: (String) -> Unit,
    cashText: String,
    onCashChange: (String) -> Unit,
    expectedPaise: Int,
    busy: Boolean,
    onDeliver: () -> Unit,
) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "🛵 On the way",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    "Ask the passenger for the 4-digit delivery code shown in their app. " +
                        "Never guess it — a wrong code keeps the order open.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("Delivery code") },
                placeholder = { Text("• • • •") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next,
                ),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cashText,
                onValueChange = onCashChange,
                label = { Text("Cash collected (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                supportingText = { Text("Expected ${Money.rupees(expectedPaise)}") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onDeliver,
                enabled = !busy && code.length == 4,
                colors = ButtonDefaults.buttonColors(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("Mark delivered", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DeliveredCard(order: Order, busy: Boolean, onDone: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("✅", style = MaterialTheme.typography.displayMedium)
            Text(
                "Delivered",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Hand over only after the passenger pays ${Money.rupees(order.totalPaise)} cash. " +
                    "They confirm the amount in their app.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onDone,
                enabled = !busy,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Text(
            text,
            Modifier.fillMaxWidth().padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
