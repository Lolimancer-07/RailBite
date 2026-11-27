package com.railbite.core.mqtt

import com.railbite.core.util.Validators

/**
 * MQTT topic scheme (packages/shared/src/topics.ts):
 *   rb/train/{trainNumber}/orders/{orderId}/status    — retained status updates (passenger)
 *   rb/train/{trainNumber}/orders/{orderId}/chat      — quick-chat messages (passenger & picker)
 *   rb/train/{trainNumber}/captain/{staffId}/alerts   — new-order alerts (picker)
 */
object RailBiteTopics {
    const val DEFAULT_TRAIN_NUMBER = "12345"

    fun orderStatus(trainNumber: String, orderId: String): String =
        "rb/train/${Validators.normalizeTrainNumber(trainNumber)}/orders/$orderId/status"

    fun orderChat(trainNumber: String, orderId: String): String =
        "rb/train/${Validators.normalizeTrainNumber(trainNumber)}/orders/$orderId/chat"

    fun captainAlerts(trainNumber: String, staffId: String): String =
        "rb/train/${Validators.normalizeTrainNumber(trainNumber)}/captain/$staffId/alerts"
}
