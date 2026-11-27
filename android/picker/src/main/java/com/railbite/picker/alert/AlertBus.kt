package com.railbite.picker.alert

import com.railbite.core.model.MqttOrderAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process bus between the foreground service (MQTT alerts or 23-byte BLE mesh orders)
 * and IncomingOrderActivity / PickerDashboardScreen.
 */
object AlertBus {

    const val SOURCE_CLOUD = "CLOUD"
    const val SOURCE_MESH = "MESH"

    private val _alert = MutableStateFlow<MqttOrderAlert?>(null)
    val alert: StateFlow<MqttOrderAlert?> = _alert.asStateFlow()

    private val _source = MutableStateFlow(SOURCE_CLOUD)
    val source: StateFlow<String> = _source.asStateFlow()

    private val _hopCount = MutableStateFlow<Int?>(null)
    val hopCount: StateFlow<Int?> = _hopCount.asStateFlow()

    /** Set when the picker accepted an order — used to route MainActivity to the progress screen. */
    private val _acceptedOrderId = MutableStateFlow<String?>(null)
    val acceptedOrderId: StateFlow<String?> = _acceptedOrderId.asStateFlow()

    fun post(
        alert: MqttOrderAlert,
        source: String = SOURCE_CLOUD,
        hopCount: Int? = null,
    ) {
        _source.value = source
        _hopCount.value = hopCount
        _alert.value = alert
    }

    fun current(): MqttOrderAlert? = _alert.value
    fun currentSource(): String = _source.value
    fun currentHopCount(): Int? = _hopCount.value

    fun clear() {
        _alert.value = null
        _source.value = SOURCE_CLOUD
        _hopCount.value = null
    }

    /** Auto-dismisses the currently ringing alert when another picker claims [orderId] on BLE. */
    fun dismissIfOrder(orderId: String): Boolean {
        if (_alert.value?.orderId == orderId) {
            clear()
            return true
        }
        return false
    }

    fun markAccepted(orderId: String) {
        _acceptedOrderId.value = orderId
    }

    /** Consumed by MainActivity right after routing — keeps old orders from resurfacing. */
    fun clearAccepted() {
        _acceptedOrderId.value = null
    }
}
