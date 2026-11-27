package com.railbite.picker.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.model.Order
import com.railbite.core.model.OrderMessage
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses
import com.railbite.picker.PickerGraph
import com.railbite.picker.alert.AlertBus
import com.railbite.picker.ui.components.PickerBottomBar
import com.railbite.picker.ui.components.PickerErrorBanner
import com.railbite.picker.ui.components.PickerSectionCard
import com.railbite.picker.ui.components.PickerSeatPill
import com.railbite.picker.ui.components.PickerStatusDot
import com.railbite.picker.ui.components.PickerStatusTag
import com.railbite.picker.ui.components.PickerTab
import com.railbite.picker.ui.components.ServerSettingsSheet
import com.railbite.picker.ui.components.shortOrderTag
import com.railbite.picker.ui.permissions.AlertPermissionState
import com.railbite.picker.ui.permissions.readAlertPermissions
import com.railbite.picker.ui.theme.PickerBottomSheetShape
import com.railbite.picker.ui.theme.PickerCardBorder
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.PickerSelectedBorder
import com.railbite.picker.ui.theme.RailBitePickerPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Stitch Screen 2 (`db3495c5`): Picker Ops Dashboard & Active Queue.
 * Strictly flat `#F4F6F8` canvas, `#FFFFFF` bordered cards, `#0F172A` seat pills, `#0891B2` actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerDashboardScreen(
    graph: PickerGraph,
    onOpenPermissions: () -> Unit,
    onOpenOrder: (orderId: String, openChat: Boolean) -> Unit,
    onOpenCash: () -> Unit,
    onLoggedOut: () -> Unit,
    onShowAlert: (String) -> Unit,
) {
    val vm = viewModel(key = "home") { HomeViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var perms by remember { mutableStateOf(readAlertPermissions(context)) }
    var pendingMesh by remember { mutableIntStateOf(0) }
    var showToolsSheet by remember { mutableStateOf(false) }
    var showServerSheet by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                perms = readAlertPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            pendingMesh = runCatching { graph.container.meshLedger.pendingCount() }.getOrDefault(0)
            delay(5_000)
        }
    }

    Scaffold(
        containerColor = RailBitePickerPalette.Canvas,
        topBar = {
            Surface(color = RailBitePickerPalette.SurfaceCard) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "PANTRY CAR OPS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = RailBitePickerPalette.CyanPrimary,
                            )
                            Text(
                                text = "Orders Dashboard",
                                style = MaterialTheme.typography.headlineMedium,
                                color = RailBitePickerPalette.TextPrimary,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(onClick = vm::forceRefresh) {
                                if (state.refreshing) {
                                    CircularProgressIndicator(
                                        color = RailBitePickerPalette.CyanPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Refresh Queue",
                                        tint = RailBitePickerPalette.TextSecondary,
                                    )
                                }
                            }

                            Box {
                                IconButton(onClick = { showToolsSheet = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = "Shift & Server Settings",
                                        tint = RailBitePickerPalette.TextPrimary,
                                    )
                                }
                                if (!perms.allGranted || pendingMesh > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 8.dp, end = 8.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(RailBitePickerPalette.SemanticAmber),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)
                }
            }
        },
        bottomBar = {
            PickerBottomBar(
                selectedTab = PickerTab.ORDERS,
                onSelectTab = { tab ->
                    if (tab == PickerTab.CASH) onOpenCash()
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = PickerMetrics.ScreenPadding,
                end = PickerMetrics.ScreenPadding,
                top = 14.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Staff Identity & Live Connection Card
            item {
                StaffIdentityCard(
                    state = state,
                    onClickConnection = { showServerSheet = true },
                )
            }

            // 2. Alert Permissions Warning (if incomplete)
            if (!perms.allGranted) {
                item {
                    PermissionsAlertBanner(
                        perms = perms,
                        onOpenPermissions = onOpenPermissions,
                    )
                }
            }

            // 3. Offline Relay / Server Unreachable Banner
            if (!state.serverReachable && state.error != null) {
                item {
                    if (state.serviceRunning) {
                        PickerSectionCard(
                            containerColor = RailBitePickerPalette.CyanTint,
                            border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                PickerStatusDot(online = false, colorOverride = RailBitePickerPalette.CyanPrimary)
                                Text(
                                    text = "Server unreachable — Bluetooth relay listener active",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = RailBitePickerPalette.CyanPressed,
                                )
                            }
                        }
                    } else {
                        PickerErrorBanner(message = state.error ?: "Cannot reach server")
                    }
                }
            }

            // 4. Hero Duty Availability Switch (ONLINE / OFFLINE)
            item {
                HeroDutyToggleCard(
                    state = state,
                    onToggle = vm::setOnline,
                )
            }

            // 5. 3-Column KPI Strip (Delivered | Collected | Confirmed)
            item {
                ShiftKpiStrip(
                    delivered = state.stats?.ordersDelivered ?: 0,
                    collectedPaise = state.stats?.collectedPaise ?: 0,
                    confirmedPaise = state.stats?.passengerConfirmedPaise ?: 0,
                    onClickCash = onOpenCash,
                )
            }

            // 6. Pending Offline Bluetooth Orders (if any)
            if (pendingMesh > 0) {
                item {
                    PickerSectionCard(
                        containerColor = RailBitePickerPalette.SemanticAmberTint,
                        border = BorderStroke(1.dp, RailBitePickerPalette.SemanticAmberBorder),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showToolsSheet = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = RailBitePickerPalette.SemanticAmberInk,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "$pendingMesh relay order${if (pendingMesh == 1) "" else "s"} queued to sync",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RailBitePickerPalette.SemanticAmberInk,
                                )
                            }
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.labelMedium,
                                color = RailBitePickerPalette.SemanticAmberInk,
                            )
                        }
                    }
                }
            }

            // 7. Active Queue Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(
                        text = "ACTIVE QUEUE (${state.activeOrders.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = RailBitePickerPalette.TextPrimary,
                    )
                    if (state.lastSyncedLabel.isNotBlank()) {
                        Text(
                            text = "Updated ${state.lastSyncedLabel}",
                            style = PickerNumericType.MetaMono,
                            color = RailBitePickerPalette.TextMuted,
                        )
                    }
                }
            }

            // 8. Active Orders OR Empty State
            if (state.activeOrders.isEmpty()) {
                item {
                    EmptyQueueCard(
                        online = state.online,
                        onTestAlert = {
                            val alert = vm.fakeAlert()
                            AlertBus.post(alert)
                            onShowAlert(alert.orderId)
                        },
                    )
                }
            } else {
                items(
                    items = state.activeOrders,
                    key = { it.id },
                ) { order ->
                    ActiveQueueOrderCard(
                        order = order,
                        unreadCount = state.unreadCounts[order.id] ?: 0,
                        latestCustomerMessage = state.latestCustomerMessages[order.id],
                        onOpenOrder = { onOpenOrder(order.id, false) },
                        onOpenChat = {
                            vm.markOrderChatSeen(order.id)
                            onOpenOrder(order.id, true)
                        },
                    )
                }
            }
        }
    }

    if (showToolsSheet) {
        DashboardToolsSheet(
            graph = graph,
            state = state,
            perms = perms,
            pendingMesh = pendingMesh,
            onOpenServerConfig = {
                showToolsSheet = false
                showServerSheet = true
            },
            onOpenPermissions = {
                showToolsSheet = false
                onOpenPermissions()
            },
            onTestAlert = {
                showToolsSheet = false
                val alert = vm.fakeAlert()
                AlertBus.post(alert)
                onShowAlert(alert.orderId)
            },
            onSignOut = {
                showToolsSheet = false
                vm.logout(onLoggedOut)
            },
            onDismiss = { showToolsSheet = false },
        )
    }

    if (showServerSheet) {
        ServerSettingsSheet(
            currentServerUrl = state.serverUrlStored,
            currentMqttWsUrl = state.mqttWsUrlStored,
            onSave = { url, wsUrl ->
                vm.updateServerConfig(url, wsUrl)
                showServerSheet = false
            },
            onDismiss = { showServerSheet = false },
        )
    }
}

