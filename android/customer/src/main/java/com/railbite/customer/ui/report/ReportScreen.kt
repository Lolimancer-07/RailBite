@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.railbite.customer.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteFilterChip
import com.railbite.core.ui.components.RailBiteSecondaryButton
import com.railbite.core.ui.components.RailBiteTextField
import com.railbite.core.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportProblemScreen(
    graph: com.railbite.customer.CustomerGraph,
    orderId: String,
    onDone: () -> Unit,
) {
    val vm = viewModel(key = "report-$orderId") { ReportViewModel(graph.container, orderId) }
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report a problem") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
            Text(
                "What went wrong with order #${orderId.take(8)}?",
                style = MaterialTheme.typography.titleMedium,
            )
            FlowChips(
                options = TIER1_TYPES,
                selected = state.type,
                onSelect = vm::setType,
            )
            RailBiteTextField(
                value = state.text,
                onValueChange = vm::setText,
                label = "Anything else? (optional)",
                singleLine = false,
                minLines = 3,
            )
            state.error?.let { ErrorBar(it) }
            if (state.done) {
                Text(
                    "Report sent — the pantry crew will follow up at your seat.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                RailBiteButton(text = "Done", onClick = onDone)
            } else {
                RailBiteButton(
                    text = "Send report",
                    onClick = vm::send,
                    busy = state.sending,
                )
                RailBiteSecondaryButton(text = "Cancel", onClick = onDone)
            }
            Text(
                "Regulatory complaints (overcharging, hygiene) are handled separately by IRCTC and are out of scope for this prototype.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlowChips(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                rowOptions.forEach { (value, label) ->
                    RailBiteFilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        label = label,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
