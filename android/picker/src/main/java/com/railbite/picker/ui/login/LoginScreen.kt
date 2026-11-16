package com.railbite.picker.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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

/** Staff ID + PIN login (demo accounts c1-c4, PIN 1234). Token is stored in DataStore. */
@Composable
fun LoginScreen(
    graph: com.railbite.picker.PickerGraph,
    onLoggedIn: () -> Unit,
) {
    val vm = viewModel(key = "login") { LoginViewModel(graph.container) }
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
                .padding(Space.xl),
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(Space.s))
            RailBiteTextField(
                value = state.pin,
                onValueChange = vm::onPinChange,
                label = "4-digit PIN",
                hint = "••••",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    vm.submit(onLoggedIn)
                }),
            )

            state.error?.let {
                Spacer(Modifier.height(Space.s))
                ErrorBar(it)
            }

            Spacer(Modifier.height(Space.m))
            RailBiteButton(
                text = "Sign in",
                onClick = {
                    focus.clearFocus()
                    vm.submit(onLoggedIn)
                },
                enabled = !state.busy && state.staffId.isNotBlank() && state.pin.length == 4,
                busy = state.busy,
            )

            Spacer(Modifier.height(Space.m))
            Text(
                "Demo logins: c1, c2, c3 or c4 · PIN 1234",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.xl))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
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
                )
                Spacer(Modifier.width(Space.xs))
                TextButton(onClick = {
                    editServerUrl = serverUrl
                    showServerDialog = true
                }) {
                    Text("Change", style = MaterialTheme.typography.labelMedium)
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
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    autoCorrect = false,
                                ),
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val normalized = ApiClient.normalizeServerUrl(editServerUrl)
                                scope.launch {
                                    graph.settings.setServerUrl(normalized)
                                }
                                showServerDialog = false
                            },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showServerDialog = false },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}
