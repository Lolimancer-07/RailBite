package com.railbite.customer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    graph: com.railbite.customer.CustomerGraph,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = "settings") { SettingsViewModel(graph.container) }
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = vm::setServerUrl,
                label = { Text("Custom server URL (advanced, optional)") },
                placeholder = { Text("https://railbite-api.onrender.com") },
                singleLine = true,
                // Uri type + no autocorrect: keyboards otherwise "fix" http:// to https://,
                // which breaks plain-HTTP local servers (e.g. a laptop on the same Wi-Fi).
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrect = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Leave empty to use the built-in RailBite cloud server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = vm::testConnection) { Text("Test connection") }
                Spacer(Modifier.width(12.dp))
                when (val t = state.testState) {
                    is OnboardingLikeTestState.Testing -> Text("Testing…", style = MaterialTheme.typography.bodyMedium)
                    is OnboardingLikeTestState.Ok -> Text(
                        "Connected ✓",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    is OnboardingLikeTestState.Fail -> Text(t.message, color = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }

            HorizontalDivider()
            Text("Your seat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.pnr,
                onValueChange = vm::setPnr,
                label = { Text("PNR (10 digits)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.coach,
                    onValueChange = vm::setCoach,
                    label = { Text("Coach") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.berth,
                    onValueChange = vm::setBerth,
                    label = { Text("Seat") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Diners", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { vm.setDiners(state.diners - 1) }) { Text("−") }
                Text("${state.diners}", fontWeight = FontWeight.Bold)
                IconButton(onClick = { vm.setDiners(state.diners + 1) }) { Text("+") }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { vm.save(onBack) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.saved) "Saved ✓" else "Save", fontWeight = FontWeight.Bold)
            }
            Text(
                "The RailBite cloud server is built into the app. Override it above only for testing (e.g. a laptop on the same Wi-Fi).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
