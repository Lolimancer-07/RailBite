package com.railbite.picker.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.model.CaptainActionRequest
import com.railbite.core.model.ChatPreset
import com.railbite.core.model.ChatPresets
import com.railbite.core.model.DeliverRequest
import com.railbite.core.model.Order
import com.railbite.core.model.OrderMessage
import com.railbite.core.model.OrderMessageCreateRequest
import com.railbite.core.util.Statuses
import com.railbite.picker.PickerContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

data class ProgressUiState(
    val order: Order? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** itemId -> packed tick; local only, the server only sees the pickup call. */
    val checklist: Map<String, Boolean> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null,
    val slaRemainingSeconds: Int = SLA_TARGET_SECONDS,
    val chatMessages: List<OrderMessage> = emptyList(),
    val unreadChatCount: Int = 0,
    val latestUnreadPreview: String? = null,
    val chatOpen: Boolean = false,
    val sendingChat: Boolean = false,
    val chatError: String? = null,
    val quickUpdateBanner: String? = null,
    /** Controls the HandoverConfirmSheet overlay on DeliveryAndHandoverScreen. */
    val showHandoverSheet: Boolean = false,
) {
    val canSendChat: Boolean
        get() = order?.status in listOf(
            Statuses.ACCEPTED,
            Statuses.PACKING,
            Statuses.OUT_FOR_DELIVERY,
        )
    val chatAvailable: Boolean
        get() = canSendChat ||
            order?.status == Statuses.DELIVERED ||
            order?.status == Statuses.CANCELLED ||
            chatMessages.isNotEmpty()

    companion object {
        const val SLA_TARGET_SECONDS = 600
    }
}

