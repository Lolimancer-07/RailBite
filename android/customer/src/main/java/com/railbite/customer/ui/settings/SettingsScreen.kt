package com.railbite.customer.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.mesh.BleMeshHub
import com.railbite.core.mesh.TransportMode
import com.railbite.core.ui.components.ErrorBar
import com.railbite.customer.ui.components.LocalBleActionRequester
import com.railbite.customer.ui.components.StatusDot
import com.railbite.customer.ui.components.TransportIndicatorPill
import com.railbite.customer.ui.onboarding.flatTextFieldColors
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    graph: com.railbite.customer.CustomerGraph,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = "settings") { SettingsViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val mqttConnected by graph.mqtt.connected.collectAsState()
    val transportStatus by graph.container.transportRouter.status.collectAsState()
    val meshStats by BleMeshHub.stats.collectAsState()
    val requestBle = LocalBleActionRequester.current

    Scaffold(
        containerColor = RailBitePalette.Canvas,
        topBar = {
            Column(Modifier.background(RailBitePalette.SurfaceCard)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RailBitePalette.SurfaceCard,
                        titleContentColor = RailBitePalette.TextPrimary,
                    ),
                    title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RailBitePalette.TextPrimary,
                            )
                        }
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(RailBiteRadii.Pill),
                            color = RailBitePalette.Canvas,
                            border = RailBiteCardBorder,
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                StatusDot(online = mqttConnected)
                                Text(
                                    text = if (mqttConnected) "MQTT Live" else "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RailBitePalette.TextPrimary,
                                )
                            }
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Card 1: Server IP / LAN URL Configuration
            Card(
                shape = RoundedCornerShape(RailBiteRadii.Card),
                colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
                border = RailBiteCardBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Server & MQTT Endpoint",
                        style = MaterialTheme.typography.titleMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                    OutlinedTextField(
                        value = state.serverUrl,
                        onValueChange = vm::setServerUrl,
                        label = { Text("Custom server URL (advanced, optional)") },
                        placeholder = { Text("https://railbite-api.onrender.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        colors = flatTextFieldColors(),
                        keyboardOptions = KeyboardOptions(
                            // Preserve http:// LAN URLs without autocorrect overriding scheme
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Leave empty to use the built-in RailBite cloud server, or enter your LAN IP (e.g. http://192.168.1.10:3000).",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                    )

                    OutlinedButton(
                        onClick = vm::testConnection,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        border = RailBiteCardBorder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Text("Test Connection", style = MaterialTheme.typography.labelLarge)
                    }

                    when (val t = state.testState) {
                        is OnboardingLikeTestState.Testing -> Text(
                            text = "Testing server health…",
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePalette.TextSecondary,
                        )
                        is OnboardingLikeTestState.Ok -> Surface(
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            color = RailBitePalette.SemanticGreenTint,
                            border = BorderStroke(1.dp, RailBitePalette.SemanticGreenBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                StatusDot(online = true)
                                Text(
                                    text = "Connected cleanly",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RailBitePalette.SemanticGreen,
                                )
                            }
                        }
                        is OnboardingLikeTestState.Fail -> ErrorBar(t.message)
                        else -> {}
                    }
                }
            }

            // Card 2: Offline Relay (Bluetooth mesh)
            Card(
                shape = RoundedCornerShape(RailBiteRadii.Card),
                colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
                border = RailBiteCardBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Offline Relay (Bluetooth Mesh)",
                        style = MaterialTheme.typography.titleMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                    TransportIndicatorPill(
                        status = transportStatus,
                        onClick = if (transportStatus.mode == TransportMode.BLE_DISABLED) requestBle else null,
                    )
                    if (!meshStats.bluetoothEnabled || !meshStats.permissionsGranted) {
                        OutlinedButton(
                            onClick = requestBle,
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.CyanPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = RailBiteMetrics.TouchTargetMin),
                        ) {
                            Text(
                                text = if (!meshStats.permissionsGranted) {
                                    "Grant Bluetooth Permissions"
                                } else {
                                    "Enable Bluetooth"
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Bluetooth mesh relay",
                                style = MaterialTheme.typography.titleSmall,
                                color = RailBitePalette.TextPrimary,
                            )
                            Text(
                                text = if (state.meshRelayEnabled) "Enabled (multi-hop flood)" else "Off",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.meshRelayEnabled) RailBitePalette.CyanPrimary else RailBitePalette.TextSecondary,
                            )
                        }
                        Switch(
                            checked = state.meshRelayEnabled,
                            onCheckedChange = vm::setMeshRelayEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RailBitePalette.CyanPrimary,
                            ),
                        )
                    }

                    Text(
                        text = "When cellular/Wi-Fi drops, 23-byte order frames hop phone-to-phone over BLE to reach the nearest picker automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Mesh Debug",
                                style = MaterialTheme.typography.titleSmall,
                                color = RailBitePalette.TextPrimary,
                            )
                            Text(
                                text = "Live packet counters & shared train key",
                                style = MaterialTheme.typography.labelSmall,
                                color = RailBitePalette.TextSecondary,
                            )
                        }
                        Switch(
                            checked = state.meshDebugEnabled,
                            onCheckedChange = vm::setMeshDebugEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RailBitePalette.CyanPrimary,
                            ),
                        )
                    }

                    if (state.meshDebugEnabled) {
                        Surface(
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            color = RailBitePalette.Canvas,
                            border = RailBiteCardBorder,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Packets Sent", style = MaterialTheme.typography.bodySmall, color = RailBitePalette.TextSecondary)
                                    Text("${meshStats.packetsSent}", style = RailBiteNumericType.MetaMono, color = RailBitePalette.TextPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Packets Relayed", style = MaterialTheme.typography.bodySmall, color = RailBitePalette.TextSecondary)
                                    Text("${meshStats.packetsRelayed}", style = RailBiteNumericType.MetaMono, color = RailBitePalette.TextPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Last RSSI / Hops", style = MaterialTheme.typography.bodySmall, color = RailBitePalette.TextSecondary)
                                    Text(
                                        text = "${meshStats.lastRssiDbm?.let { "$it dBm" } ?: "—"} • ${meshStats.lastHopCount?.let { "$it hops" } ?: "—"}",
                                        style = RailBiteNumericType.MetaMono,
                                        color = RailBitePalette.CyanPrimary,
                                    )
                                }
                                OutlinedTextField(
                                    value = state.meshKey,
                                    onValueChange = vm::setMeshKey,
                                    label = { Text("Train Mesh Key (HMAC-16)") },
                                    singleLine = true,
                                    colors = flatTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    if (state.meshPending > 0) {
                        Text(
                            text = "${state.meshPending} offline order${if (state.meshPending == 1) "" else "s"} waiting for LAN sync",
                            style = MaterialTheme.typography.labelLarge,
                            color = RailBitePalette.CyanPrimary,
                        )
                    }

                    OutlinedButton(
                        onClick = vm::syncMeshOrders,
                        enabled = !state.meshSyncing,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        border = RailBiteCardBorder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Text(
                            text = if (state.meshSyncing) "Syncing…" else "Sync Now",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    state.meshSyncMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePalette.CyanPressed,
                        )
                    }
                }
            }

            // Card 3: Seat Details & Save CTA
            Card(
                shape = RoundedCornerShape(RailBiteRadii.Card),
                colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
                border = RailBiteCardBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Your Seat",
                        style = MaterialTheme.typography.titleMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                    OutlinedTextField(
                        value = state.pnr,
                        onValueChange = vm::setPnr,
                        label = { Text("PNR (10 digits)") },
                        textStyle = RailBiteNumericType.PriceMedium,
                        singleLine = true,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        colors = flatTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.coach,
                            onValueChange = vm::setCoach,
                            label = { Text("Coach") },
                            textStyle = RailBiteNumericType.PriceMedium,
                            singleLine = true,
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            colors = flatTextFieldColors(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.berth,
                            onValueChange = vm::setBerth,
                            label = { Text("Seat") },
                            textStyle = RailBiteNumericType.PriceMedium,
                            singleLine = true,
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            colors = flatTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Text("Diners", style = MaterialTheme.typography.titleSmall, color = RailBitePalette.TextPrimary)
                        Surface(
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            color = RailBitePalette.Canvas,
                            border = RailBiteCardBorder,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { vm.setDiners(state.diners - 1) },
                                    modifier = Modifier
                                        .size(RailBiteMetrics.TouchTargetMin)
                                        .semantics { contentDescription = "Fewer diners" },
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = null, tint = RailBitePalette.TextPrimary)
                                }
                                Text(
                                    text = "${state.diners}",
                                    style = RailBiteNumericType.PriceMedium,
                                    color = RailBitePalette.TextPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                IconButton(
                                    onClick = { vm.setDiners(state.diners + 1) },
                                    modifier = Modifier
                                        .size(RailBiteMetrics.TouchTargetMin)
                                        .semantics { contentDescription = "More diners" },
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = RailBitePalette.TextPrimary)
                                }
                            }
                        }
                    }

                    state.error?.let { ErrorBar(it) }

                    Button(
                        onClick = { vm.save(onBack) },
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RailBitePalette.CyanPrimary,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RailBiteMetrics.PrimaryButtonHeight),
                    ) {
                        Text(
                            text = if (state.saved) "Saved" else "Save Settings",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }

            // Card 4: Account & Sign Out
            val session by graph.authStore.session.collectAsState()
            Card(
                shape = RoundedCornerShape(RailBiteRadii.Card),
                colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
                border = RailBiteCardBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Account", style = MaterialTheme.typography.titleMedium, color = RailBitePalette.TextPrimary)
                    session?.let {
                        Text(
                            text = "Signed in as ${it.user.phone ?: it.user.name ?: it.user.id}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RailBitePalette.TextSecondary,
                        )
                    }
                    OutlinedButton(
                        onClick = { vm.logout() },
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        border = BorderStroke(1.dp, RailBitePalette.SemanticRedBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.SemanticRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Text("Sign Out", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
