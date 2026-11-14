@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.railbite.customer.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "What went wrong with order #${orderId.take(8)}?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            FlowChips(
                options = TIER1_TYPES,
                selected = state.type,
                onSelect = vm::setType,
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::setText,
                label = { Text("Anything else? (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.done) {
                Text(
                    "✅ Report sent — the pantry crew will follow up at your seat.",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            } else {
                Button(
                    onClick = vm::send,
                    enabled = !state.sending,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.sending) "Sending…" else "Send report", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
            Text(
                "Regulatory complaints (overcharging, hygiene) are handled separately by IRCTC and are out of scope for this prototype.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlowChips(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }
    }
}
