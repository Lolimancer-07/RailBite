package com.railbite.picker.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.railbite.core.model.ChatPreset
import com.railbite.core.model.ChatPresets
import com.railbite.core.model.OrderMessage
import com.railbite.core.util.Statuses
import com.railbite.picker.ui.components.PickerErrorBanner
import com.railbite.picker.ui.components.PickerSeatPill
import com.railbite.picker.ui.components.shortOrderTag
import com.railbite.picker.ui.theme.PickerBottomSheetShape
import com.railbite.picker.ui.theme.PickerCardBorder
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.RailBitePickerPalette

/**
 * Stitch Screen 5b (`41ab9eb5`): Order-Scoped Quick-Chat Modal Bottom Sheet.
 * - Chronological message stream (`#FFFFFF` bordered passenger bubbles, `#0891B2` picker bubbles)
 * - All 4 [ChatPresets.PICKER] 1-tap quick chips (`IM_HERE`, `ENTERING_COACH`, `TWO_MINS`, `PACKING`)
 * - 120-character custom text composer
 * - Locked read-only state on DELIVERED / CANCELLED / EXPIRED orders
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderQuickChatSheet(
    orderId: String,
    coach: String,
    berth: Int,
    orderStatus: String,
    messages: List<OrderMessage>,
    canSend: Boolean,
    sending: Boolean,
    error: String?,
    onSendPreset: (ChatPreset) -> Unit,
    onSendCustom: (String) -> Unit,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

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
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Context Header with Seat Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Button),
                    color = RailBitePickerPalette.CyanTint,
                    border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = RailBitePickerPalette.CyanPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Passenger Quick-Chat",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = RailBitePickerPalette.TextPrimary,
                        )
                        PickerSeatPill(coach = coach, berth = berth)
                    }
                    Text(
                        text = if (canSend) {
                            "${shortOrderTag(orderId)} • Real-time seat updates"
                        } else {
                            "${shortOrderTag(orderId)} • Closed (read-only)"
                        },
                        style = PickerNumericType.MetaMono,
                        color = RailBitePickerPalette.TextSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close chat",
                        tint = RailBitePickerPalette.TextSecondary,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)

            // Message History Surface
            Surface(
                shape = RoundedCornerShape(PickerRadii.Card),
                color = RailBitePickerPalette.Canvas,
                border = PickerCardBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 270.dp),
            ) {
                if (messages.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    ) {
                        Text(
                            text = if (canSend) {
                                "No messages yet. Tap a 1-tap preset below to notify the passenger."
                            } else {
                                "No messages exchanged for this order."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePickerPalette.TextSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            QuickChatBubble(msg)
                        }
                    }
                }
            }

            error?.let { msg ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    PickerErrorBanner(message = msg)
                    // Allow tapping banner to clear
                    Surface(
                        onClick = onDismissError,
                        color = Color.Transparent,
                        modifier = Modifier.matchParentSize(),
                    ) {}
                }
            }

            if (canSend) {
                // 4 Picker Quick-Preset Chips (2x2 clean grid)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatPresets.PICKER.chunked(2).forEach { pair ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            pair.forEach { preset ->
                                Surface(
                                    onClick = { if (!sending) onSendPreset(preset) },
                                    shape = RoundedCornerShape(PickerRadii.Pill),
                                    color = RailBitePickerPalette.CyanTint,
                                    border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = preset.body,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = RailBitePickerPalette.CyanPressed,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 120-Character Custom Note Input + Send CTA
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { input ->
                            if (input.length <= ChatPresets.MAX_BODY_LENGTH) {
                                draft = input
                            }
                        },
                        placeholder = {
                            Text(
                                text = "Short note (${draft.length}/${ChatPresets.MAX_BODY_LENGTH})…",
                                style = MaterialTheme.typography.bodySmall,
                                color = RailBitePickerPalette.TextMuted,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(PickerRadii.Button),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RailBitePickerPalette.CyanPrimary,
                            unfocusedBorderColor = RailBitePickerPalette.BorderSubtle,
                            focusedContainerColor = RailBitePickerPalette.Canvas,
                            unfocusedContainerColor = RailBitePickerPalette.Canvas,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val clean = draft.trim()
                            if (clean.isNotEmpty() && !sending) {
                                onSendCustom(clean)
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank() && !sending,
                        shape = RoundedCornerShape(PickerRadii.Button),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RailBitePickerPalette.CyanPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = RailBitePickerPalette.ImageTile,
                            disabledContentColor = RailBitePickerPalette.TextMuted,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        modifier = Modifier.heightIn(min = 52.dp),
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            } else {
                // Read-Only Lock Banner on DELIVERED / CANCELLED / EXPIRED
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Button),
                    color = RailBitePickerPalette.Canvas,
                    border = PickerCardBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = RailBitePickerPalette.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (orderStatus == Statuses.DELIVERED) {
                                "Order delivered — chat closed (read-only)"
                            } else {
                                "Chat closed — read-only history"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = RailBitePickerPalette.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChatBubble(msg: OrderMessage) {
    val mine = msg.senderRole.equals("PICKER", ignoreCase = true)
    Row(
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(PickerRadii.Button),
            color = if (mine) RailBitePickerPalette.CyanPrimary else RailBitePickerPalette.SurfaceCard,
            border = if (mine) null else PickerCardBorder,
            modifier = Modifier.widthIn(max = 275.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = msg.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mine) Color.White else RailBitePickerPalette.TextPrimary,
                )
                Text(
                    text = (if (mine) "You" else "Passenger") + " • " + formatChatTimestamp(msg.createdAt),
                    style = PickerNumericType.MetaMono,
                    color = if (mine) Color.White.copy(alpha = 0.85f) else RailBitePickerPalette.TextSecondary,
                )
            }
        }
    }
}

private fun formatChatTimestamp(epochMs: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault("")

