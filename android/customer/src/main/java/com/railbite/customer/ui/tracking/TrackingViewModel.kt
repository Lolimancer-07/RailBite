package com.railbite.customer.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.mesh.BleMeshHub
import com.railbite.core.mesh.BlePacketCodec
import com.railbite.core.mesh.MeshEntry
import com.railbite.core.mesh.MeshOrderPayload
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    /** Order travelling over the 23-byte BLE mesh — lives in the local outbox until LAN sync. */
    val relayed: Boolean = false,
    val relayCode: String? = null,
    val relayCoach: String? = null,
    val relayBerth: Int? = null,
    val relayItems: List<MeshOrderPayload.Item> = emptyList(),
    val relayTotalPaise: Int = 0,
    val retryingBroadcast: Boolean = false,
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
        get() = status == Statuses.ACCEPTED ||
            status == Statuses.PACKING ||
            status == Statuses.OUT_FOR_DELIVERY
    val chatAvailable: Boolean
        get() = canSendChat || status == Statuses.DELIVERED || messages.isNotEmpty()
}

class TrackingViewModel(
    private val container: AppContainer,
    private val orderId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private val seenMessageIds = mutableSetOf<String>()
    private val bleMsgTimestamps = ConcurrentHashMap<String, Long>()
    private var initialChatSynced = false
    private var initialBleChatSynced = false

    /** True while the order exists only in the local Bluetooth-mesh outbox. */
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
        // BLE Mesh preset quick-chat frames (`0x13`) for this order
        viewModelScope.launch {
            val shortId = BlePacketCodec.uuidToShortId(orderId)
            BleMeshHub.quickMessages.collect { byOrder ->
                val list = byOrder[shortId].orEmpty()
                if (list.isNotEmpty()) {
                    val converted = list.mapNotNull { pkt ->
                        val preset = BlePacketCodec.presetForCode(pkt.presetCode) ?: return@mapNotNull null
                        val msgId = "ble-${pkt.senderRole}-${pkt.msgSeq}"
                        val ts = bleMsgTimestamps.getOrPut(msgId) { System.currentTimeMillis() }
                        OrderMessage(
                            id = msgId,
                            orderId = orderId,
                            senderRole = if (pkt.senderRole == BlePacketCodec.ROLE_PICKER) "PICKER" else "CUSTOMER",
                            senderId = null,
                            presetCode = preset.code,
                            body = preset.body,
                            createdAt = ts,
                        )
                    }
                    if (converted.isNotEmpty()) {
                        mergeMessages(converted, markBaselineOnly = !initialBleChatSynced)
                        initialBleChatSynced = true
                    }
                }
            }
        }
        // REST + BLE Mesh tracking lifecycle
        pollJob = viewModelScope.launch {
            val entry = runCatching { container.meshSender.relayEntry(orderId) }.getOrNull()
            if (entry != null && entry.state != "SYNCED") {
                if (entry.state == "CANCELLED") {
                    showRelay(entry)
                    return@launch
                }
                relayMode = true
                container.meshSender.startLoop()
                showRelay(entry)
                container.meshSender.observeEntry(orderId)
                    .takeWhile { now ->
                        when {
                            now == null -> isActive
                            now.state == "SYNCED" -> {
                                relayMode = false
                                _state.update { it.copy(relayed = false) }
                                false
                            }
                            now.state == "CANCELLED" -> {
                                relayMode = false
                                showRelay(now)
                                false
                            }
                            else -> {
                                showRelay(now)
                                isActive
                            }
                        }
                    }
                    .collect {}
            }
            if (_state.value.cancelled) return@launch
            while (isActive) {
                loadOnce()
                val st = _state.value.status
                if (Statuses.isTerminal(st)) break
                delay(3_000)
            }
        }
    }

    /**
     * Local offline snapshot; BLE `STATUS_ACK (0x12)` updates ([MeshEntry.ack])
     * advance the timeline cleanly without internet:
     * BROADCASTING -> ACCEPTED -> PACKING -> OUT_FOR_DELIVERY -> DELIVERED.
     */
    private fun showRelay(entry: MeshEntry) {
        val payload = MeshOrderPayload.decode(entry.payload)
        val status = when {
            entry.state == "CANCELLED" -> Statuses.CANCELLED
            entry.ack == Statuses.ACCEPTED -> Statuses.ACCEPTED
            entry.ack == Statuses.PACKING -> Statuses.PACKING
            entry.ack == Statuses.OUT_FOR_DELIVERY -> Statuses.OUT_FOR_DELIVERY
            entry.ack == Statuses.DELIVERED -> Statuses.DELIVERED
            else -> Statuses.BROADCASTING
        }
        _state.update { s ->
            s.copy(
                loading = false,
                error = null,
                relayed = true,
                cancelled = entry.state == "CANCELLED",
                relayCode = payload?.deliveryCode ?: s.relayCode,
                relayCoach = payload?.coach ?: s.relayCoach,
                relayBerth = payload?.berth ?: s.relayBerth,
                relayItems = payload?.items ?: s.relayItems,
                relayTotalPaise = payload?.totalPaise ?: s.relayTotalPaise,
                captainName = entry.captainId?.let { "Crew $it" } ?: s.captainName,
                etaMin = entry.etaMin ?: s.etaMin,
                cashConfirmed = entry.passengerConfirmed || s.cashConfirmed,
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
            if (_state.value.relayed || !container.transportRouter.checkLanReachable()) {
                runCatching { container.meshSender.markCashConfirmed(orderId) }
                _state.update { it.copy(cashConfirming = false, cashConfirmed = true) }
                return@launch
            }
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

    fun retryBroadcast() {
        if (_state.value.retryingBroadcast) return
        _state.update { it.copy(retryingBroadcast = true, error = null) }
        viewModelScope.launch {
            val ok = runCatching { container.meshSender.retryBroadcast(orderId) }.getOrDefault(false)
            _state.update {
                it.copy(
                    retryingBroadcast = false,
                    error = if (ok) null else "Enable Bluetooth & BLE permissions to broadcast",
                )
            }
        }
    }

    fun cancelOfflineSearch(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { container.meshSender.cancelOfflineOrder(orderId) }
            runCatching { container.settings.setActiveOrderId("") }
            _state.update { it.copy(status = Statuses.CANCELLED, cancelled = true) }
            onDone()
        }
    }

    fun cancelOrder() {
        if (_state.value.relayed) {
            cancelOfflineSearch()
            return
        }
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
            if (!_state.value.relayed && container.transportRouter.checkLanReachable()) {
                runCatching { container.client.api().orderMessages(orderId) }
                    .getOrNull()
                    ?.let { mergeMessages(it, markBaselineOnly = false) }
            }
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
            val lanReachable = !current.relayed && container.transportRouter.checkLanReachable()
            if (lanReachable) {
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
                    return@launch
                } catch (e: HttpException) {
                    val msg = if (e.code() == 409) {
                        "Chat closed — order is no longer active"
                    } else {
                        "Could not send message (HTTP ${e.code()})"
                    }
                    _state.update { it.copy(sendingMessage = false, chatError = msg) }
                    if (e.code() == 409) loadOnce()
                    return@launch
                } catch (_: Exception) {
                    // Fall through to BLE preset failover when LAN fails mid-request
                }
            }

            if (presetCode == null) {
                _state.update {
                    it.copy(
                        sendingMessage = false,
                        chatError = "Offline BLE mode supports 1-tap quick presets only",
                    )
                }
                return@launch
            }
            val coach = current.order?.coach
                ?: current.relayCoach
                ?: runCatching { container.settings.coach.first() }.getOrDefault("")
            val berth = current.order?.berth
                ?: current.relayBerth
                ?: runCatching { container.settings.berth.first() }.getOrDefault(0)
            val sent = container.meshSender.sendQuickMessage(
                orderId = orderId,
                coach = coach,
                berth = berth,
                presetCode = presetCode,
            )
            if (sent != null) {
                mergeMessages(listOf(sent), markBaselineOnly = false)
                _state.update { it.copy(sendingMessage = false) }
            } else {
                _state.update {
                    it.copy(
                        sendingMessage = false,
                        chatError = "Bluetooth off — enable Bluetooth to message over BLE mesh",
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
