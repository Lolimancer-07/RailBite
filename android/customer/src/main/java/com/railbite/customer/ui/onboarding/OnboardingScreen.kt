package com.railbite.customer.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.components.ErrorBar
import com.railbite.customer.ui.components.StatusDot
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

private val QUICK_COACHES = listOf("A1", "B1", "B2", "B3", "S1", "S2")
private val QUICK_TRAINS = listOf(
    "12345" to "Saraighat Exp",
    "12423" to "Rajdhani Exp",
)

@OptIn(ExperimentalLayoutApi::class)
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
            .background(RailBitePalette.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top live connection pill strip (flat, informative)
        ConnectionStatusBanner(state = state, onRetry = vm::testConnection)

        // Main flat Stitch Seat Setup Card
        Card(
            shape = RoundedCornerShape(RailBiteRadii.Card),
            colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
            border = RailBiteCardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Brand row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(RailBiteRadii.Control),
                        color = RailBitePalette.CyanPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Train,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "RailBite",
                                style = MaterialTheme.typography.titleLarge,
                                color = RailBitePalette.TextPrimary,
                            )
                            Surface(
                                shape = RoundedCornerShape(RailBiteRadii.Control),
                                color = RailBitePalette.CyanTint,
                                border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                            ) {
                                Text(
                                    text = "EXPRESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RailBitePalette.CyanPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = "Train Pantry Car",
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePalette.TextSecondary,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Enter Travel Details",
                        style = MaterialTheme.typography.headlineMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                    Text(
                        text = "Order hot meals, snacks & fresh chai delivered straight to your train berth at official IRCTC MRP.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RailBitePalette.TextSecondary,
                    )
                }

                // 10-Digit PNR Input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "10-Digit PNR Number",
                            style = MaterialTheme.typography.titleSmall,
                            color = RailBitePalette.TextPrimary,
                        )
                        Text(
                            text = "Auto-detects train online",
                            style = MaterialTheme.typography.labelSmall,
                            color = RailBitePalette.TextSecondary,
                        )
                    }
                    OutlinedTextField(
                        value = state.pnr,
                        onValueChange = vm::setPnr,
                        placeholder = { Text("8492000001", style = RailBiteNumericType.PriceMedium) },
                        textStyle = RailBiteNumericType.PriceMedium,
                        singleLine = true,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        trailingIcon = {
                            if (state.pnr.length == 10) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Valid PNR",
                                    tint = RailBitePalette.SemanticGreen,
                                )
                            }
                        },
                        colors = flatTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Mandatory 5-Digit Train Number Input + Quick Pick Demo Chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "5-Digit Train Number",
                            style = MaterialTheme.typography.titleSmall,
                            color = RailBitePalette.TextPrimary,
                        )
                        Text(
                            text = state.autoFilledTrainName ?: "Required for train isolation",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.autoFilledTrainName != null) {
                                RailBitePalette.SemanticGreen
                            } else {
                                RailBitePalette.TextSecondary
                            },
                        )
                    }
                    OutlinedTextField(
                        value = state.trainNumber,
                        onValueChange = vm::setTrainNumber,
                        placeholder = { Text("12345", style = RailBiteNumericType.PriceMedium) },
                        textStyle = RailBiteNumericType.PriceMedium,
                        singleLine = true,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        trailingIcon = {
                            if (com.railbite.core.util.Validators.isValidTrainNumber(state.trainNumber)) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Valid train number",
                                    tint = RailBitePalette.SemanticGreen,
                                )
                            }
                        },
                        colors = flatTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QUICK_TRAINS.forEach { (num, name) ->
                            val selected = state.trainNumber == num
                            Surface(
                                onClick = { vm.setTrainNumber(num) },
                                shape = RoundedCornerShape(RailBiteRadii.Pill),
                                color = if (selected) RailBitePalette.CyanPrimary else RailBitePalette.Canvas,
                                contentColor = if (selected) Color.White else RailBitePalette.TextPrimary,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) RailBitePalette.CyanPrimary else RailBitePalette.BorderSubtle,
                                ),
                                modifier = Modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = "$num · $name",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                }

                // Coach Number + Quick Pick Chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Coach Number",
                            style = MaterialTheme.typography.titleSmall,
                            color = RailBitePalette.TextPrimary,
                        )
                        Text(
                            text = "AC / Sleeper",
                            style = MaterialTheme.typography.labelSmall,
                            color = RailBitePalette.TextSecondary,
                        )
                    }
                    OutlinedTextField(
                        value = state.coach,
                        onValueChange = vm::setCoach,
                        placeholder = { Text("B2", style = RailBiteNumericType.PriceMedium) },
                        textStyle = RailBiteNumericType.PriceMedium,
                        singleLine = true,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        colors = flatTextFieldColors(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QUICK_COACHES.forEach { code ->
                            val selected = state.coach.equals(code, ignoreCase = true)
                            Surface(
                                onClick = { vm.setCoach(code) },
                                shape = RoundedCornerShape(RailBiteRadii.Pill),
                                color = if (selected) RailBitePalette.CyanPrimary else RailBitePalette.Canvas,
                                contentColor = if (selected) Color.White else RailBitePalette.TextPrimary,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) RailBitePalette.CyanPrimary else RailBitePalette.BorderSubtle,
                                ),
                                modifier = Modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = code,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }

                // Berth + Diners
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Berth / Seat",
                            style = MaterialTheme.typography.titleSmall,
                            color = RailBitePalette.TextPrimary,
                        )
                        OutlinedTextField(
                            value = state.berth,
                            onValueChange = vm::setBerth,
                            placeholder = { Text("14", style = RailBiteNumericType.PriceMedium) },
                            textStyle = RailBiteNumericType.PriceMedium,
                            singleLine = true,
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            colors = flatTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Diners",
                            style = MaterialTheme.typography.titleSmall,
                            color = RailBitePalette.TextPrimary,
                        )
                        Surface(
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            color = RailBitePalette.Canvas,
                            border = RailBiteCardBorder,
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(horizontal = 4.dp),
                            ) {
                                IconButton(
                                    onClick = { vm.setDiners(state.diners - 1) },
                                    modifier = Modifier
                                        .size(RailBiteMetrics.TouchTargetMin)
                                        .semantics { contentDescription = "Fewer diners" },
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = null, tint = RailBitePalette.TextPrimary)
                                }
                                Text(
                                    text = "${state.diners}",
                                    style = RailBiteNumericType.PriceMedium,
                                    color = RailBitePalette.TextPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                )
                                IconButton(
                                    onClick = { vm.setDiners(state.diners + 1) },
                                    modifier = Modifier
                                        .size(RailBiteMetrics.TouchTargetMin)
                                        .semantics { contentDescription = "More diners" },
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = RailBitePalette.TextPrimary)
                                }
                            }
                        }
                    }
                }

                state.error?.let { ErrorBar(it) }

                Button(
                    onClick = { vm.complete(onDone) },
                    enabled = state.seatValid,
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RailBitePalette.CyanPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = RailBitePalette.ImageTile,
                        disabledContentColor = RailBitePalette.TextMuted,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RailBiteMetrics.PrimaryButtonHeight),
                ) {
                    Text(
                        text = "Start Ordering",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Official badge footer inside card
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    color = RailBitePalette.Canvas,
                    border = RailBiteCardBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            tint = RailBitePalette.SemanticGreen,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Official IRCTC Pantry Partner • Zero Platform Markup",
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePalette.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusBanner(
    state: OnboardingUiState,
    onRetry: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RailBiteRadii.Pill),
        color = RailBitePalette.SurfaceCard,
        border = RailBiteCardBorder,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            when (state.connection) {
                OnboardingUiState.Connection.Testing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = RailBitePalette.CyanPrimary,
                    )
                    Text(
                        text = "PANTRY NETWORK • CHECKING SERVER…",
                        style = MaterialTheme.typography.labelSmall,
                        color = RailBitePalette.TextSecondary,
                    )
                }

                OnboardingUiState.Connection.Ok -> {
                    StatusDot(online = true)
                    Text(
                        text = "PANTRY CAR NETWORK • ONLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = RailBitePalette.TextPrimary,
                    )
                }

                is OnboardingUiState.Connection.Fail -> {
                    StatusDot(online = false)
                    Text(
                        text = "SERVER UNREACHABLE • OFFLINE RELAY AVAILABLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = RailBitePalette.SemanticRed,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelMedium,
                            color = RailBitePalette.CyanPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun flatTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RailBitePalette.CyanPrimary,
    unfocusedBorderColor = RailBitePalette.BorderSubtle,
    focusedContainerColor = RailBitePalette.SurfaceCard,
    unfocusedContainerColor = RailBitePalette.SurfaceCard,
    focusedTextColor = RailBitePalette.TextPrimary,
    unfocusedTextColor = RailBitePalette.TextPrimary,
    cursorColor = RailBitePalette.CyanPrimary,
)
