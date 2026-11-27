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
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.util.Money
import com.railbite.picker.MainActivity
import com.railbite.picker.PickerRoutes
import com.railbite.picker.ui.components.PickerStatusTag
import com.railbite.picker.ui.components.shortOrderTag
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.RailBitePickerPalette
import com.railbite.picker.ui.theme.RailBitePickerTheme
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Stitch Screen 3 (`9e2ed81e`): Full-screen Lock-Screen Incoming Order Alert Takeover.
 * Dark `#0F172A` surface, loud ALARM stream ring + repeating vibration, 60s countdown ring,
 * Coach/Seat hero coordinate, white Items Dispatch card, and REJECT / ACCEPT ORDER CTAs.
 */
class IncomingOrderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "orderId"
        const val COUNTDOWN_SECONDS = 60
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        setContent {
            RailBitePickerTheme(darkTheme = true) {
                IncomingOrderAlertContent(
                    activityContext = this,
                    onBuzz = { runCatching { buzz() } },
                    onFinished = { finish() },
                    onAccepted = { orderId ->
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN_ORDER_ID, orderId)
                                .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                                ),
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
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400), -1))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun IncomingOrderAlertContent(
    activityContext: ComponentActivity,
    onBuzz: () -> Unit,
    onFinished: () -> Unit,
    onAccepted: (String) -> Unit,
) {
    var alert by remember { mutableStateOf(AlertBus.current()) }
    val source by AlertBus.source.collectAsState()
    val hopCount by AlertBus.hopCount.collectAsState()
    var secondsLeft by remember { mutableIntStateOf(IncomingOrderActivity.COUNTDOWN_SECONDS) }
    var busy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val pulseTransition = rememberInfiniteTransition(label = "alert-pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )

    LaunchedEffect(Unit) {
        AlertBus.alert.collect { incoming ->
            if (incoming != null) {
                alert = incoming
            } else if (alert != null && !busy) {
                AlertSoundController.stop()
                onFinished()
            }
        }
    }

    LaunchedEffect(alert?.orderId) {
        val a = alert
        if (a != null) {
            val app = activityContext.applicationContext as com.railbite.picker.RailBitePickerApp
            val expectedTrain = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.railbite.core.util.Validators.normalizeTrainNumber(
                    app.container.settings.activeTrainNumber.first(),
                )
            }
            val incomingTrain = com.railbite.core.util.Validators.normalizeTrainNumber(a.trainNumber)
            val matched = incomingTrain == expectedTrain
            android.util.Log.i(
                "RailBiteTrainMatch",
                "Incoming orderId=${a.orderId} incomingTrain=$incomingTrain activeTrain=$expectedTrain ${if (matched) "MATCH" else "DROP"}",
            )
            if (!matched) {
                AlertSoundController.stop()
                AlertBus.clear()
                onFinished()
                return@LaunchedEffect
            }

            val expiresAt = try {
                Instant.parse(a.expiresAt).epochSecond
            } catch (_: Exception) {
                Instant.now().epochSecond + IncomingOrderActivity.COUNTDOWN_SECONDS
            }
            if (expiresAt - Instant.now().epochSecond <= 0) {
                statusMessage = "Dispatch expired — re-dispatching..."
                delay(600)
                if (!busy) {
                    AlertActions.reject(activityContext, a) { onFinished() }
                }
                return@LaunchedEffect
            }
            onBuzz()
            AlertSoundController.start(activityContext)
            while (true) {
                val left = (expiresAt - Instant.now().epochSecond).toInt()
                secondsLeft = left.coerceIn(0, IncomingOrderActivity.COUNTDOWN_SECONDS)
                if (left <= 0) break
                delay(250)
            }
            if (!busy) {
                AlertSoundController.stop()
                statusMessage = "Timed out — re-dispatching to another picker..."
                AlertActions.reject(activityContext, a) { onFinished() }
            }
        }
    }

    val progress = secondsLeft / IncomingOrderActivity.COUNTDOWN_SECONDS.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(250, easing = LinearEasing),
        label = "countdown-arc",
    )

    Surface(
        color = RailBitePickerPalette.DarkSlate,
        contentColor = Color.White,
        modifier = Modifier.fillMaxSize(),
    ) {
        val a = alert
        if (a == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
            ) {
                Text(
                    text = statusMessage ?: "No pending order dispatch",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onFinished,
                    shape = RoundedCornerShape(PickerRadii.Button),
                    border = BorderStroke(1.dp, RailBitePickerPalette.DarkSlateBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.heightIn(min = PickerMetrics.TouchTargetMin),
                ) {
                    Text("Close")
                }
            }
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1. Top Pulsing Alert Pill + Countdown Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(PickerRadii.Pill),
                        color = Color(0xFF450A0A),
                        border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .alpha(pulseAlpha)
                                    .clip(CircleShape)
                                    .background(RailBitePickerPalette.SemanticRed),
                            )
                            Text(
                                text = "NEW PANTRY ORDER ALERT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = RailBitePickerPalette.SemanticRedBorder,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(PickerRadii.Pill),
                        color = if (secondsLeft <= 10) Color(0xFF450A0A) else RailBitePickerPalette.DarkSlateCell,
                        border = BorderStroke(
                            1.dp,
                            if (secondsLeft <= 10) RailBitePickerPalette.SemanticRed else RailBitePickerPalette.DarkSlateBorder,
                        ),
                    ) {
                        Text(
                            text = "${secondsLeft}s left",
                            style = PickerNumericType.MetaMono,
                            color = if (secondsLeft <= 10) RailBitePickerPalette.SemanticRedBorder else RailBitePickerPalette.CyanPinDigit,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                // 2. Cyan Foreground Service Active Strip
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Button),
                    color = RailBitePickerPalette.CyanPrimary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "RINGING LOUDLY • FOREGROUND SERVICE ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }

                // 3. Countdown Ring + Hero Seat Destination Card
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Card),
                    color = RailBitePickerPalette.DarkSlateCell,
                    border = BorderStroke(1.dp, RailBitePickerPalette.DarkSlateBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    PickerStatusTag(
                                        text = if (a.paymentMode == "UPI_QUEUED") "UPI PAID" else "CASH ON DELIVERY",
                                        bgColor = if (a.paymentMode == "UPI_QUEUED") Color(0xFF083344) else Color(0xFF451A03),
                                        textColor = if (a.paymentMode == "UPI_QUEUED") RailBitePickerPalette.CyanPinDigit else RailBitePickerPalette.SemanticAmberBorder,
                                        borderColor = if (a.paymentMode == "UPI_QUEUED") RailBitePickerPalette.CyanPrimary else RailBitePickerPalette.SemanticAmber,
                                    )
                                    if (source == AlertBus.SOURCE_MESH) {
                                        val hops = hopCount ?: 0
                                        PickerStatusTag(
                                            text = "VIA BLE MESH • $hops HOP${if (hops == 1) "" else "S"}",
                                            bgColor = Color(0xFF083344),
                                            textColor = RailBitePickerPalette.CyanPinDigit,
                                            borderColor = RailBitePickerPalette.CyanPrimary,
                                        )
                                    }
                                    if (a.attempt > 1) {
                                        PickerStatusTag(
                                            text = "RETRY #${a.attempt}",
                                            bgColor = Color(0xFF450A0A),
                                            textColor = RailBitePickerPalette.SemanticRedBorder,
                                            borderColor = RailBitePickerPalette.SemanticRed,
                                        )
                                    }
                                }
                                Text(
                                    text = "Train ${a.trainNumber} • COACH ${a.coach.uppercase()} • SEAT ${a.berth}",
                                    style = PickerNumericType.AlertHeroCoach,
                                    color = Color.White,
                                )
                                Text(
                                    text = "${shortOrderTag(a.orderId)} • PNR ${a.pnr} • ${a.diners} diner${if (a.diners == 1) "" else "s"}",
                                    style = PickerNumericType.MetaMono,
                                    color = RailBitePickerPalette.TextMuted,
                                )
                            }

                            AlertCountdownRing(
                                secondsLeft = secondsLeft,
                                animatedProgress = animatedProgress,
                            )
                        }

                        // Flat countdown bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(PickerRadii.Pill))
                                .background(RailBitePickerPalette.DarkSlateBorder),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(PickerRadii.Pill))
                                    .background(
                                        if (secondsLeft <= 10) {
                                            RailBitePickerPalette.SemanticRed
                                        } else {
                                            RailBitePickerPalette.CyanPinDigit
                                        },
                                    ),
                            )
                        }
                    }
                }

                // 4. High-Contrast White Items to Dispatch Card
                Surface(
                    shape = RoundedCornerShape(PickerRadii.Card),
                    color = RailBitePickerPalette.SurfaceCard,
                    contentColor = RailBitePickerPalette.TextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "ITEMS TO DISPATCH (${a.items.sumOf { it.qty }})",
                                style = MaterialTheme.typography.labelLarge,
                                color = RailBitePickerPalette.TextSecondary,
                            )
                            Text(
                                text = a.track,
                                style = PickerNumericType.MetaMono,
                                color = RailBitePickerPalette.CyanPressed,
                            )
                        }

                        HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)

                        a.items.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(PickerRadii.Control),
                                    color = RailBitePickerPalette.CyanTint,
                                    border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                                ) {
                                    Text(
                                        text = "${item.qty}×",
                                        style = PickerNumericType.BadgeMono,
                                        color = RailBitePickerPalette.CyanPressed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = RailBitePickerPalette.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = Money.rupees(item.unitPricePaise * item.qty),
                                    style = PickerNumericType.PriceMedium,
                                    color = RailBitePickerPalette.TextPrimary,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(PickerRadii.Button),
                            color = RailBitePickerPalette.DarkSlate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = if (a.paymentMode == "UPI_QUEUED") {
                                        "UPI PRE-PAID • NO CASH"
                                    } else {
                                        "COLLECT CASH AT SEAT"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RailBitePickerPalette.BorderSubtle,
                                )
                                Text(
                                    text = Money.rupees(a.total),
                                    style = PickerNumericType.HeroAmount,
                                    color = RailBitePickerPalette.CyanPinDigit,
                                )
                            }
                        }
                    }
                }

                statusMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(PickerRadii.Button),
                        color = Color(0xFF450A0A),
                        border = BorderStroke(1.dp, RailBitePickerPalette.SemanticRed),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RailBitePickerPalette.SemanticRedBorder,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            // 5. Sticky Bottom Actions (REJECT 35% + ACCEPT ORDER 65%)
            Surface(
                color = RailBitePickerPalette.DarkSlateCell,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.DarkSlateBorder)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (!busy) {
                                        busy = true
                                        AlertSoundController.stop()
                                        statusMessage = "Rejected — re-dispatching..."
                                        AlertActions.reject(activityContext, a) { onFinished() }
                                    }
                                },
                                enabled = !busy,
                                shape = RoundedCornerShape(PickerRadii.Button),
                                border = BorderStroke(1.dp, RailBitePickerPalette.DarkSlateBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = RailBitePickerPalette.DarkSlate,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .weight(0.35f)
                                    .height(PickerMetrics.PrimaryButtonHeight),
                            ) {
                                Text(
                                    text = "REJECT",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                )
                            }

                            Button(
                                onClick = {
                                    if (!busy) {
                                        busy = true
                                        AlertSoundController.stop()
                                        AlertActions.accept(
                                            activityContext,
                                            a,
                                            onAccepted = {
                                                AlertBus.clear()
                                                onAccepted(a.orderId)
                                            },
                                            onFailed = { err ->
                                                busy = false
                                                statusMessage = err
                                                AlertSoundController.start(activityContext)
                                            },
                                        )
                                    }
                                },
                                enabled = !busy,
                                shape = RoundedCornerShape(PickerRadii.Button),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RailBitePickerPalette.SemanticGreen,
                                    contentColor = Color.White,
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                modifier = Modifier
                                    .weight(0.65f)
                                    .height(PickerMetrics.PrimaryButtonHeight),
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else {
                                    Text(
                                        text = "ACCEPT ORDER (${secondsLeft}s) →",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Ignoring for ${IncomingOrderActivity.COUNTDOWN_SECONDS}s automatically re-dispatches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePickerPalette.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCountdownRing(
    secondsLeft: Int,
    animatedProgress: Float,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(76.dp),
    ) {
        Canvas(Modifier.size(76.dp)) {
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = RailBitePickerPalette.DarkSlateBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = if (secondsLeft <= 10) {
                    RailBitePickerPalette.SemanticRed
                } else {
                    RailBitePickerPalette.CyanPinDigit
                },
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${secondsLeft}s",
                style = PickerNumericType.PriceLarge,
                color = Color.White,
            )
        }
    }
}

