package com.railbite.picker.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteSecondaryButton
import com.railbite.core.ui.components.RailBiteTextField
import com.railbite.core.ui.theme.Metrics
import com.railbite.core.ui.theme.Space
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

    // Pre-fill the collected-cash field: the expected total for COD, 0 for UPI_QUEUED
    // (passenger pays digitally; the picker records nothing unless odd cash changes hands)
    LaunchedEffect(state.order?.id, state.order?.status, state.order?.totalPaise) {
        val o = state.order ?: return@LaunchedEffect
        if (!cashTouched && o.status == Statuses.OUT_FOR_DELIVERY) {
            val prefilled = if (o.paymentMode == "UPI_QUEUED") 0 else o.totalPaise
            cashText = String.format(Locale.US, "%.2f", prefilled / 100.0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order #${orderId.take(8)}") },
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
                .padding(Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            val order = state.order
            if (state.loading && order == null) {
                LoadingSkeleton(rows = 4)
                return@Column
            }
            if (order == null) {
                EmptyState(
                    title = "Order unavailable",
                    subtitle = state.error ?: "Try again from the home screen",
                )
                RailBiteSecondaryButton(
                    text = "Back",
                    onClick = onBack,
                    modifier = Modifier.padding(horizontal = Space.screen),
                )
                return@Column
            }

            StepsRow(order.status)
            HeaderCard(order)

            state.message?.let { ErrorBar(it) }
            if (state.error != null && !Statuses.isTerminal(order.status)) {
                ErrorBar(state.error!!)
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
                    upiOrder = order.paymentMode == "UPI_QUEUED",
                    busy = state.busy,
                    onDeliver = { vm.deliver(code, cashText) },
                )
                Statuses.DELIVERED -> DeliveredCard(order, busy = state.busy, onDone = { vm.finish(onBack) })
                Statuses.CANCELLED, Statuses.EXPIRED -> EmptyState(
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
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Coach ${order.coach} · Seat ${order.berth}",
                    style = MaterialTheme.typography.titleMedium,
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
                    if (order.verified == 0) " · unverified" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            order.items?.let { items ->
                Text(
                    items.sumOf { it.qty }.toString() + " item(s) · " + Money.rupees(order.totalPaise) + " " +
                        (if (order.paymentMode == "UPI_QUEUED") "UPI (digital)" else "COD"),
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
                        Text(
                            "✓",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
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
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
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

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Picking checklist",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$packed / ${items.size} picked",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items.forEach { item ->
                // Whole row is the tap target (≥ 44 dp); checkbox itself is display-only here.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Metrics.tapTarget)
                        .clickable { vm.tick(item.itemId) },
                ) {
                    Checkbox(
                        checked = state.checklist[item.itemId] ?: false,
                        onCheckedChange = null,
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
            Spacer(Modifier.height(Space.xs))
            RailBiteButton(
                text = "All picked — start delivery",
                onClick = vm::startPickup,
                enabled = allPacked,
                busy = state.busy,
            )
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
    upiOrder: Boolean,
    busy: Boolean,
    onDeliver: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text("On the way", style = MaterialTheme.typography.titleMedium)
            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    "Ask the passenger for the 4-digit delivery code shown in their app. " +
                        "Never guess it — a wrong code keeps the order open.",
                    Modifier.padding(Space.s),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // Passenger's 4-digit code first — the handover authority.
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
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.tapTarget),
            )
            // Then the collected cash.
            RailBiteTextField(
                value = cashText,
                onValueChange = onCashChange,
                label = if (upiOrder) "Cash collected (₹) — UPI order" else "Cash collected (₹)",
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                supportingText = if (upiOrder) {
                    "Passenger pays ${Money.rupees(expectedPaise)} digitally via UPI — leave 0"
                } else {
                    "Expected ${Money.rupees(expectedPaise)}"
                },
            )
            RailBiteButton(
                text = "Mark delivered",
                onClick = onDeliver,
                enabled = code.length == 4,
                busy = busy,
            )
        }
    }
}

@Composable
private fun DeliveredCard(order: Order, busy: Boolean, onDone: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text(
                "Delivered",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                if (order.paymentMode == "UPI_QUEUED") {
                    "The passenger pays ${Money.rupees(order.totalPaise)} digitally via UPI — no cash to collect. " +
                        "They confirm the payment in their app."
                } else {
                    "Hand over only after the passenger pays ${Money.rupees(order.totalPaise)} cash. " +
                        "They confirm the amount in their app."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            RailBiteButton(
                text = "Done",
                onClick = onDone,
                busy = busy,
            )
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
