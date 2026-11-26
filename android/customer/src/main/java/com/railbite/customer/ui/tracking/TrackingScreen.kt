package com.railbite.customer.ui.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.railbite.core.ui.ProductThumb
import com.railbite.core.ui.StatusChip
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteSecondaryButton
import com.railbite.core.ui.theme.Space
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses
import com.railbite.core.util.Upi

private val timelineLabels = listOf(
    "Order placed",
    "Picker accepted",
    "Packing",
    "Out for delivery",
    "Delivered",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    graph: com.railbite.customer.CustomerGraph,
    orderId: String,
    onBack: () -> Unit,
    onReport: () -> Unit,
) {
    val vm = viewModel(key = "tracking-$orderId") { TrackingViewModel(graph.container, orderId) }
    val state by vm.state.collectAsState()
    val mqttStatuses by vm.mqttStatuses(orderId).collectAsState()

    // Merge MQTT pushes into the VM state
    LaunchedEffect(mqttStatuses) {
        vm.refreshMerged(mqttStatuses)
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    var showPayDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.delivered) {
        // Relayed orders settle in cash at the offline handover — no cloud confirm.
        if (state.delivered && !state.cashConfirmed && !state.relayed) showPayDialog = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            if (state.loading) {
                com.railbite.core.ui.LoadingSkeleton(rows = 4)
            } else {
                val order = state.order
                val code = order?.deliveryCode

                if (state.relayed) {
                    RelayBanner()
                    // The passenger's own code rides in the mesh payload — show it
                    // immediately; the picker verifies it at handover exactly as for
                    // cloud orders.
                    state.relayCode?.let { DeliveryCodeBlock(it, relayHint) }
                } else if (state.status == Statuses.OUT_FOR_DELIVERY && code != null) {
                    DeliveryCodeBlock(code)
                }

                StatusBanner(state)
                Timeline(state.status)
                state.captainName?.let { name ->
                    Text(
                        "Your picker: ${pickerDisplayName(name)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.relayed) {
                    Text(
                        when (state.status) {
                            Statuses.ACCEPTED -> "Picker accepted — packing your order"
                            Statuses.OUT_FOR_DELIVERY -> "On the way to your seat"
                            Statuses.DELIVERED -> "Delivered — cash handover complete"
                            else -> "Waiting for a picker nearby — your order is on the mesh"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        "ETA ~${state.etaMin ?: 10} mins",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // UPI_QUEUED orders: pay (or re-open) the UPI app until delivered.
                if (order?.paymentMode == "UPI_QUEUED" && state.status != Statuses.DELIVERED) {
                    UpiPayCard(
                        orderId = orderId,
                        totalPaise = order.totalPaise,
                        onNoUpiApp = {
                            scope.launch {
                                snackbar.showSnackbar("No UPI app found — scan-and-pay any UPI app to ${Upi.VPA}")
                            }
                        },
                    )
                }

                if (state.status == Statuses.DELIVERED) {
                    BillCard(state.order)
                } else if (state.relayed) {
                    RelayItemsCard(state.relayItems, state.relayTotalPaise)
                } else {
                    ItemsCard(state.order)
                }
                if ((state.status == Statuses.PLACED || state.status == Statuses.ASSIGNED) && !state.relayed) {
                    RailBiteSecondaryButton("Cancel order", vm::cancelOrder)
                }
                RailBiteSecondaryButton("Report a problem", onReport)
            }
        }
    }

    if (showPayDialog && state.delivered) {
        val total = state.order?.totalPaise ?: 0
        val upi = state.order?.paymentMode == "UPI_QUEUED"
        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            title = { Text(if (upi) "Confirm your UPI payment" else "Did you pay ${Money.rupees(total)}?") },
            text = {
                Text(
                    if (upi) "Confirm so the ledger records your ${Money.rupees(total)} UPI payment."
                    else "Confirm so the cash ledger records your payment."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPayDialog = false
                        vm.confirmCash()
                    },
                    enabled = !state.cashConfirming,
                ) { Text(if (upi) "Yes, I paid ${Money.rupees(total)}" else "Yes, I paid") }
            },
            dismissButton = {
                TextButton(onClick = { showPayDialog = false }) { Text("Not yet") }
            },
        )
    }
}

/** Offline hint shown instead of the standard "do not share earlier" handover line. */
private val relayHint = "Show this to the picker when the food reaches your seat."

/** Offline relay state: the order is on the mesh, updates travel the same way back. */
@Composable
private fun RelayBanner() {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            "Sent over Bluetooth. Your picker's updates arrive the same way — " +
                "no internet needed.",
            modifier = Modifier.padding(Space.s),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Items + total for a relayed order, straight from the local mesh payload. */
@Composable
private fun RelayItemsCard(
    items: List<com.railbite.core.mesh.MeshOrderPayload.Item>,
    totalPaise: Int,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Items", style = MaterialTheme.typography.titleMedium)
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    ProductThumb(item.name, Modifier.size(56.dp))
                    Text(
                        "${item.qty} × ${item.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(Money.rupees(item.unitPricePaise * item.qty), style = MaterialTheme.typography.titleSmall)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total (COD)", style = MaterialTheme.typography.titleSmall)
                Text(
                    Money.rupees(totalPaise),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * The delivery code: large at the top of the order screen, with the
 * handover hint. Shown while the order is out for delivery.
 */
@Composable
private fun DeliveryCodeBlock(code: String, hint: String = "Tell this to the person at your seat. Do not share it earlier.") {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                "Delivery code",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                code,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** UPI_QUEUED payment card: opens any installed UPI app with amount + order note prefilled. */
@Composable
private fun UpiPayCard(orderId: String, totalPaise: Int, onNoUpiApp: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Payment", style = MaterialTheme.typography.titleMedium)
            Text(
                "This order is queued for UPI — pay ${Money.rupees(totalPaise)} with any UPI app. " +
                    "Confirm after delivery so the ledger records it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RailBiteButton(
                text = "Pay ${Money.rupees(totalPaise)} via UPI",
                onClick = { if (!Upi.launch(context, orderId, totalPaise)) onNoUpiApp() },
            )
        }
    }
}

/** Strips the "(Picker B1-B2)" suffix the server keeps in staff display names. */
private fun pickerDisplayName(name: String): String =
    name.substringBefore(" (Picker").trim()

@Composable
private fun StatusBanner(state: TrackingUiState) {
    when {
        state.failed -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Text(
                if (state.status == Statuses.CANCELLED) "Order cancelled"
                else "No picker accepted in time — the order expired",
                modifier = Modifier.padding(Space.s),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        state.delivered -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                if (state.relayed) "Delivered — cash handover complete offline."
                else "Delivered. Enjoy your food.",
                modifier = Modifier.padding(Space.s),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun Timeline(currentStatus: String) {
    val step = Statuses.timelineStep(currentStatus)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Order status", style = MaterialTheme.typography.titleMedium)
            timelineLabels.forEachIndexed { index, label ->
                val done = step >= index
                val active = step == index && !Statuses.isFailed(currentStatus)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(22.dp).background(
                            when {
                                done -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (done) "✓" else "${index + 1}",
                            color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Spacer(Modifier.width(Space.s))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (done || active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (active) {
                        Spacer(Modifier.width(Space.xs))
                        StatusChip("now", MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemsCard(order: com.railbite.core.model.Order?) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Items", style = MaterialTheme.typography.titleMedium)
            val items = order?.items.orEmpty()
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    ProductThumb(item.name ?: "", Modifier.size(56.dp))
                    Text(
                        "${item.qty} × ${item.name ?: item.itemId.take(8)}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(Money.rupees(item.unitPricePaise * item.qty), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (items.isEmpty()) Text(
                "Items load when the server is reachable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (order?.paymentMode == "UPI_QUEUED") "Total (UPI)" else "Total (COD)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    Money.rupees(order?.totalPaise ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Clean, professional bill for a delivered order. Rendered in place of the plain
 * items card once the order reaches DELIVERED — itemised lines, grand total,
 * payment mode and seat/order facts, all from the design tokens.
 */
@Composable
private fun BillCard(order: com.railbite.core.model.Order?) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bill", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Order #${order?.id?.take(8) ?: ""}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "PNR ${order?.pnr ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Seat ${order?.coach ?: "—"} · Berth ${order?.berth ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            order?.let {
                Text(
                    "Placed ${formatBillTime(it.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            val billItems = order?.items.orEmpty()
            if (billItems.isEmpty()) {
                Text(
                    "Items load when the server is reachable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            billItems.forEach { item ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${item.qty} × ${item.name ?: item.itemId.take(8)}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                    )
                    Text(
                        Money.rupees(item.unitPricePaise * item.qty),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    Money.rupees(order?.totalPaise ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            order?.let {
                Text(
                    "Payment: ${if (it.paymentMode == "UPI_QUEUED") "UPI" else "COD"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Formats an ISO-8601 instant for bill display. */
private fun formatBillTime(iso: String): String = runCatching {
    java.time.Instant.parse(iso)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
}.getOrDefault(iso)
