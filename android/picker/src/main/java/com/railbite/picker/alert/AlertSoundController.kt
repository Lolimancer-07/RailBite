package com.railbite.picker.alert

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Alarm sound + repeating vibration + partial wake lock while an alert is ringing.
 * Started by AlertActivity; must always be stopped via [stop].
 */
@SuppressLint("WakelockTimeout")
object AlertSoundController {

    private const val WAKELOCK_TAG = "railbite:alert"
    private const val ALARM_DURATION_MS = 70_000L

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800, 400, 800)

    fun start(context: Context) {
        stop() // idempotent restart

        // 1. Partial wake lock so the countdown keeps ticking on the lock screen
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                acquire(ALARM_DURATION_MS)
            }
        }

        // 2. Loud looping alarm sound on the ALARM stream
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val p = MediaPlayer()
            p.setDataSource(context, uri)
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            p.isLooping = true
            p.prepare()
            p.start()
            player = p
        }

        // 3. Repeating strong vibration
        runCatching {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (v.hasVibrator()) {
                v.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
                vibrator = v
            }
        }
    }

    fun stop(context: Context? = null) {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
        wakeLock = null
    }
}
