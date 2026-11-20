package com.railbite.customer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteSecondaryButton
import com.railbite.core.ui.components.RailBiteTextField
import com.railbite.core.ui.components.iconDescription
import com.railbite.core.ui.theme.Metrics
import com.railbite.core.ui.theme.Space

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
                .padding(Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            RailBiteTextField(
                value = state.serverUrl,
                onValueChange = vm::setServerUrl,
                label = "Custom server URL (advanced, optional)",
                hint = "https://railbite-api.onrender.com",
                keyboardOptions = KeyboardOptions(
                    // Uri type + no autocorrect: keyboards otherwise "fix" http:// to https://,
                    // which breaks plain-HTTP local servers (e.g. a laptop on the same Wi-Fi).
                    keyboardType = KeyboardType.Uri,
                    autoCorrect = false,
                ),
            )
            Text(
                "Leave empty to use the built-in RailBite cloud server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RailBiteSecondaryButton(text = "Test connection", onClick = vm::testConnection)
            when (val t = state.testState) {
                is OnboardingLikeTestState.Testing -> Text(
                    "Testing…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is OnboardingLikeTestState.Ok -> Text(
                    "Connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                is OnboardingLikeTestState.Fail -> ErrorBar(t.message)
                else -> {}
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("Offline relay (Bluetooth mesh)", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.tapTarget),
            ) {
                Text(
                    "Bluetooth relay",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.meshRelayEnabled,
                    onCheckedChange = vm::setMeshRelayEnabled,
                )
            }
            Text(
                if (state.meshRelayEnabled) "On" else "Off",
                style = MaterialTheme.typography.labelLarge,
                color = if (state.meshRelayEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "When the cloud can't be reached and this is ON, orders travel phone-to-phone " +
                    "over Bluetooth and ring at the pantry phone like any other order. They " +
                    "sync to the cloud automatically once you have internet again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.meshPending > 0) {
                Text(
                    "${state.meshPending} relay order${if (state.meshPending == 1) "" else "s"} waiting for internet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            RailBiteSecondaryButton(
                text = if (state.meshSyncing) "Syncing…" else "Sync now",
                onClick = vm::syncMeshOrders,
                enabled = !state.meshSyncing,
            )
            state.meshSyncMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("Your seat", style = MaterialTheme.typography.titleMedium)
            RailBiteTextField(
                value = state.pnr,
                onValueChange = vm::setPnr,
                label = "PNR (10 digits)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                RailBiteTextField(
                    value = state.coach,
                    onValueChange = vm::setCoach,
                    label = "Coach",
                    modifier = Modifier.weight(1f),
                )
                RailBiteTextField(
                    value = state.berth,
                    onValueChange = vm::setBerth,
                    label = "Seat",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = Metrics.tapTarget),
            ) {
                Text("Diners", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(Space.s))
                IconButton(
                    onClick = { vm.setDiners(state.diners - 1) },
                    modifier = Modifier.iconDescription("Fewer diners"),
                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                Text("${state.diners}", style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { vm.setDiners(state.diners + 1) },
                    modifier = Modifier.iconDescription("More diners"),
                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            }

            state.error?.let { ErrorBar(it) }
            RailBiteButton(
                text = if (state.saved) "Saved" else "Save",
                onClick = { vm.save(onBack) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("Account", style = MaterialTheme.typography.titleMedium)
            val session by graph.authStore.session.collectAsState()
            session?.let {
                Text(
                    "Signed in as ${it.user.phone ?: it.user.name ?: it.user.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RailBiteSecondaryButton(text = "Sign out", onClick = { vm.logout(onBack) })
            Text(
                "The RailBite cloud server is built into the app. Override it above only for testing (e.g. a laptop on the same Wi-Fi).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
