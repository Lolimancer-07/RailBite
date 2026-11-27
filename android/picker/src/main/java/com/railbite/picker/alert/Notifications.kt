package com.railbite.picker.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.util.Money
import com.railbite.core.util.Validators

/**
 * Notification plumbing: two channels —
 *  - "picker_status"  (LOW): persistent "You are online" foreground-service notification
 *  - "order_alerts"   (HIGH): full-screen new-order alerts
 */
object Notifications {

    const val CHANNEL_STATUS = "picker_status"
    const val CHANNEL_ALERTS = "order_alerts"
    const val FOREGROUND_NOTIFICATION_ID = 41
    const val ALERT_NOTIFICATION_ID = 42

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                "Picker presence",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown while you are online and receiving orders"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "New order alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Loud full-screen alerts for new orders near your zone"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(
                    alarmUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setBypassDnd(true)
            },
        )
    }

    /** Persistent notification for the ONLINE foreground service. */
    fun onlineNotification(context: Context, trainNumber: String = ""): android.app.Notification {
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, AlertForegroundService::class.java).setAction(AlertForegroundService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cleanTrain = Validators.normalizeTrainNumber(trainNumber)
        val contentText = if (cleanTrain.isNotBlank()) {
            "Listening on Train $cleanTrain for incoming orders"
        } else {
            "RailBite Picker is listening for new orders"
        }
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(com.railbite.picker.R.drawable.ic_stat_picker)
            .setContentTitle("You are online")
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent,
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** High-importance notification with a full-screen intent for a new order. */
    fun alertNotification(
        context: Context,
        alert: MqttOrderAlert,
        hopCount: Int? = null,
    ): android.app.Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            alert.orderId.hashCode(),
            Intent(context, IncomingOrderActivity::class.java).putExtra(IncomingOrderActivity.EXTRA_ORDER_ID, alert.orderId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val itemsText = alert.items.joinToString("\n") { "${it.qty} × ${it.name}" }
        val upiOrder = alert.paymentMode == "UPI_QUEUED"
        val meshPrefix = hopCount?.let { "Via BLE Mesh ($it hop${if (it == 1) "" else "s"}) • " } ?: ""
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(com.railbite.picker.R.drawable.ic_stat_picker)
            .setContentTitle("${meshPrefix}Train ${alert.trainNumber} • Coach ${alert.coach} · Seat ${alert.berth}")
            .setContentText("${alert.items.sumOf { it.qty }} items · ${Money.rupees(alert.total)} · ${if (upiOrder) "UPI" else "Cash"}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$itemsText\nTotal ${Money.rupees(alert.total)} " +
                        "(${if (upiOrder) "UPI — passenger pays digitally" else "COD"})",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
    }

    fun notifyAlert(context: Context, alert: MqttOrderAlert, hopCount: Int? = null) {
        if (hasPostNotifications(context)) {
            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(ALERT_NOTIFICATION_ID, alertNotification(context, alert, hopCount))
            }
        }
    }

    /**
     * Launches the full-screen ringing [IncomingOrderActivity] immediately so orders received
     * while unlocked, in the foreground, or without notification full-screen auto-launch always ring.
     */
    fun launchIncomingActivity(context: Context, orderId: String) {
        runCatching {
            val intent = Intent(context, IncomingOrderActivity::class.java).apply {
                putExtra(IncomingOrderActivity.EXTRA_ORDER_ID, orderId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            context.startActivity(intent)
        }.onFailure { err ->
            Log.w("RailBiteTrainMatch", "Could not start IncomingOrderActivity directly: ${err.message}")
        }
    }

    private fun hasPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canUseFullScreenIntent(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManagerCompat.from(context).canUseFullScreenIntent()
        } else {
            true
        }
}
