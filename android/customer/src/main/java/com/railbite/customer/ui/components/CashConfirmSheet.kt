package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.railbite.core.util.Money
import com.railbite.customer.ui.theme.RailBiteBottomSheetShape
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

/**
 * Stitch Screen 5 Post-Delivery Cash / Payment Confirmation Sheet (`ModalBottomSheet`).
 * Preserves the exact `vm.confirmCash()` flow from TrackingScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashConfirmSheet(
    totalPaise: Int,
    isUpi: Boolean,
    captainName: String?,
    confirming: Boolean,
    onConfirmPaid: () -> Unit,
    onReportMismatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RailBiteBottomSheetShape,
        containerColor = RailBitePalette.SurfaceCard,
        scrimColor = RailBitePalette.DarkSlate.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(RailBiteRadii.Pill))
                    .background(RailBitePalette.BorderSubtle),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    color = RailBitePalette.CyanTint,
                    border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Payments,
                            contentDescription = null,
                            tint = RailBitePalette.CyanPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = if (isUpi) "UPI Payment Verification" else "Cash Handover Verification",
                        style = MaterialTheme.typography.titleLarge,
                        color = RailBitePalette.TextPrimary,
                    )
                    Text(
                        text = "Helps keep the pantry ledger transparent",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                    )
                }
            }

            val runnerLabel = captainName?.substringBefore(" (Picker")?.trim() ?: "the pantry runner"
            Text(
                text = if (isUpi) {
                    "Did you complete ${Money.rupees(totalPaise)} UPI payment for this delivered order?"
                } else {
                    "Did you pay ${Money.rupees(totalPaise)} cash to $runnerLabel upon berth delivery?"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = RailBitePalette.TextPrimary,
            )

            Button(
                onClick = onConfirmPaid,
                enabled = !confirming,
                shape = RoundedCornerShape(RailBiteRadii.Button),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RailBitePalette.CyanPrimary,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RailBiteMetrics.PrimaryButtonHeight),
            ) {
                if (confirming) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (isUpi) {
                            "  Yes, Paid ${Money.rupees(totalPaise)} via UPI"
                        } else {
                            "  Yes, Paid ${Money.rupees(totalPaise)} Cash"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    border = RailBiteCardBorder,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.TextPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RailBiteMetrics.TouchTargetMin),
                ) {
                    Text("Not Yet", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onReportMismatch()
                    },
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    border = BorderStroke(1.dp, RailBitePalette.SemanticRedBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.SemanticRed),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RailBiteMetrics.TouchTargetMin),
                ) {
                    Text("Report Issue", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Inline Cash Confirmation card pinned on TrackingScreen so passengers can always confirm even after closing the modal. */
@Composable
fun CashConfirmInlineCard(
    totalPaise: Int,
    isUpi: Boolean,
    confirmed: Boolean,
    confirming: Boolean,
    captainName: String?,
    onConfirmPaid: () -> Unit,
    onReportIssue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = if (confirmed) BorderStroke(1.dp, RailBitePalette.SemanticGreenBorder) else RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (confirmed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = RailBitePalette.SemanticGreen,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            text = if (isUpi) "UPI Payment Confirmed" else "Cash Handover Verified",
                            style = MaterialTheme.typography.titleSmall,
                            color = RailBitePalette.SemanticGreen,
                        )
                        Text(
                            text = "Recorded ${Money.rupees(totalPaise)} in the RailBite ledger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePalette.TextSecondary,
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Payments,
                        contentDescription = null,
                        tint = RailBitePalette.CyanPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (isUpi) "Confirm UPI Payment" else "Cash Handover Verification",
                        style = MaterialTheme.typography.titleMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                }

                val runnerLabel = captainName?.substringBefore(" (Picker")?.trim() ?: "the runner"
                Text(
                    text = if (isUpi) {
                        "Did you complete your ${Money.rupees(totalPaise)} UPI payment?"
                    } else {
                        "Did you pay ${Money.rupees(totalPaise)} cash to $runnerLabel upon berth arrival?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = RailBitePalette.TextSecondary,
                )

                Button(
                    onClick = onConfirmPaid,
                    enabled = !confirming,
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RailBitePalette.CyanPrimary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RailBiteMetrics.PrimaryButtonHeight),
                ) {
                    if (confirming) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = if (isUpi) {
                                "  Yes, Paid ${Money.rupees(totalPaise)} UPI"
                            } else {
                                "  Yes, Paid ${Money.rupees(totalPaise)} Cash"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onReportIssue,
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    border = RailBiteCardBorder,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.TextPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RailBiteMetrics.TouchTargetMin),
                ) {
                    Text("Report Amount Mismatch / Issue", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

