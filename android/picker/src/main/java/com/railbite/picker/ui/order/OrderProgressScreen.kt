package com.railbite.picker.ui.order

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.util.Statuses
import com.railbite.picker.ui.components.PickerErrorBanner
import com.railbite.picker.ui.components.PickerPrimaryButton
import com.railbite.picker.ui.components.PickerSectionCard
import com.railbite.picker.ui.components.shortOrderTag
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.RailBitePickerPalette
import java.util.Locale

/**
 * Coordinates the picker fulfillment workflow for one order:
 * - ACCEPTED / PACKING -> [PickingChecklistScreen] (Stitch Screen 4)
 * - OUT_FOR_DELIVERY / DELIVERED / terminal -> [DeliveryAndHandoverScreen] (Stitch Screen 5a)
 * - Order-scoped Quick-Chat modal -> [OrderQuickChatSheet] (Stitch Screen 5b)
 * - Handover PIN & cash collection modal -> [HandoverConfirmSheet] (Stitch Screen 5c)
 */
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

    // Both transports unavailable (or LAN rejected the send): surface a transient toast even
    // when the sheet has been dismissed.
    LaunchedEffect(state.chatError) {
        state.chatError?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    var code by remember { mutableStateOf("") }
    var cashText by remember { mutableStateOf("") }
    var cashTouched by remember { mutableStateOf(false) }

    // Pre-fill the collected-cash field: the expected total for COD, 0 for UPI_QUEUED
    // (passenger pays digitally; the picker records nothing unless odd cash changes hands).
    LaunchedEffect(state.order?.id, state.order?.status, state.order?.totalPaise) {
        val o = state.order ?: return@LaunchedEffect
        if (!cashTouched && o.status == Statuses.OUT_FOR_DELIVERY) {
            val prefilled = if (o.paymentMode == "UPI_QUEUED") 0 else o.totalPaise
            cashText = String.format(Locale.US, "%.2f", prefilled / 100.0)
        }
    }

    val currentOrder = state.order
    if (currentOrder != null) {
        if (currentOrder.status == Statuses.ACCEPTED || currentOrder.status == Statuses.PACKING) {
            PickingChecklistScreen(
                order = currentOrder,
                state = state,
                onBack = onBack,
                onOpenChat = vm::openChat,
                onTickItem = vm::tick,
                onStartPickup = vm::startPickup,
            )
        } else {
            DeliveryAndHandoverScreen(
                order = currentOrder,
                state = state,
                onBack = onBack,
                onOpenChat = vm::openChat,
                onSendQuickChip = vm::sendQuickChip,
                onOpenHandoverSheet = vm::openHandoverSheet,
                onDone = { vm.finish(onBack) },
            )
        }

        if (state.chatOpen) {
            OrderQuickChatSheet(
                orderId = currentOrder.id,
                coach = currentOrder.coach,
                berth = currentOrder.berth,
                orderStatus = currentOrder.status,
                messages = state.chatMessages,
                canSend = state.canSendChat,
                offlinePresetsOnly = state.offlinePresetsOnly,
                sending = state.sendingChat,
                error = state.chatError,
                pendingIds = state.pendingChatIds,
                onSendPreset = { preset -> vm.sendChatMessage(preset.body, preset.code) },
                onSendCustom = { text -> vm.sendChatMessage(text, null) },
                onDismissError = vm::clearChatError,
                onDismiss = vm::closeChat,
            )
        }

        if (state.showHandoverSheet && currentOrder.status == Statuses.OUT_FOR_DELIVERY) {
            HandoverConfirmSheet(
                order = currentOrder,
                code = code,
                onCodeChange = { code = it.filter(Char::isDigit).take(4) },
                cashText = cashText,
                onCashChange = {
                    cashTouched = true
                    cashText = it
                },
                busy = state.busy,
                errorMessage = state.message,
                onConfirmDeliver = { vm.deliver(code, cashText) },
                onDismiss = vm::closeHandoverSheet,
            )
        }
        return
    }

    // Loading / Unavailable fallback in flat Stitch style
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
                                text = "Order Details",
                                style = MaterialTheme.typography.titleLarge,
                                color = RailBitePickerPalette.TextPrimary,
                            )
                            Text(
                                text = shortOrderTag(orderId),
                                style = PickerNumericType.MetaMono,
                                color = RailBitePickerPalette.TextSecondary,
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(PickerMetrics.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                LoadingSkeleton(rows = 4)
            } else {
                PickerErrorBanner(
                    message = state.error ?: "Order unavailable — return to Active Orders and refresh.",
                )
                PickerSectionCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Order Unavailable",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = RailBitePickerPalette.TextPrimary,
                        )
                        Text(
                            text = state.error ?: "This order could not be loaded or may have been reassigned.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePickerPalette.TextSecondary,
                        )
                        PickerPrimaryButton(
                            text = "Back to Orders",
                            onClick = onBack,
                            containerColor = RailBitePickerPalette.DarkSlate,
                        )
                    }
                }
            }
        }
    }
}

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
