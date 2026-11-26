package com.railbite.customer.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.components.ErrorBar
import com.railbite.customer.ui.onboarding.flatTextFieldColors
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii
import com.railbite.customer.ui.theme.RailBiteSelectedBorder

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
        containerColor = RailBitePalette.Canvas,
        topBar = {
            Column(Modifier.background(RailBitePalette.SurfaceCard)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RailBitePalette.SurfaceCard,
                        titleContentColor = RailBitePalette.TextPrimary,
                    ),
                    title = { Text("Report a Problem", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RailBitePalette.TextPrimary,
                            )
                        }
                    },
                )
                HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                shape = RoundedCornerShape(RailBiteRadii.Card),
                colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
                border = RailBiteCardBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "What went wrong with order #${orderId.take(8).uppercase()}?",
                        style = MaterialTheme.typography.titleMedium,
                        color = RailBitePalette.TextPrimary,
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
                        singleLine = false,
                        minLines = 3,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        colors = flatTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.error?.let { ErrorBar(it) }

                    if (state.done) {
                        Surface(
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            color = RailBitePalette.SemanticGreenTint,
                            border = BorderStroke(1.dp, RailBitePalette.SemanticGreenBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = RailBitePalette.SemanticGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Report sent — the pantry crew will follow up at your berth.",
                                    color = RailBitePalette.SemanticGreen,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Button(
                            onClick = onDone,
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RailBitePalette.CyanPrimary,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(RailBiteMetrics.PrimaryButtonHeight),
                        ) {
                            Text("Done", style = MaterialTheme.typography.titleSmall)
                        }
                    } else {
                        Button(
                            onClick = vm::send,
                            enabled = !state.sending,
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RailBitePalette.CyanPrimary,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(RailBiteMetrics.PrimaryButtonHeight),
                        ) {
                            if (state.sending) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Text("Send Report", style = MaterialTheme.typography.titleSmall)
                            }
                        }

                        OutlinedButton(
                            onClick = onDone,
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            border = RailBiteCardBorder,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.TextPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = RailBiteMetrics.TouchTargetMin),
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Text(
                text = "Regulatory complaints (overcharging, hygiene) are handled separately by IRCTC.",
                style = MaterialTheme.typography.bodySmall,
                color = RailBitePalette.TextSecondary,
            )
        }
    }
}

@Composable
private fun FlowChips(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (value, label) ->
                    val active = selected == value
                    Surface(
                        onClick = { onSelect(value) },
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        color = if (active) RailBitePalette.CyanTint else RailBitePalette.Canvas,
                        contentColor = if (active) RailBitePalette.CyanPressed else RailBitePalette.TextPrimary,
                        border = if (active) RailBiteSelectedBorder else RailBiteCardBorder,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
