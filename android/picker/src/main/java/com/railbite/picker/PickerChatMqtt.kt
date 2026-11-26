package com.railbite.picker

import com.railbite.core.api.ApiClient
import com.railbite.core.data.RailBiteSettings
import com.railbite.core.model.OrderMessage
import com.railbite.core.mqtt.MqttConnector
import com.railbite.core.mqtt.RailBiteTopics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Dedicated MQTT client for Picker connectivity state and order-scoped quick chat.
 *
 * Uses client ID `"c-$staffId-chat"` so it satisfies the broker's `"c-"` ACL
 * (`rb/{tripId}/orders/{orderId}/chat`) without evicting [RailBiteAlertService]'s
 * `"c-$staffId"` alert session.
 */
class PickerChatMqtt(private val settings: RailBiteSettings) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()

    private var connector: MqttConnector? = null
    private var tripId: String = RailBiteTopics.DEFAULT_TRIP_ID
    private var currentStaffId: String = ""
    private val watchedOrderIds = mutableSetOf<String>()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _orderMessages = MutableStateFlow<Map<String, List<OrderMessage>>>(emptyMap())
    val orderMessages: StateFlow<Map<String, List<OrderMessage>>> = _orderMessages.asStateFlow()

    suspend fun ensureConnected(staffId: String) {
        val cleanStaff = staffId.trim().lowercase()
        val serverUrl = settings.serverUrl.first()
        if (cleanStaff.isBlank() || serverUrl.isBlank()) return
        mutex.withLock {
            if (connector != null && currentStaffId == cleanStaff) return
            connectLocked(cleanStaff, serverUrl)
        }
    }

    suspend fun reconnect(staffId: String) {
        val cleanStaff = staffId.trim().lowercase()
        val serverUrl = settings.serverUrl.first()
        mutex.withLock {
            connector?.stop()
            connector = null
            _connected.value = false
            if (cleanStaff.isBlank() || serverUrl.isBlank()) return
            connectLocked(cleanStaff, serverUrl)
            watchedOrderIds.forEach { orderId ->
                subscribeOrderLocked(orderId)
            }
        }
    }

    suspend fun watchOrder(orderId: String, staffId: String) {
        val cleanStaff = staffId.trim().lowercase()
        if (orderId.isBlank() || cleanStaff.isBlank()) return
        val serverUrl = settings.serverUrl.first()
        if (serverUrl.isBlank()) return
        mutex.withLock {
            watchedOrderIds.add(orderId)
            if (connector == null || currentStaffId != cleanStaff) {
                connectLocked(cleanStaff, serverUrl)
            }
            subscribeOrderLocked(orderId)
        }
    }

    fun unwatchOrder(orderId: String) {
        scope.launch {
            mutex.withLock {
                watchedOrderIds.remove(orderId)
                connector?.unsubscribe(RailBiteTopics.orderChat(tripId, orderId))
                _orderMessages.update { it - orderId }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            mutex.withLock {
                connector?.stop()
                connector = null
                currentStaffId = ""
                watchedOrderIds.clear()
                _connected.value = false
                _orderMessages.value = emptyMap()
            }
        }
    }

    private suspend fun connectLocked(staffId: String, serverUrl: String) {
        connector?.stop()
        currentStaffId = staffId
        tripId = withTimeoutOrNull(1_500L) {
            runCatching { ApiClient.testConnection(serverUrl).tripId }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: RailBiteTopics.DEFAULT_TRIP_ID
        val c = MqttConnector.fromServerUrl(
            serverUrl = serverUrl,
            clientId = "c-$staffId-chat",
            onConnected = { _connected.value = true },
            onDisconnected = { _connected.value = false },
        )
        connector = c
        scope.launch { c.start() }
    }

    private suspend fun subscribeOrderLocked(orderId: String) {
        val c = connector ?: return
        c.subscribe(RailBiteTopics.orderChat(tripId, orderId)) { payload ->
            runCatching {
                val msg = json.decodeFromString<OrderMessage>(payload.decodeToString())
                _orderMessages.update { prev ->
                    val current = prev[orderId].orEmpty()
                    if (current.any { it.id == msg.id }) {
                        prev
                    } else {
                        prev + (orderId to (current + msg).sortedBy { it.createdAt })
                    }
                }
            }
        }
    }
}

