package com.railbite.picker.ui.order

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.data.OfflineMenuCatalog
import com.railbite.core.mesh.ChatTransport
import com.railbite.core.mesh.MeshEntry
import com.railbite.core.mesh.MeshOrderPayload
import com.railbite.core.mesh.OrderChatRepository
import com.railbite.core.mesh.QuickChatUnavailableException
import com.railbite.core.model.CaptainActionRequest
import com.railbite.core.model.ChatPreset
import com.railbite.core.model.ChatPresets
import com.railbite.core.model.DeliverRequest
import com.railbite.core.model.Order
import com.railbite.core.model.OrderItem
import com.railbite.core.model.OrderMessage
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
import kotlinx.coroutines.flow.takeWhile
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
    val isOfflineMesh: Boolean = false,
    val lanReachable: Boolean = false,
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
    /** Client UUIDs currently shown optimistically with a "Sending…" indicator. */
    val pendingChatIds: Set<String> = emptySet(),
    val quickUpdateBanner: String? = null,
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

    val offlinePresetsOnly: Boolean
        get() = isOfflineMesh || !lanReachable

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
    private var cachedExpectedPin: String? = null

    init {
        viewModelScope.launch {
            container.transportRouter.status.collect { st ->
                _state.update { it.copy(lanReachable = st.lanReachable) }
            }
        }
        viewModelScope.launch {
            val staffId = container.settings.staffId.first()
            if (staffId.isNotBlank() && container.transportRouter.checkLanReachable()) {
                runCatching { container.chatMqtt.watchOrder(orderId, staffId) }
            }
        }
        // Single chat flow: MQTT and BLE 0x13 both write into OrderChatRepository.
        viewModelScope.launch {
            OrderChatRepository.messages.collect { byOrder ->
                val live = byOrder[orderId].orEmpty()
                if (live.isNotEmpty()) {
                    mergeChatMessages(live, markBaselineOnly = !initialChatSynced)
                    initialChatSynced = true
                }
            }
        }
        // Live 1s SLA countdown ticker
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

    /** Loads from local MeshLedger first if it's an unsynced BLE order, then follows LAN polling. */
    fun load() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val initialMesh = runCatching { container.meshLedger.entryOf(orderId) }.getOrNull()
            if (initialMesh != null && initialMesh.state != "SYNCED") {
                container.meshLedger.entries
                    .takeWhile { list ->
                        val cur = list.firstOrNull { it.payload.contains("\"orderId\":\"$orderId\"") }
                        if (cur != null && cur.state != "SYNCED") {
                            applyMeshEntry(cur)
                            isActive
                        } else {
                            false
                        }
                    }
                    .collect {}
            }

            while (isActive) {
                refreshOnce()
                val o = _state.value.order
                if (o != null && Statuses.isTerminal(o.status)) break
                delay(3_000)
            }
        }
    }

    private suspend fun applyMeshEntry(entry: MeshEntry) {
        val payload = MeshOrderPayload.decode(entry.payload) ?: return
        cachedExpectedPin = entry.deliveryCode ?: payload.deliveryCode
        var effectiveState = entry.state

        if (effectiveState == Statuses.ACCEPTED && !autoPacked) {
            autoPacked = true
            effectiveState = Statuses.PACKING
            container.meshLedger.markState(orderId, Statuses.PACKING)
            container.meshGateway.broadcastStatus(orderId, Statuses.PACKING, cashPaise = payload.totalPaise)
        }

        val catalog = OfflineMenuCatalog.bundledEntries(container.app)
        val orderItems = payload.items.map { item ->
            val cat = catalog.firstOrNull {
                it.id.equals(item.itemId, ignoreCase = true) ||
                    it.name.equals(item.name, ignoreCase = true)
            }?.category ?: "SNACKS"
            OrderItem(
                itemId = item.itemId,
                qty = item.qty,
                unitPricePaise = item.unitPricePaise,
                name = item.name,
                category = cat,
            )
        }

        val order = Order(
            id = payload.orderId,
            trainNumber = payload.trainNumber,
            pnr = payload.pnr,
            coach = payload.coach,
            berth = payload.berth,
            diners = payload.diners,
            track = "READY",
            status = effectiveState,
            paymentMode = "COD",
            totalPaise = payload.totalPaise,
            source = "RELAY",
            verified = 1,
            createdAt = payload.createdAt,
            updatedAt = entry.createdAt,
            deliveryCode = cachedExpectedPin,
            items = orderItems,
        )

        val remainingSla = runCatching {
            val createdEpoch = Instant.parse(order.createdAt).epochSecond
            val elapsed = (Instant.now().epochSecond - createdEpoch).toInt().coerceAtLeast(0)
            ProgressUiState.SLA_TARGET_SECONDS - elapsed
        }.getOrDefault(ProgressUiState.SLA_TARGET_SECONDS)

        _state.update { st ->
            st.copy(
                order = order,
                isOfflineMesh = true,
                loading = false,
                error = null,
                slaRemainingSeconds = remainingSla,
                checklist = if (st.checklist.isEmpty()) {
                    orderItems.associate { it.itemId to (effectiveState != Statuses.ACCEPTED && effectiveState != Statuses.PACKING) }
                } else {
                    st.checklist
                },
            )
        }
    }

    private suspend fun refreshOnce() {
        try {
            if (!container.transportRouter.checkLanReachable()) {
                val fallbackMesh = runCatching { container.meshLedger.entryOf(orderId) }.getOrNull()
                if (fallbackMesh != null) {
                    applyMeshEntry(fallbackMesh)
                    return
                }
                _state.update { it.copy(loading = false, error = "Offline mode — no local record found") }
                return
            }

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
                    isOfflineMesh = false,
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
                runCatching {
                    container.chatMqtt.watchOrder(orderId, container.settings.staffId.first(), order.trainNumber)
                }
                val history = runCatching { api.orderMessages(orderId) }.getOrNull()
                if (history != null) {
                    OrderChatRepository.ingestAll(orderId, history, ChatTransport.LAN)
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

    /** All items picked -> transition to OUT_FOR_DELIVERY (LAN or BLE Mesh). */
    fun startPickup() {
        val s = _state.value
        if (s.busy || s.order == null) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            if (s.isOfflineMesh) {
                container.meshLedger.markState(orderId, Statuses.OUT_FOR_DELIVERY)
                container.meshGateway.broadcastStatus(
                    orderId = orderId,
                    state = Statuses.OUT_FOR_DELIVERY,
                    etaMins = 5,
                    cashPaise = s.order.totalPaise,
                )
                _state.update { cur ->
                    cur.copy(
                        order = cur.order?.copy(status = Statuses.OUT_FOR_DELIVERY),
                        busy = false,
                    )
                }
                return@launch
            }
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
     * Final handover. Verifies the passenger's 4-digit PIN (server or embedded BLE PIN)
     * and records exact cash collected before marking DELIVERED.
     */
    fun deliver(code: String, cashText: String) {
        val s = _state.value
        val curOrder = s.order ?: return
        if (s.busy) return
        val cleanCode = code.trim()
        if (cleanCode.length != 4) {
            _state.update { it.copy(message = "Enter the 4-digit delivery PIN shown on the passenger's screen") }
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            if (s.isOfflineMesh) {
                val expectedPin = cachedExpectedPin ?: curOrder.deliveryCode
                if (!expectedPin.isNullOrBlank() && cleanCode != expectedPin) {
                    _state.update {
                        it.copy(
                            busy = false,
                            message = "Wrong delivery PIN — ask passenger to check their Order Tracking screen",
                        )
                    }
                    return@launch
                }
                val collected = parseCash(cashText) ?: curOrder.totalPaise
                val staffId = container.settings.staffId.first().ifBlank { "c1" }
                container.meshLedger.markState(
                    orderId = orderId,
                    state = Statuses.DELIVERED,
                    deliveryCode = expectedPin ?: cleanCode,
                    cashPaise = collected,
                    captainId = staffId,
                )
                container.settings.setPickerOrderId("")
                container.meshGateway.broadcastStatus(
                    orderId = orderId,
                    state = Statuses.DELIVERED,
                    etaMins = 0,
                    cashPaise = collected,
                )
                _state.update { cur ->
                    cur.copy(
                        order = cur.order?.copy(status = Statuses.DELIVERED),
                        busy = false,
                        message = null,
                        showHandoverSheet = false,
                    )
                }
                return@launch
            }
            try {
                val order = container.client.api().deliverOrder(
                    orderId,
                    DeliverRequest(
                        captainId = container.settings.staffId.first(),
                        cashPaise = parseCash(cashText),
                        deliveryCode = cleanCode,
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
            if (!_state.value.isOfflineMesh && container.transportRouter.checkLanReachable()) {
                runCatching { container.client.api().orderMessages(orderId) }
                    .getOrNull()
                    ?.let {
                        OrderChatRepository.ingestAll(orderId, it, ChatTransport.LAN)
                        mergeChatMessages(it, markBaselineOnly = false)
                    }
            }
        }
    }

    fun closeChat() {
        _state.update { it.copy(chatOpen = false, chatError = null) }
    }

    fun clearChatError() {
        _state.update { it.copy(chatError = null) }
    }

    fun openHandoverSheet() {
        _state.update { it.copy(showHandoverSheet = true, message = null) }
    }

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
        val curOrder = current.order
        if (!current.canSendChat || curOrder == null) {
            _state.update { it.copy(chatError = "Chat closed — order already completed") }
            return
        }
        if (current.sendingChat) return

        val clientMsgId = UUID.randomUUID().toString()
        val optimistic = OrderMessage(
            id = clientMsgId,
            orderId = orderId,
            senderRole = "PICKER",
            senderId = null,
            presetCode = presetCode,
            body = trimmed,
            createdAt = System.currentTimeMillis(),
        )
        // Optimistic SENDING bubble — replaced by the LAN/BLE message or removed on failure.
        _state.update { s ->
            s.copy(
                sendingChat = true,
                chatError = null,
                chatMessages = (s.chatMessages + optimistic).sortedBy { it.createdAt },
                pendingChatIds = s.pendingChatIds + clientMsgId,
            )
        }
        viewModelScope.launch {
            try {
                val staffId = container.settings.staffId.first().ifBlank { null }
                val meshKey = runCatching { container.settings.meshKey.first() }.getOrDefault("")
                val result = container.transportRouter.sendQuickMessage(
                    orderId = orderId,
                    senderRole = "PICKER",
                    senderId = staffId,
                    presetCode = presetCode,
                    body = trimmed,
                    coach = curOrder.coach,
                    berth = curOrder.berth,
                    meshKey = meshKey,
                )
                _state.update { s ->
                    s.copy(
                        sendingChat = false,
                        pendingChatIds = s.pendingChatIds - clientMsgId,
                        chatMessages = s.chatMessages.filterNot { it.id == clientMsgId },
                    )
                }
                mergeChatMessages(listOf(result.message), markBaselineOnly = false)
                showBannerIfRequested(trimmed, showBanner)
            } catch (e: HttpException) {
                val msg = if (e.code() == 409) {
                    "Chat closed — order already completed"
                } else {
                    "Could not send message (HTTP ${e.code()})"
                }
                Log.w(TAG, "picker quick-chat failed: HTTP ${e.code()}", e)
                failChatSend(clientMsgId, msg)
                if (e.code() == 409) refreshOnce()
            } catch (e: QuickChatUnavailableException) {
                Log.w(TAG, "picker quick-chat unavailable: ${e.message}")
                failChatSend(clientMsgId, e.message ?: "Message could not be sent")
            } catch (e: Exception) {
                Log.w(TAG, "picker quick-chat failed", e)
                failChatSend(clientMsgId, "Message could not be sent — check connection")
            }
        }
    }

    private fun failChatSend(clientMsgId: String, message: String) {
        _state.update { s ->
            s.copy(
                sendingChat = false,
                pendingChatIds = s.pendingChatIds - clientMsgId,
                chatMessages = s.chatMessages.filterNot { it.id == clientMsgId },
                chatError = message,
            )
        }
    }

    private fun showBannerIfRequested(trimmed: String, showBanner: Boolean) {
        if (!showBanner) return
        _state.update {
            it.copy(quickUpdateBanner = "Sent: $trimmed")
        }
        viewModelScope.launch {
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

    private companion object {
        const val TAG = "RailBiteChat"
    }
}
