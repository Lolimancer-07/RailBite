package com.railbite.picker.ui.order

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.model.ChatPreset
import com.railbite.core.model.ChatPresets
import com.railbite.core.model.Order
import com.railbite.core.model.OrderMessage
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.ui.StatusChip
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteSecondaryButton
import com.railbite.core.ui.components.RailBiteTextField
import com.railbite.core.ui.theme.Metrics
import com.railbite.core.ui.theme.Space
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses
import java.util.Locale

/**
 * Picker workflow for one order:
 * ACCEPTED (auto -> packing) -> PACKING: tick every item -> pickup -> OUT_FOR_DELIVERY:
 * repeat the passenger's 4-digit code + collected cash -> DELIVERED.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderProgressScreen(
    graph: com.railbite.picker.PickerGraph,
    orderId: String,
    onBack: () -> Unit,
    startChatOpen: Boolean = false,
) {
    val vm = viewModel(key = "progress-$orderId") {
        OrderProgressViewModel(graph.container, orderId)
    }
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(startChatOpen) {
        if (startChatOpen) vm.openChat()
    }

    LaunchedEffect(vm) {
        vm.incomingCustomerAlerts.collect {
            playIncomingChatCue(context)
        }
    }

    var code by remember { mutableStateOf("") }
    var cashText by remember { mutableStateOf("") }
    var cashTouched by remember { mutableStateOf(false) }

    // Pre-fill the collected-cash field: the expected total for COD, 0 for UPI_QUEUED
    // (passenger pays digitally; the picker records nothing unless odd cash changes hands)
    LaunchedEffect(state.order?.id, state.order?.status, state.order?.totalPaise) {
        val o = state.order ?: return@LaunchedEffect
        if (!cashTouched && o.status == Statuses.OUT_FOR_DELIVERY) {
            val prefilled = if (o.paymentMode == "UPI_QUEUED") 0 else o.totalPaise
            cashText = String.format(Locale.US, "%.2f", prefilled / 100.0)
        }
    }

    val currentOrder = state.order
    if (currentOrder != null && (currentOrder.status == Statuses.ACCEPTED || currentOrder.status == Statuses.PACKING)) {
        PickingChecklistScreen(
            order = currentOrder,
            state = state,
            onBack = onBack,
            onOpenChat = vm::openChat,
            onTickItem = vm::tick,
            onStartPickup = vm::startPickup,
        )
        if (state.chatOpen) {
            PickerChatBottomSheet(
                coach = currentOrder.coach,
                berth = currentOrder.berth,
                messages = state.chatMessages,
                canSend = state.canSendChat,
                sending = state.sendingChat,
                error = state.chatError,
                onSendPreset = { preset -> vm.sendChatMessage(preset.body, preset.code) },
                onSendCustom = { text -> vm.sendChatMessage(text, null) },
                onDismissError = vm::clearChatError,
                onDismiss = vm::closeChat,
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order #${orderId.take(8)}") },
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
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            val order = state.order
            if (state.loading && order == null) {
                LoadingSkeleton(rows = 4)
                return@Column
            }
            if (order == null) {
                EmptyState(
                    title = "Order unavailable",
                    subtitle = state.error ?: "Try again from the home screen",
                )
                RailBiteSecondaryButton(
                    text = "Back",
                    onClick = onBack,
                    modifier = Modifier.padding(horizontal = Space.screen),
                )
                return@Column
            }

            StepsRow(order.status)
            HeaderCard(order)

            if (state.chatAvailable) {
                QuickMessageCard(
                    unreadCount = state.unreadChatCount,
                    unreadPreview = state.latestUnreadPreview,
                    latestMessage = state.chatMessages.lastOrNull(),
                    canSend = state.canSendChat,
                    onClick = vm::openChat,
                )
            }

            state.message?.let { ErrorBar(it) }
            if (state.error != null && !Statuses.isTerminal(order.status)) {
                ErrorBar(state.error!!)
            }

            when (order.status) {
                Statuses.PACKING -> PackingCard(state, vm)
                Statuses.OUT_FOR_DELIVERY -> DeliveryCard(
                    code = code,
                    onCodeChange = { code = it.filter(Char::isDigit).take(4) },
                    cashText = cashText,
                    onCashChange = {
                        cashTouched = true
                        cashText = it
                    },
                    expectedPaise = order.totalPaise,
                    upiOrder = order.paymentMode == "UPI_QUEUED",
                    busy = state.busy,
                    onDeliver = { vm.deliver(code, cashText) },
                )
                Statuses.DELIVERED -> DeliveredCard(order, busy = state.busy, onDone = { vm.finish(onBack) })
                Statuses.CANCELLED, Statuses.EXPIRED -> EmptyState(
                    title = Statuses.label(order.status),
                    subtitle = "Nothing to do — this order left the flow",
                )
                else -> InfoCard(
                    "Waiting — order is ${Statuses.label(order.status).lowercase()}. " +
                        "It appears here once it is accepted by you.",
                )
            }
        }
    }

    val order = state.order
    if (state.chatOpen && order != null) {
        PickerChatBottomSheet(
            coach = order.coach,
            berth = order.berth,
            messages = state.chatMessages,
            canSend = state.canSendChat,
            sending = state.sendingChat,
            error = state.chatError,
            onSendPreset = { preset -> vm.sendChatMessage(preset.body, preset.code) },
            onSendCustom = { text -> vm.sendChatMessage(text, null) },
            onDismissError = vm::clearChatError,
            onDismiss = vm::closeChat,
        )
    }
}

@Composable
private fun HeaderCard(order: Order) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Coach ${order.coach} · Seat ${order.berth}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    Statuses.label(order.status),
                    color = when {
                        order.status == Statuses.DELIVERED -> MaterialTheme.colorScheme.primary
                        Statuses.isFailed(order.status) -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    },
                )
            }
            Text(
                "PNR ${order.pnr} · ${order.diners} diner${if (order.diners == 1) "" else "s"}" +
                    if (order.verified == 0) " · unverified" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            order.items?.let { items ->
                Text(
                    items.sumOf { it.qty }.toString() + " item(s) · " + Money.rupees(order.totalPaise) + " " +
                        (if (order.paymentMode == "UPI_QUEUED") "UPI (digital)" else "COD"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StepsRow(status: String) {
    val step = when (status) {
        Statuses.ACCEPTED -> 0
        Statuses.PACKING -> 1
        Statuses.OUT_FOR_DELIVERY -> 2
        Statuses.DELIVERED -> 3
        else -> 0
    }
    val labels = listOf("Accepted", "Packing", "On the way", "Delivered")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val done = index < step
            val active = index == step
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done || active -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Text(
                            "✓",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else if (active) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary),
                        )
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (done || active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
            if (index < labels.lastIndex) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 4.dp)
                        .background(
                            if (index < step) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun PackingCard(state: ProgressUiState, vm: OrderProgressViewModel) {
    val order = state.order ?: return
    val items = order.items.orEmpty()
    val packed = state.checklist.values.count { it }
    val allPacked = items.isNotEmpty() && packed == items.size

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Picking checklist",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$packed / ${items.size} picked",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items.forEach { item ->
                // Whole row is the tap target (≥ 44 dp); checkbox itself is display-only here.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Metrics.tapTarget)
                        .clickable { vm.tick(item.itemId) },
                ) {
                    Checkbox(
                        checked = state.checklist[item.itemId] ?: false,
                        onCheckedChange = null,
                    )
                    Text(
                        "${item.qty} × ${item.name ?: item.itemId}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Money.rupees(item.unitPricePaise * item.qty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.isEmpty()) {
                Text(
                    "No item list on this order — pick per the printed slip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.xs))
            RailBiteButton(
                text = "All picked — start delivery",
                onClick = vm::startPickup,
                enabled = allPacked,
                busy = state.busy,
            )
            if (!allPacked) {
                Text(
                    "Tick every item to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeliveryCard(
    code: String,
    onCodeChange: (String) -> Unit,
    cashText: String,
    onCashChange: (String) -> Unit,
    expectedPaise: Int,
    upiOrder: Boolean,
    busy: Boolean,
    onDeliver: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text("On the way", style = MaterialTheme.typography.titleMedium)
            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    "Ask the passenger for the 4-digit delivery code shown in their app. " +
                        "Never guess it — a wrong code keeps the order open.",
                    Modifier.padding(Space.s),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // Passenger's 4-digit code first — the handover authority.
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("Delivery code") },
                placeholder = { Text("• • • •") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next,
                ),
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.tapTarget),
            )
            // Then the collected cash.
            RailBiteTextField(
                value = cashText,
                onValueChange = onCashChange,
                label = if (upiOrder) "Cash collected (₹) — UPI order" else "Cash collected (₹)",
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                supportingText = if (upiOrder) {
                    "Passenger pays ${Money.rupees(expectedPaise)} digitally via UPI — leave 0"
                } else {
                    "Expected ${Money.rupees(expectedPaise)}"
                },
            )
            RailBiteButton(
                text = "Mark delivered",
                onClick = onDeliver,
                enabled = code.length == 4,
                busy = busy,
            )
        }
    }
}

@Composable
private fun DeliveredCard(order: Order, busy: Boolean, onDone: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text(
                "Delivered",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                if (order.paymentMode == "UPI_QUEUED") {
                    "The passenger pays ${Money.rupees(order.totalPaise)} digitally via UPI — no cash to collect. " +
                        "They confirm the payment in their app."
                } else {
                    "Hand over only after the passenger pays ${Money.rupees(order.totalPaise)} cash. " +
                        "They confirm the amount in their app."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            RailBiteButton(
                text = "Done",
                onClick = onDone,
                busy = busy,
            )
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickMessageCard(
    unreadCount: Int,
    unreadPreview: String?,
    latestMessage: OrderMessage?,
    canSend: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (unreadCount > 0) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (unreadCount > 0) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.tapTarget)
                .padding(Space.m),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Quick Message",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = when {
                    unreadPreview != null -> "Customer: $unreadPreview"
                    latestMessage != null -> {
                        val who = if (latestMessage.senderRole == "PICKER") "You: " else "Customer: "
                        who + latestMessage.body
                    }
                    canSend -> "Send 1-tap update to passenger"
                    else -> "Order complete — view chat history"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unreadCount > 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (unreadCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "$unreadCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerChatBottomSheet(
    coach: String,
    berth: Int,
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
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Passenger Chat · $coach Seat $berth",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (canSend) "Live quick updates for this order" else "Chat closed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close chat")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 260.dp),
            ) {
                if (messages.isEmpty()) {
                    Text(
                        text = "No messages yet. Tap a quick preset below to update the passenger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = Space.s),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            PickerChatBubble(msg)
                        }
                    }
                }
            }

            if (error != null) {
                Surface(
                    onClick = onDismissError,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
                    )
                }
            }

            if (canSend) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatPresets.PICKER.chunked(2).forEach { rowPresets ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowPresets.forEach { preset ->
                                Surface(
                                    onClick = { if (!sending) onSendPreset(preset) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
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
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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
                        placeholder = { Text("Short note (max 120)…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val toSend = draft.trim()
                            if (toSend.isNotEmpty() && !sending) {
                                onSendCustom(toSend)
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank() && !sending,
                        modifier = Modifier.heightIn(min = Metrics.tapTarget),
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
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
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Chat closed",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerChatBubble(msg: OrderMessage) {
    val mine = msg.senderRole == "PICKER"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 270.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = msg.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = (if (mine) "You" else "Customer") + " · " + formatPickerChatTime(msg.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mine) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun formatPickerChatTime(epochMs: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault("")

private fun playIncomingChatCue(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return@runCatching
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: return@runCatching
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.play()
    }
}
