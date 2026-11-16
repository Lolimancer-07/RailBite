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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteTextField
import com.railbite.core.ui.components.iconDescription
import com.railbite.core.ui.theme.Space

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
            .padding(Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Spacer(Modifier.height(Space.xl))
        Text("RailBite", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Food delivered to your train seat in ~10 minutes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xs))

        SeatCard(vm = vm, state = state, onDone = { vm.complete(onDone) })

        ConnectionStatus(state = state, onRetry = vm::testConnection)

        state.error?.let { ErrorBar(it) }
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun SeatCard(
    vm: OnboardingViewModel,
    state: OnboardingUiState,
    onDone: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text("Your seat", style = MaterialTheme.typography.titleMedium)
            RailBiteTextField(
                value = state.pnr,
                onValueChange = vm::setPnr,
                label = "PNR (10 digits)",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                RailBiteTextField(
                    value = state.coach,
                    onValueChange = vm::setCoach,
                    label = "Coach",
                    hint = "B2",
                    modifier = Modifier.weight(1f),
                )
                RailBiteTextField(
                    value = state.berth,
                    onValueChange = vm::setBerth,
                    label = "Seat",
                    hint = "14",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text("Diners", style = MaterialTheme.typography.bodyLarge)
                TextButton(
                    onClick = { vm.setDiners(state.diners - 1) },
                    modifier = Modifier.iconDescription("Fewer diners"),
                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                Text("${state.diners}", style = MaterialTheme.typography.titleMedium)
                TextButton(
                    onClick = { vm.setDiners(state.diners + 1) },
                    modifier = Modifier.iconDescription("More diners"),
                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            }
            RailBiteButton(
                text = "Start ordering",
                onClick = onDone,
                enabled = state.seatValid,
            )
        }
    }
}

@Composable
private fun ConnectionStatus(state: OnboardingUiState, onRetry: () -> Unit) {
    // Non-blocking: the server is baked into the app; this is purely informational.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        when (state.connection) {
            OnboardingUiState.Connection.Testing -> {
                CircularProgressIndicator(
                    Modifier
                        .height(18.dp)
                        .width(18.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    "Checking the RailBite cloud…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OnboardingUiState.Connection.Ok -> Text(
                "RailBite cloud connected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
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
