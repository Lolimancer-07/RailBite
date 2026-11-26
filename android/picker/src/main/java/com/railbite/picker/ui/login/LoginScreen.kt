package com.railbite.picker.ui.login

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.api.ApiClient
import com.railbite.picker.PickerGraph
import com.railbite.picker.ui.components.PickerErrorBanner
import com.railbite.picker.ui.components.PickerPrimaryButton
import com.railbite.picker.ui.components.PickerSectionCard
import com.railbite.picker.ui.components.PickerStatusDot
import com.railbite.picker.ui.components.PickerStatusTag
import com.railbite.picker.ui.components.PinCellsRow
import com.railbite.picker.ui.components.ServerSettingsSheet
import com.railbite.picker.ui.theme.PickerCardBorder
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.PickerSelectedBorder
import com.railbite.picker.ui.theme.RailBitePickerPalette

private data class SectorPreview(
    val label: String,
    val subtitle: String,
    val staffName: String,
)

private fun previewSectorFor(staffId: String): SectorPreview = when (staffId.trim().uppercase()) {
    "C1" -> SectorPreview("Coaches B1 – B2", "AC 3-Tier • Pantry Forward", "Ramesh Kumar")
    "C2" -> SectorPreview("Coaches B3 – B4", "AC 3-Tier • Pantry Aft", "Suresh Sharma")
    "C3" -> SectorPreview("Coaches S1 – S2", "Sleeper • Forward Wing", "Amit Verma")
    "C4" -> SectorPreview("Coaches S3 – S4", "Sleeper • Rear Wing", "Deepak Patel")
    else -> SectorPreview("Assigned Coach Sector", "Loaded from pantry roster on sign-in", "Crew Member")
}

private fun shortHostLabel(url: String): String {
    if (url.isBlank()) return "Configure IP"
    return url
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore("/")
}

/**
 * Stitch Screen 1 (`0db36cb0`): Strictly flat white/slate/cyan Picker Login & Shift Config.
 * Supports Staff ID (`c1`–`c4`), 4-digit shift PIN (`1234`) or password (`railbite123`),
 * and runtime LAN Server IP + MQTT WebSocket configuration via [ServerSettingsSheet].
 */
