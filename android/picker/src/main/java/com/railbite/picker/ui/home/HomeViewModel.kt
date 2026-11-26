package com.railbite.picker.ui.home

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.api.ApiClient
import com.railbite.core.api.RailBiteApi
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.model.Order
import com.railbite.core.model.OrderMessage
import com.railbite.core.model.PickerStats
import com.railbite.core.util.Statuses
import com.railbite.picker.PickerContainer
import com.railbite.picker.alert.AlertForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class HomeUiState(
    val staffId: String = "",
    val staffName: String = "",
    val zone: String = "",
    val online: Boolean = false,
    val serviceRunning: Boolean = false,
    val mqttConnected: Boolean = false,
    val serverReachable: Boolean = false,
    val serverUrl: String = "",
    val serverUrlStored: String = "",
    val mqttWsUrlStored: String = "",
    val stats: PickerStats? = null,
    val activeOrders: List<Order> = emptyList(),
    val unreadCounts: Map<String, Int> = emptyMap(),
    val latestCustomerMessages: Map<String, OrderMessage> = emptyMap(),
    val lastSyncedLabel: String = "",
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val activeOrder: Order? get() = activeOrders.firstOrNull()
    val connectionConnected: Boolean get() = mqttConnected || serverReachable
}

class HomeViewModel(private val container: PickerContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val restMessages = MutableStateFlow<Map<String, List<OrderMessage>>>(emptyMap())
    private val seenLatestMessageIds = MutableStateFlow<Map<String, String>>(emptyMap())

    private var refreshJob: Job? = null
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        viewModelScope.launch {
            val staffId = container.settings.staffId.first()
            _state.update {
                it.copy(
                    staffId = staffId,
                    staffName = container.settings.staffName.first(),
                    zone = container.settings.staffZone.first(),
                    serverUrl = container.settings.serverUrl.first(),
                    serverUrlStored = container.settings.serverUrlStored.first(),
                    mqttWsUrlStored = container.settings.mqttWsUrl.first(),
                )
            }
            if (staffId.isNotBlank()) {
                container.chatMqtt.ensureConnected(staffId)
            }
        }
        viewModelScope.launch {
            container.settings.serverUrl.collect { url ->
                _state.update { it.copy(serverUrl = url) }
            }
        }
        viewModelScope.launch {
            container.settings.serverUrlStored.collect { stored ->
                _state.update { it.copy(serverUrlStored = stored) }
            }
        }
        viewModelScope.launch {
            container.settings.mqttWsUrl.collect { wsUrl ->
                _state.update { it.copy(mqttWsUrlStored = wsUrl) }
            }
        }
        viewModelScope.launch {
            container.chatMqtt.connected.collect { connected ->
                _state.update { it.copy(mqttConnected = connected) }
            }
        }
        viewModelScope.launch {
            container.settings.online.collect { online ->
                _state.update { it.copy(online = online) }
            }
        }
        viewModelScope.launch {
            AlertForegroundService.running.collect { running ->
                _state.update { it.copy(serviceRunning = running) }
            }
        }
        viewModelScope.launch {
            combine(
                restMessages,
                container.chatMqtt.orderMessages,
                seenLatestMessageIds,
            ) { rest, mqtt, seen ->
                val allOrderIds = (rest.keys + mqtt.keys).distinct()
                val latestMap = mutableMapOf<String, OrderMessage>()
                val unreadMap = mutableMapOf<String, Int>()
                allOrderIds.forEach { id ->
                    val merged = (rest[id].orEmpty() + mqtt[id].orEmpty())
                        .distinctBy { it.id }
                        .sortedBy { it.createdAt }
                    val latestCustomer = merged.lastOrNull {
                        it.senderRole.equals("CUSTOMER", ignoreCase = true)
                    }
                    if (latestCustomer != null) {
                        latestMap[id] = latestCustomer
                        if (seen[id] == latestCustomer.id) {
                            unreadMap[id] = 0
                        } else {
                            val lastPickerIdx = merged.indexOfLast {
                                it.senderRole.equals("PICKER", ignoreCase = true)
                            }
                            val unread = if (lastPickerIdx >= 0) {
                                merged.drop(lastPickerIdx + 1).count {
                                    it.senderRole.equals("CUSTOMER", ignoreCase = true)
                                }
                            } else {
                                merged.count {
                                    it.senderRole.equals("CUSTOMER", ignoreCase = true)
                                }
                            }
                            if (unread > 0) unreadMap[id] = unread
                        }
                    }
                }
                latestMap to unreadMap
            }.collect { (latestMap, unreadMap) ->
                _state.update {
                    it.copy(
                        latestCustomerMessages = latestMap,
                        unreadCounts = unreadMap,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                loadOnce()
                delay(5_000)
            }
        }
    }

    fun forceRefresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            loadOnce()
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun markOrderChatSeen(orderId: String) {
        val latest = _state.value.latestCustomerMessages[orderId]?.id ?: return
        seenLatestMessageIds.update { it + (orderId to latest) }
    }

    private suspend fun loadOnce() {
        try {
            val api = container.client.api()
            val staffId = container.settings.staffId.first()
            val stats = if (staffId.isNotBlank()) api.staffStats(staffId) else null

            val queueResult = if (staffId.isNotBlank()) {
                runCatching { api.staffOrders(staffId) }
            } else {
                Result.success(emptyList())
            }

            val activeList: List<Order> = if (queueResult.isSuccess) {
                queueResult.getOrDefault(emptyList()).filter { !Statuses.isTerminal(it.status) }
            } else {
                // Backward-compatible fallback for older servers without GET /staff/:id/orders
                val activeId = container.settings.pickerOrderId.first()
                if (activeId.isNotBlank()) {
                    val order = runCatching { api.order(activeId, role = "picker") }.getOrNull()
                    if (order != null && !Statuses.isTerminal(order.status)) {
                        listOf(order)
                    } else {
                        if (order != null) container.settings.setPickerOrderId("")
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

            val storedOrderId = container.settings.pickerOrderId.first()
            if (activeList.isEmpty() && storedOrderId.isNotBlank() && queueResult.isSuccess) {
                container.settings.setPickerOrderId("")
            } else if (activeList.isNotEmpty() && storedOrderId.isBlank()) {
                container.settings.setPickerOrderId(activeList.first().id)
            }

            if (staffId.isNotBlank() && activeList.isNotEmpty()) {
                val msgSnapshot = mutableMapOf<String, List<OrderMessage>>()
                activeList.forEach { order ->
                    container.chatMqtt.watchOrder(order.id, staffId)
                    runCatching { api.orderMessages(order.id) }.getOrNull()?.let { msgs ->
                        msgSnapshot[order.id] = msgs
                    }
                }
                restMessages.value = msgSnapshot
            } else if (activeList.isEmpty()) {
                restMessages.value = emptyMap()
            }

            _state.update {
                it.copy(
                    stats = stats,
                    activeOrders = activeList,
                    lastSyncedLabel = LocalTime.now().format(timeFormatter),
                    serverReachable = true,
                    loading = false,
                    error = null,
                )
            }
        } catch (_: Exception) {
            _state.update {
                it.copy(
                    serverReachable = false,
                    loading = false,
                    error = "Cannot reach server — retrying",
                )
            }
        }
    }

    /** ONLINE/OFFLINE toggle: starts/stops the alert foreground service and tells the server. */
    fun setOnline(online: Boolean) {
        val app = container.app
        if (online) {
            ContextCompat.startForegroundService(
                app,
                Intent(app, AlertForegroundService::class.java).setAction(AlertForegroundService.ACTION_START),
            )
        } else {
            app.startService(
                Intent(app, AlertForegroundService::class.java).setAction(AlertForegroundService.ACTION_STOP),
            )
        }
        viewModelScope.launch {
            container.settings.setOnline(online)
            runCatching {
                container.client.api().setStaffOnline(
                    container.settings.staffId.first(),
                    RailBiteApi.OnlineRequest(online),
                )
            }
        }
    }

    /** Fake alert so the picker can test the loud alarm without a real passenger order. */
    fun fakeAlert(): MqttOrderAlert = MqttOrderAlert(
        orderId = "test-" + System.currentTimeMillis(),
        coach = "B2",
        berth = 14,
        items = listOf(
            MqttOrderAlert.AlertItem(name = "Packed Veg Biryani", qty = 2, unitPricePaise = 9_000),
            MqttOrderAlert.AlertItem(name = "Masala Chai", qty = 1, unitPricePaise = 1_500),
        ),
        total = 19_500,
        track = "READY",
        attempt = 1,
        expiresAt = Instant.now().plusSeconds(60).toString(),
        pnr = "8492000014",
        diners = 2,
        verified = 1,
    )

    fun updateServerUrl(rawUrl: String) {
        updateServerConfig(rawUrl, _state.value.mqttWsUrlStored)
    }

    fun updateServerConfig(rawServerUrl: String, rawMqttWsUrl: String) {
        val normalized = ApiClient.normalizeServerUrl(rawServerUrl)
        viewModelScope.launch {
            container.settings.setServerUrl(normalized)
            container.settings.setMqttWsUrl(rawMqttWsUrl.trim())
            val effective = container.settings.serverUrl.first()
            val staffId = container.settings.staffId.first()
            _state.update {
                it.copy(
                    serverUrl = effective,
                    serverUrlStored = normalized,
                    mqttWsUrlStored = rawMqttWsUrl.trim(),
                    serverReachable = false,
                    loading = true,
                    error = null,
                )
            }
            if (staffId.isNotBlank()) {
                container.chatMqtt.reconnect(staffId)
            }
            if (_state.value.online) {
                ContextCompat.startForegroundService(
                    container.app,
                    Intent(container.app, AlertForegroundService::class.java)
                        .setAction(AlertForegroundService.ACTION_RECONNECT),
                )
            }
            refresh()
        }
    }

    /** Signs out: revokes the server session and clears the stored login. */
    fun logout(onDone: () -> Unit) {
        _state.update { it.copy(online = false) }
        container.chatMqtt.disconnect()
        container.app.startService(
            Intent(container.app, AlertForegroundService::class.java)
                .setAction(AlertForegroundService.ACTION_STOP),
        )
        viewModelScope.launch {
            container.auth.logout()
            container.settings.clearStaff()
            onDone()
        }
    }
}
