package com.railbite.core.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.railbite.core.R
import com.railbite.core.data.RailBiteSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shared Android Foreground Service (`connectedDevice`) running the 23-byte binary BLE
 * scanner + time-sliced advertiser for both `com.railbite.customer` and `com.railbite.picker`.
 */
class BleMeshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Bounded hand-off from the BLE [ScanCallback] to a dedicated background dispatcher.
     * The callback only copies bytes + RSSI and `trySend`s; HMAC decode, dedup LRU, StateFlow
     * updates and relay enqueueing happen in [scanConsumer]. Under a burst the oldest frame is
     * dropped rather than blocking the scanner.
     */
    private val scanFrames = Channel<ScanFrame>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val scanScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: RailBiteSettings
    private lateinit var scheduler: BleTxScheduler

    private data class ScanFrame(val bytes: ByteArray, val rssi: Int)

    @Volatile
    private var currentLowLatency = false
    @Volatile
    private var currentRelayEnabled = true
    @Volatile
    private var currentMeshKey = BlePacketCodec.RAILBITE_MESH_KEY
    @Volatile
    private var activeScanner: BluetoothLeScanner? = null
    @Volatile
    private var activeScanCallback: ScanCallback? = null

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                BleMeshHub.refreshRadioState(this@BleMeshService)
                when (btState) {
                    BluetoothAdapter.STATE_ON -> restartScanner()
                    BluetoothAdapter.STATE_TURNING_OFF,
                    BluetoothAdapter.STATE_OFF -> stopScanner()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = RailBiteSettings(this)
        scheduler = BleTxScheduler(
            advertiser = AndroidFrameAdvertiser(this),
        )
        BleMeshHub.attachScheduler(scheduler)
        createNotificationChannel()
        registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        scanScope.launch {
            for (frame in scanFrames) {
                runCatching {
                    BleMeshHub.onFrameScanned(
                        context = applicationContext,
                        rawBytes = frame.bytes,
                        rssi = frame.rssi,
                        meshKey = currentMeshKey,
                        relayEnabled = currentRelayEnabled,
                    )
                }.onFailure { Log.w(TAG, "scan frame processing failed: ${it.message}") }
            }
        }
        scope.launch {
            settings.meshKey.collect { key -> currentMeshKey = key }
        }
        scope.launch {
            settings.meshRelayEnabled.collect { enabled -> currentRelayEnabled = enabled }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServiceCleanly()
            return START_NOT_STICKY
        }

        if (BlePermissions.requiredPermissions(this).isNotEmpty()) {
            Log.w(TAG, "Cannot start connectedDevice BLE service — runtime BLE permissions missing")
            BleMeshHub.updateServiceState(running = false, lowLatencyScan = false, context = this)
            stopSelf()
            return START_NOT_STICKY
        }

        val lowLatency = intent?.getBooleanExtra(EXTRA_LOW_LATENCY, false) ?: false
        val scanModeChanged = lowLatency != currentLowLatency || activeScanCallback == null
        currentLowLatency = lowLatency

        val startedForeground = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(lowLatency),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            true
        }.getOrElse { err ->
            Log.e(TAG, "startForeground(connectedDevice) failed: ${err.message}")
            false
        }

        if (!startedForeground) {
            BleMeshHub.updateServiceState(running = false, lowLatencyScan = false, context = this)
            stopSelf()
            return START_NOT_STICKY
        }

        BleMeshHub.updateServiceState(
            running = true,
            lowLatencyScan = currentLowLatency,
            context = this,
        )

        scope.launch {
            currentRelayEnabled = settings.meshRelayEnabled.first()
            currentMeshKey = settings.meshKey.first()
        }

        if (scanModeChanged) {
            restartScanner()
        }
        return START_STICKY
    }

    private fun restartScanner() {
        stopScanner()
        if (!BlePermissions.isReadyForMesh(this)) return
        val adapter = BlePermissions.adapter(this) ?: return
        val scanner = adapter.bluetoothLeScanner ?: return

        val filter = ScanFilter.Builder()
            .setManufacturerData(BlePacketCodec.COMPANY_ID, byteArrayOf())
            .build()
        val scanMode = if (currentLowLatency) {
            ScanSettings.SCAN_MODE_LOW_LATENCY
        } else {
            ScanSettings.SCAN_MODE_BALANCED
        }
        val scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // ScanCallback runs on the Main thread when no Handler is supplied. Keep this
                // path allocation-light: copy the manufacturer payload and hand it to the
                // bounded background channel. No HMAC / LRU / DB / advertiser calls here.
                val payload = result.scanRecord
                    ?.getManufacturerSpecificData(BlePacketCodec.COMPANY_ID)
                    ?: return
                scanFrames.trySend(ScanFrame(payload.copyOf(), result.rssi))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) {
                    onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed with code=$errorCode")
            }
        }

        runCatching {
            scanner.startScan(listOf(filter), scanSettings, callback)
            activeScanner = scanner
            activeScanCallback = callback
        }.onFailure { err ->
            Log.w(TAG, "Unable to start BLE scan: ${err.message}")
        }
    }

    private fun stopScanner() {
        val scanner = activeScanner
        val callback = activeScanCallback
        activeScanner = null
        activeScanCallback = null
        if (scanner != null && callback != null && BlePermissions.requiredPermissions(this).isEmpty()) {
            runCatching { scanner.stopScan(callback) }
        }
    }

    private fun stopServiceCleanly() {
        stopScanner()
        scheduler.stopAll()
        BleMeshHub.updateServiceState(running = false, lowLatencyScan = false, context = this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(btStateReceiver) }
        stopScanner()
        scanFrames.close()
        scanScope.cancel()
        scheduler.shutdown()
        BleMeshHub.detachScheduler(scheduler)
        BleMeshHub.updateServiceState(running = false, lowLatencyScan = false, context = this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Offline BLE Mesh Relay",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps offline peer-to-peer order & delivery mesh active without internet"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(lowLatency: Boolean): Notification {
        val stopIntent = android.app.PendingIntent.getService(
            this,
            91,
            Intent(this, BleMeshService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ble_mesh)
            .setContentTitle("RailBite Offline BLE Mesh")
            .setContentText(
                if (lowLatency) "Active — low-latency coach mesh scanning"
                else "Standby — balanced coach relay active",
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Pause Mesh", stopIntent)
            .build()
    }

    private class AndroidFrameAdvertiser(private val context: Context) : BleFrameAdvertiser {
        private val advCallback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "BLE advertise failed errorCode=$errorCode")
            }
        }

        private val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        override fun startAdvertising(payload23: ByteArray): Boolean {
            if (!BlePermissions.isReadyForMesh(context)) return false
            val advertiser = BlePermissions.adapter(context)?.bluetoothLeAdvertiser ?: return false
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(BlePacketCodec.COMPANY_ID, payload23)
                .build()
            return runCatching {
                advertiser.stopAdvertising(advCallback)
                advertiser.startAdvertising(settings, data, advCallback)
                true
            }.getOrDefault(false)
        }

        override fun stopAdvertising() {
            if (BlePermissions.requiredPermissions(context).isEmpty()) {
                runCatching {
                    BlePermissions.adapter(context)?.bluetoothLeAdvertiser?.stopAdvertising(advCallback)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BleMeshService"
        private const val CHANNEL_ID = "railbite_ble_mesh"
        private const val NOTIFICATION_ID = 942

        const val ACTION_START = "com.railbite.core.mesh.action.START"
        const val ACTION_STOP = "com.railbite.core.mesh.action.STOP"
        const val EXTRA_LOW_LATENCY = "low_latency"

        fun start(context: Context, lowLatency: Boolean = false) {
            if (BlePermissions.requiredPermissions(context).isNotEmpty()) {
                BleMeshHub.refreshRadioState(context)
                return
            }
            if (!BlePermissions.hasBleHardware(context)) return
            val intent = Intent(context, BleMeshService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_LOW_LATENCY, lowLatency)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, BleMeshService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
