package com.railbite.core.mqtt

import java.util.concurrent.ConcurrentHashMap

/**
 * MQTT topic scheme (packages/shared/src/topics.ts):
 *   rb/{tripId}/orders/{orderId}/status        — retained status updates (passenger)
 *   rb/{tripId}/captain/{captainId}/alerts     — new-order alerts (picker)
 */
object RailBiteTopics {
    const val DEFAULT_TRIP_ID = "TRIP_001"

    fun orderStatus(tripId: String, orderId: String): String =
        "rb/$tripId/orders/$orderId/status"

    fun orderChat(tripId: String, orderId: String): String =
        "rb/$tripId/orders/$orderId/chat"

    fun captainAlerts(tripId: String, captainId: String): String =
        "rb/$tripId/captain/$captainId/alerts"
}
