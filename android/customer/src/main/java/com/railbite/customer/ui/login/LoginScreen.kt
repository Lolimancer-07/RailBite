package com.railbite.customer.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.api.ApiClient
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteTextField
import com.railbite.core.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Customer auth gate — dummy phone + demo OTP login page.
 *
 * No Firebase SMS: the OTP is generated on-device and displayed on screen.
 * Real Firebase Phone Auth is deferred until later.
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(Space.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("RailBite", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Food delivered to your seat",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.xl))

            if (state.stage == CustomerLoginStage.PHONE) {
                RailBiteTextField(
                    value = state.phone,
                    onValueChange = vm::onPhoneChange,
                    label = "Mobile number (+91)",
                    hint = "98765 43210",
                    supportingText = "We'll text a 6-digit code to this number",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focus.clearFocus()
                        vm.sendOtp()
                    }),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Code for +91 ${formatPhone(state.phone)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::changeNumber) {
                        Text("Change")
                    }
                }
                RailBiteTextField(
                    value = state.otp,
                    onValueChange = vm::onOtpChange,
                    label = "6-digit OTP",
                    hint = "Enter the demo code",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focus.clearFocus()
                        vm.verifyCode()
                    }),
                )
                Spacer(Modifier.height(Space.s))
            }

            state.info?.let {
                Spacer(Modifier.height(Space.s))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let {
                Spacer(Modifier.height(Space.s))
                ErrorBar(it)
            }

            Spacer(Modifier.height(Space.m))

            RailBiteButton(
                text = if (state.stage == CustomerLoginStage.OTP) "Verify & continue" else "Send OTP",
                onClick = {
                    focus.clearFocus()
                    if (state.stage == CustomerLoginStage.OTP) vm.verifyCode() else vm.sendOtp()
                },
                enabled = !state.busy &&
                    (if (state.stage == CustomerLoginStage.OTP) state.codeValid else state.phoneValid),
                busy = state.busy,
            )

            if (state.stage == CustomerLoginStage.OTP) {
                TextButton(onClick = vm::resendOtp) {
                    Text("Generate a new code")
                }
            }

            Spacer(Modifier.height(Space.s))
            Text(
                "Demo mode — no SMS is sent. The OTP is generated on this device and shown above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Space.xl))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Server: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (serverUrl.isNotBlank()) serverUrl else "Not configured",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Space.xs))
                TextButton(
                    onClick = {
                        editServerUrl = serverUrl
                        showServerDialog = true
                    },
                ) {
                    Text("Change", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (showServerDialog) {
            AlertDialog(
                onDismissRequest = { showServerDialog = false },
                title = { Text("Server address") },
                text = {
                    Column {
                        Text(
                            "Custom server URL — leave empty to use the built-in RailBite cloud server (railbite-api.onrender.com):",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(Space.s))
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
}

private fun formatPhone(digits: String): String =
    if (digits.length <= 5) digits else "${digits.take(5)} ${digits.drop(5)}"
