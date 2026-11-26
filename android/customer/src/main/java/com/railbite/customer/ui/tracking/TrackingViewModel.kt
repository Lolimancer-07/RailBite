package com.railbite.customer.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.api.RailBiteApi
import com.railbite.core.model.ChatPresets
import com.railbite.core.model.MqttOrderStatus
import com.railbite.core.model.Order
import com.railbite.core.model.OrderMessage
import com.railbite.core.model.OrderMessageCreateRequest
import com.railbite.core.util.Statuses
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.UUID

data class TrackingUiState(
    val loading: Boolean = true,
    val order: Order? = null,
    val status: String = "PLACED",
    val captainName: String? = null,
    val etaMin: Int? = null,
    val error: String? = null,
    val cashConfirming: Boolean = false,
    val cashConfirmed: Boolean = false,
    val cancelled: Boolean = false,
    /** Order travelling over the Bluetooth relay — exists only in the local outbox. */
    val relayed: Boolean = false,
    val relayCode: String? = null,
    val relayItems: List<com.railbite.core.mesh.MeshOrderPayload.Item> = emptyList(),
    val relayTotalPaise: Int = 0,
    val messages: List<OrderMessage> = emptyList(),
    val unreadCount: Int = 0,
    val chatOpen: Boolean = false,
    val chatBanner: OrderMessage? = null,
    val sendingMessage: Boolean = false,
    val chatError: String? = null,
) {
    val delivered: Boolean get() = status == Statuses.DELIVERED
    val failed: Boolean get() = Statuses.isFailed(status)
    val canSendChat: Boolean
        get() = !relayed && (
            status == Statuses.ACCEPTED ||
                status == Statuses.PACKING ||
                status == Statuses.OUT_FOR_DELIVERY
            )
    val chatAvailable: Boolean
        get() = !relayed && (canSendChat || status == Statuses.DELIVERED || messages.isNotEmpty())
}

