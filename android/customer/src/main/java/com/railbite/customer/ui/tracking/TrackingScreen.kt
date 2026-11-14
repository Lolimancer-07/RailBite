package com.railbite.customer.ui.tracking

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.ProductEmoji
import com.railbite.core.ui.StatusChip
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses

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
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    var showPayDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.delivered) {
        if (state.delivered && !state.cashConfirmed) showPayDialog = true
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                com.railbite.core.ui.LoadingSkeleton(rows = 4)
            } else {
                StatusBanner(state)
                Timeline(state.status)
                state.captainName?.let { name ->
                    Text(
                        "Your picker: ${pickerDisplayName(name)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "ETA ~${state.etaMin ?: 10} mins",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Delivery code reveal while out for delivery (FOT-style handover)
                val order = state.order
                val code = order?.deliveryCode
                if (state.status == Statuses.OUT_FOR_DELIVERY && code != null) {
                    DeliveryCodeCard(code)
                }

                ItemsCard(state.order)
                if (state.status == Statuses.PLACED || state.status == Statuses.ASSIGNED) {
                    OutlinedButton(onClick = vm::cancelOrder, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel order")
                    }
                }
                OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) {
                    Text("Report a problem")
                }
            }
        }
    }

    if (showPayDialog && state.delivered) {
        val total = state.order?.totalPaise ?: 0
        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            title = { Text("Did you pay ${Money.rupees(total)}?") },
            text = { Text("Confirm so the cash ledger records your payment.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPayDialog = false
                        vm.confirmCash()
                    },
                    enabled = !state.cashConfirming,
                ) { Text("Yes, I paid") }
            },
            dismissButton = {
                TextButton(onClick = { showPayDialog = false }) { Text("Not yet") }
            },
        )
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
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
        state.delivered -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                "🛵 Delivered. Enjoy your food!",
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Timeline(currentStatus: String) {
    val step = Statuses.timelineStep(currentStatus)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Order status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    Spacer(Modifier.width(10.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (done || active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (active) {
                        Spacer(Modifier.width(8.dp))
                        StatusChip("now", MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryCodeCard(code: String) {
    var revealed by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FA)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("🔐 Delivery code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Show this to the picker when they hand you the food.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { revealed = !revealed }) {
                Text(if (revealed) code else "••••  (tap to reveal)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ItemsCard(order: com.railbite.core.model.Order?) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val items = order?.items.orEmpty()
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ProductEmoji.forItem(item.name ?: "", "MEALS"))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${item.qty} × ${item.name ?: item.itemId.take(8)}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(Money.rupees(item.unitPricePaise * item.qty), fontWeight = FontWeight.SemiBold)
                }
            }
            if (items.isEmpty()) Text(
                "Items load when the server is reachable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total (COD)", fontWeight = FontWeight.Bold)
                Text(Money.rupees(order?.totalPaise ?: 0), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
