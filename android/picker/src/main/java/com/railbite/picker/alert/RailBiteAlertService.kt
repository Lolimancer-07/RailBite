package com.railbite.picker.alert

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.railbite.core.api.ApiClient
import com.railbite.core.mqtt.MqttConnector
import com.railbite.core.mqtt.RailBiteTopics
import com.railbite.core.model.MqttOrderAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Foreground service ("dataSync") that keeps the picker's MQTT subscription alive while
 * the picker is ONLINE — even when the app is closed or the phone is locked.
 *
 * Started/stopped by the ONLINE toggle on the Home screen. Shows a persistent
 * "You are online" notification with a Stop action.
 */
class RailBiteAlertService : Service() {

    companion object {
        const val ACTION_START = "com.railbite.picker.action.START"
        const val ACTION_STOP = "com.railbite.picker.action.STOP"
        const val ACTION_RECONNECT = "com.railbite.picker.action.RECONNECT"

        /** Whether the alert pipeline is connected (UI binds to this for the ONLINE indicator). */
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var connector: MqttConnector? = null
    private val mutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    runCatching { setOnline(false) }
                    stopForegroundService()
                }
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> {
                startForegroundCompat()
                scope.launch {
                    mutex.withLock {
                        connector?.stop()
                        connector = null
                    }
                    connectAlerts()
                    runCatching { setOnline(true) }
                }
                return START_STICKY
            }
            else -> {
                startForegroundCompat()
                scope.launch { connectAlerts() }
                // Re-assert server-side online state: after a server restart or a login
                // snap-off the dispatcher would silently skip this picker even though the
                // app shows ONLINE (the service running is the source of truth).
                scope.launch { runCatching { setOnline(true) } }
                return START_STICKY
            }
        }
    }

    private fun startForegroundCompat() {
        ServiceCompat.startForeground(
            this,
            Notifications.FOREGROUND_NOTIFICATION_ID,
            Notifications.onlineNotification(this),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        _running.value = true
    }

    private suspend fun connectAlerts() {
        val app = application as com.railbite.picker.RailBitePickerApp
        val container = app.container

        // Bluetooth relay (offline feature) runs regardless of cloud state — with the
        // train Wi-Fi dead it is the only path orders can take. MeshGateway.start() is
        // idempotent, so repeated service starts are safe; permissions are checked so
        // the scan never throws.
        if (container.settings.meshSinkEnabled.first() &&
            container.meshTransport.requiredPermissions().isEmpty()
        ) {
            runCatching { container.meshGateway.start() }
        }

        val serverUrl = container.settings.serverUrl.first()
        val staffId = container.settings.staffId.first()
        if (serverUrl.isBlank() || staffId.isBlank()) return

        val tripId = runCatching { ApiClient.testConnection(serverUrl).tripId }
            .getOrDefault(RailBiteTopics.DEFAULT_TRIP_ID)

        mutex.withLock {
            if (connector != null) return
            val c = MqttConnector.fromServerUrl(
                serverUrl = serverUrl,
                clientId = "c-$staffId",
                onConnected = { },
                onDisconnected = { },
            )
            connector = c
            c.subscribe(RailBiteTopics.captainAlerts(tripId, staffId)) { payload ->
                runCatching {
                    val alert = json.decodeFromString<MqttOrderAlert>(payload.decodeToString())
                    AlertBus.post(alert)
                    Notifications.notifyAlert(this, alert)
                }
            }
            c.start()
        }
    }

    private suspend fun setOnline(value: Boolean) {
        val app = application as com.railbite.picker.RailBitePickerApp
        val container = app.container
        val staffId = container.settings.staffId.first()
        val serverUrl = container.settings.serverUrl.first()
        if (staffId.isBlank() || serverUrl.isBlank()) return
        // Authed client: the service holds no Activity, but the bearer token lives in
        // the encrypted auth store, which the interceptor injects.
        runCatching {
            container.client.api().setStaffOnline(staffId, com.railbite.core.api.RailBiteApi.OnlineRequest(value))
        }
        container.settings.setOnline(value)
    }

    private fun stopForegroundService() {
        connector?.stop()
        connector = null
        (application as? com.railbite.picker.RailBitePickerApp)?.container?.meshGateway?.stop()
        _running.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        connector?.stop()
        connector = null
        _running.value = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
