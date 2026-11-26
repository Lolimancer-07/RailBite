package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.railbite.core.util.Money
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

/**
 * Floating dark-slate `#0F172A` cart bar pinned above the bottom navigation bar (`Home`, `Orders`)
 * with `navigationBarsPadding()` support.
 */
@Composable
fun FloatingCartBar(
    itemCount: Int,
    totalPaise: Int,
    seatSummary: String?,
    onOpenCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpenCart,
        shape = RoundedCornerShape(RailBiteRadii.Card),
        color = RailBitePalette.DarkSlate,
        contentColor = Color.White,
        border = BorderStroke(1.dp, RailBitePalette.DarkSlateBorder),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = RailBitePalette.DarkSlateCell,
                border = BorderStroke(1.dp, RailBitePalette.DarkSlateBorder),
                shadowElevation = 0.dp,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ShoppingBag,
                        contentDescription = null,
                        tint = RailBitePalette.CyanPinDigit,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "$itemCount item${if (itemCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = RailBitePalette.TextMuted,
                    )
                    Text(
                        text = Money.rupees(totalPaise),
                        style = RailBiteNumericType.PriceMedium,
                        color = RailBitePalette.CyanPinDigit,
                    )
                }
                Text(
                    text = seatSummary?.let { "Delivering to $it" } ?: "Tap to review cart",
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextMuted,
                    maxLines = 1,
                )
            }

            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Pill),
                color = RailBitePalette.CyanPrimary,
                contentColor = Color.White,
                shadowElevation = 0.dp,
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "View Cart",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

