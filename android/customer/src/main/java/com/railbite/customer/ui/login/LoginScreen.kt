package com.railbite.customer.ui.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.api.ApiClient
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteTextField
import com.railbite.core.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Customer auth gate: Firebase Phone Auth (SMS code) or local demo sign-in.
 * Token-driven UI — colours/typography/spacing come only from the design tokens.
 */
@Composable
fun CustomerLoginScreen(
    graph: com.railbite.customer.CustomerGraph,
    onLoggedIn: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val vm: CustomerLoginViewModel = viewModel(key = "customer-login") { CustomerLoginViewModel(graph.container, activity, onLoggedIn) }
    val state by vm.state.collectAsState()
    val serverUrl by graph.settings.serverUrl.collectAsState(initial = "")
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var showServerDialog by remember { mutableStateOf(false) }
    var editServerUrl by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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

            RailBiteTextField(
                value = state.phone,
                onValueChange = vm::onPhoneChange,
                label = "Mobile number",
                hint = "+91 98765 43210",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(Space.s))

            if (state.stage == CustomerLoginStage.OTP) {
                RailBiteTextField(
                    value = state.otp,
                    onValueChange = vm::onOtpChange,
                    label = "SMS code",
                    hint = "6-digit code",
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focus.clearFocus()
                        vm.verifyCode()
                    }),
                )
                Spacer(Modifier.height(Space.s))
            }

            if (state.error != null) {
                Spacer(Modifier.height(Space.s))
                ErrorBar(state.error ?: "")
            }

            Spacer(Modifier.height(Space.m))

            RailBiteButton(
                text = when {
                    state.devMode -> "Continue with demo sign-in"
                    state.stage == CustomerLoginStage.OTP -> "Verify & continue"
                    else -> "Send OTP"
                },
                onClick = {
                    focus.clearFocus()
                    when {
                        state.devMode -> vm.sendOtp()
                        state.stage == CustomerLoginStage.OTP -> vm.verifyCode()
                        else -> vm.sendOtp()
                    }
                },
                enabled = !state.busy && !state.devMode && (if (state.stage == CustomerLoginStage.OTP) state.codeValid else state.phoneValid),
                busy = state.busy,
            )

            Spacer(Modifier.height(Space.m))

            if (state.devMode) {
                Text(
                    "Demo mode — this build has no Firebase config, so sign in via the local demo server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Space.xl))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    "Server: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (serverUrl.isNotBlank()) serverUrl else "Not configured",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
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

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
