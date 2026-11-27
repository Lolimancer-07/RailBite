package com.railbite.picker.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.railbite.core.mesh.BlePermissions
import com.railbite.picker.PickerGraph
import com.railbite.picker.ui.components.LocalPickerBleActionRequester
import com.railbite.picker.ui.components.PickerPrimaryButton
import com.railbite.picker.ui.components.PickerSectionCard
import com.railbite.picker.ui.components.PickerStatusTag
import com.railbite.picker.ui.theme.PickerCardBorder
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.RailBitePickerPalette

/**
 * Explains and requests the three permissions required for reliable lock-screen order alerts:
 * notifications, full-screen intent (Android 14+), and battery optimization exemption.
 */
@Composable
fun PermissionsScreen(
    graph: PickerGraph,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val requestBle = LocalPickerBleActionRequester.current
    var perms by remember { mutableStateOf(readAlertPermissions(context)) }
    val bleReady = BlePermissions.isReadyForMesh(context)

    // Re-read after returning from system settings screens
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) perms = readAlertPermissions(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { perms = readAlertPermissions(context) }

    fun openAppSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    Scaffold(
        containerColor = RailBitePickerPalette.Canvas,
        topBar = {
            Surface(color = RailBitePickerPalette.SurfaceCard) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RailBitePickerPalette.TextPrimary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "ALERT RELIABILITY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = RailBitePickerPalette.CyanPrimary,
                            )
                            Text(
                                text = "Alert Permissions",
                                style = MaterialTheme.typography.titleLarge,
                                color = RailBitePickerPalette.TextPrimary,
                            )
                        }
                        PickerStatusTag(
                            text = "${perms.grantedCount}/3 Ready",
                            bgColor = if (perms.allGranted) RailBitePickerPalette.SemanticGreenTint else RailBitePickerPalette.SemanticAmberTint,
                            textColor = if (perms.allGranted) RailBitePickerPalette.SemanticGreenInk else RailBitePickerPalette.SemanticAmberInk,
                            borderColor = if (perms.allGranted) RailBitePickerPalette.SemanticGreenBorder else RailBitePickerPalette.SemanticAmberBorder,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = PickerMetrics.ScreenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "New-order alerts ring loudly even when your phone is locked inside the pantry car. Grant all three so zero coach orders are missed.",
                style = MaterialTheme.typography.bodyMedium,
                color = RailBitePickerPalette.TextSecondary,
            )

            // 1. Notifications
            PermCard(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = if (perms.notifications) RailBitePickerPalette.SemanticGreen else RailBitePickerPalette.CyanPrimary,
                    )
                },
                title = "Notifications",
                subtitle = if (perms.notifications) {
                    "Allowed — live foreground service and order alerts enabled"
                } else {
                    "Required to display new-order alerts and keep shift connection alive"
                },
                granted = perms.notifications,
                primaryLabel = "Allow Notifications",
                onPrimaryClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        perms = readAlertPermissions(context)
                    }
                },
                secondaryLabel = "Open App Settings",
                onSecondaryClick = ::openAppSettings,
            )

            // 2. Full-screen intent
            PermCard(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Alarm,
                        contentDescription = null,
                        tint = if (perms.fullScreen) RailBitePickerPalette.SemanticGreen else RailBitePickerPalette.CyanPrimary,
                    )
                },
                title = "Full-Screen Alerts",
                subtitle = when {
                    perms.fullScreen -> "Allowed — incoming orders take over the lock screen"
                    Build.VERSION.SDK_INT >= 34 -> "Allow \"full screen notifications\" for instant lock-screen takeover"
                    else -> "Covered automatically by Notifications permission"
                },
                granted = perms.fullScreen,
                primaryLabel = if (Build.VERSION.SDK_INT >= 34) "Allow Full-Screen Intent" else null,
                onPrimaryClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .setData(Uri.parse("package:" + context.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure { openAppSettings() }
                },
            )

            // 3. Battery optimization
            PermCard(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.BatteryAlert,
                        contentDescription = null,
                        tint = if (perms.battery) RailBitePickerPalette.SemanticGreen else RailBitePickerPalette.CyanPrimary,
                    )
                },
                title = "Battery Optimization",
                subtitle = if (perms.battery) {
                    "Exempted — MQTT alert socket stays active when screen is locked"
                } else {
                    "Disable battery optimization so Android does not suspend alerts mid-shift"
                },
                granted = perms.battery,
                primaryLabel = "Exempt App",
                onPrimaryClick = {
                    val launched = runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:" + context.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.isSuccess
                    if (!launched) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure { openAppSettings() }
                    }
                },
            )

            // 4. Nearby Bluetooth & BLE Mesh
            PermCard(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = if (bleReady) RailBitePickerPalette.SemanticGreen else RailBitePickerPalette.CyanPrimary,
                    )
                },
                title = "Bluetooth Mesh (Offline Orders)",
                subtitle = if (bleReady) {
                    "Ready — offline 23B multi-hop BLE dispatch & quick-chat enabled"
                } else if (!BlePermissions.requiredPermissions(context).isEmpty()) {
                    "Grant Nearby Devices Bluetooth permission to receive offline BLE orders"
                } else {
                    "Turn on Bluetooth to receive coach orders when Wi-Fi is unavailable"
                },
                granted = bleReady,
                primaryLabel = if (!BlePermissions.requiredPermissions(context).isEmpty()) {
                    "Grant Bluetooth Permission"
                } else {
                    "Enable Bluetooth"
                },
                onPrimaryClick = requestBle,
            )

            if (perms.allGranted) {
                PickerSectionCard(
                    containerColor = RailBitePickerPalette.SemanticGreenTint,
                    border = BorderStroke(1.dp, RailBitePickerPalette.SemanticGreenBorder),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = RailBitePickerPalette.SemanticGreen,
                        )
                        Text(
                            text = "All set — lock-screen ringers and background alerts are ready.",
                            style = MaterialTheme.typography.titleSmall,
                            color = RailBitePickerPalette.SemanticGreenInk,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PermCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    granted: Boolean,
    primaryLabel: String? = null,
    onPrimaryClick: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    PickerSectionCard(
        containerColor = if (granted) RailBitePickerPalette.SurfaceCard else RailBitePickerPalette.SurfaceCard,
        border = if (granted) {
            BorderStroke(1.dp, RailBitePickerPalette.SemanticGreenBorder)
        } else {
            PickerCardBorder
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(PickerRadii.Button))
                        .background(
                            if (granted) RailBitePickerPalette.SemanticGreenTint else RailBitePickerPalette.CyanTint,
                        ),
                ) {
                    icon()
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = RailBitePickerPalette.TextPrimary,
                    )
                }
                PickerStatusTag(
                    text = if (granted) "ALLOWED" else "REQUIRED",
                    bgColor = if (granted) RailBitePickerPalette.SemanticGreenTint else RailBitePickerPalette.SemanticAmberTint,
                    textColor = if (granted) RailBitePickerPalette.SemanticGreenInk else RailBitePickerPalette.SemanticAmberInk,
                    borderColor = if (granted) RailBitePickerPalette.SemanticGreenBorder else RailBitePickerPalette.SemanticAmberBorder,
                )
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = RailBitePickerPalette.TextSecondary,
            )

            if (!granted && primaryLabel != null && onPrimaryClick != null) {
                PickerPrimaryButton(
                    text = primaryLabel,
                    onClick = onPrimaryClick,
                    modifier = Modifier.height(PickerMetrics.TouchTargetMin),
                )
            }

            if (!granted && secondaryLabel != null && onSecondaryClick != null) {
                OutlinedButton(
                    onClick = onSecondaryClick,
                    shape = RoundedCornerShape(PickerRadii.Button),
                    border = PickerCardBorder,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RailBitePickerPalette.TextPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PickerMetrics.TouchTargetMin),
                ) {
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