@Composable
private fun StaffIdentityCard(
    state: HomeUiState,
    onClickConnection: () -> Unit,
) {
    val cleanName = state.staffName
        .substringBefore(" (")
        .ifBlank { "Pantry Crew" }
    val initials = cleanName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { state.staffId.uppercase().take(2).ifBlank { "RB" } }

    PickerSectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(RailBitePickerPalette.DarkSlate),
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = cleanName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = RailBitePickerPalette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.staffId.isNotBlank()) {
                        Text(
                            text = "(${state.staffId.lowercase()})",
                            style = PickerNumericType.MetaMono,
                            color = RailBitePickerPalette.TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(PickerRadii.Control))
                        .clickable(onClick = onClickConnection)
                        .padding(vertical = 2.dp),
                ) {
                    PickerStatusDot(online = state.connectionConnected)
                    Text(
                        text = when {
                            state.mqttConnected -> "Connected • MQTT Live"
                            state.serverReachable -> "Connected • REST Polling"
                            else -> "Disconnected • Tap to configure"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (state.connectionConnected) {
                            RailBitePickerPalette.SemanticGreenInk
                        } else {
                            RailBitePickerPalette.SemanticRed
                        },
                    )
                }
            }

            if (state.zone.isNotBlank()) {
                PickerStatusTag(
                    text = state.zone.replace(", ", "–"),
                    bgColor = RailBitePickerPalette.CyanTint,
                    textColor = RailBitePickerPalette.CyanPressed,
                    borderColor = RailBitePickerPalette.CyanBorder,
                )
            }
        }
    }
}

