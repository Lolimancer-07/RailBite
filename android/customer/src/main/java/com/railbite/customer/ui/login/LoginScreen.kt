package com.railbite.customer.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.api.ApiClient
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteTextField
import com.railbite.customer.ui.components.StatusDot
import com.railbite.customer.ui.onboarding.flatTextFieldColors
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii
import kotlinx.coroutines.launch

/**
 * Customer auth gate — dummy phone + demo OTP login page.
 * Preserves local demo OTP generation + dev-login JWT exchange + Server URL editor.
 */
@Composable
fun CustomerLoginScreen(
    graph: com.railbite.customer.CustomerGraph,
    onLoggedIn: () -> Unit,
) {
    val vm: CustomerLoginViewModel = viewModel(key = "customer-login") {
        CustomerLoginViewModel(graph.container, onLoggedIn)
    }
    val state by vm.state.collectAsState()
    val serverUrl by graph.settings.serverUrl.collectAsState(initial = "")
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var showServerDialog by remember { mutableStateOf(false) }
    var editServerUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RailBitePalette.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            shape = RoundedCornerShape(RailBiteRadii.Card),
            colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
            border = RailBiteCardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(RailBiteRadii.Control),
                        color = RailBitePalette.CyanPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Train, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("RailBite", style = MaterialTheme.typography.titleLarge, color = RailBitePalette.TextPrimary)
                            Surface(
                                shape = RoundedCornerShape(RailBiteRadii.Control),
                                color = RailBitePalette.CyanTint,
                                border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                            ) {
                                Text(
                                    text = "PASSENGER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RailBitePalette.CyanPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            "Food delivered straight to your berth",
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePalette.TextSecondary,
                        )
                    }
                }

                if (state.stage == CustomerLoginStage.PHONE) {
                    Text(
                        "Sign in with Mobile Number",
                        style = MaterialTheme.typography.headlineSmall,
                        color = RailBitePalette.TextPrimary,
                    )
                    OutlinedTextField(
                        value = state.phone,
                        onValueChange = vm::onPhoneChange,
                        label = { Text("Mobile number (+91)") },
                        placeholder = { Text("9876543210", style = RailBiteNumericType.PriceMedium) },
                        supportingText = { Text("We'll generate a 6-digit demo verification code on device") },
                        textStyle = RailBiteNumericType.PriceMedium,
                        singleLine = true,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        colors = flatTextFieldColors(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focus.clearFocus()
                                vm.sendOtp()
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Code for +91 ${formatPhone(state.phone)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = RailBitePalette.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = vm::changeNumber,
                            modifier = Modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
                        ) {
                            Text("Change", color = RailBitePalette.CyanPrimary)
                        }
                    }

                    OutlinedTextField(
                        value = state.otp,
                        onValueChange = vm::onOtpChange,
                        label = { Text("6-digit OTP") },
                        placeholder = { Text("000000", style = RailBiteNumericType.PriceLarge) },
                        textStyle = RailBiteNumericType.PriceLarge,
                        singleLine = true,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        colors = flatTextFieldColors(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focus.clearFocus()
                                vm.verifyCode()
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.info?.let { infoText ->
                    Surface(
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        color = RailBitePalette.CyanTint,
                        border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = infoText,
                            style = RailBiteNumericType.MetaMono,
                            color = RailBitePalette.CyanPressed,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }

                state.error?.let { ErrorBar(it) }

                val enabled = !state.busy &&
                    (if (state.stage == CustomerLoginStage.OTP) state.codeValid else state.phoneValid)

                Button(
                    onClick = {
                        focus.clearFocus()
                        if (state.stage == CustomerLoginStage.OTP) vm.verifyCode() else vm.sendOtp()
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RailBitePalette.CyanPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = RailBitePalette.ImageTile,
                        disabledContentColor = RailBitePalette.TextMuted,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RailBiteMetrics.PrimaryButtonHeight),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = if (state.stage == CustomerLoginStage.OTP) "Verify & Continue" else "Send OTP",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                if (state.stage == CustomerLoginStage.OTP) {
                    TextButton(
                        onClick = vm::resendOtp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .heightIn(min = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Text("Generate a new code", color = RailBitePalette.CyanPrimary)
                    }
                }

                Text(
                    "Demo mode — no SMS is sent. Verification OTP is generated directly on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Runtime Server URL trigger
        Surface(
            onClick = {
                editServerUrl = serverUrl
                showServerDialog = true
            },
            shape = RoundedCornerShape(RailBiteRadii.Pill),
            color = RailBitePalette.SurfaceCard,
            border = RailBiteCardBorder,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RailBiteMetrics.TouchTargetMin),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                StatusDot(online = serverUrl.isNotBlank())
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Server: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = RailBitePalette.TextSecondary,
                )
                Text(
                    text = if (serverUrl.isNotBlank()) serverUrl else "Default cloud",
                    style = MaterialTheme.typography.labelMedium,
                    color = RailBitePalette.CyanPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelMedium,
                    color = RailBitePalette.TextPrimary,
                )
            }
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            containerColor = RailBitePalette.SurfaceCard,
            shape = RoundedCornerShape(RailBiteRadii.Card),
            title = { Text("Server address", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        "Custom server URL — leave empty to use the built-in RailBite cloud server (railbite-api.onrender.com):",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    RailBiteTextField(
                        value = editServerUrl,
                        onValueChange = { editServerUrl = it },
                        label = "Server URL",
                        hint = "https://railbite-api.onrender.com",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrect = false),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { graph.settings.setServerUrl(ApiClient.normalizeServerUrl(editServerUrl)) }
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RailBitePalette.CyanPrimary),
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

private fun formatPhone(digits: String): String =
    if (digits.length <= 5) digits else "${digits.take(5)} ${digits.drop(5)}"
