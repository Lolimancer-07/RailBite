package com.railbite.customer

import android.util.Base64
import com.railbite.core.api.ApiClient
import com.railbite.core.data.RailBiteSettings
import com.railbite.core.mqtt.MqttConnector
import com.railbite.core.mqtt.RailBiteTopics
import com.railbite.core.model.MqttOrderStatus
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

/**
 * Passenger MQTT link. Client id "p-..." satisfies the broker ACL (passengers may
 * subscribe to rb/{tripId}/orders/{orderId}/status). One connector per process; per-order status
 * subscriptions are tracked in [orderStatuses].
 */
class CustomerMqtt(private val settings: RailBiteSettings) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()

    private var connector: MqttConnector? = null
    private var tripId: String = RailBiteTopics.DEFAULT_TRIP_ID

    /** orderId -> latest retained/live status payload. */
    private val _orderStatuses = MutableStateFlow<Map<String, MqttOrderStatus>>(emptyMap())
    val orderStatuses: StateFlow<Map<String, MqttOrderStatus>> = _orderStatuses.asStateFlow()

    val connected: StateFlow<Boolean>
        get() = connector?.connected ?: MutableStateFlow(false)

    /** Connects (idempotent) using host + port derived from the saved server URL. */
    suspend fun ensureConnected() {
        val serverUrl = settings.serverUrl.first()
        if (serverUrl.isBlank()) return
        mutex.withLock {
            if (connector != null) return
            val clientId = "p-" + randomId()
            val c = MqttConnector.fromServerUrl(serverUrl, clientId)
            connector = c
            scope.launch {
                c.start()
            }
        }
    }

    suspend fun fetchTripId(): String {
        val serverUrl = settings.serverUrl.first()
        if (serverUrl.isBlank()) return tripId
        return try {
            ApiClient.testConnection(serverUrl).tripId
        } catch (_: Exception) {
            tripId
        }
    }

    /** Subscribes to an order's status topic and returns the shared status flow. */
    suspend fun watchOrder(orderId: String) {
        ensureConnected()
        val c = connector ?: return
        c.subscribe(RailBiteTopics.orderStatus(tripId, orderId)) { payload ->
            runCatching {
                val status = json.decodeFromString<MqttOrderStatus>(payload.decodeToString())
                _orderStatuses.update { it + (orderId to status) }
            }
        }
    }

    fun unwatchOrder(orderId: String) {
        connector?.unsubscribe(RailBiteTopics.orderStatus(tripId, orderId))
        _orderStatuses.update { it - orderId }
    }

    fun disconnect() {
        connector?.stop()
        connector = null
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