class TrackingViewModel(
    private val container: AppContainer,
    private val orderId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private val seenMessageIds = mutableSetOf<String>()
    private var initialChatSynced = false

    /** True while the order exists only in the local Bluetooth-relay outbox. */
    private var relayMode = false

    init {
        // MQTT live updates (retained message gives instant current status)
        viewModelScope.launch {
            runCatching { container.mqtt.watchOrder(orderId) }
        }
        viewModelScope.launch {
            container.mqtt.orderMessages.collect { byOrder ->
                val live = byOrder[orderId].orEmpty()
                if (live.isNotEmpty()) {
                    mergeMessages(live, markBaselineOnly = false)
                }
            }
        }
        // REST fallback poll every 3 s while the order is active
        pollJob = viewModelScope.launch {
            // Bluetooth-relay orders live in the local outbox until the cloud is
            // reachable: follow the outbox (no cloud polling, no error noise). Pantry
            // outcomes arrive over the same Bluetooth mesh and advance the timeline
            // offline; when the order syncs to the cloud the normal timeline takes over.
            val entry = runCatching { container.meshSender.relayEntry(orderId) }.getOrNull()
            if (entry != null && entry.state != "SYNCED") {
                relayMode = true
                container.meshSender.startLoop() // re-broadcast + ack listening while visible
                showRelay(entry)
                while (isActive && relayMode) {
                    delay(4_000)
                    val now = runCatching { container.meshSender.relayEntry(orderId) }.getOrNull()
                    when {
                        now == null -> Unit // keep showing the local snapshot
                        now.state == "SYNCED" -> {
                            // The server has the order now — normal cloud timeline takes over.
                            relayMode = false
                            _state.update { it.copy(relayed = false) }
                        }
                        else -> showRelay(now)
                    }
                }
            }
            while (isActive) {
                loadOnce()
                val st = _state.value.status
                if (Statuses.isTerminal(st)) break
                delay(3_000)
            }
        }
    }

    /**
     * Local offline snapshot; pantry outcomes heard over the mesh ([MeshEntry.ack])
     * advance the timeline — placed → accepted → delivered, all without internet.
     */
    private fun showRelay(entry: com.railbite.core.mesh.MeshEntry) {
        val payload = com.railbite.core.mesh.MeshOrderPayload.decode(entry.payload)
        val status = when (entry.ack) {
            com.railbite.core.mesh.MeshPacket.STATUS_ACCEPTED -> Statuses.ACCEPTED
            com.railbite.core.mesh.MeshPacket.STATUS_OUT_FOR_DELIVERY -> Statuses.OUT_FOR_DELIVERY
            com.railbite.core.mesh.MeshPacket.STATUS_DELIVERED -> Statuses.DELIVERED
            else -> Statuses.PLACED
        }
        _state.update { s ->
            s.copy(
                loading = false,
                error = if (entry.ack == com.railbite.core.mesh.MeshPacket.STATUS_REJECTED) {
                    "A picker declined the order — try another pantry nearby"
                } else {
                    null
                },
                relayed = true,
                relayCode = payload?.deliveryCode,
                relayItems = payload?.items.orEmpty(),
                relayTotalPaise = payload?.totalPaise ?: 0,
                status = status,
            )
        }
    }

    fun mqttStatuses(orderId: String): StateFlow<Map<String, MqttOrderStatus>> = container.mqtt.orderStatuses

    /** Latest merged snapshot (REST + MQTT) used by the UI. */
    fun refreshMerged(mqtt: Map<String, MqttOrderStatus>) {
        val mqttStatus = mqtt[orderId]
        val order = _state.value.order
        _state.update { s ->
            s.copy(
                status = mqttStatus?.status ?: s.status,
                captainName = mqttStatus?.captainName ?: s.captainName,
                etaMin = mqttStatus?.etaMin ?: s.etaMin,
            )
        }
        // keep the persisted "active order" pointer in sync
        viewModelScope.launch {
            if (order != null && (order.status != _state.value.status) && Statuses.isTerminal(_state.value.status)) {
                container.settings.setActiveOrderId("")
            }
        }
    }

    private suspend fun loadOnce() {
        try {
            val api = container.client.api()
            val order = api.order(orderId)
            _state.update { s ->
                s.copy(
                    loading = false,
                    order = order,
                    status = order.status,
                    error = null,
                )
            }
            if (order.status != Statuses.PLACED && order.status != Statuses.ASSIGNED) {
                val history = runCatching { api.orderMessages(orderId) }.getOrNull()
                if (history != null) {
                    mergeMessages(history, markBaselineOnly = !initialChatSynced)
                    initialChatSynced = true
                }
            }
            if (Statuses.isTerminal(order.status)) {
                container.settings.setActiveOrderId("")
            }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = friendly(e)) }
        }
    }

    fun confirmCash() {
        _state.update { it.copy(cashConfirming = true) }
        viewModelScope.launch {
            try {
                container.client.api().confirmCash(orderId)
                _state.update { it.copy(cashConfirming = false, cashConfirmed = true) }
            } catch (e: Exception) {
                val notFound = e.message?.contains("404") == true || e.message?.contains("record", true) == true
                _state.update {
                    it.copy(
                        cashConfirming = false,
                        cashConfirmed = notFound || it.cashConfirmed,
                        error = if (notFound) null else "Could not confirm payment — try again",
                    )
                }
            }
        }
    }

    fun cancelOrder() {
        viewModelScope.launch {
            try {
                val order = container.client.api().cancelOrder(orderId)
                _state.update { it.copy(order = order, status = order.status, cancelled = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Cannot cancel: ${e.message}") }
            }
        }
    }

    fun openChat() {
        _state.update { s ->
            s.messages.forEach { seenMessageIds.add(it.id) }
            s.copy(
                chatOpen = true,
                unreadCount = 0,
                chatBanner = null,
                chatError = null,
            )
        }
        viewModelScope.launch {
            runCatching { container.client.api().orderMessages(orderId) }
                .getOrNull()
                ?.let { mergeMessages(it, markBaselineOnly = false) }
        }
    }

    fun closeChat() {
        _state.update { it.copy(chatOpen = false, chatError = null) }
    }

    fun clearChatBanner() {
        _state.update { it.copy(chatBanner = null) }
    }

    fun clearChatError() {
        _state.update { it.copy(chatError = null) }
    }

    fun sendChatMessage(rawBody: String, presetCode: String? = null) {
        val trimmed = rawBody.trim().take(ChatPresets.MAX_BODY_LENGTH)
        if (trimmed.isEmpty()) return
        val current = _state.value
        if (!current.canSendChat) {
            _state.update { it.copy(chatError = "Chat is closed for this order") }
            return
        }
        if (current.sendingMessage) return
        _state.update { it.copy(sendingMessage = true, chatError = null) }
        val messageId = UUID.randomUUID().toString()
        viewModelScope.launch {
            try {
                val created = container.client.api().sendOrderMessage(
                    orderId,
                    OrderMessageCreateRequest(
                        id = messageId,
                        senderRole = "CUSTOMER",
                        presetCode = presetCode,
                        body = trimmed,
                    ),
                )
                mergeMessages(listOf(created), markBaselineOnly = false)
                _state.update { it.copy(sendingMessage = false) }
            } catch (e: HttpException) {
                val msg = if (e.code() == 409) {
                    "Chat closed — order is no longer active"
                } else {
                    "Could not send message (HTTP ${e.code()})"
                }
                _state.update { it.copy(sendingMessage = false, chatError = msg) }
                if (e.code() == 409) loadOnce()
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        sendingMessage = false,
                        chatError = "Message not sent — check server connection",
                    )
                }
            }
        }
    }

    private fun mergeMessages(incoming: List<OrderMessage>, markBaselineOnly: Boolean) {
        if (incoming.isEmpty() && _state.value.messages.isEmpty()) return
        _state.update { s ->
            val byId = LinkedHashMap<String, OrderMessage>()
            s.messages.forEach { byId[it.id] = it }
            incoming.forEach { byId[it.id] = it }
            val merged = byId.values.sortedBy { it.createdAt }
            if (s.chatOpen || (markBaselineOnly && Statuses.isTerminal(s.status))) {
                merged.forEach { seenMessageIds.add(it.id) }
            } else {
                merged.filter { it.senderRole != "PICKER" }.forEach { seenMessageIds.add(it.id) }
            }
            val unseenPicker = merged.filter { it.senderRole == "PICKER" && it.id !in seenMessageIds }
            val newPicker = if (!s.chatOpen && !markBaselineOnly && !Statuses.isTerminal(s.status)) {
                incoming.filter { msg ->
                    msg.senderRole == "PICKER" && s.messages.none { it.id == msg.id } && msg.id !in seenMessageIds
                }.maxByOrNull { it.createdAt }
            } else {
                null
            }
            s.copy(
                messages = merged,
                unreadCount = unseenPicker.size,
                chatBanner = newPicker ?: s.chatBanner,
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        pollJob?.cancel()
        // Keep the MQTT subscription alive briefly for the active-order pill; unwatch on terminal.
        val st = _state.value.status
        if (Statuses.isTerminal(st)) container.mqtt.unwatchOrder(orderId)
        super.onCleared()
    }

    private fun friendly(e: Exception): String {
        val msg = e.message ?: "unknown"
        return if (msg.contains("Unable to resolve", true) || msg.contains("Failed to connect", true) ||
            msg.contains("timeout", true)
        ) {
            "Reconnecting to the pantry server…"
        } else {
            "Order updates may be delayed ($msg)"
        }
    }
}
