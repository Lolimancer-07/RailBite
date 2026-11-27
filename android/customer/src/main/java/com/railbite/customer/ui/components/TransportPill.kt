package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.railbite.core.mesh.TransportMode
import com.railbite.core.mesh.TransportStatus
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

/** Hook allowing any Customer Compose screen to trigger runtime BLE permissions or BT enable dialog. */
val LocalBleActionRequester = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Clean flat transport indicator pill displayed across Customer screens:
 * `"LAN Connected"`, `"BLE Mesh Offline (Active)"`, or `"BLE Off — Enable Bluetooth"`.
 */
@Composable
fun TransportIndicatorPill(
    status: TransportStatus,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (bgColor, borderColor, inkColor, icon) = when (status.mode) {
        TransportMode.LAN_CONNECTED -> Tuple4(
            RailBitePalette.SemanticGreenTint,
            RailBitePalette.SemanticGreenBorder,
            RailBitePalette.SemanticGreen,
            Icons.Filled.Wifi,
        )
        TransportMode.BLE_MESH_ACTIVE -> Tuple4(
            RailBitePalette.CyanTint,
            RailBitePalette.CyanBorder,
            RailBitePalette.CyanPressed,
            Icons.Filled.Bluetooth,
        )
        TransportMode.BLE_DISABLED -> Tuple4(
            RailBitePalette.SemanticAmberTint,
            RailBitePalette.SemanticAmberBorder,
            RailBitePalette.SemanticAmber,
            Icons.Filled.BluetoothDisabled,
        )
    }

    val surfaceContent: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = inkColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = inkColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(RailBiteRadii.Pill),
            color = bgColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 0.dp,
            modifier = modifier,
        ) { surfaceContent() }
    } else {
        Surface(
            shape = RoundedCornerShape(RailBiteRadii.Pill),
            color = bgColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 0.dp,
            modifier = modifier,
        ) { surfaceContent() }
    }
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
