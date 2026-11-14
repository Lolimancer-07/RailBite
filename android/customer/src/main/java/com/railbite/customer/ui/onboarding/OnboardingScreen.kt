package com.railbite.customer.ui.onboarding

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.customer.AppContainer
import com.railbite.customer.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    graph: com.railbite.customer.CustomerGraph,
    onDone: () -> Unit,
) {
    val container = graph.container
    val vm = viewModel(key = "onboarding") {
        OnboardingViewModel(container, BuildConfig.DEFAULT_SERVER_URL)
    }
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("🚆", style = MaterialTheme.typography.headlineLarge)
        Text("RailBite", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text(
            "Food delivered to your train seat in ~10 minutes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (state.step == 0) {
            ServerStep(vm = vm, state = state)
        } else {
            SeatStep(vm = vm, state = state, onBack = { vm.backToServer() }, onDone = { vm.complete(onDone) })
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ServerStep(vm: OnboardingViewModel, state: OnboardingUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("1 · Connect to the pantry server", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = vm::setServerUrl,
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.45:8080") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Ask for the laptop's IP on the train Wi-Fi, e.g. http://192.168.1.45:8080",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = vm::testConnection) { Text("Test connection") }
                Spacer(Modifier.width(12.dp))
                when (val t = state.testState) {
                    is OnboardingUiState.TestState.Testing -> CircularProgressIndicator(Modifier.height(20.dp))
                    is OnboardingUiState.TestState.Ok -> Text(
                        "Connected ✓",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    is OnboardingUiState.TestState.Fail -> Text(
                        t.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> {}
                }
            }
            Button(
                onClick = vm::nextStep,
                enabled = state.testState is OnboardingUiState.TestState.Ok,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Continue") }
        }
    }
}

@Composable
private fun SeatStep(
    vm: OnboardingViewModel,
    state: OnboardingUiState,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("2 · Your seat", style = MaterialTheme.typography.titleLarge)
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
                    placeholder = { Text("B2") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.berth,
                    onValueChange = vm::setBerth,
                    label = { Text("Seat") },
                    placeholder = { Text("14") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Diners", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { vm.setDiners(state.diners - 1) }) { Text("−") }
                Text("${state.diners}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { vm.setDiners(state.diners + 1) }) { Text("+") }
            }
            Row {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    Spacer(Modifier.width(4.dp))
                    Text("Back")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDone, enabled = state.seatValid) { Text("Start ordering") }
            }
        }
    }
}
