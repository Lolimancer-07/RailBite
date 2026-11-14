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
            else -> {
                startForegroundCompat()
                scope.launch { connectAlerts() }
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
        val serverUrl = container.settings.serverUrl.first()
        val staffId = container.settings.staffId.first()
        if (serverUrl.isBlank() || staffId.isBlank()) return

        val host = ApiClient.hostFromServerUrl(serverUrl)
        if (host.isBlank()) return
        val tripId = runCatching { ApiClient.testConnection(serverUrl).tripId }
            .getOrDefault(RailBiteTopics.DEFAULT_TRIP_ID)

        mutex.withLock {
            if (connector != null) return
            val c = MqttConnector(
                host = host,
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
        runCatching {
            ApiClient.api(serverUrl).setStaffOnline(staffId, com.railbite.core.api.RailBiteApi.OnlineRequest(value))
        }
        container.settings.setOnline(value)
    }

    private fun stopForegroundService() {
        connector?.stop()
        connector = null
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
