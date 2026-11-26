package com.railbite.picker.fcm

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.railbite.core.model.MqttOrderAlert
import com.railbite.picker.RailBitePickerApp
import com.railbite.picker.alert.AlertBus
import com.railbite.picker.alert.IncomingOrderActivity
import com.railbite.picker.alert.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Primary wake-up service for new orders via Firebase Cloud Messaging.
 * Receives high-priority data messages from the edge server and immediately launches
 * the full-screen alert (loud sound, vibration, countdown, accept/reject).
 */
class PickerFirebaseMessagingService : FirebaseMessagingService() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM registration token generated: ${token.take(12)}...")
        val app = applicationContext as? RailBitePickerApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            FcmTokenRegistrar.registerCurrentToken(app.container, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        Log.i(TAG, "FCM order dispatch data message received: $data")

        val type = data["type"] ?: return
        if (type != "ORDER_DISPATCH") return

        val orderId = data["orderId"] ?: return
        val coach = data["coach"] ?: ""
        val berth = data["berth"]?.toIntOrNull() ?: 1
        val total = data["total"]?.toIntOrNull() ?: 0
        val expiresAt = data["expiresAt"] ?: ""

        val items = runCatching {
            val itemsJson = data["items"] ?: "[]"
            json.decodeFromString<List<MqttOrderAlert.AlertItem>>(itemsJson)
        }.getOrDefault(emptyList())

        val alert = MqttOrderAlert(
            orderId = orderId,
            coach = coach,
            berth = berth,
            items = items,
            total = total,
            track = data["track"] ?: "READY",
            attempt = data["attempt"]?.toIntOrNull() ?: 1,
            expiresAt = expiresAt,
            pnr = data["pnr"] ?: "",
            diners = data["diners"]?.toIntOrNull() ?: 1,
            verified = data["verified"]?.toIntOrNull() ?: 1,
            paymentMode = data["paymentMode"] ?: "COD",
        )

        // 1. Post to in-app event bus
        AlertBus.post(alert)

        // 2. Fire high-priority notification with full-screen intent
        Notifications.notifyAlert(applicationContext, alert)

        // 3. Directly launch IncomingOrderActivity over the lock screen
        runCatching {
            val alertIntent = Intent(applicationContext, IncomingOrderActivity::class.java).apply {
                putExtra(IncomingOrderActivity.EXTRA_ORDER_ID, alert.orderId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(alertIntent)
        }.onFailure { e ->
            Log.w(TAG, "Could not start IncomingOrderActivity directly from background (handled by full-screen intent): ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PickerFCM"
    }
}
