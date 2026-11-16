package com.railbite.picker.alert

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.theme.Metrics
import com.railbite.core.ui.theme.RailBiteTheme
import com.railbite.core.ui.theme.Space
import com.railbite.core.util.Money
import com.railbite.picker.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import java.time.Instant

/**
 * Full-screen, high-contrast new-order alert on a dark (#141414) surface:
 * rings loudly (ALARM stream) + vibrates until the picker ACCEPTs / REJECTs or 60 s elapse
 * (timeout counts as reject; the server re-dispatches to another picker).
 * Launched over the lock screen via a full-screen intent.
 */
class AlertActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "orderId"
        const val COUNTDOWN_SECONDS = 60
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen and turn the screen on
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching { km.requestDismissKeyguard(this, null) }
        }

        // Immediate buzz so even a muted device signals
        runCatching { buzz() }

        setContent {
            // The alert screen is always dark — it must cut through daylight
            // and be unmistakable at a glance, regardless of system theme.
            RailBiteTheme(darkTheme = true) {
                AlertScreen(
                    activityContext = this,
                    onFinished = { finish() },
                    onAccepted = { orderId ->
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN_ORDER_ID, orderId)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                        finish()
                    },
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        // one-shot buzz; the looping vibration comes from AlertSoundController
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400), -1))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun AlertScreen(
    activityContext: ComponentActivity,
    onFinished: () -> Unit,
    onAccepted: (String) -> Unit,
) {
    var alert by remember { mutableStateOf(AlertBus.current()) }
    var secondsLeft by remember { mutableIntStateOf(AlertActivity.COUNTDOWN_SECONDS) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Follow the shared alert bus (a newer alert replaces this screen's content)
    LaunchedEffect(Unit) {
        AlertBus.alert.collect { a -> if (a != null) alert = a }
    }

    // Ring + countdown; timeout counts as reject (server re-dispatches)
    LaunchedEffect(alert?.orderId) {
        val a = alert
        if (a != null) {
            AlertSoundController.start(activityContext)
            val expiresAt = try {
                Instant.parse(a.expiresAt).epochSecond
            } catch (_: Exception) {
                Instant.now().epochSecond + AlertActivity.COUNTDOWN_SECONDS
            }
            while (true) {
                val left = (expiresAt - Instant.now().epochSecond).toInt()
                secondsLeft = left.coerceIn(0, AlertActivity.COUNTDOWN_SECONDS)
                if (left <= 0) break
                delay(250)
            }
            if (!busy) {
                AlertSoundController.stop()
                message = "Missed — re-dispatching to another picker…"
                AlertActions.reject(activityContext, a) { onFinished() }
            }
        }
    }

    val progress: Float = secondsLeft / AlertActivity.COUNTDOWN_SECONDS.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(250, easing = LinearEasing),
        label = "countdown",
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Space.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text(
                "New order",
                style = MaterialTheme.typography.headlineLarge,
            )

            val a = alert
            if (a == null) {
                Text(
                    message ?: "No pending alert",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(
                    onClick = onFinished,
                    modifier = Modifier.heightIn(min = Metrics.tapTarget),
                ) { Text("Close") }
                return@Surface
            }

            CountdownRing(secondsLeft, animatedProgress)

            OrderDetailsCard(a)

            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                RailBiteButton(
                    text = "Accept",
                    onClick = {
                        if (!busy) {
                            busy = true
                            AlertSoundController.stop()
                            // Capture before accept: AlertActions/AlertBus.clear() reset the source.
                            val meshSource = AlertBus.currentSource() == AlertBus.SOURCE_MESH
                            AlertActions.accept(
                                activityContext,
                                a,
                                onAccepted = {
                                    AlertBus.clear()
                                    onAccepted(
                                        if (meshSource) com.railbite.picker.PickerRoutes.MESH_PREFIX + a.orderId
                                        else a.orderId,
                                    )
                                },
                                onFailed = { err ->
                                    busy = false
                                    message = err
                                    AlertSoundController.start(activityContext)
                                },
                            )
                        }
                    },
                    enabled = !busy,
                    busy = busy,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        if (!busy) {
                            busy = true
                            AlertSoundController.stop()
                            message = "Rejected — re-dispatching…"
                            AlertActions.reject(activityContext, a) { onFinished() }
                        }
                    },
                    enabled = !busy,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Metrics.primaryButtonHeight),
                ) {
                    Text("Reject", style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                "Ignoring this alert for ${AlertActivity.COUNTDOWN_SECONDS} s counts as a reject.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CountdownRing(secondsLeft: Int, animatedProgress: Float) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
        Canvas(Modifier.size(110.dp)) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = if (secondsLeft <= 10) {
                    com.railbite.core.ui.theme.Palette.Warning
                } else {
                    com.railbite.core.ui.theme.Palette.DarkAccent
                },
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = stroke,
            )
        }
        Text(
            "${secondsLeft}s",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun OrderDetailsCard(a: MqttOrderAlert) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                "Coach ${a.coach} · Seat ${a.berth}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "PNR ${a.pnr} · ${a.diners} diner${if (a.diners == 1) "" else "s"}" +
                    if (a.verified == 0) " · unverified" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(Space.xs))
            a.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.qty} × ${item.name}", style = MaterialTheme.typography.bodyLarge)
                    Text(Money.rupees(item.unitPricePaise * item.qty), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(Space.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (a.paymentMode == "UPI_QUEUED") "UPI — passenger pays digitally" else "Cash on delivery",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    Money.rupees(a.total),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (a.attempt > 1) {
                Text(
                    "Attempt ${a.attempt} — other pickers declined",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
