package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

/**
 * Slim high-contrast `#0F172A` floating "View Cart" pill right above the Home bottom bar.
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
        shape = RoundedCornerShape(RailBiteRadii.Pill),
        color = RailBitePalette.DarkSlate,
        contentColor = Color.White,
        border = BorderStroke(1.dp, RailBitePalette.DarkSlateBorder),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .heightIn(min = RailBiteMetrics.TouchTargetMin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ShoppingBag,
                contentDescription = null,
                tint = RailBitePalette.CyanPinDigit,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "$itemCount item${if (itemCount == 1) "" else "s"} • ${Money.rupees(totalPaise)}",
                style = RailBiteNumericType.PriceMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Pill),
                color = RailBitePalette.CyanPrimary,
                contentColor = Color.White,
                shadowElevation = 0.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(text = "View Cart", style = MaterialTheme.typography.labelLarge)
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
