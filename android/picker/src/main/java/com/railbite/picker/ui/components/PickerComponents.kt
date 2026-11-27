package com.railbite.picker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.railbite.picker.ui.theme.PickerCardBorder
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.RailBitePickerPalette

/** High-contrast `#0F172A` seat coordinate pill (`B2 • 14`). */
@Composable
fun PickerSeatPill(
    coach: String,
    berth: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(PickerRadii.Control),
        color = RailBitePickerPalette.DarkSlate,
        modifier = modifier,
    ) {
        Text(
            text = "${coach.uppercase()} • $berth",
            style = PickerNumericType.BadgeMono,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Semantic status dot (`#16A34A` live / `#DC2626` offline). */
@Composable
fun PickerStatusDot(
    online: Boolean,
    modifier: Modifier = Modifier,
    colorOverride: Color? = null,
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                colorOverride ?: if (online) {
                    RailBitePickerPalette.SemanticGreen
                } else {
                    RailBitePickerPalette.SemanticRed
                },
            ),
    )
}

/** Crisp flat status badge (`PACKING`, `DELIVERING`, `CONFIRMED`, etc.). */
@Composable
fun PickerStatusTag(
    text: String,
    bgColor: Color,
    textColor: Color,
    borderColor: Color? = null,
    dotColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(PickerRadii.Pill),
        color = bgColor,
        border = borderColor?.let { BorderStroke(1.dp, it) },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            if (dotColor != null) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Primary flat cyan CTA (`56.dp` height, `12.dp` radius, zero shadow). */
@Composable
fun PickerPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    containerColor: Color = RailBitePickerPalette.CyanPrimary,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(PickerRadii.Button),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = RailBitePickerPalette.ImageTile,
            disabledContentColor = RailBitePickerPalette.TextMuted,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
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
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Flat 2-tab bottom navigation bar (`Orders` | `Cash`) with `navigationBarsPadding()`. */
enum class PickerTab { ORDERS, CASH }

@Composable
fun PickerBottomBar(
    selectedTab: PickerTab,
    onSelectTab: (PickerTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = RailBitePickerPalette.SurfaceCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
            ) {
                BottomTabItem(
                    label = "Orders",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    selected = selectedTab == PickerTab.ORDERS,
                    onClick = { onSelectTab(PickerTab.ORDERS) },
                    modifier = Modifier.weight(1f),
                )
                BottomTabItem(
                    label = "Cash",
                    icon = Icons.Filled.Payments,
                    selected = selectedTab == PickerTab.CASH,
                    onClick = { onSelectTab(PickerTab.CASH) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) RailBitePickerPalette.CyanPrimary else RailBitePickerPalette.TextSecondary
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = PickerMetrics.TouchTargetMin)
            .clip(RoundedCornerShape(PickerRadii.Button))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = tint,
            )
        }
    }
}

/** Flat 4-cell PIN display backed by an input (supports digits or dots for masked PINs). */
@Composable
fun PinCellsRow(
    digits: String,
    masked: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        repeat(4) { index ->
            val ch = digits.getOrNull(index)
            val isCurrent = focused && index == digits.length.coerceAtMost(3)
            Surface(
                shape = RoundedCornerShape(PickerRadii.Button),
                color = RailBitePickerPalette.ImageTile,
                border = BorderStroke(
                    width = if (isCurrent) 1.5.dp else 1.dp,
                    color = if (isCurrent) RailBitePickerPalette.CyanPrimary else RailBitePickerPalette.BorderSubtle,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (ch != null) {
                        if (masked) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(RailBitePickerPalette.DarkSlate),
                            )
                        } else {
                            Text(
                                text = ch.toString(),
                                style = PickerNumericType.PinDigit,
                                color = RailBitePickerPalette.DarkSlate,
                            )
                        }
                    } else {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(RailBitePickerPalette.BorderSubtle),
                        )
                    }
                }
            }
        }
    }
}

/** Flat error banner matching the Stitch palette. */
@Composable
fun PickerErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(PickerRadii.Button),
        color = RailBitePickerPalette.SemanticRedTint,
        border = BorderStroke(1.dp, RailBitePickerPalette.SemanticRedBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = RailBitePickerPalette.SemanticRed,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** Flat hairline-bordered section card. */
@Composable
fun PickerSectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = RailBitePickerPalette.SurfaceCard,
    border: BorderStroke = PickerCardBorder,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(PickerRadii.Card),
        color = containerColor,
        border = border,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        content()
    }
}

/** Formats short order display tag (`#RB-<6 chars>` or `#<8 chars>`). */
fun shortOrderTag(orderId: String): String =
    "#RB-${orderId.take(6).uppercase()}"

/** Formats ISO-8601 timestamp to `HH:mm` in local time. */
fun formatLocalTime(iso: String): String = runCatching {
    java.time.Instant.parse(iso)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault("")

