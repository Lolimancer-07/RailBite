package com.railbite.picker.alert

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.railbite.core.api.ApiClient
import com.railbite.core.mesh.BleMeshService
import com.railbite.core.mesh.BlePermissions
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.mqtt.MqttConnector
import com.railbite.core.mqtt.RailBiteTopics
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
 * Android 14+ foreground service ("dataSync") keeping the picker's MQTT dispatch subscription
 * alive while the picker is ONLINE — even when locked or backgrounded.
 *
 * Uses runtime-configured server URL and optional MQTT WebSocket URL from [RailBiteSettings].
 */
class AlertForegroundService : Service() {

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
    private var subscribedTrainNumber: String = ""
    private var subscribedStaffId: String = ""
    @Volatile
    private var currentTrainNumber: String = RailBiteTopics.DEFAULT_TRAIN_NUMBER
    private val mutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        val app = application as com.railbite.picker.RailBitePickerApp
        scope.launch {
            app.container.settings.activeTrainNumber.collect { train ->
                currentTrainNumber = train
                    .trim()
                    .takeIf { com.railbite.core.util.Validators.isValidTrainNumber(it) }
                    ?: RailBiteTopics.DEFAULT_TRAIN_NUMBER
            }
        }
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
                // Re-assert server-side online state so a server restart or login snap-off
                // cannot leave the picker marked offline server-side while foregrounded.
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

        // Always start BLE mesh foreground scanner in LOW_LATENCY while picker is ONLINE.
        if (BlePermissions.isReadyForMesh(this)) {
            runCatching {
                BleMeshService.start(this, lowLatency = true)
                container.meshGateway.start(dispatchAlerts = true)
            }
        }

        if (!container.transportRouter.checkLanReachable()) return

        val serverUrl = container.settings.serverUrl.first()
        val mqttWsOverride = container.settings.mqttWsUrl.first()
        val staffId = container.settings.staffId.first()
        val activeTrainNumber = container.settings.activeTrainNumber.first()
            .trim()
            .takeIf { com.railbite.core.util.Validators.isValidTrainNumber(it) }
            ?: RailBiteTopics.DEFAULT_TRAIN_NUMBER
        currentTrainNumber = activeTrainNumber
        if ((serverUrl.isBlank() && mqttWsOverride.isBlank()) || staffId.isBlank()) return

        mutex.withLock {
            if (connector != null && subscribedTrainNumber == activeTrainNumber && subscribedStaffId == staffId) {
                return
            }
            connector?.stop()
            connector = null
            subscribedTrainNumber = activeTrainNumber
            subscribedStaffId = staffId
            val c = MqttConnector.fromServerUrl(
                serverUrl = serverUrl,
                clientId = "c-$staffId",
                mqttWsUrlOverride = mqttWsOverride,
                onConnected = { },
                onDisconnected = { },
            )
            connector = c
            c.subscribe(RailBiteTopics.captainAlerts(activeTrainNumber, staffId)) { payload ->
                runCatching {
                    val alert = json.decodeFromString<MqttOrderAlert>(payload.decodeToString())
                    val currentTrain = currentTrainNumber
                    if (alert.trainNumber != currentTrain) {
                        android.util.Log.i(
                            "RailBitePicker",
                            "Ignored cross-train order ${alert.trainNumber} != $currentTrain (order=${alert.orderId})",
                        )
                        return@runCatching
                    }
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
        val activeTrain = container.settings.activeTrainNumber.first()
            .trim()
            .takeIf { com.railbite.core.util.Validators.isValidTrainNumber(it) }
            ?: RailBiteTopics.DEFAULT_TRAIN_NUMBER
        val serverUrl = container.settings.serverUrl.first()
        if (staffId.isBlank() || serverUrl.isBlank()) return
        runCatching {
            container.client.api().setPickerStatus(
                staffId,
                com.railbite.core.api.RailBiteApi.PickerStatusRequest(value, activeTrain),
            )
        }.onFailure {
            runCatching {
                container.client.api().setStaffOnline(
                    staffId,
                    com.railbite.core.api.RailBiteApi.OnlineRequest(value, activeTrain),
                )
            }
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

