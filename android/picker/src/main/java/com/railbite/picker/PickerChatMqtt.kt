package com.railbite.picker

import com.railbite.core.api.ApiClient
import com.railbite.core.data.RailBiteSettings
import com.railbite.core.mesh.ChatTransport
import com.railbite.core.mesh.OrderChatRepository
import com.railbite.core.model.OrderMessage
import com.railbite.core.mqtt.MqttConnector
import com.railbite.core.mqtt.RailBiteTopics
import com.railbite.core.util.Validators
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
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated MQTT client for Picker connectivity state and order-scoped quick chat.
 *
 * Uses client ID `"c-$staffId-chat"` so it satisfies the broker's `"c-"` ACL
 * (`rb/train/{trainNumber}/orders/{orderId}/chat`) without evicting [AlertForegroundService]'s
 * `"c-$staffId"` alert session.
 */
class PickerChatMqtt(private val settings: RailBiteSettings) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()

    private var connector: MqttConnector? = null
    private var activeTrainNumber: String = RailBiteTopics.DEFAULT_TRAIN_NUMBER
    private var currentStaffId: String = ""
    private val watchedOrderTrains = ConcurrentHashMap<String, String>()

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

    suspend fun reconnect(staffId: String, trainNumberOverride: String? = null) {
        val cleanStaff = staffId.trim().lowercase()
        val serverUrl = settings.serverUrl.first()
        mutex.withLock {
            connector?.stop()
            connector = null
            _connected.value = false
            if (cleanStaff.isBlank() || serverUrl.isBlank()) return
            connectLocked(cleanStaff, serverUrl, trainNumberOverride)
            watchedOrderTrains.forEach { (orderId, train) ->
                subscribeOrderLocked(orderId, train)
            }
        }
    }

    suspend fun watchOrder(orderId: String, staffId: String, trainNumberOverride: String? = null) {
        val cleanStaff = staffId.trim().lowercase()
        if (orderId.isBlank() || cleanStaff.isBlank()) return
        val serverUrl = settings.serverUrl.first()
        if (serverUrl.isBlank()) return
        mutex.withLock {
            if (connector == null || currentStaffId != cleanStaff) {
                connectLocked(cleanStaff, serverUrl, trainNumberOverride)
            }
            val trainNumber = trainNumberOverride
                ?.let { Validators.normalizeTrainNumber(it) }
                ?.takeIf { Validators.isValidTrainNumber(it) }
                ?: activeTrainNumber
            val previous = watchedOrderTrains.put(orderId, trainNumber)
            if (previous != null && previous != trainNumber) {
                connector?.unsubscribe(RailBiteTopics.orderChat(previous, orderId))
            }
            subscribeOrderLocked(orderId, trainNumber)
        }
    }

    fun unwatchOrder(orderId: String) {
        scope.launch {
            mutex.withLock {
                val train = watchedOrderTrains.remove(orderId) ?: activeTrainNumber
                connector?.unsubscribe(RailBiteTopics.orderChat(train, orderId))
                _orderMessages.update { it - orderId }
                OrderChatRepository.clear(orderId)
            }
        }
    }

    fun disconnect() {
        scope.launch {
            mutex.withLock {
                connector?.stop()
                connector = null
                currentStaffId = ""
                watchedOrderTrains.clear()
                _connected.value = false
                _orderMessages.value = emptyMap()
            }
        }
    }

    private suspend fun connectLocked(
        staffId: String,
        serverUrl: String,
        trainNumberOverride: String? = null,
    ) {
        connector?.stop()
        currentStaffId = staffId
        val wsOverride = settings.mqttWsUrl.first()
        activeTrainNumber = trainNumberOverride
            ?.let { Validators.normalizeTrainNumber(it) }
            ?.takeIf { Validators.isValidTrainNumber(it) }
            ?: Validators.normalizeTrainNumber(settings.activeTrainNumber.first())
                .takeIf { Validators.isValidTrainNumber(it) }
            ?: RailBiteTopics.DEFAULT_TRAIN_NUMBER
        val c = MqttConnector.fromServerUrl(
            serverUrl = serverUrl,
            clientId = "c-$staffId-chat",
            mqttWsUrlOverride = wsOverride,
            onConnected = { _connected.value = true },
            onDisconnected = { _connected.value = false },
        )
        connector = c
        scope.launch { c.start() }
    }

    private suspend fun subscribeOrderLocked(orderId: String, trainNumber: String) {
        val c = connector ?: return
        c.subscribe(RailBiteTopics.orderChat(trainNumber, orderId)) { payload ->
            runCatching {
                val msg = json.decodeFromString<OrderMessage>(payload.decodeToString())
                OrderChatRepository.ingest(orderId, msg, ChatTransport.LAN)
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

