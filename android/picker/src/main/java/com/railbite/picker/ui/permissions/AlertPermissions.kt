package com.railbite.picker.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.railbite.picker.alert.Notifications

/** Snapshot of the three permissions the loud-alert pipeline needs. */
data class AlertPermissionState(
    val notifications: Boolean,
    val fullScreen: Boolean,
    val battery: Boolean,
) {
    val allGranted: Boolean get() = notifications && fullScreen && battery
    val grantedCount: Int get() = listOf(notifications, fullScreen, battery).count { it }
}

fun readAlertPermissions(context: Context): AlertPermissionState = AlertPermissionState(
    notifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED,
    fullScreen = Notifications.canUseFullScreenIntent(context),
    battery = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(true),
)
