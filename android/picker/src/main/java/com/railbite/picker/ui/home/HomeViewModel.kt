package com.railbite.picker.ui.home

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.api.ApiClient
import com.railbite.core.api.RailBiteApi
import com.railbite.core.data.OfflineMenuCatalog
import com.railbite.core.mesh.BleMeshHub
import com.railbite.core.mesh.BleMeshService
import com.railbite.core.mesh.BlePacketCodec
import com.railbite.core.mesh.BlePermissions
import com.railbite.core.mesh.MeshEntry
import com.railbite.core.mesh.MeshOrderPayload
import com.railbite.core.mesh.OrderChatRepository
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.model.Order
import com.railbite.core.model.OrderItem
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
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
    val meshKey: String = BlePacketCodec.RAILBITE_MESH_KEY,
    val meshDebugEnabled: Boolean = false,
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

    private val restOrders = MutableStateFlow<List<Order>>(emptyList())
    private val restStats = MutableStateFlow<PickerStats?>(null)
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
                    meshKey = container.settings.meshKey.first(),
                )
            }
            if (staffId.isNotBlank() && container.transportRouter.checkLanReachable()) {
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
            container.settings.meshKey.collect { key ->
                _state.update { it.copy(meshKey = key) }
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

        // Merge REST + live offline BLE Mesh active orders & delivered cash KPIs
        viewModelScope.launch {
            combine(
                restOrders,
                restStats,
                container.meshLedger.entries,
            ) { serverActive, serverStats, meshEntries ->
                val catalog = OfflineMenuCatalog.bundledEntries(container.app)
                val meshActive = meshEntries
                    .filter { it.state in ACTIVE_MESH_STATES }
                    .mapNotNull { entryToOrder(it, catalog) }
                val mergedOrders = (meshActive + serverActive)
                    .distinctBy { it.id }

                val unsyncedDelivered = meshEntries.filter { it.state == Statuses.DELIVERED }
                val extraDeliveredCount = unsyncedDelivered.size
                val extraCollectedPaise = unsyncedDelivered.sumOf { entry ->
                    entry.cashPaise ?: MeshOrderPayload.decode(entry.payload)?.totalPaise ?: 0
                }
                val extraConfirmedPaise = unsyncedDelivered
                    .filter { it.passengerConfirmed }
                    .sumOf { entry -> entry.cashPaise ?: MeshOrderPayload.decode(entry.payload)?.totalPaise ?: 0 }

                val st = _state.value
                val mergedStats = if (serverStats != null) {
                    serverStats.copy(
                        ordersDelivered = serverStats.ordersDelivered + extraDeliveredCount,
                        expectedPaise = serverStats.expectedPaise + extraCollectedPaise,
                        collectedPaise = serverStats.collectedPaise + extraCollectedPaise,
                        passengerConfirmedPaise = serverStats.passengerConfirmedPaise + extraConfirmedPaise,
                    )
                } else {
                    PickerStats(
                        staffId = st.staffId,
                        staffName = st.staffName.ifBlank { st.staffId.uppercase() },
                        date = LocalDate.now().toString(),
                        ordersDelivered = extraDeliveredCount,
                        expectedPaise = extraCollectedPaise,
                        collectedPaise = extraCollectedPaise,
                        passengerConfirmedPaise = extraConfirmedPaise,
                    )
                }
                mergedOrders to mergedStats
            }.flowOn(Dispatchers.Default).collect { (mergedOrders, mergedStats) ->
                _state.update {
                    it.copy(
                        activeOrders = mergedOrders,
                        stats = mergedStats,
                    )
                }
            }
        }

        // Merge REST + single shared chat repository (MQTT + BLE 0x13 land in OrderChatRepository).
        viewModelScope.launch {
            combine(
                restMessages,
                OrderChatRepository.messages,
                seenLatestMessageIds,
            ) { rest, chat, seen ->
                val allOrderIds = (rest.keys + chat.keys).distinct()
                val latestMap = mutableMapOf<String, OrderMessage>()
                val unreadMap = mutableMapOf<String, Int>()
                allOrderIds.forEach { id ->
                    val merged = (rest[id].orEmpty() + chat[id].orEmpty())
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
            }.flowOn(Dispatchers.Default).collect { (latestMap, unreadMap) ->
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
                withContext(Dispatchers.IO) { loadOnce() }
                delay(5_000)
            }
        }
    }

    fun forceRefresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            if (container.transportRouter.checkLanReachable()) {
                runCatching { container.meshGateway.replayPending() }
            }
            loadOnce()
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun markOrderChatSeen(orderId: String) {
        val latest = _state.value.latestCustomerMessages[orderId]?.id ?: return
        seenLatestMessageIds.update { it + (orderId to latest) }
    }

    fun setMeshDebugEnabled(enabled: Boolean) {
        _state.update { it.copy(meshDebugEnabled = enabled) }
    }

    fun updateMeshKey(newKey: String) {
        val clean = newKey.trim().take(32)
        viewModelScope.launch {
            container.settings.setMeshKey(clean)
        }
    }

    private suspend fun loadOnce() {
        val lanReachable = container.transportRouter.checkLanReachable()
        if (!lanReachable) {
            restOrders.value = emptyList()
            _state.update {
                it.copy(
                    serverReachable = false,
                    loading = false,
                    error = null,
                )
            }
            return
        }
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

            restStats.value = stats
            restOrders.value = activeList

            _state.update {
                it.copy(
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
                    error = null,
                )
            }
        }
    }

    /** ONLINE/OFFLINE toggle: starts/stops alert foreground service & LOW_LATENCY BLE scanning. */
    fun setOnline(online: Boolean) {
        val app = container.app
        if (online) {
            ContextCompat.startForegroundService(
                app,
                Intent(app, AlertForegroundService::class.java).setAction(AlertForegroundService.ACTION_START),
            )
            if (BlePermissions.isReadyForMesh(app)) {
                BleMeshService.start(app, lowLatency = true)
                container.meshGateway.start(dispatchAlerts = true)
            }
        } else {
            app.startService(
                Intent(app, AlertForegroundService::class.java).setAction(AlertForegroundService.ACTION_STOP),
            )
            container.meshGateway.stop()
        }
        viewModelScope.launch {
            container.settings.setOnline(online)
            if (container.transportRouter.checkLanReachable()) {
                runCatching {
                    container.client.api().setStaffOnline(
                        container.settings.staffId.first(),
                        RailBiteApi.OnlineRequest(online),
                    )
                }
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
            if (staffId.isNotBlank() && container.transportRouter.checkLanReachable()) {
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

    /** Signs out: revokes server session, stops alert & BLE dispatch, and clears stored login. */
    fun logout(onDone: () -> Unit) {
        _state.update { it.copy(online = false) }
        container.chatMqtt.disconnect()
        container.meshGateway.stop()
        BleMeshService.stop(container.app)
        container.app.startService(
            Intent(container.app, AlertForegroundService::class.java)
                .setAction(AlertForegroundService.ACTION_STOP),
        )
        viewModelScope.launch {
            runCatching { container.auth.logout() }
            container.settings.clearStaff()
            onDone()
        }
    }

    private fun entryToOrder(
        entry: MeshEntry,
        catalog: List<OfflineMenuCatalog.CatalogEntry>,
    ): Order? {
        val payload = MeshOrderPayload.decode(entry.payload) ?: return null
        val items = payload.items.map { item ->
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
        return Order(
            id = payload.orderId,
            pnr = payload.pnr,
            coach = payload.coach,
            berth = payload.berth,
            diners = payload.diners,
            track = "READY",
            status = entry.state,
            paymentMode = "COD",
            totalPaise = payload.totalPaise,
            source = "RELAY",
            verified = 1,
            createdAt = payload.createdAt,
            updatedAt = entry.createdAt,
            deliveryCode = entry.deliveryCode ?: payload.deliveryCode,
            items = items,
        )
    }

    companion object {
        private val ACTIVE_MESH_STATES = setOf(
            Statuses.ACCEPTED,
            Statuses.PACKING,
            Statuses.OUT_FOR_DELIVERY,
        )
    }
}
