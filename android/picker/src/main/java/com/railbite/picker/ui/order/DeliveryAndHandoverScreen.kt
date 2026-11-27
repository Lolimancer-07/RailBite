package com.railbite.picker.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.railbite.core.model.ChatPreset
import com.railbite.core.model.ChatPresets
import com.railbite.core.model.Order
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses
import com.railbite.picker.ui.components.PickerErrorBanner
import com.railbite.picker.ui.components.PickerPrimaryButton
import com.railbite.picker.ui.components.PickerSeatPill
import com.railbite.picker.ui.components.PickerSectionCard
import com.railbite.picker.ui.components.PickerStatusTag
import com.railbite.picker.ui.components.shortOrderTag
import com.railbite.picker.ui.theme.PickerCardBorder
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.PickerSelectedBorder
import com.railbite.picker.ui.theme.RailBitePickerPalette

/**
 * Stitch Screen 5a (`d28dadfc`): Delivery En-Route, 1-Tap Quick-Chat Chips, & Handover Trigger.
 * Handles OUT_FOR_DELIVERY, DELIVERED, and terminal failure states cleanly in the flat white/slate/cyan system.
 */
@Composable
fun DeliveryAndHandoverScreen(
    order: Order,
    state: ProgressUiState,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onSendQuickChip: (ChatPreset) -> Unit,
    onOpenHandoverSheet: () -> Unit,
    onDone: () -> Unit,
) {
    val isOutForDelivery = order.status == Statuses.OUT_FOR_DELIVERY
    val isDelivered = order.status == Statuses.DELIVERED
    val isUpi = order.paymentMode == "UPI_QUEUED"
    val itemCount = order.items.orEmpty().sumOf { it.qty }.coerceAtLeast(order.items.orEmpty().size)
    val itemsSummary = order.items.orEmpty().joinToString(", ") { "${it.qty}× ${it.name ?: it.itemId}" }

    val (statusLabel, statusBg, statusInk, statusBorder) = when (order.status) {
        Statuses.DELIVERED -> listOf(
            "DELIVERED",
            RailBitePickerPalette.SemanticGreenTint,
            RailBitePickerPalette.SemanticGreenInk,
            RailBitePickerPalette.SemanticGreenBorder,
        )
        Statuses.CANCELLED, Statuses.EXPIRED -> listOf(
            Statuses.label(order.status).uppercase(),
            RailBitePickerPalette.SemanticRedTint,
            RailBitePickerPalette.SemanticRed,
            RailBitePickerPalette.SemanticRedBorder,
        )
        else -> listOf(
            "OUT FOR DELIVERY",
            RailBitePickerPalette.CyanTint,
            RailBitePickerPalette.CyanPressed,
            RailBitePickerPalette.CyanBorder,
        )
    }

    val latestMsg = state.chatMessages.lastOrNull()

    Scaffold(
        containerColor = RailBitePickerPalette.Canvas,
        topBar = {
            Surface(color = RailBitePickerPalette.SurfaceCard) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RailBitePickerPalette.TextPrimary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Delivery & Handover",
                                style = MaterialTheme.typography.titleLarge,
                                color = RailBitePickerPalette.TextPrimary,
                            )
                            Text(
                                text = shortOrderTag(order.id),
                                style = PickerNumericType.MetaMono,
                                color = RailBitePickerPalette.TextSecondary,
                            )
                        }
                        PickerStatusTag(
                            text = statusLabel as String,
                            bgColor = statusBg as Color,
                            textColor = statusInk as Color,
                            borderColor = statusBorder as Color,
                        )

                        Box(modifier = Modifier.padding(end = 6.dp)) {
                            Surface(
                                onClick = onOpenChat,
                                shape = CircleShape,
                                color = if (state.unreadChatCount > 0) {
                                    RailBitePickerPalette.CyanTint
                                } else {
                                    RailBitePickerPalette.Canvas
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (state.unreadChatCount > 0) {
                                        RailBitePickerPalette.CyanBorder
                                    } else {
                                        RailBitePickerPalette.BorderSubtle
                                    },
                                ),
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = "Quick Chat",
                                        tint = RailBitePickerPalette.CyanPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (state.unreadChatCount > 0) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(RailBitePickerPalette.SemanticRed),
                                ) {
                                    Text(
                                        text = state.unreadChatCount.coerceAtMost(9).toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)
                }
            }
        },
        bottomBar = {
            if (isOutForDelivery || isDelivered || Statuses.isFailed(order.status)) {
                Surface(
                    color = RailBitePickerPalette.SurfaceCard,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = PickerMetrics.ScreenPadding, vertical = 12.dp),
                        ) {
                            when {
                                isOutForDelivery -> PickerPrimaryButton(
                                    text = "Complete Handover (Enter PIN) →",
                                    onClick = onOpenHandoverSheet,
                                    busy = state.busy,
                                    containerColor = RailBitePickerPalette.SemanticGreen,
                                )
                                isDelivered -> PickerPrimaryButton(
                                    text = "Done — Back to Orders →",
                                    onClick = onDone,
                                    busy = state.busy,
                                    containerColor = RailBitePickerPalette.CyanPrimary,
                                )
                                else -> PickerPrimaryButton(
                                    text = "Back to Dashboard",
                                    onClick = onDone,
                                    busy = state.busy,
                                    containerColor = RailBitePickerPalette.DarkSlate,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = PickerMetrics.ScreenPadding,
                end = PickerMetrics.ScreenPadding,
                top = 14.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Target Destination Hero Card
            item {
                PickerSectionCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(PickerRadii.Button))
                                    .background(
                                        if (isDelivered) {
                                            RailBitePickerPalette.SemanticGreenTint
                                        } else {
                                            RailBitePickerPalette.CyanTint
                                        },
                                    ),
                            ) {
                                Icon(
                                    imageVector = if (isDelivered) {
                                        Icons.Filled.CheckCircle
                                    } else {
                                        Icons.AutoMirrored.Filled.DirectionsWalk
                                    },
                                    contentDescription = null,
                                    tint = if (isDelivered) {
                                        RailBitePickerPalette.SemanticGreen
                                    } else {
                                        RailBitePickerPalette.CyanPrimary
                                    },
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (isDelivered) "DELIVERED TO SEAT" else "TARGET DESTINATION",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RailBitePickerPalette.TextSecondary,
                                )
                                Text(
                                    text = "Coach ${order.coach.uppercase()} • Seat ${order.berth}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = RailBitePickerPalette.TextPrimary,
                                )
                            }
                            PickerSeatPill(coach = order.coach, berth = order.berth)
                        }

                        Text(
                            text = "PNR ${order.pnr} • ${order.diners} diner${if (order.diners == 1) "" else "s"}" +
                                (if (order.verified == 0) " • unverified" else ""),
                            style = PickerNumericType.MetaMono,
                            color = RailBitePickerPalette.TextSecondary,
                        )

                        if (itemsSummary.isNotBlank()) {
                            Text(
                                text = "$itemCount item${if (itemCount == 1) "" else "s"}: $itemsSummary",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RailBitePickerPalette.TextSecondary,
                            )
                        }

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
                                Column {
                                    Text(
                                        text = if (isUpi) "UPI DIGITAL ORDER" else "COLLECT CASH ON SEAT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isUpi) RailBitePickerPalette.CyanPressed else RailBitePickerPalette.BorderSubtle,
                                    )
                                    Text(
                                        text = if (isUpi) "Zero cash collection due" else "Ask passenger for exact tender",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isUpi) RailBitePickerPalette.CyanPressed else RailBitePickerPalette.TextMuted,
                                    )
                                }
                                Text(
                                    text = Money.rupees(order.totalPaise),
                                    style = PickerNumericType.HeroAmount,
                                    color = if (isUpi) RailBitePickerPalette.CyanPressed else RailBitePickerPalette.CyanPinDigit,
                                )
                            }
                        }
                    }
                }
            }

            state.message?.let { msg ->
                item { PickerErrorBanner(message = msg) }
            }
            if (state.error != null && !Statuses.isTerminal(order.status)) {
                item { PickerErrorBanner(message = state.error) }
            }

            // 2. 1-Tap Quick-Message Chips (while OUT_FOR_DELIVERY)
            if (isOutForDelivery) {
                item {
                    PickerSectionCard {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "1-TAP PASSENGER UPDATES",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RailBitePickerPalette.TextPrimary,
                                )
                                state.quickUpdateBanner?.let { toastText ->
                                    PickerStatusTag(
                                        text = toastText,
                                        bgColor = RailBitePickerPalette.SemanticGreenTint,
                                        textColor = RailBitePickerPalette.SemanticGreenInk,
                                        borderColor = RailBitePickerPalette.SemanticGreenBorder,
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                ChatPresets.PICKER.forEach { preset ->
                                    Surface(
                                        onClick = { if (!state.sendingChat) onSendQuickChip(preset) },
                                        shape = RoundedCornerShape(PickerRadii.Pill),
                                        color = RailBitePickerPalette.CyanTint,
                                        border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                                        modifier = Modifier.heightIn(min = 40.dp),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        ) {
                                            Text(
                                                text = preset.body,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = RailBitePickerPalette.CyanPressed,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Integrated Passenger Quick-Chat Card (live or read-only history)
            if (state.chatAvailable) {
                item {
                    val hasUnread = state.unreadChatCount > 0
                    PickerSectionCard(
                        containerColor = if (hasUnread) RailBitePickerPalette.CyanTint else RailBitePickerPalette.SurfaceCard,
                        border = if (hasUnread) PickerSelectedBorder else PickerCardBorder,
                    ) {
                        Surface(
                            onClick = onOpenChat,
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(14.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(PickerRadii.Button))
                                        .background(
                                            if (hasUnread) RailBitePickerPalette.SurfaceCard else RailBitePickerPalette.CyanTint,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = if (state.canSendChat) Icons.AutoMirrored.Filled.Chat else Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = RailBitePickerPalette.CyanPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "Order Quick-Chat",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = RailBitePickerPalette.TextPrimary,
                                        )
                                        if (hasUnread) {
                                            PickerStatusTag(
                                                text = "${state.unreadChatCount} NEW",
                                                bgColor = RailBitePickerPalette.CyanPrimary,
                                                textColor = Color.White,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    val preview = when {
                                        !state.latestUnreadPreview.isNullOrBlank() ->
                                            "Passenger: ${state.latestUnreadPreview}"
                                        latestMsg != null -> {
                                            val speaker = if (latestMsg.senderRole.equals("PICKER", ignoreCase = true)) "You" else "Passenger"
                                            "$speaker: ${latestMsg.body}"
                                        }
                                        state.canSendChat -> "Tap to open chat or send custom message"
                                        else -> "Order delivered — tap to view chat log"
                                    }
                                    Text(
                                        text = preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RailBitePickerPalette.TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                PickerStatusTag(
                                    text = if (state.canSendChat) "Open Chat" else "View Log",
                                    bgColor = RailBitePickerPalette.SurfaceCard,
                                    textColor = RailBitePickerPalette.CyanPressed,
                                    borderColor = RailBitePickerPalette.CyanBorder,
                                )
                            }
                        }
                    }
                }
            }

            // 4. Stage Specific Instructions / Completion Banner
            when {
                isOutForDelivery -> {
                    item {
                        PickerSectionCard(containerColor = RailBitePickerPalette.SurfaceCard) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                            ) {
                                Text(
                                    text = "SEAT HANDOVER PROTOCOL",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RailBitePickerPalette.TextPrimary,
                                )
                                Text(
                                    text = "At Seat ${order.berth}, ask for the 4-digit delivery PIN shown on the passenger's screen. Never guess — a mismatched PIN keeps the order open and protects your cash tally.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RailBitePickerPalette.TextSecondary,
                                )
                            }
                        }
                    }
                }
                isDelivered -> {
                    item {
                        PickerSectionCard(
                            containerColor = RailBitePickerPalette.SemanticGreenTint,
                            border = BorderStroke(1.dp, RailBitePickerPalette.SemanticGreenBorder),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = RailBitePickerPalette.SemanticGreen,
                                    )
                                    Text(
                                        text = "Handover Complete & Ledger Recorded",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = RailBitePickerPalette.SemanticGreenInk,
                                    )
                                }
                                Text(
                                    text = if (isUpi) {
                                        "Digital UPI order delivered. No cash added to your shift float."
                                    } else {
                                        "Recorded ${Money.rupees(order.totalPaise)} in today's Cash Ledger. Passenger can confirm receipt in their app."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RailBitePickerPalette.SemanticGreenInk,
                                )
                            }
                        }
                    }
                }
                Statuses.isFailed(order.status) -> {
                    item {
                        PickerSectionCard(
                            containerColor = RailBitePickerPalette.SemanticRedTint,
                            border = BorderStroke(1.dp, RailBitePickerPalette.SemanticRedBorder),
                        ) {
                            Text(
                                text = "This order exited active fulfillment (${Statuses.label(order.status)}).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RailBitePickerPalette.SemanticRed,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
                else -> {
                    item {
                        PickerSectionCard {
                            Text(
                                text = "Status: ${Statuses.label(order.status)}. Waiting for order assignment update.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RailBitePickerPalette.TextSecondary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

