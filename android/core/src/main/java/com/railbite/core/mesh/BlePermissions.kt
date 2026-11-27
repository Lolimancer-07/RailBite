package com.railbite.core.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime BLE permission & Bluetooth hardware readiness checks across Android 10–15. */
object BlePermissions {

    /** BLE scan/advertise/connect permissions needed to start [BleMeshService] cleanly. */
    fun requiredPermissions(context: Context): List<String> {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
    }

    /** Optional notification permission on Android 13+ (recommended for foreground badge). */
    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun adapter(context: Context): BluetoothAdapter? =
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun hasBleHardware(context: Context): Boolean =
        context.applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            adapter(context) != null

    fun isBluetoothEnabled(context: Context): Boolean =
        adapter(context)?.isEnabled == true

    fun isReadyForMesh(context: Context): Boolean =
        requiredPermissions(context).isEmpty() && isBluetoothEnabled(context)
}