@Composable
private fun PermissionsAlertBanner(
    perms: AlertPermissionState,
    onOpenPermissions: () -> Unit,
) {
    PickerSectionCard(
        containerColor = RailBitePickerPalette.SemanticRedTint,
        border = BorderStroke(1.dp, RailBitePickerPalette.SemanticRedBorder),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenPermissions)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = RailBitePickerPalette.SemanticRed,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Alert Permissions Incomplete (${perms.grantedCount}/3)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = RailBitePickerPalette.SemanticRed,
                )
                Text(
                    text = "Required for loud lock-screen order takeover",
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePickerPalette.SemanticRed,
                )
            }
            PickerStatusTag(
                text = "Fix",
                bgColor = RailBitePickerPalette.SurfaceCard,
                textColor = RailBitePickerPalette.SemanticRed,
                borderColor = RailBitePickerPalette.SemanticRedBorder,
            )
        }
    }
}

@Composable
private fun HeroDutyToggleCard(
    state: HomeUiState,
    onToggle: (Boolean) -> Unit,
) {
    val online = state.online
    PickerSectionCard(
        containerColor = if (online) {
            RailBitePickerPalette.SemanticGreenTint
        } else {
            RailBitePickerPalette.SurfaceCard
        },
        border = if (online) {
            BorderStroke(1.5.dp, RailBitePickerPalette.SemanticGreenBorder)
        } else {
            PickerCardBorder
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(PickerRadii.Button))
                    .background(
                        if (online) RailBitePickerPalette.SemanticGreen else RailBitePickerPalette.ImageTile,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = if (online) Color.White else RailBitePickerPalette.TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (online) "You are ONLINE" else "You are OFFLINE",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (online) {
                            RailBitePickerPalette.SemanticGreenInk
                        } else {
                            RailBitePickerPalette.TextPrimary
                        },
                    )
                    if (online && state.serviceRunning) {
                        PickerStatusTag(
                            text = "LIVE",
                            bgColor = RailBitePickerPalette.SurfaceCard,
                            textColor = RailBitePickerPalette.SemanticGreenInk,
                            borderColor = RailBitePickerPalette.SemanticGreenBorder,
                            dotColor = RailBitePickerPalette.SemanticGreen,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (online) {
                        "Listening for coach orders near ${state.zone.ifBlank { "assigned sector" }}"
                    } else {
                        "Switch ON to receive new coach order alerts"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (online) {
                        RailBitePickerPalette.SemanticGreenInk
                    } else {
                        RailBitePickerPalette.TextSecondary
                    },
                )
            }

            Switch(
                checked = online,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = RailBitePickerPalette.SemanticGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = RailBitePickerPalette.BorderSubtle,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun ShiftKpiStrip(
    delivered: Int,
    collectedPaise: Int,
    confirmedPaise: Int,
    onClickCash: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        KpiTile(
            label = "DELIVERED",
            value = delivered.toString(),
            valueColor = RailBitePickerPalette.TextPrimary,
            onClick = onClickCash,
            modifier = Modifier.weight(1f),
        )
        KpiTile(
            label = "COLLECTED",
            value = Money.rupees(collectedPaise),
            valueColor = RailBitePickerPalette.TextPrimary,
            onClick = onClickCash,
            modifier = Modifier.weight(1f),
        )
        KpiTile(
            label = "CONFIRMED",
            value = Money.rupees(confirmedPaise),
            valueColor = if (confirmedPaise > 0) {
                RailBitePickerPalette.SemanticGreenInk
            } else {
                RailBitePickerPalette.TextPrimary
            },
            onClick = onClickCash,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    valueColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerSectionCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = RailBitePickerPalette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = PickerNumericType.KpiHero,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActiveQueueOrderCard(
    order: Order,
    unreadCount: Int,
    latestCustomerMessage: OrderMessage?,
    onOpenOrder: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val itemList = order.items.orEmpty()
    val totalQty = itemList.sumOf { it.qty }.coerceAtLeast(itemList.size)
    val itemsSummary = itemList.joinToString(", ") { item ->
        "${item.qty}× ${item.name ?: item.itemId}"
    }.ifBlank { "Order items" }
    val paymentLabel = if (order.paymentMode == "UPI_QUEUED") "UPI" else "COD"

    val (statusBg, statusInk, statusBorder) = when (order.status) {
        Statuses.OUT_FOR_DELIVERY -> Triple(
            RailBitePickerPalette.CyanTint,
            RailBitePickerPalette.CyanPressed,
            RailBitePickerPalette.CyanBorder,
        )
        Statuses.PACKING -> Triple(
            RailBitePickerPalette.SemanticAmberTint,
            RailBitePickerPalette.SemanticAmberInk,
            RailBitePickerPalette.SemanticAmberBorder,
        )
        else -> Triple(
            RailBitePickerPalette.SemanticGreenTint,
            RailBitePickerPalette.SemanticGreenInk,
            RailBitePickerPalette.SemanticGreenBorder,
        )
    }

    PickerSectionCard(
        border = if (unreadCount > 0) PickerSelectedBorder else PickerCardBorder,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Seat pill + Order tag + Status badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PickerSeatPill(coach = order.coach, berth = order.berth)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = shortOrderTag(order.id),
                        style = PickerNumericType.MetaMono,
                        color = RailBitePickerPalette.TextSecondary,
                    )
                    Text(
                        text = "$totalQty item${if (totalQty == 1) "" else "s"} • ${Money.rupees(order.totalPaise)} $paymentLabel",
                        style = PickerNumericType.PriceMedium,
                        color = RailBitePickerPalette.TextPrimary,
                    )
                }
                PickerStatusTag(
                    text = Statuses.label(order.status).uppercase(),
                    bgColor = statusBg,
                    textColor = statusInk,
                    borderColor = statusBorder,
                )
            }

            // Items preview
            Text(
                text = itemsSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = RailBitePickerPalette.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Latest customer quick-message preview bar
            if (latestCustomerMessage != null) {
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Button),
                    color = RailBitePickerPalette.CyanTint,
                    border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenChat)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = RailBitePickerPalette.CyanPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "Passenger:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RailBitePickerPalette.CyanPressed,
                                )
                                if (unreadCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(PickerRadii.Pill),
                                        color = RailBitePickerPalette.CyanPrimary,
                                    ) {
                                        Text(
                                            text = "$unreadCount NEW",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = latestCustomerMessage.body,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = RailBitePickerPalette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(PickerRadii.Control),
                            color = RailBitePickerPalette.SurfaceCard,
                            border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                        ) {
                            Text(
                                text = "Reply",
                                style = MaterialTheme.typography.labelMedium,
                                color = RailBitePickerPalette.CyanPressed,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            // Primary 48.dp Action CTA
            Button(
                onClick = onOpenOrder,
                shape = RoundedCornerShape(PickerRadii.Button),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RailBitePickerPalette.CyanPrimary,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = if (order.status == Statuses.OUT_FOR_DELIVERY) {
                        "Continue Handover →"
                    } else {
                        "Resume Picking →"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun EmptyQueueCard(
    online: Boolean,
    onTestAlert: () -> Unit,
) {
    PickerSectionCard {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 22.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(PickerRadii.Card))
                    .background(RailBitePickerPalette.ImageTile),
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = RailBitePickerPalette.TextSecondary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = "No Active Orders",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = RailBitePickerPalette.TextPrimary,
            )
            Text(
                text = if (online) {
                    "You are ONLINE. Incoming coach orders will wake your screen and ring at full alarm volume."
                } else {
                    "Switch your duty toggle ONLINE above to start receiving coach dispatch alerts."
                },
                style = MaterialTheme.typography.bodySmall,
                color = RailBitePickerPalette.TextSecondary,
            )

            Surface(
                shape = RoundedCornerShape(PickerRadii.Button),
                color = RailBitePickerPalette.CyanTint,
                border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = RailBitePickerPalette.CyanPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Loud Alerts Active",
                            style = MaterialTheme.typography.labelLarge,
                            color = RailBitePickerPalette.CyanPressed,
                        )
                    }
                    OutlinedButton(
                        onClick = onTestAlert,
                        shape = RoundedCornerShape(PickerRadii.Control),
                        border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RailBitePickerPalette.CyanPressed,
                            containerColor = RailBitePickerPalette.SurfaceCard,
                        ),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Text("Test Ring", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardToolsSheet(
    graph: PickerGraph,
    state: HomeUiState,
    perms: AlertPermissionState,
    pendingMesh: Int,
    onOpenServerConfig: () -> Unit,
    onOpenPermissions: () -> Unit,
    onTestAlert: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val container = graph.container
    val sinkEnabled by container.settings.meshSinkEnabled.collectAsState(initial = false)
    var missingBlePerms by remember { mutableStateOf(container.meshTransport.requiredPermissions()) }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        missingBlePerms = container.meshTransport.requiredPermissions()
        if (missingBlePerms.isEmpty() && sinkEnabled) {
            scope.launch { runCatching { container.meshGateway.start() } }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PickerBottomSheetShape,
        containerColor = RailBitePickerPalette.SurfaceCard,
        scrimColor = RailBitePickerPalette.DarkSlate.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Picker Shift Tools",
                style = MaterialTheme.typography.titleLarge,
                color = RailBitePickerPalette.TextPrimary,
            )
            HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)

            ToolActionRow(
                icon = Icons.Filled.Router,
                title = "Edge Server & MQTT URL",
                subtitle = state.serverUrl.ifBlank { "Tap to configure laptop LAN IP" },
                onClick = onOpenServerConfig,
            )

            ToolActionRow(
                icon = Icons.Filled.NotificationsActive,
                title = "Lock-Screen & Battery Permissions",
                subtitle = "${perms.grantedCount}/3 granted",
                onClick = onOpenPermissions,
            )

            ToolActionRow(
                icon = Icons.Filled.NotificationsActive,
                title = "Test Loud Incoming Order Ring",
                subtitle = "Triggers alarm, vibration & 60s countdown takeover",
                onClick = onTestAlert,
            )

            // Preserved offline BLE relay toggle
            PickerSectionCard(containerColor = RailBitePickerPalette.Canvas) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bluetooth,
                            contentDescription = null,
                            tint = RailBitePickerPalette.CyanPrimary,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Offline Bluetooth Relay",
                                style = MaterialTheme.typography.titleSmall,
                                color = RailBitePickerPalette.TextPrimary,
                            )
                            Text(
                                text = if (sinkEnabled) {
                                    "Listening for offline passenger packets ($pendingMesh queued)"
                                } else {
                                    "Disabled"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = RailBitePickerPalette.TextSecondary,
                            )
                        }
                        Switch(
                            checked = sinkEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    container.settings.setMeshSinkEnabled(enabled)
                                    if (enabled) {
                                        val missing = container.meshTransport.requiredPermissions()
                                        if (missing.isEmpty() && container.meshTransport.isUsable()) {
                                            container.meshGateway.start()
                                        } else {
                                            blePermissionLauncher.launch(missing.toTypedArray())
                                        }
                                    } else {
                                        container.meshGateway.stop()
                                    }
                                }
                            },
                        )
                    }
                    if (missingBlePerms.isNotEmpty() && sinkEnabled) {
                        OutlinedButton(
                            onClick = { blePermissionLauncher.launch(missingBlePerms.toTypedArray()) },
                            shape = RoundedCornerShape(PickerRadii.Control),
                            border = PickerCardBorder,
                        ) {
                            Text("Grant Nearby Bluetooth Permission")
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(PickerRadii.Button),
                border = BorderStroke(1.dp, RailBitePickerPalette.SemanticRedBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = RailBitePickerPalette.SemanticRedTint,
                    contentColor = RailBitePickerPalette.SemanticRed,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun ToolActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    PickerSectionCard(containerColor = RailBitePickerPalette.Canvas) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PickerMetrics.TouchTargetMin)
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(PickerRadii.Control))
                    .background(RailBitePickerPalette.SurfaceCard),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = RailBitePickerPalette.CyanPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = RailBitePickerPalette.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePickerPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