class OrderProgressViewModel(
    private val container: PickerContainer,
    private val orderId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    private val _incomingCustomerAlerts = MutableSharedFlow<OrderMessage>(extraBufferCapacity = 8)
    val incomingCustomerAlerts: SharedFlow<OrderMessage> = _incomingCustomerAlerts.asSharedFlow()

    private var pollJob: Job? = null
    private var autoPacked = false
    private val seenChatIds = mutableSetOf<String>()
    private var initialChatSynced = false

    init {
        viewModelScope.launch {
            val staffId = container.settings.staffId.first()
            if (staffId.isNotBlank()) {
                runCatching { container.chatMqtt.watchOrder(orderId, staffId) }
            }
        }
        viewModelScope.launch {
            container.chatMqtt.orderMessages.collect { byOrder ->
                val live = byOrder[orderId].orEmpty()
                if (live.isNotEmpty()) {
                    mergeChatMessages(live, markBaselineOnly = false)
                }
            }
        }
        // Live 1s SLA countdown ticker computed from order.createdAt
        viewModelScope.launch {
            while (isActive) {
                val createdAtIso = _state.value.order?.createdAt
                if (!createdAtIso.isNullOrBlank()) {
                    val createdEpoch = runCatching { Instant.parse(createdAtIso).epochSecond }.getOrNull()
                    if (createdEpoch != null) {
                        val elapsed = (Instant.now().epochSecond - createdEpoch).toInt().coerceAtLeast(0)
                        val remaining = ProgressUiState.SLA_TARGET_SECONDS - elapsed
                        _state.update { it.copy(slaRemainingSeconds = remaining) }
                    }
                }
                delay(1_000)
            }
        }
        load()
    }

    /** Loads the order and keeps polling every 3 s until it reaches a terminal state. */
    fun load() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                val o = _state.value.order
                if (o != null && Statuses.isTerminal(o.status)) break
                delay(3_000)
            }
        }
    }

    private suspend fun refreshOnce() {
        try {
            val api = container.client.api()
            var order = api.order(orderId, role = "picker")
            if (order.status == Statuses.ACCEPTED && !autoPacked) {
                autoPacked = true
                runCatching {
                    api.startPacking(orderId, CaptainActionRequest(container.settings.staffId.first()))
                }
                order = api.order(orderId, role = "picker")
            }
            val remainingSla = runCatching {
                val createdEpoch = Instant.parse(order.createdAt).epochSecond
                val elapsed = (Instant.now().epochSecond - createdEpoch).toInt().coerceAtLeast(0)
                ProgressUiState.SLA_TARGET_SECONDS - elapsed
            }.getOrDefault(ProgressUiState.SLA_TARGET_SECONDS)

            _state.update { st ->
                val incoming = order.items
                st.copy(
                    order = order,
                    loading = false,
                    error = null,
                    slaRemainingSeconds = remainingSla,
                    checklist = if (st.checklist.isEmpty() && incoming != null) {
                        incoming.associate { it.itemId to false }
                    } else {
                        st.checklist
                    },
                )
            }
            if (order.status != Statuses.PLACED && order.status != Statuses.ASSIGNED) {
                val history = runCatching { api.orderMessages(orderId) }.getOrNull()
                if (history != null) {
                    mergeChatMessages(history, markBaselineOnly = !initialChatSynced)
                    initialChatSynced = true
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = friendly(e)) }
        }
    }

    fun tick(itemId: String) {
        _state.update { st ->
            st.copy(checklist = st.checklist + (itemId to !(st.checklist[itemId] ?: false)))
        }
    }

    /** All items picked -> transition to OUT_FOR_DELIVERY. */
    fun startPickup() {
        val s = _state.value
        if (s.busy || s.order == null) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val order = container.client.api().pickupOrder(
                    orderId,
                    CaptainActionRequest(container.settings.staffId.first()),
                )
                _state.update { it.copy(order = order, busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    /**
     * Final handover. Verifies the passenger's 4-digit PIN (403 on mismatch)
     * and records exact cash collected (server defaults to order total when null).
     */
    fun deliver(code: String, cashText: String) {
        val s = _state.value
        if (s.busy || s.order == null) return
        if (code.trim().length != 4) {
            _state.update { it.copy(message = "Enter the 4-digit delivery PIN shown on the passenger's screen") }
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val order = container.client.api().deliverOrder(
                    orderId,
                    DeliverRequest(
                        captainId = container.settings.staffId.first(),
                        cashPaise = parseCash(cashText),
                        deliveryCode = code.trim(),
                    ),
                )
                _state.update {
                    it.copy(
                        order = order,
                        busy = false,
                        message = null,
                        showHandoverSheet = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    /** Clears the current-order pointer and leaves the screen. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            container.settings.setPickerOrderId("")
            onDone()
        }
    }

    fun openChat() {
        _state.update { s ->
            s.chatMessages.forEach { seenChatIds.add(it.id) }
            s.copy(
                chatOpen = true,
                unreadChatCount = 0,
                latestUnreadPreview = null,
                chatError = null,
            )
        }
        viewModelScope.launch {
            runCatching { container.client.api().orderMessages(orderId) }
                .getOrNull()
                ?.let { mergeChatMessages(it, markBaselineOnly = false) }
        }
    }

    fun closeChat() {
        _state.update { it.copy(chatOpen = false, chatError = null) }
    }

    fun clearChatError() {
        _state.update { it.copy(chatError = null) }
    }

    /** Shows the HandoverConfirmSheet overlay on the delivery screen. */
    fun openHandoverSheet() {
        _state.update { it.copy(showHandoverSheet = true, message = null) }
    }

    /** Hides the HandoverConfirmSheet overlay. */
    fun closeHandoverSheet() {
        _state.update { it.copy(showHandoverSheet = false) }
    }

    fun sendQuickChip(preset: ChatPreset) {
        sendChatMessage(preset.body, preset.code, showBanner = true)
    }

    fun sendChatMessage(rawBody: String, presetCode: String? = null, showBanner: Boolean = false) {
        val trimmed = rawBody.trim().take(ChatPresets.MAX_BODY_LENGTH)
        if (trimmed.isEmpty()) return
        val current = _state.value
        if (!current.canSendChat) {
            _state.update { it.copy(chatError = "Chat closed — order already completed") }
            return
        }
        if (current.sendingChat) return
        _state.update { it.copy(sendingChat = true, chatError = null) }
        val messageId = UUID.randomUUID().toString()
        viewModelScope.launch {
            try {
                val staffId = container.settings.staffId.first().ifBlank { null }
                val created = container.client.api().sendOrderMessage(
                    orderId,
                    OrderMessageCreateRequest(
                        id = messageId,
                        senderRole = "PICKER",
                        senderId = staffId,
                        presetCode = presetCode,
                        body = trimmed,
                    ),
                )
                mergeChatMessages(listOf(created), markBaselineOnly = false)
                _state.update {
                    it.copy(
                        sendingChat = false,
                        quickUpdateBanner = if (showBanner) "Sent: $trimmed" else it.quickUpdateBanner,
                    )
                }
                if (showBanner) {
                    launch {
                        delay(2_500)
                        _state.update { cur ->
                            if (cur.quickUpdateBanner == "Sent: $trimmed") {
                                cur.copy(quickUpdateBanner = null)
                            } else {
                                cur
                            }
                        }
                    }
                }
            } catch (e: HttpException) {
                val msg = if (e.code() == 409) {
                    "Chat closed — order already completed"
                } else {
                    "Could not send message (HTTP ${e.code()})"
                }
                _state.update { it.copy(sendingChat = false, chatError = msg) }
                if (e.code() == 409) refreshOnce()
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        sendingChat = false,
                        chatError = "Message not sent — check connection",
                    )
                }
            }
        }
    }

    private fun mergeChatMessages(incoming: List<OrderMessage>, markBaselineOnly: Boolean) {
        if (incoming.isEmpty() && _state.value.chatMessages.isEmpty()) return
        var notifyCustomerMsg: OrderMessage? = null
        _state.update { s ->
            val byId = LinkedHashMap<String, OrderMessage>()
            s.chatMessages.forEach { byId[it.id] = it }
            incoming.forEach { byId[it.id] = it }
            val merged = byId.values.sortedBy { it.createdAt }
            val terminal = Statuses.isTerminal(s.order?.status.orEmpty())
            val newlyArrivedCustomer = if (!markBaselineOnly && !terminal) {
                incoming.filter { msg ->
                    msg.senderRole == "CUSTOMER" &&
                        s.chatMessages.none { it.id == msg.id } &&
                        msg.id !in seenChatIds
                }.maxByOrNull { it.createdAt }
            } else {
                null
            }
            if (newlyArrivedCustomer != null) {
                notifyCustomerMsg = newlyArrivedCustomer
            }
            if (s.chatOpen || (markBaselineOnly && terminal)) {
                merged.forEach { seenChatIds.add(it.id) }
            } else {
                merged.filter { it.senderRole != "CUSTOMER" }.forEach { seenChatIds.add(it.id) }
            }
            val unseenCustomer = merged.filter { it.senderRole == "CUSTOMER" && it.id !in seenChatIds }
            s.copy(
                chatMessages = merged,
                unreadChatCount = unseenCustomer.size,
                latestUnreadPreview = unseenCustomer.lastOrNull()?.body,
            )
        }
        notifyCustomerMsg?.let { _incomingCustomerAlerts.tryEmit(it) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        container.chatMqtt.unwatchOrder(orderId)
        super.onCleared()
    }

    private fun parseCash(text: String): Int? = runCatching {
        val cleaned = text.replace("₹", "").replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact()
    }.getOrNull()

    private fun friendly(e: Exception): String {
        val code = (e as? HttpException)?.code()
        return when {
            code == 403 || e.message?.contains("403") == true ->
                "Wrong delivery PIN — ask passenger to check their Order Tracking screen"
            code == 409 || e.message?.contains("409") == true ->
                "The order is already in that state"
            code == 404 || e.message?.contains("404") == true ->
                "Order no longer exists"
            else -> "Network error — check train Wi-Fi"
        }
    }
}
