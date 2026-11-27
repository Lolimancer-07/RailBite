package com.railbite.picker.alert

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.railbite.core.mesh.BleMeshService
import com.railbite.core.mesh.BlePermissions
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.mqtt.MqttConnector
import com.railbite.core.mqtt.RailBiteTopics
import com.railbite.core.util.Validators
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
        const val ACTION_UPDATE_TRAIN = "com.railbite.picker.action.UPDATE_TRAIN"
        const val EXTRA_TRAIN_NUMBER = "extra_train_number"

        /** Whether the alert pipeline is connected (UI binds to this for the ONLINE indicator). */
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** Exact train number currently subscribed on MQTT by this service. */
        private val _subscribedTrain = MutableStateFlow("")
        val subscribedTrain: StateFlow<String> = _subscribedTrain.asStateFlow()
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
                val resolved = Validators.normalizeTrainNumber(train)
                    .takeIf { Validators.isValidTrainNumber(it) }
                    ?: RailBiteTopics.DEFAULT_TRAIN_NUMBER
                if (resolved != currentTrainNumber) {
                    switchTrainSubscription(resolved)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val extraTrain = intent?.getStringExtra(EXTRA_TRAIN_NUMBER)
            ?.let { Validators.normalizeTrainNumber(it) }
            ?.takeIf { Validators.isValidTrainNumber(it) }
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    runCatching { setOnline(false) }
                    stopForegroundService()
                }
                return START_NOT_STICKY
            }
            ACTION_UPDATE_TRAIN -> {
                scope.launch {
                    val app = application as com.railbite.picker.RailBitePickerApp
                    val target = extraTrain ?: Validators
                        .normalizeTrainNumber(app.container.settings.activeTrainNumber.first())
                        .takeIf { Validators.isValidTrainNumber(it) }
                        ?: RailBiteTopics.DEFAULT_TRAIN_NUMBER
                    switchTrainSubscription(target)
                    if (BlePermissions.isReadyForMesh(this@AlertForegroundService)) {
                        runCatching {
                            BleMeshService.start(this@AlertForegroundService, lowLatency = true)
                        }
                    }
                }
                return START_STICKY
            }
            ACTION_RECONNECT -> {
                if (extraTrain != null) currentTrainNumber = extraTrain
                startForegroundCompat()
                scope.launch {
                    mutex.withLock {
                        connector?.stop()
                        connector = null
                    }
                    connectAlerts(extraTrain)
                    runCatching { setOnline(true, extraTrain) }
                }
                return START_STICKY
            }
            else -> {
                if (extraTrain != null) currentTrainNumber = extraTrain
                startForegroundCompat()
                scope.launch { connectAlerts(extraTrain) }
                if (intent == null || extraTrain == null) {
                    // System restart recovery without explicit UI toggle
                    scope.launch { runCatching { setOnline(true, extraTrain) } }
                }
                return START_STICKY
            }
        }
    }

    private fun startForegroundCompat() {
        ServiceCompat.startForeground(
            this,
            Notifications.FOREGROUND_NOTIFICATION_ID,
            Notifications.onlineNotification(this, currentTrainNumber),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        _running.value = true
    }

    private suspend fun connectAlerts(explicitTrain: String? = null) {
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
        val activeTrainNumber = explicitTrain
            ?: Validators.normalizeTrainNumber(container.settings.activeTrainNumber.first())
                .takeIf { Validators.isValidTrainNumber(it) }
            ?: RailBiteTopics.DEFAULT_TRAIN_NUMBER
        currentTrainNumber = activeTrainNumber
        if ((serverUrl.isBlank() && mqttWsOverride.isBlank()) || staffId.isBlank()) return

        mutex.withLock {
            val existing = connector
            if (existing != null && subscribedStaffId == staffId) {
                if (subscribedTrainNumber == activeTrainNumber) {
                    _subscribedTrain.value = activeTrainNumber
                    startForegroundCompat()
                    return
                }
                if (subscribedTrainNumber.isNotBlank()) {
                    existing.unsubscribe(RailBiteTopics.captainAlerts(subscribedTrainNumber, staffId))
                }
                subscribeAlertsLocked(existing, activeTrainNumber, staffId)
                return
            }
            connector?.stop()
            connector = null
            val c = MqttConnector.fromServerUrl(
                serverUrl = serverUrl,
                clientId = "c-$staffId",
                mqttWsUrlOverride = mqttWsOverride,
                onConnected = { },
                onDisconnected = { },
            )
            connector = c
            subscribeAlertsLocked(c, activeTrainNumber, staffId)
            c.start()
        }
    }

    private suspend fun switchTrainSubscription(newTrain: String) {
        val normalized = Validators.normalizeTrainNumber(newTrain)
        if (!Validators.isValidTrainNumber(normalized)) return
        val app = application as com.railbite.picker.RailBitePickerApp
        val container = app.container
        val staffId = container.settings.staffId.first()
        if (staffId.isBlank()) {
            currentTrainNumber = normalized
            return
        }
        mutex.withLock {
            val c = connector
            if (c != null) {
                if (subscribedTrainNumber.isNotBlank() && subscribedStaffId.isNotBlank()) {
                    c.unsubscribe(RailBiteTopics.captainAlerts(subscribedTrainNumber, subscribedStaffId))
                }
                subscribeAlertsLocked(c, normalized, staffId)
            } else {
                currentTrainNumber = normalized
            }
        }
        if (connector == null && container.settings.online.first()) {
            connectAlerts(normalized)
        }
    }

    private suspend fun subscribeAlertsLocked(
        c: MqttConnector,
        trainNumber: String,
        staffId: String,
    ) {
        val topic = RailBiteTopics.captainAlerts(trainNumber, staffId)
        subscribedTrainNumber = trainNumber
        subscribedStaffId = staffId
        currentTrainNumber = trainNumber
        _subscribedTrain.value = trainNumber
        android.util.Log.i("RailBiteTrainMatch", "Subscribed picker $staffId to MQTT topic $topic")
        c.subscribe(topic) { payload ->
            runCatching {
                val alert = json.decodeFromString<MqttOrderAlert>(payload.decodeToString())
                val incomingTrain = Validators.normalizeTrainNumber(alert.trainNumber)
                val activeTrain = Validators.normalizeTrainNumber(currentTrainNumber)
                val matched = incomingTrain == activeTrain
                android.util.Log.i(
                    "RailBiteTrainMatch",
                    "Incoming orderId=${alert.orderId} incomingTrain=$incomingTrain activeTrain=$activeTrain ${if (matched) "MATCH" else "DROP"}",
                )
                if (!matched) {
                    return@runCatching
                }
                val normalizedAlert = alert.copy(trainNumber = incomingTrain)
                AlertBus.post(normalizedAlert)
                Notifications.notifyAlert(this, normalizedAlert)
                Notifications.launchIncomingActivity(this, normalizedAlert.orderId)
            }
        }
        startForegroundCompat()
    }

    private suspend fun setOnline(value: Boolean, explicitTrain: String? = null) {
        val app = application as com.railbite.picker.RailBitePickerApp
        val container = app.container
        val staffId = container.settings.staffId.first()
        val activeTrain = explicitTrain
            ?: Validators.normalizeTrainNumber(container.settings.activeTrainNumber.first())
                .takeIf { Validators.isValidTrainNumber(it) }
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
        subscribedTrainNumber = ""
        subscribedStaffId = ""
        _subscribedTrain.value = ""
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

