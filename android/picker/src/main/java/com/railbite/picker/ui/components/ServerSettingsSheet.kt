package com.railbite.picker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.railbite.core.api.ApiClient
import com.railbite.picker.BuildConfig
import com.railbite.picker.ui.theme.PickerBottomSheetShape
import com.railbite.picker.ui.theme.PickerCardBorder
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.RailBitePickerPalette
import kotlinx.coroutines.launch

/**
 * Runtime Server IP & MQTT WebSocket URL configuration bottom sheet (Stitch `0db36cb0`).
 * Supports laptop LAN URLs (`http://<LAN_IP>:8080`), cloud URLs, and optional MQTT WS overrides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsSheet(
    currentServerUrl: String,
    currentMqttWsUrl: String,
    onSave: (serverUrl: String, mqttWsUrl: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var serverInput by remember(currentServerUrl) { mutableStateOf(currentServerUrl) }
    var mqttInput by remember(currentMqttWsUrl) { mutableStateOf(currentMqttWsUrl) }
    var testing by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val effectivePreview = ApiClient.normalizeServerUrl(serverInput).ifBlank { BuildConfig.DEFAULT_SERVER_URL }
    val derivedMqttPreview = ApiClient.deriveMqttWsUrl(effectivePreview, mqttInput)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PickerBottomSheetShape,
        containerColor = RailBitePickerPalette.SurfaceCard,
        scrimColor = RailBitePickerPalette.DarkSlate.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(PickerRadii.Pill))
                    .background(RailBitePickerPalette.BorderSubtle),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Button),
                    color = RailBitePickerPalette.CyanTint,
                    border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Router,
                            contentDescription = null,
                            tint = RailBitePickerPalette.CyanPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(
                        text = "Edge Server Config",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = RailBitePickerPalette.TextPrimary,
                    )
                    Text(
                        text = "Laptop LAN IP or cloud HTTPS endpoint",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePickerPalette.TextSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = RailBitePickerPalette.TextSecondary,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Server URL (blank = built-in cloud)",
                    style = MaterialTheme.typography.labelLarge,
                    color = RailBitePickerPalette.TextPrimary,
                )
                OutlinedTextField(
                    value = serverInput,
                    onValueChange = {
                        serverInput = it
                        probeResult = null
                    },
                    placeholder = {
                        Text(
                            text = "http://192.168.1.45:8080",
                            style = PickerNumericType.MetaMono,
                            color = RailBitePickerPalette.TextMuted,
                        )
                    },
                    singleLine = true,
                    textStyle = PickerNumericType.PriceMedium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "MQTT WebSocket URL (optional override)",
                    style = MaterialTheme.typography.labelLarge,
                    color = RailBitePickerPalette.TextPrimary,
                )
                OutlinedTextField(
                    value = mqttInput,
                    onValueChange = {
                        mqttInput = it
                        probeResult = null
                    },
                    placeholder = {
                        Text(
                            text = derivedMqttPreview.ifBlank { "ws://<laptop-ip>:8080/mqtt" },
                            style = PickerNumericType.MetaMono,
                            color = RailBitePickerPalette.TextMuted,
                        )
                    },
                    singleLine = true,
                    textStyle = PickerNumericType.MetaMono,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
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
                Text(
                    text = "Active MQTT: ${derivedMqttPreview.ifBlank { "Not configured" }}",
                    style = PickerNumericType.MetaMono,
                    color = RailBitePickerPalette.TextSecondary,
                )
            }

            probeResult?.let { (ok, message) ->
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Button),
                    color = if (ok) RailBitePickerPalette.SemanticGreenTint else RailBitePickerPalette.SemanticRedTint,
                    border = BorderStroke(
                        1.dp,
                        if (ok) RailBitePickerPalette.SemanticGreenBorder else RailBitePickerPalette.SemanticRedBorder,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) RailBitePickerPalette.SemanticGreenInk else RailBitePickerPalette.SemanticRed,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        if (testing) return@OutlinedButton
                        testing = true
                        probeResult = null
                        scope.launch {
                            val target = effectivePreview
                            val start = System.currentTimeMillis()
                            val res = runCatching { ApiClient.testConnection(target) }
                            val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1)
                            probeResult = res.fold(
                                onSuccess = { h -> true to "Connected • ${rtt}ms • Trip ${h.tripId}" },
                                onFailure = { e -> false to "Unreachable: ${e.message ?: "check Wi-Fi & port 8080"}" },
                            )
                            testing = false
                        }
                    },
                    shape = RoundedCornerShape(PickerRadii.Button),
                    border = PickerCardBorder,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RailBitePickerPalette.TextPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = PickerMetrics.TouchTargetMin),
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            color = RailBitePickerPalette.CyanPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Test Ping", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            ApiClient.normalizeServerUrl(serverInput),
                            mqttInput.trim(),
                        )
                    },
                    shape = RoundedCornerShape(PickerRadii.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RailBitePickerPalette.CyanPrimary,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .heightIn(min = PickerMetrics.TouchTargetMin),
                ) {
                    Text(
                        text = "Save & Connect",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

