package com.railbite.picker.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
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
import com.railbite.picker.PickerGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Staff ID + password login (bcrypt on the server; demo accounts c1-c4, password railbite123). */
@Composable
fun LoginScreen(
    graph: PickerGraph,
    onLoggedIn: () -> Unit,
) {
    val vm = viewModel(key = "login") { LoginViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val serverUrl by graph.settings.serverUrl.collectAsState(initial = "")
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
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
            Text("RailBite Picker", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Train pantry staff",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.xl))

            RailBiteTextField(
                value = state.staffId,
                onValueChange = vm::onStaffIdChange,
                label = "Staff ID",
                hint = "c1",
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(Space.s))
            RailBiteTextField(
                value = state.password,
                onValueChange = vm::onPasswordChange,
                label = "PIN or Password",
                hint = "1234 or railbite123",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    focus.clearFocus()
                    vm.submit(onLoggedIn)
                }),
            )

            if (state.error != null) {
                Spacer(Modifier.height(Space.s))
                ErrorBar(state.error ?: "")
            }

            Spacer(Modifier.height(Space.m))
            RailBiteButton(
                text = "Sign in",
                onClick = {
                    focus.clearFocus()
                    vm.submit(onLoggedIn)
                },
                enabled = !state.busy && state.staffId.isNotBlank() && state.password.length >= 4,
                busy = state.busy,
            )

            Spacer(Modifier.height(Space.m))
            Text(
                "Demo logins: c1, c2, c3 or c4 · PIN 1234 (or railbite123)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                            hint = "http://192.168.1.45:8080",
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                autoCorrectEnabled = false,
                            ),
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
