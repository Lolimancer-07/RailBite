package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.railbite.core.ui.components.ErrorBar
import com.railbite.customer.ui.home.HomeUiState
import com.railbite.customer.ui.home.MenuSortOption
import com.railbite.customer.ui.onboarding.flatTextFieldColors
import com.railbite.customer.ui.theme.RailBiteBottomSheetShape
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii
import com.railbite.customer.ui.theme.RailBiteSelectedBorder

private val COACH_QUICK_PICKS = listOf("A1", "B1", "B2", "B3", "S1", "S2")
private val TRAIN_QUICK_PICKS = listOf("12345" to "Saraighat", "12423" to "Rajdhani")

private val PRICE_CAPS: List<Pair<Int?, String>> = listOf(
    null to "Any Price",
    3000 to "Under ₹30",
    6000 to "Under ₹60",
    10000 to "Under ₹100",
)

/**
 * Stitch `2291ee04` Edit Berth, PNR & Train bottom sheet (`SeatSetupBottomSheet`).
 * Pre-fills persisted Train/PNR/Coach/Berth and persists updates directly to DataStore.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SeatSetupBottomSheet(
    initialTrainNumber: String,
    initialPnr: String,
    initialCoach: String,
    initialBerth: Int?,
    onLookupPnr: suspend (String) -> com.railbite.core.model.ManifestLookupResponse? = { null },
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onSave: (trainNumber: String, pnr: String, coach: String, berthText: String, onSuccess: () -> Unit, onErr: (String) -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var trainNumber by remember(initialTrainNumber) { mutableStateOf(initialTrainNumber) }
    var pnr by remember(initialPnr) { mutableStateOf(initialPnr) }
    var coach by remember(initialCoach) { mutableStateOf(initialCoach) }
    var berthText by remember(initialBerth) { mutableStateOf(initialBerth?.takeIf { it > 0 }?.toString() ?: "") }
    var autoFilledTrainName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(pnr) {
        if (pnr.length == 10) {
            val found = onLookupPnr(pnr)
            if (found != null) {
                trainNumber = found.trainNumber
                autoFilledTrainName = found.trainName
                if (coach.isBlank()) coach = found.coach
                if (berthText.isBlank()) berthText = found.berth.toString()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RailBiteBottomSheetShape,
        containerColor = RailBitePalette.SurfaceCard,
        scrimColor = RailBitePalette.DarkSlate.copy(alpha = 0.45f),
        dragHandle = { SheetDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Edit Berth / Seat Details",
                        style = MaterialTheme.typography.titleLarge,
                        color = RailBitePalette.TextPrimary,
                    )
                    Text(
                        text = "Update delivery destination for your coach",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = RailBitePalette.TextSecondary)
                }
            }

            OutlinedTextField(
                value = pnr,
                onValueChange = { pnr = it.filter(Char::isDigit).take(10); error = null },
                label = { Text("10-Digit PNR Number") },
                placeholder = { Text("8492000001", style = RailBiteNumericType.PriceMedium) },
                textStyle = RailBiteNumericType.PriceMedium,
                singleLine = true,
                shape = RoundedCornerShape(RailBiteRadii.Button),
                trailingIcon = {
                    if (pnr.length == 10) {
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

            OutlinedTextField(
                value = trainNumber,
                onValueChange = {
                    trainNumber = it.filter(Char::isDigit).take(5)
                    autoFilledTrainName = null
                    error = null
                },
                label = {
                    Text(
                        autoFilledTrainName?.let { "5-Digit Train Number ($it)" }
                            ?: "5-Digit Train Number",
                    )
                },
                placeholder = { Text("12345", style = RailBiteNumericType.PriceMedium) },
                textStyle = RailBiteNumericType.PriceMedium,
                singleLine = true,
                shape = RoundedCornerShape(RailBiteRadii.Button),
                trailingIcon = {
                    if (com.railbite.core.util.Validators.isValidTrainNumber(trainNumber)) {
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
                TRAIN_QUICK_PICKS.forEach { (num, label) ->
                    val selected = trainNumber == num
                    Surface(
                        onClick = { trainNumber = num; autoFilledTrainName = null; error = null },
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
                            Text("$num · $label", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Text(
                text = "Coach Identifier",
                style = MaterialTheme.typography.titleSmall,
                color = RailBitePalette.TextPrimary,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                COACH_QUICK_PICKS.forEach { code ->
                    val selected = coach.equals(code, ignoreCase = true)
                    Surface(
                        onClick = { coach = code; error = null },
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
                            Text(code, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = coach,
                    onValueChange = { coach = it.take(3).uppercase(); error = null },
                    label = { Text("Coach") },
                    placeholder = { Text("B2") },
                    textStyle = RailBiteNumericType.PriceMedium,
                    singleLine = true,
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    colors = flatTextFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = berthText,
                    onValueChange = { berthText = it.filter(Char::isDigit).take(3); error = null },
                    label = { Text("Seat / Berth") },
                    placeholder = { Text("14") },
                    textStyle = RailBiteNumericType.PriceMedium,
                    singleLine = true,
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    colors = flatTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            error?.let { ErrorBar(it) }

            Button(
                onClick = {
                    onSave(
                        trainNumber,
                        pnr,
                        coach,
                        berthText,
                        { onDismiss() },
                        { msg -> error = msg },
                    )
                },
                shape = RoundedCornerShape(RailBiteRadii.Button),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RailBitePalette.CyanPrimary,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RailBiteMetrics.PrimaryButtonHeight),
            ) {
                Text("Update Seat & Continue", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }

            TextButton(
                onClick = {
                    onDismiss()
                    onOpenSettings()
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = RailBiteMetrics.TouchTargetMin),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = RailBitePalette.CyanPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "  Train Wi-Fi & Server Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = RailBitePalette.CyanPrimary,
                )
            }
        }
    }
}

/**
 * Stitch `ad2f85d4` Filter Sheet (`FilterBottomSheet`: Veg Only / In-Stock / Price cap).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onApply: (vegOnly: Boolean, inStockOnly: Boolean, maxPricePaise: Int?) -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var vegOnly by remember(state.vegOnly) { mutableStateOf(state.vegOnly) }
    var inStockOnly by remember(state.inStockOnly) { mutableStateOf(state.inStockOnly) }
    var maxPricePaise by remember(state.maxPricePaise) { mutableStateOf(state.maxPricePaise) }

    val previewCount = state.previewFilteredCount(vegOnly, inStockOnly, maxPricePaise)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RailBiteBottomSheetShape,
        containerColor = RailBitePalette.SurfaceCard,
        scrimColor = RailBitePalette.DarkSlate.copy(alpha = 0.45f),
        dragHandle = { SheetDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    tint = RailBitePalette.CyanPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "  Filter Options",
                    style = MaterialTheme.typography.titleLarge,
                    color = RailBitePalette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        vegOnly = false
                        inStockOnly = false
                        maxPricePaise = null
                        onReset()
                    },
                    modifier = Modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
                ) {
                    Text("Reset All", style = MaterialTheme.typography.labelLarge, color = RailBitePalette.CyanPrimary)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Dietary & Availability",
                    style = MaterialTheme.typography.titleSmall,
                    color = RailBitePalette.TextPrimary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterTogglePill(
                        label = "Pure Veg Only",
                        selected = vegOnly,
                        onClick = { vegOnly = !vegOnly },
                    )
                    FilterTogglePill(
                        label = "In Stock in Pantry",
                        selected = inStockOnly,
                        onClick = { inStockOnly = !inStockOnly },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Price Cap",
                    style = MaterialTheme.typography.titleSmall,
                    color = RailBitePalette.TextPrimary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PRICE_CAPS.forEach { (cap, label) ->
                        FilterTogglePill(
                            label = label,
                            selected = maxPricePaise == cap,
                            onClick = { maxPricePaise = cap },
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        onReset()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    border = RailBiteCardBorder,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.TextPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(RailBiteMetrics.PrimaryButtonHeight),
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = {
                        onApply(vegOnly, inStockOnly, maxPricePaise)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RailBitePalette.CyanPrimary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .weight(2f)
                        .height(RailBiteMetrics.PrimaryButtonHeight),
                ) {
                    Text(
                        text = "Apply Filters ($previewCount item${if (previewCount == 1) "" else "s"})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterTogglePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(RailBiteRadii.Pill),
        color = if (selected) RailBitePalette.CyanTint else RailBitePalette.Canvas,
        contentColor = if (selected) RailBitePalette.CyanPressed else RailBitePalette.TextPrimary,
        border = if (selected) RailBiteSelectedBorder else RailBiteCardBorder,
        modifier = Modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = RailBitePalette.CyanPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Stitch `ad2f85d4` Sort Sheet (`SortBottomSheet`: Price Low–High, Price High–Low, Name).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    selectedSort: MenuSortOption,
    onDismiss: () -> Unit,
    onSelectSort: (MenuSortOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RailBiteBottomSheetShape,
        containerColor = RailBitePalette.SurfaceCard,
        scrimColor = RailBitePalette.DarkSlate.copy(alpha = 0.45f),
        dragHandle = { SheetDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Sort By",
                style = MaterialTheme.typography.titleLarge,
                color = RailBitePalette.TextPrimary,
            )

            MenuSortOption.entries.forEach { option ->
                val selected = option == selectedSort
                Surface(
                    onClick = {
                        onSelectSort(option)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(RailBiteRadii.Card),
                    color = if (selected) RailBitePalette.CyanTint else RailBitePalette.Canvas,
                    border = if (selected) RailBiteSelectedBorder else RailBiteCardBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RailBiteMetrics.TouchTargetMin),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = RailBitePalette.TextPrimary,
                            )
                            Text(
                                text = option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = RailBitePalette.TextSecondary,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (selected) RailBitePalette.CyanPrimary else Color.Transparent,
                            border = BorderStroke(
                                1.5.dp,
                                if (selected) RailBitePalette.CyanPrimary else RailBitePalette.BorderSubtle,
                            ),
                            modifier = Modifier.size(22.dp),
                        ) {
                            if (selected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * Account, Orders & Server IP Quick Menu opened by `AccountIconButton` on Storefront.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMenuSheet(
    userLabel: String,
    online: Boolean,
    serverUrl: String,
    onDismiss: () -> Unit,
    onOpenOrders: () -> Unit,
    onEditSeat: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RailBiteBottomSheetShape,
        containerColor = RailBitePalette.SurfaceCard,
        scrimColor = RailBitePalette.DarkSlate.copy(alpha = 0.45f),
        dragHandle = { SheetDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusDot(online = online)
                Column {
                    Text(
                        text = userLabel.ifBlank { "RailBite Passenger" },
                        style = MaterialTheme.typography.titleMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                    Text(
                        text = if (serverUrl.isNotBlank()) serverUrl else "Built-in RailBite Cloud",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)

            AccountMenuRow(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "My Orders & Live Tracking",
                subtitle = "View active status, handover PINs & bills",
                onClick = {
                    onDismiss()
                    onOpenOrders()
                },
            )
            AccountMenuRow(
                icon = Icons.Filled.EditLocationAlt,
                title = "Edit PNR, Coach & Seat",
                subtitle = "Change delivery coach or berth number",
                onClick = {
                    onDismiss()
                    onEditSeat()
                },
            )
            AccountMenuRow(
                icon = Icons.Filled.Settings,
                title = "Server IP & Offline Relay Settings",
                subtitle = "Switch LAN/Cloud API URL & Bluetooth mesh",
                onClick = {
                    onDismiss()
                    onOpenSettings()
                },
            )
            AccountMenuRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "Sign Out / Switch Phone",
                subtitle = "Return to OTP login screen",
                destructive = true,
                onClick = {
                    onDismiss()
                    onSignOut()
                },
            )

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AccountMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(RailBiteRadii.Card),
        color = RailBitePalette.Canvas,
        border = if (destructive) BorderStroke(1.dp, RailBitePalette.SemanticRedBorder) else RailBiteCardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RailBiteMetrics.TouchTargetMin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) RailBitePalette.SemanticRed else RailBitePalette.CyanPrimary,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (destructive) RailBitePalette.SemanticRed else RailBitePalette.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .size(width = 40.dp, height = 4.dp)
            .clip(RoundedCornerShape(RailBiteRadii.Pill))
            .background(RailBitePalette.BorderSubtle),
    )
}

