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
import retrofit2.HttpException

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
        // Bluetooth relay order: the server doesn't know it yet — record the acceptance
        // locally and route to the offline handover screen; the gateway replays it later.
        if (AlertBus.currentSource() == AlertBus.SOURCE_MESH) {
            scope.launch {
                val staffId = app.container.settings.staffId.first().ifBlank { "c1" }
                app.container.meshLedger.markState(
                    orderId = alert.orderId,
                    state = com.railbite.core.util.Statuses.ACCEPTED,
                    captainId = staffId,
                )
                // Broadcast 0x12 ACCEPTED (`ttl = 6`, `15s`) so customer timeline updates
                // and any distant pickers stand down immediately.
                app.container.meshGateway.broadcastStatus(
                    orderId = alert.orderId,
                    state = com.railbite.core.util.Statuses.ACCEPTED,
                    cashPaise = alert.total,
                )
                app.container.settings.setPickerOrderId(alert.orderId)
                AlertBus.markAccepted(alert.orderId)
                cancelAlertNotification(context)
                onAccepted()
            }
            return
        }
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
                val code = (e as? HttpException)?.code()
                onFailed(
                    when {
                        code == 401 -> "Session expired — please log in again"
                        code == 409 -> "Already taken by another picker"
                        code == 404 -> "Order no longer exists"
                        msg.contains("409") -> "Already taken by another picker"
                        msg.contains("404") -> "Order no longer exists"
                        msg.contains("Unable to resolve host", ignoreCase = true) ||
                            msg.contains("Failed to connect", ignoreCase = true) ||
                            msg.contains("timeout", ignoreCase = true) -> "Network error — check the connection and retry"
                        else -> "Network error — try again"
                    },
                )
            }
        }
    }

    fun reject(context: Context, alert: MqttOrderAlert, onDone: () -> Unit) {
        val app = context.applicationContext as RailBitePickerApp
        if (AlertBus.currentSource() == AlertBus.SOURCE_MESH) {
            // Rejected BLE order: ignore locally on this picker and broadcast REJECTED (`ttl=6`, `15s`)
            // while other pickers may still claim it until ACCEPTED+.
            scope.launch {
                app.container.meshLedger.markState(alert.orderId, "REJECTED")
                app.container.meshGateway.broadcastStatus(
                    orderId = alert.orderId,
                    state = "REJECTED",
                )
            }
            cancelAlertNotification(context)
            AlertBus.clear()
            onDone()
            return
        }
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
