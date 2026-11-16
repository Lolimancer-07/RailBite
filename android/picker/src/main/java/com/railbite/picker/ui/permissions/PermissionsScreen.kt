package com.railbite.picker.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteSecondaryButton
import com.railbite.core.ui.theme.Space
import com.railbite.picker.PickerGraph

/**
 * Explains and requests the three things the alert pipeline needs:
 * notification permission, full-screen intent permission (Android 14+) and
 * battery-optimization exemption (so the foreground service keeps MQTT alive).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    graph: PickerGraph,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var perms by remember { mutableStateOf(readAlertPermissions(context)) }

    // Re-read after returning from the system settings screens
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
        topBar = {
            TopAppBar(
                title = { Text("Alert permissions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text(
                "New-order alerts ring at full volume even when the phone is locked. " +
                    "Grant all three so no order is missed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 1. Notifications
            PermCard(
                icon = { Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary) },
                title = "Notifications",
                subtitle = if (perms.notifications) "Allowed" else "Required to show order alerts",
                granted = perms.notifications,
                button = if (perms.notifications) null else {
                    {
                        RailBiteButton(text = "Allow", onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                perms = readAlertPermissions(context)
                            }
                        })
                    }
                },
                extraButton = if (perms.notifications) null else {
                    {
                        RailBiteSecondaryButton(text = "App settings", onClick = ::openAppSettings)
                    }
                },
            )

            // 2. Full-screen intent
            PermCard(
                icon = { Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.primary) },
                title = "Full-screen alerts",
                subtitle = when {
                    perms.fullScreen -> "Allowed — alerts open over the lock screen"
                    Build.VERSION.SDK_INT >= 34 -> "Allow \"full screen notifications\" for this app"
                    else -> "Covered by the notifications permission"
                },
                granted = perms.fullScreen,
                button = if (perms.fullScreen || Build.VERSION.SDK_INT < 34) null else {
                    {
                        RailBiteButton(text = "Allow", onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                        .setData(Uri.parse("package:" + context.packageName))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure { openAppSettings() }
                        })
                    }
                },
            )

            // 3. Battery optimization
            PermCard(
                icon = { Icon(Icons.Filled.BatteryAlert, null, tint = MaterialTheme.colorScheme.primary) },
                title = "Battery optimization",
                subtitle = if (perms.battery) {
                    "Exempted — alerts keep flowing while the screen is off"
                } else {
                    "Exempt the app so the online connection is not killed"
                },
                granted = perms.battery,
                button = if (perms.battery) null else {
                    {
                        RailBiteButton(text = "Allow", onClick = {
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
                        })
                    }
                },
            )

            if (perms.allGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.xs),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "  All set — you are ready to go online",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    granted: Boolean,
    button: (@Composable () -> Unit)?,
    extraButton: (@Composable () -> Unit)? = null,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Text(
                    "  $title",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (button != null || extraButton != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    button?.invoke()
                    extraButton?.invoke()
                }
            }
        }
    }
}
