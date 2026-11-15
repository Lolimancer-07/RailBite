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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.customer.CustomerGraph

@Composable
fun OnboardingScreen(
    graph: com.railbite.customer.CustomerGraph,
    onDone: () -> Unit,
) {
    val vm = viewModel(key = "onboarding") { OnboardingViewModel(graph.container) }
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

        SeatCard(vm = vm, state = state, onDone = { vm.complete(onDone) })

        ConnectionStatus(state = state, onRetry = vm::testConnection)

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SeatCard(
    vm: OnboardingViewModel,
    state: OnboardingUiState,
    onDone: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your seat", style = MaterialTheme.typography.titleLarge)
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
                TextButton(onClick = { vm.setDiners(state.diners - 1) }) { Text("−") }
                Text("${state.diners}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { vm.setDiners(state.diners + 1) }) { Text("+") }
            }
            Button(
                onClick = onDone,
                enabled = state.seatValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start ordering") }
        }
    }
}

@Composable
private fun ConnectionStatus(state: OnboardingUiState, onRetry: () -> Unit) {
    // Non-blocking: the server is baked into the app; this is purely informational.
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state.connection) {
            OnboardingUiState.Connection.Testing -> {
                CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Checking the RailBite cloud…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OnboardingUiState.Connection.Ok -> Text(
                "RailBite cloud connected ✓",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            is OnboardingUiState.Connection.Fail -> {
                Text(
                    "Cannot reach the RailBite cloud — check your internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
