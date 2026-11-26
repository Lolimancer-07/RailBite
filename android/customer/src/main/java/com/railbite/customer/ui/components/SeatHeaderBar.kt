package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

/**
 * Stitch Storefront sticky header:
 * Brand mark + live Pantry status dot + settings trigger (`44.dp`), plus tappable
 * cyan-tinted seat chip (`Coach B2 • Berth 14`) & `~10 min` delivery strip.
 */
@Composable
fun SeatHeaderBar(
    seatLabel: String,
    online: Boolean,
    onSeatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RailBitePalette.SurfaceCard)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Row 1: Brand badge + status pill + 44dp Settings gear
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Control),
                    color = RailBitePalette.CyanPrimary,
                    contentColor = Color.White,
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Train,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = "RAILBITE EXPRESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = RailBitePalette.TextSecondary,
                    )
                    Text(
                        text = "Storefront",
                        style = MaterialTheme.typography.titleMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                }

                Surface(
                    onClick = onSettingsClick,
                    shape = RoundedCornerShape(RailBiteRadii.Pill),
                    color = RailBitePalette.SurfaceCard,
                    border = BorderStroke(1.dp, RailBitePalette.BorderSubtle),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .heightIn(min = 36.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        StatusDot(online = online)
                        Text(
                            text = if (online) "Live" else "Offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = RailBitePalette.TextPrimary,
                        )
                    }
                }

                Surface(
                    onClick = onSettingsClick,
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    color = RailBitePalette.ImageTile,
                    border = BorderStroke(1.dp, RailBitePalette.BorderSubtle),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .size(RailBiteMetrics.TouchTargetMin)
                        .semantics { contentDescription = "Settings" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = RailBitePalette.TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Row 2: Tappable Seat Pill + "~10 min" delivery label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    onClick = onSeatClick,
                    shape = RoundedCornerShape(RailBiteRadii.Pill),
                    color = RailBitePalette.CyanTint,
                    contentColor = RailBitePalette.CyanPressed,
                    border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                    shadowElevation = 0.dp,
                    modifier = Modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = seatLabel,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Edit seat",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Pill),
                    color = RailBitePalette.Canvas,
                    border = BorderStroke(1.dp, RailBitePalette.BorderSubtle),
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = RailBitePalette.CyanPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "~10 min",
                            style = MaterialTheme.typography.labelMedium,
                            color = RailBitePalette.TextPrimary,
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
    }
}
