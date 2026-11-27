package com.railbite.picker.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.railbite.core.model.Order
import com.railbite.core.util.Money
import com.railbite.picker.ui.components.PickerErrorBanner
import com.railbite.picker.ui.components.PickerPrimaryButton
import com.railbite.picker.ui.components.PickerSeatPill
import com.railbite.picker.ui.components.PinCellsRow
import com.railbite.picker.ui.components.shortOrderTag
import com.railbite.picker.ui.theme.PickerBottomSheetShape
import com.railbite.picker.ui.theme.PickerCardBorder
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.RailBitePickerPalette
import java.util.Locale

/**
 * Stitch Screen 5c (`d28dadfc` modal): Seat Handover PIN + Cash Collection Confirmation Sheet.
 * Verifies the passenger's 4-digit delivery PIN (server returns 403 on mismatch) and
 * records exact cash collected (COD or ₹0.00 for pre-paid UPI) before marking DELIVERED.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoverConfirmSheet(
    order: Order,
    code: String,
    onCodeChange: (String) -> Unit,
    cashText: String,
    onCashChange: (String) -> Unit,
    busy: Boolean,
    errorMessage: String?,
    onConfirmDeliver: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = LocalFocusManager.current
    var pinFocused by remember { mutableStateOf(false) }

    val isUpi = order.paymentMode == "UPI_QUEUED"
    val exactCodRupees = String.format(Locale.US, "%.2f", order.totalPaise / 100.0)
    val exactCashMatched = !isUpi && (
        cashText.trim() == exactCodRupees ||
            (order.totalPaise % 100 == 0 && cashText.trim() == (order.totalPaise / 100).toString())
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PickerBottomSheetShape,
        containerColor = RailBitePickerPalette.SurfaceCard,
        scrimColor = RailBitePickerPalette.DarkSlate.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(PickerRadii.Pill))
                    .background(RailBitePickerPalette.BorderSubtle),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Button),
                    color = RailBitePickerPalette.SemanticGreenTint,
                    border = BorderStroke(1.dp, RailBitePickerPalette.SemanticGreenBorder),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = RailBitePickerPalette.SemanticGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Confirm Seat Handover",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = RailBitePickerPalette.TextPrimary,
                        )
                        PickerSeatPill(coach = order.coach, berth = order.berth)
                    }
                    Text(
                        text = shortOrderTag(order.id),
                        style = PickerNumericType.MetaMono,
                        color = RailBitePickerPalette.TextSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = RailBitePickerPalette.TextSecondary,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)

            // Payment Callout Strip
            Surface(
                shape = RoundedCornerShape(PickerRadii.Button),
                color = if (isUpi) RailBitePickerPalette.CyanTint else RailBitePickerPalette.DarkSlate,
                border = if (isUpi) BorderStroke(1.dp, RailBitePickerPalette.CyanBorder) else null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = if (isUpi) "UPI DIGITAL • PAID IN APP" else "CASH ON DELIVERY DUE",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isUpi) RailBitePickerPalette.CyanPressed else RailBitePickerPalette.BorderSubtle,
                    )
                    Text(
                        text = if (isUpi) "₹0 Cash" else Money.rupees(order.totalPaise),
                        style = PickerNumericType.PriceLarge,
                        color = if (isUpi) RailBitePickerPalette.CyanPressed else RailBitePickerPalette.CyanPinDigit,
                    )
                }
            }

            // 4-Digit Delivery PIN Verification
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "4-Digit Passenger Delivery PIN",
                    style = MaterialTheme.typography.labelLarge,
                    color = RailBitePickerPalette.TextPrimary,
                )
                Text(
                    text = "Ask the passenger for the 4-digit PIN displayed on their Order Tracking screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePickerPalette.TextSecondary,
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    PinCellsRow(
                        digits = code,
                        masked = false,
                        focused = pinFocused,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BasicTextField(
                        value = code,
                        onValueChange = { raw ->
                            val clean = raw.filter(Char::isDigit).take(4)
                            onCodeChange(clean)
                            if (clean.length == 4) focus.clearFocus()
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.Transparent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.01f)
                            .onFocusChanged { pinFocused = it.isFocused },
                    )
                }
            }

            // Cash Collected Input + Exact Match Checkbox
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isUpi) "Cash Collected (₹0 for UPI)" else "Cash Collected (₹)",
                    style = MaterialTheme.typography.labelLarge,
                    color = RailBitePickerPalette.TextPrimary,
                )
                OutlinedTextField(
                    value = cashText,
                    onValueChange = onCashChange,
                    prefix = {
                        Text(
                            text = "₹ ",
                            style = PickerNumericType.PriceLarge,
                            color = RailBitePickerPalette.TextPrimary,
                        )
                    },
                    singleLine = true,
                    textStyle = PickerNumericType.PriceLarge,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focus.clearFocus()
                            if (code.length == 4) onConfirmDeliver()
                        },
                    ),
                    shape = RoundedCornerShape(PickerRadii.Button),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RailBitePickerPalette.CyanPrimary,
                        unfocusedBorderColor = RailBitePickerPalette.BorderSubtle,
                        focusedContainerColor = RailBitePickerPalette.Canvas,
                        unfocusedContainerColor = RailBitePickerPalette.Canvas,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!isUpi) {
                    Surface(
                        shape = RoundedCornerShape(PickerRadii.Button),
                        color = RailBitePickerPalette.Canvas,
                        border = PickerCardBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCashChange(if (exactCashMatched) "" else exactCodRupees) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = exactCashMatched,
                                onCheckedChange = { checked ->
                                    onCashChange(if (checked) exactCodRupees else "")
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = RailBitePickerPalette.SemanticGreen,
                                ),
                            )
                            Text(
                                text = "Exact cash collected (${Money.rupees(order.totalPaise)})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = RailBitePickerPalette.TextPrimary,
                            )
                        }
                    }
                }
            }

            errorMessage?.let { msg ->
                PickerErrorBanner(message = msg)
            }

            Spacer(Modifier.height(2.dp))

            PickerPrimaryButton(
                text = "Confirm Delivery & Record Cash →",
                onClick = {
                    focus.clearFocus()
                    onConfirmDeliver()
                },
                enabled = code.length == 4,
                busy = busy,
                containerColor = RailBitePickerPalette.SemanticGreen,
            )
        }
    }
}

