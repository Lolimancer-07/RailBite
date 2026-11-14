@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.railbite.picker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.model.Order
import com.railbite.core.model.PickerStats
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses
import com.railbite.picker.alert.AlertBus
import com.railbite.picker.ui.permissions.AlertPermissionState
import com.railbite.picker.ui.permissions.readAlertPermissions

/**
 * Picker home: big ONLINE/OFFLINE toggle (drives the alert foreground service),
 * today's stats, the current order card, alert-permission status and a test-alert button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    graph: com.railbite.picker.PickerGraph,
    onOpenPermissions: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenCash: () -> Unit,
    onLoggedOut: () -> Unit,
    onShowAlert: (String) -> Unit,
) {
    val vm = viewModel(key = "home") { HomeViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var perms by remember { mutableStateOf(readAlertPermissions(context)) }

    // Re-check after returning from the permissions screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) perms = readAlertPermissions(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RailBite Picker", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = { vm.logout(onLoggedOut) }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.staffName.ifBlank { "Picker" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (state.zone.isNotBlank()) {
                            Text(
                                "Zone ${state.zone}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.error != null) {
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item { OnlineCard(state, vm::setOnline) }

            if (!perms.allGranted) {
                item { PermissionsCard(perms, onOpenPermissions) }
            }

            state.activeOrder?.let { order ->
                item { CurrentOrderCard(order, onOpenOrder) }
            }

            item { StatsSection(state.stats, onOpenCash) }

            item {
                Card(
                    onClick = {
                        val alert = vm.fakeAlert()
                        AlertBus.post(alert)
                        onShowAlert(alert.orderId)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🔔", style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Test alert", fontWeight = FontWeight.Bold)
                            Text(
                                "Check that the loud alarm, vibration and countdown work",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Server ${state.serverUrl}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun OnlineCard(state: HomeUiState, onToggle: (Boolean) -> Unit) {
    val online = state.online
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (online) Color(0xFF0E8A4B) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (online) "You are ONLINE" else "You are OFFLINE",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (online) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (state.serviceRunning) {
                        "Listening for new orders near ${state.zone.ifBlank { "your zone" }}"
                    } else {
                        "Go online to receive order alerts"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (online) {
                        Color.White.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(
                checked = online,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF0E8A4B),
                    checkedThumbColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun PermissionsCard(perms: AlertPermissionState, onOpenPermissions: () -> Unit) {
    Card(
        onClick = onOpenPermissions,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Alert permissions incomplete (${perms.grantedCount}/3)",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Notifications · full-screen · battery",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                "Review",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CurrentOrderCard(order: Order, onOpenOrder: (String) -> Unit) {
    Card(
        onClick = { onOpenOrder(order.id) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "CURRENT ORDER",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Coach ${order.coach} · Seat ${order.berth}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        Statuses.label(order.status) + " · PNR ${order.pnr}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    Money.rupees(order.totalPaise),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "Open checklist →",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatsSection(stats: PickerStats?, onOpenCash: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Cash summary →",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenCash),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                "${stats?.ordersDelivered ?: 0}",
                "delivered",
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
        if (stats != null && stats.avgRating != null && stats.feedbackCount > 0) {
            RatingRow(stats)
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
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

@Composable
private fun RatingRow(stats: PickerStats) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(5) { index ->
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    if (index < stats.avgRating!!.toInt()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            )
        }
        Text(
            " %.1f from ${stats.feedbackCount} passengers".format(stats.avgRating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
