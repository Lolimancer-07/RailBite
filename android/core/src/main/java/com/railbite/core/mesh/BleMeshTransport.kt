package com.railbite.core.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Bluetooth transport for the mesh: the packet's chunk frames ride in the manufacturer
 * data of legacy BLE advertisements ([MeshChunker] does the framing), and the scanner
 * reassembles frames heard from any nearby phone before handing the packet to [MeshRelay].
 *
 * Radio notes (prototype scope): legacy adv leaves ~26 payload bytes after flags; we use
 * a 20-byte manufacturer frame (see [MeshChunker.MAX_CHUNK]). Frames are re-broadcast in
 * a repeating burst so nearby phones hear every chunk several times. All callbacks land
 * on [Dispatchers.Default]; BLE binder threads never touch app state directly.
 */
class BleMeshTransport(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    companion object {
        /** Unregistered test company id (0x0822) — prototype use, not a real vendor. */
        const val MANUFACTURER_ID = 0x0822
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val appContext: Context = context.applicationContext

    private val reassembler = MeshChunker.Reassembler()

    private var advertiseJob: Job? = null
    private var scanJob: Job? = null
    private var scanCallback: ScanCallback? = null

    /** Runtime BLE permissions this transport needs (API-level dependent). */
    fun requiredPermissions(): List<String> {
        val base = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        return base.filter {
            ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /** True when BT is on and this device supports peripheral advertising. */
    fun isUsable(): Boolean {
        val a = adapter ?: return false
        return a.isEnabled && a.bluetoothLeAdvertiser != null && a.bluetoothLeScanner != null
    }

    /**
     * Broadcasts one packet: chunks it and advertises the frames in a repeating burst
     * for [seconds], giving every nearby phone several chances to hear each chunk.
     */
    fun advertiseBurst(packet: MeshPacket, seconds: Int = 8, onDone: (Boolean) -> Unit = {}) {
        val advertiser: BluetoothLeAdvertiser = adapter?.bluetoothLeAdvertiser ?: run {
            onDone(false); return
        }
        if (!hasAdvertisePermission()) {
            onDone(false); return
        }
        val frames = MeshChunker.chunk(MeshCodec.encode(packet))
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val done = SkipFlag()
        advertiseJob?.cancel()
        advertiseJob = scope.launch {
            val deadline = System.currentTimeMillis() + seconds * 1000L
            var idx = 0
            while (isActive && System.currentTimeMillis() < deadline && !done.raised) {
                val frame = frames[idx % frames.size]
                idx++
                runCatching {
                    advertiser.startAdvertising(
                        settings,
                        AdvertiseData.Builder()
                            .setIncludeDeviceName(false)
                            .addManufacturerData(MANUFACTURER_ID, frame)
                            .build(),
                        advCallback,
                    )
                }
                delay(150)
                runCatching { advertiser.stopAdvertising(advCallback) }
            }
            done.raise()
            onDone(frames.isNotEmpty())
        }
    }

    fun stopAdvertising() {
        advertiseJob?.cancel()
        advertiseJob = null
    }

    /** Starts duty-cycled scanning; every reassembled, decoded packet goes to [onPacket]. */
    fun startScanning(onPacket: (MeshPacket) -> Unit) {
        val scanner: BluetoothLeScanner = adapter?.bluetoothLeScanner ?: return
        if (!hasScanPermission() || scanJob?.isActive == true) return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER) // OS duty-cycles to save battery
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val frame =
                    result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID) ?: return
                val bytes = reassembler.offer(frame) ?: return
                MeshCodec.decode(bytes)?.let(onPacket)
            }
        }
        scanCallback = callback
        scanJob = scope.launch {
            // One scanner session per start; LOW_POWER keeps it cheap. Scanner callbacks
            // arrive on binder threads — [onPacket] must hop if it touches UI state.
            runCatching { scanner.startScan(null, settings, callback) }
            awaitCancellation()
        }
    }

    fun stopScanning() {
        val scanner = adapter?.bluetoothLeScanner
        val cb = scanCallback
        scanJob?.cancel()
        scanJob = null
        scanCallback = null
        if (scanner != null && cb != null && hasScanPermission()) {
            runCatching { scanner.stopScan(cb) }
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasAdvertisePermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_ADVERTISE)

    private fun hasScanPermission(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 31) granted(Manifest.permission.BLUETOOTH_SCAN)
        else granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private val advCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) = Unit
    }

    private class SkipFlag {
        @Volatile var raised = false
        fun raise() {
            raised = true
        }
    }
}