@Composable
fun LoginScreen(
    graph: PickerGraph,
    onLoggedIn: () -> Unit,
) {
    val vm = viewModel(key = "login") { LoginViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val focus = LocalFocusManager.current
    var showServerSheet by remember { mutableStateOf(false) }
    var pinFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val sector = remember(state.staffId) { previewSectorFor(state.staffId) }
    val derivedWs = ApiClient.deriveMqttWsUrl(state.serverUrl, state.mqttWsUrl)

    Surface(
        color = RailBitePickerPalette.Canvas,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PickerMetrics.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Top Brand Header + Clickable LAN Server Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(PickerRadii.Button),
                        color = RailBitePickerPalette.CyanPrimary,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Train,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "RailBite",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = RailBitePickerPalette.TextPrimary,
                        )
                        Text(
                            text = "PANTRY OPS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = RailBitePickerPalette.CyanPrimary,
                        )
                    }
                }

                Surface(
                    onClick = { showServerSheet = true },
                    shape = RoundedCornerShape(PickerRadii.Pill),
                    color = RailBitePickerPalette.SurfaceCard,
                    border = PickerCardBorder,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        PickerStatusDot(
                            online = state.serverReachable == true,
                            colorOverride = when (state.serverReachable) {
                                true -> RailBitePickerPalette.SemanticGreen
                                false -> RailBitePickerPalette.SemanticRed
                                null -> RailBitePickerPalette.SemanticAmber
                            },
                        )
                        Text(
                            text = shortHostLabel(state.serverUrl),
                            style = PickerNumericType.MetaMono,
                            color = RailBitePickerPalette.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 2. Active Sector Run Summary Strip
            PickerSectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "ACTIVE TRAIN RUN",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = RailBitePickerPalette.TextSecondary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = state.healthTripId.ifBlank { "Edge Trip — Offline / LAN Ready" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = RailBitePickerPalette.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    when (state.serverReachable) {
                        true -> PickerStatusTag(
                            text = "${state.healthLatencyMs ?: 1}ms • LIVE",
                            bgColor = RailBitePickerPalette.SemanticGreenTint,
                            textColor = RailBitePickerPalette.SemanticGreenInk,
                            borderColor = RailBitePickerPalette.SemanticGreenBorder,
                            dotColor = RailBitePickerPalette.SemanticGreen,
                        )
                        false -> PickerStatusTag(
                            text = "UNREACHABLE",
                            bgColor = RailBitePickerPalette.SemanticRedTint,
                            textColor = RailBitePickerPalette.SemanticRed,
                            borderColor = RailBitePickerPalette.SemanticRedBorder,
                            dotColor = RailBitePickerPalette.SemanticRed,
                        )
                        null -> PickerStatusTag(
                            text = "CHECKING",
                            bgColor = RailBitePickerPalette.SemanticAmberTint,
                            textColor = RailBitePickerPalette.SemanticAmberInk,
                            borderColor = RailBitePickerPalette.SemanticAmberBorder,
                        )
                    }
                }
            }

            // 3. Main Picker Shift Login Card
            PickerSectionCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Picker Shift Sign-In",
                            style = MaterialTheme.typography.headlineMedium,
                            color = RailBitePickerPalette.TextPrimary,
                        )
                        Text(
                            text = "Enter your Crew ID and 4-digit PIN (or demo password) to receive coach order alerts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RailBitePickerPalette.TextSecondary,
                        )
                    }

                    // Staff ID + C1-C4 Quick Select Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Staff ID",
                            style = MaterialTheme.typography.labelLarge,
                            color = RailBitePickerPalette.TextPrimary,
                        )
                        OutlinedTextField(
                            value = state.staffId,
                            onValueChange = vm::onStaffIdChange,
                            placeholder = {
                                Text(
                                    text = "c1",
                                    style = PickerNumericType.PriceMedium,
                                    color = RailBitePickerPalette.TextMuted,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Badge,
                                    contentDescription = null,
                                    tint = RailBitePickerPalette.CyanPrimary,
                                )
                            },
                            singleLine = true,
                            textStyle = PickerNumericType.PriceMedium,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next,
                                autoCorrectEnabled = false,
                            ),
                            shape = RoundedCornerShape(PickerRadii.Button),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RailBitePickerPalette.CyanPrimary,
                                unfocusedBorderColor = RailBitePickerPalette.BorderSubtle,
                                focusedContainerColor = RailBitePickerPalette.Canvas,
                                unfocusedContainerColor = RailBitePickerPalette.Canvas,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf("C1", "C2", "C3", "C4").forEach { code ->
                                val selected = state.staffId.equals(code, ignoreCase = true)
                                Surface(
                                    onClick = { vm.onStaffIdChange(code) },
                                    shape = RoundedCornerShape(PickerRadii.Button),
                                    color = if (selected) RailBitePickerPalette.CyanTint else RailBitePickerPalette.Canvas,
                                    border = if (selected) PickerSelectedBorder else PickerCardBorder,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 38.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = code,
                                            style = PickerNumericType.BadgeMono,
                                            color = if (selected) RailBitePickerPalette.CyanPressed else RailBitePickerPalette.TextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Dynamic Assigned Distribution Sector Preview
                    Surface(
                        shape = RoundedCornerShape(PickerRadii.Button),
                        color = RailBitePickerPalette.Canvas,
                        border = PickerCardBorder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "ASSIGNED DISTRIBUTION SECTOR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RailBitePickerPalette.TextSecondary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = sector.label,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = RailBitePickerPalette.TextPrimary,
                                )
                                Text(
                                    text = "${sector.staffName} • ${sector.subtitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RailBitePickerPalette.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (state.staffId.isNotBlank()) {
                                PickerStatusTag(
                                    text = state.staffId,
                                    bgColor = RailBitePickerPalette.DarkSlate,
                                    textColor = Color.White,
                                )
                            }
                        }
                    }

                    // 4-Digit PIN / Password Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (state.useFullPassword) "Shift Password" else "4-Digit Shift PIN",
                                style = MaterialTheme.typography.labelLarge,
                                color = RailBitePickerPalette.TextPrimary,
                            )
                            Text(
                                text = if (state.useFullPassword) "Use 4-digit PIN" else "Use password",
                                style = MaterialTheme.typography.labelMedium,
                                color = RailBitePickerPalette.CyanPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(PickerRadii.Control))
                                    .clickable { vm.onTogglePasswordMode(!state.useFullPassword) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }

                        if (state.useFullPassword) {
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = vm::onPasswordChange,
                                placeholder = {
                                    Text(
                                        text = "railbite123",
                                        style = PickerNumericType.PriceMedium,
                                        color = RailBitePickerPalette.TextMuted,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = RailBitePickerPalette.CyanPrimary,
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (passwordVisible) "Hide" else "Show",
                                            tint = RailBitePickerPalette.TextSecondary,
                                        )
                                    }
                                },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focus.clearFocus()
                                        vm.submit(onLoggedIn)
                                    },
                                ),
                                shape = RoundedCornerShape(PickerRadii.Button),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RailBitePickerPalette.CyanPrimary,
                                    unfocusedBorderColor = RailBitePickerPalette.BorderSubtle,
                                    focusedContainerColor = RailBitePickerPalette.Canvas,
                                    unfocusedContainerColor = RailBitePickerPalette.Canvas,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                PinCellsRow(
                                    digits = state.password,
                                    masked = true,
                                    focused = pinFocused,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                BasicTextField(
                                    value = state.password,
                                    onValueChange = { raw ->
                                        vm.onPasswordChange(raw)
                                        val digits = raw.filter { it.isDigit() }.take(4)
                                        if (digits.length == 4 && state.staffId.isNotBlank()) {
                                            focus.clearFocus()
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.Transparent),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.NumberPassword,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            focus.clearFocus()
                                            vm.submit(onLoggedIn)
                                        },
                                    ),
                                    modifier = Modifier
                                        .matchParentSize()
                                        .alpha(0.01f)
                                        .onFocusChanged { pinFocused = it.isFocused },
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Demo accounts: c1–c4 • PIN 1234",
                                style = MaterialTheme.typography.bodySmall,
                                color = RailBitePickerPalette.TextSecondary,
                            )
                            Text(
                                text = "Fill Demo",
                                style = MaterialTheme.typography.labelMedium,
                                color = RailBitePickerPalette.CyanPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(PickerRadii.Control))
                                    .clickable {
                                        if (state.staffId.isBlank()) vm.onStaffIdChange("C1")
                                        if (state.useFullPassword) vm.onTogglePasswordMode(false)
                                        vm.onPasswordChange("1234")
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    state.error?.let { msg ->
                        PickerErrorBanner(message = msg)
                    }

                    PickerPrimaryButton(
                        text = "Start Shift Roster →",
                        onClick = {
                            focus.clearFocus()
                            vm.submit(onLoggedIn)
                        },
                        enabled = !state.busy && state.staffId.isNotBlank() && state.password.length >= 4,
                        busy = state.busy,
                    )
                }
            }

            // 4. Local Edge Node Connection Card
            PickerSectionCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(PickerRadii.Button))
                                .background(RailBitePickerPalette.CyanTint),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Router,
                                contentDescription = null,
                                tint = RailBitePickerPalette.CyanPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Local Edge Node & MQTT",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RailBitePickerPalette.TextPrimary,
                            )
                            Text(
                                text = state.serverUrl.ifBlank { "Server URL not configured" },
                                style = PickerNumericType.MetaMono,
                                color = RailBitePickerPalette.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "WS: ${derivedWs.ifBlank { "Derived from Server URL" }}",
                                style = PickerNumericType.MetaMono,
                                color = RailBitePickerPalette.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = { showServerSheet = true },
                            shape = RoundedCornerShape(PickerRadii.Button),
                            border = PickerCardBorder,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = RailBitePickerPalette.CyanPrimary,
                            ),
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) {
                            Text(
                                text = "Configure",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)
                    Text(
                        text = "Never hardcoded — configure laptop hotspot IP before duty shift.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePickerPalette.TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }

    if (showServerSheet) {
        ServerSettingsSheet(
            currentServerUrl = state.serverUrlStored,
            currentMqttWsUrl = state.mqttWsUrl,
            onSave = { url, wsUrl ->
                vm.updateServerConfig(url, wsUrl)
                showServerSheet = false
            },
            onDismiss = { showServerSheet = false },
        )
    }
}
