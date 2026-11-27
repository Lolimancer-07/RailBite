package com.railbite.customer

import android.util.Base64
import com.railbite.core.api.ApiClient
import com.railbite.core.data.RailBiteSettings
import com.railbite.core.mesh.ChatTransport
import com.railbite.core.mesh.OrderChatRepository
import com.railbite.core.mqtt.MqttConnector
import com.railbite.core.mqtt.RailBiteTopics
import com.railbite.core.model.MqttOrderStatus
import com.railbite.core.model.OrderMessage
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
 * Passenger MQTT link. Client id "p-..." satisfies the broker ACL (passengers may
 * subscribe to rb/train/{trainNumber}/orders/{orderId}/status and /chat). One connector
 * per process; per-order status & chat subscriptions are tracked in [orderStatuses].
 */
class CustomerMqtt(private val settings: RailBiteSettings) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()

    private var connector: MqttConnector? = null
    private val watchedOrderTrains = ConcurrentHashMap<String, String>()

    /** orderId -> latest retained/live status payload. */
    private val _orderStatuses = MutableStateFlow<Map<String, MqttOrderStatus>>(emptyMap())
    val orderStatuses: StateFlow<Map<String, MqttOrderStatus>> = _orderStatuses.asStateFlow()

    /** orderId -> live quick-chat messages received over MQTT. */
    private val _orderMessages = MutableStateFlow<Map<String, List<OrderMessage>>>(emptyMap())
    val orderMessages: StateFlow<Map<String, List<OrderMessage>>> = _orderMessages.asStateFlow()

    val connected: StateFlow<Boolean>
        get() = connector?.connected ?: MutableStateFlow(false)

    /** Connects (idempotent) using host + port derived from the saved server URL. */
    suspend fun ensureConnected() {
        val serverUrl = settings.serverUrl.first()
        if (serverUrl.isBlank()) return
        mutex.withLock {
            if (connector != null) return
            val wsOverride = settings.mqttWsUrl.first()
            val clientId = "p-" + randomId()
            val c = MqttConnector.fromServerUrl(
                serverUrl = serverUrl,
                clientId = clientId,
                mqttWsUrlOverride = wsOverride,
            )
            connector = c
            scope.launch {
                c.start()
            }
        }
    }

    /** Subscribes to an order's train-scoped status and chat topics. */
    suspend fun watchOrder(orderId: String, trainNumberOverride: String? = null) {
        ensureConnected()
        val c = connector ?: return
        val trainNumber = trainNumberOverride
            ?.trim()
            ?.takeIf { Validators.isValidTrainNumber(it) }
            ?: settings.trainNumber.first().trim().takeIf { Validators.isValidTrainNumber(it) }
            ?: RailBiteTopics.DEFAULT_TRAIN_NUMBER

        val previousTrain = watchedOrderTrains.put(orderId, trainNumber)
        if (previousTrain != null && previousTrain != trainNumber) {
            c.unsubscribe(RailBiteTopics.orderStatus(previousTrain, orderId))
            c.unsubscribe(RailBiteTopics.orderChat(previousTrain, orderId))
        }

        c.subscribe(RailBiteTopics.orderStatus(trainNumber, orderId)) { payload ->
            runCatching {
                val status = json.decodeFromString<MqttOrderStatus>(payload.decodeToString())
                _orderStatuses.update { it + (orderId to status) }
            }
        }
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

    fun unwatchOrder(orderId: String) {
        val trainNumber = watchedOrderTrains.remove(orderId) ?: RailBiteTopics.DEFAULT_TRAIN_NUMBER
        connector?.unsubscribe(RailBiteTopics.orderStatus(trainNumber, orderId))
        connector?.unsubscribe(RailBiteTopics.orderChat(trainNumber, orderId))
        _orderStatuses.update { it - orderId }
        _orderMessages.update { it - orderId }
        OrderChatRepository.clear(orderId)
    }

    fun disconnect() {
        connector?.stop()
        connector = null
        watchedOrderTrains.clear()
        _orderStatuses.update { emptyMap() }
    }

    private fun randomId(): String {
        val bytes = ByteArray(9)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
            .replace("[^A-Za-z0-9]".toRegex(), "")
            .take(12)
    }
}
