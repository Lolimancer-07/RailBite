package com.railbite.picker.alert

import com.railbite.core.model.MqttOrderAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process bus between the foreground service (which receives MQTT alerts or Bluetooth
 * relay packets) and the AlertActivity / Home screen. Holds the currently pending alert.
 */
object AlertBus {

    /** Where the current alert arrived from: the cloud (MQTT) or the Bluetooth relay. */
    const val SOURCE_CLOUD = "CLOUD"
    const val SOURCE_MESH = "MESH"

    private val _alert = MutableStateFlow<MqttOrderAlert?>(null)
    val alert: StateFlow<MqttOrderAlert?> = _alert.asStateFlow()

    private val _source = MutableStateFlow(SOURCE_CLOUD)
    val source: StateFlow<String> = _source.asStateFlow()

    /** Set when the picker accepted an order — used to route MainActivity to the progress screen. */
    private val _acceptedOrderId = MutableStateFlow<String?>(null)
    val acceptedOrderId: StateFlow<String?> = _acceptedOrderId.asStateFlow()

    fun post(alert: MqttOrderAlert, source: String = SOURCE_CLOUD) {
        _source.value = source
        _alert.value = alert
    }

    fun current(): MqttOrderAlert? = _alert.value
    fun currentSource(): String = _source.value

    fun clear() {
        _alert.value = null
        _source.value = SOURCE_CLOUD
    }

    fun markAccepted(orderId: String) {
        _acceptedOrderId.value = orderId
    }
}
