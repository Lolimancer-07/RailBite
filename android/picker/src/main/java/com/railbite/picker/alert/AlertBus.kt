package com.railbite.picker.alert

import com.railbite.core.model.MqttOrderAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process bus between the foreground service (which receives MQTT alerts) and the
 * AlertActivity / Home screen. Holds the currently pending alert.
 */
object AlertBus {

    private val _alert = MutableStateFlow<MqttOrderAlert?>(null)
    val alert: StateFlow<MqttOrderAlert?> = _alert.asStateFlow()

    /** Set when the picker accepted an order — used to route MainActivity to the progress screen. */
    private val _acceptedOrderId = MutableStateFlow<String?>(null)
    val acceptedOrderId: StateFlow<String?> = _acceptedOrderId.asStateFlow()

    fun post(alert: MqttOrderAlert) {
        _alert.value = alert
    }

    fun current(): MqttOrderAlert? = _alert.value

    fun clear() {
        _alert.value = null
    }

    fun markAccepted(orderId: String) {
        _acceptedOrderId.value = orderId
    }
}
