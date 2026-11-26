@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.railbite.picker.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.model.Order
import com.railbite.core.model.PickerStats
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteSecondaryButton
import com.railbite.core.ui.components.RailBiteTextField
import com.railbite.core.ui.theme.Metrics
import com.railbite.core.ui.theme.Space
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
    var showServerDialog by remember { mutableStateOf(false) }
    var editServerUrl by remember { mutableStateOf("") }

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
                title = { Text("RailBite Picker") },
                actions = {
                    ConnectionStatusPill(
                        connected = state.connectionConnected,
                        mqttLive = state.mqttConnected,
                        onClick = {
                            editServerUrl = state.serverUrlStored
                            showServerDialog = true
                        },
                    )
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
            contentPadding = PaddingValues(
                start = Space.screen,
                end = Space.screen,
                top = Space.xs,
                bottom = Space.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Text(
                            state.staffName.ifBlank { "Picker" },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (state.zone.isNotBlank()) {
                            Text(
                                "Zone ${state.zone}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when {
                        state.error == null -> {}
                        // Alert pipeline is up while the cloud is unreachable: orders
                        // still arrive over the Bluetooth relay — reassure, don't alarm.
                        state.serviceRunning -> OfflineRelayChip()
                        else -> ErrorBar(state.error!!)
                    }
                }
            }

            item { OnlineCard(state, vm::setOnline) }

            item {
                MeshRelayCard(
                    container = graph.container,
                    online = state.online,
                )
            }

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
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Metrics.tapTarget)
                            .padding(Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Test alert", style = MaterialTheme.typography.titleMedium)
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
                ServerSettingsCard(
                    serverUrl = state.serverUrl,
                    connected = state.connectionConnected,
                    mqttLive = state.mqttConnected,
                    onConfigure = {
                        editServerUrl = state.serverUrlStored
                        showServerDialog = true
                    },
                )
            }
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Server address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        "Enter LAN server URL (e.g. http://192.168.1.45:8080) or leave blank for built-in cloud:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RailBiteTextField(
                        value = editServerUrl,
                        onValueChange = { editServerUrl = it },
                        label = "Server URL",
                        hint = "http://192.168.1.45:8080",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false,
                        ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.updateServerUrl(editServerUrl)
                        showServerDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ConnectionStatusPill(
    connected: Boolean,
    mqttLive: Boolean,
    onClick: () -> Unit,
) {
    val dotColor = if (connected) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
    val label = when {
        mqttLive -> "Connected"
        connected -> "Connected"
        else -> "Disconnected"
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ServerSettingsCard(
    serverUrl: String,
    connected: Boolean,
    mqttLive: Boolean,
    onConfigure: () -> Unit,
) {
    Card(
        onClick = onConfigure,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.tapTarget)
                .padding(Space.m),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (mqttLive) "Server · MQTT Live" else if (connected) "Server · Connected" else "Server · Disconnected",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = serverUrl.ifBlank { "Not configured" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Change",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Quiet inline state for "no internet, but the Bluetooth relay is listening". */
@Composable
private fun OfflineRelayChip() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xs),
    ) {
        Text(
            "Cloud unreachable — listening over Bluetooth",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
        )
    }
}

@Composable
private fun OnlineCard(state: HomeUiState, onToggle: (Boolean) -> Unit) {
    val online = state.online
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (online) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Space.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (online) "You are ONLINE" else "You are OFFLINE",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (online) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    if (state.serviceRunning) {
                        "Listening for new orders near ${state.zone.ifBlank { "your zone" }}"
                    } else {
                        "Go online to receive order alerts"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (online) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(
                checked = online,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun PermissionsCard(perms: AlertPermissionState, onOpenPermissions: () -> Unit) {
    Card(
        onClick = onOpenPermissions,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.tapTarget)
                .padding(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Alert permissions incomplete (${perms.grantedCount}/3)",
                    style = MaterialTheme.typography.titleMedium,
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
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun CurrentOrderCard(order: Order, onOpenOrder: (String) -> Unit) {
    Card(
        onClick = { onOpenOrder(order.id) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.tapTarget)
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                "Current order",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Coach ${order.coach} · Seat ${order.berth}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        Statuses.label(order.status) + " · PNR ${order.pnr}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    Money.rupees(order.totalPaise),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                "Open checklist",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StatsSection(stats: PickerStats?, onOpenCash: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOpenCash) {
                Text("Cash summary", style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
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
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
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
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
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

/**
 * Bluetooth relay (offline feature) card: pantry-sink toggle + BLE permission row +
 * how many relay orders are waiting for the cloud. While the picker is ONLINE the alert
 * service starts the gateway, so mesh orders ring like normal alerts.
 */
@Composable
private fun MeshRelayCard(container: com.railbite.picker.PickerContainer, online: Boolean) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sinkEnabled by container.settings.meshSinkEnabled.collectAsState(initial = false)
    var pending by remember { mutableStateOf(0) }
    var missingPerms by remember { mutableStateOf<List<String>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        missingPerms = container.meshTransport.requiredPermissions()
        // Just granted: start the sink immediately, no app relaunch needed.
        if (missingPerms.isEmpty() && sinkEnabled) {
            scope.launch { runCatching { container.meshGateway.start() } }
        }
    }

    LaunchedEffect(Unit) {
        missingPerms = container.meshTransport.requiredPermissions()
        while (true) {
            pending = runCatching { container.meshLedger.pendingCount() }.getOrDefault(0)
            kotlinx.coroutines.delay(5_000)
        }
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = Metrics.tapTarget),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Bluetooth relay", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !sinkEnabled -> "Off — enable below to accept offline orders"
                            missingPerms.isNotEmpty() -> "Allow Nearby devices to accept offline orders"
                            else -> "Listening over Bluetooth — works with no internet"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = sinkEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            container.settings.setMeshSinkEnabled(enabled)
                            if (enabled) {
                                // The sink runs regardless of cloud state — with the train
                                // Wi-Fi dead the mesh is the only path orders can take.
                                val missing = container.meshTransport.requiredPermissions()
                                if (missing.isEmpty() && container.meshTransport.isUsable()) {
                                    container.meshGateway.start()
                                } else {
                                    permissionLauncher.launch(missing.toTypedArray())
                                }
                            } else {
                                container.meshGateway.stop()
                            }
                        }
                    },
                )
            }
            if (missingPerms.isNotEmpty()) {
                Text(
                    "Bluetooth permission needed to receive relay orders",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                RailBiteSecondaryButton(
                    text = "Allow Bluetooth",
                    onClick = { permissionLauncher.launch(missingPerms.toTypedArray()) },
                )
            }
            if (pending > 0) {
                Text(
                    "$pending relay order${if (pending == 1) "" else "s"} waiting to sync to the cloud",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
