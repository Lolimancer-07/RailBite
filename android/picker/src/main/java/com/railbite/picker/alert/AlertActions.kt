package com.railbite.picker.alert

import android.content.Context
import com.railbite.core.model.CaptainActionRequest
import com.railbite.core.model.MqttOrderAlert
import com.railbite.picker.RailBitePickerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Accept/reject actions for alerts, run on an app-scoped coroutine (survives activity finish). */
object AlertActions {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun accept(
        context: Context,
        alert: MqttOrderAlert,
        onAccepted: () -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val app = context.applicationContext as RailBitePickerApp
        scope.launch {
            try {
                val api = app.container.client.api()
                api.acceptOrder(
                    alert.orderId,
                    CaptainActionRequest(app.container.settings.staffId.first()),
                )
                app.container.settings.setPickerOrderId(alert.orderId)
                AlertBus.markAccepted(alert.orderId)
                cancelAlertNotification(context)
                onAccepted()
            } catch (e: Exception) {
                val msg = e.message ?: ""
                onFailed(
                    when {
                        msg.contains("409") -> "Already taken by another picker"
                        msg.contains("404") -> "Order no longer exists"
                        else -> "Network error — try again"
                    },
                )
            }
        }
    }

    fun reject(context: Context, alert: MqttOrderAlert, onDone: () -> Unit) {
        val app = context.applicationContext as RailBitePickerApp
        scope.launch {
            try {
                val api = app.container.client.api()
                api.rejectOrder(
                    alert.orderId,
                    CaptainActionRequest(app.container.settings.staffId.first()),
                )
            } catch (_: Exception) {
                // server re-dispatches on its own timer anyway
            }
            cancelAlertNotification(context)
            AlertBus.clear()
            onDone()
        }
    }

    private fun cancelAlertNotification(context: Context) {
        val nm = androidx.core.app.NotificationManagerCompat.from(context)
        nm.cancel(Notifications.ALERT_NOTIFICATION_ID)
    }
}
