package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.railbite.core.util.Money
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

/** Floating high-contrast `#0F172A` capsule displaying tabular ₹ MRP over food artwork. */
@Composable
fun PricePill(
    paise: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(RailBiteRadii.Pill),
        color = RailBitePalette.DarkSlate,
        contentColor = Color.White,
        border = BorderStroke(1.dp, RailBitePalette.DarkSlateBorder),
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Text(
            text = Money.rupees(paise),
            style = RailBiteNumericType.PricePill,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Green/red live server & MQTT connection indicator dot. */
@Composable
fun StatusDot(
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    val dotColor = if (online) RailBitePalette.SemanticGreen else RailBitePalette.SemanticRed
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(dotColor),
    )
}

/** Semantic green "IRCTC MRP" verification label / compact badge. */
@Composable
fun MrpBadge(
    label: String = "IRCTC MRP",
    soldOut: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        if (soldOut) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(RailBitePalette.SemanticRed),
            )
            Text(
                text = "STOCK EXHAUSTED",
                style = MaterialTheme.typography.labelSmall,
                color = RailBitePalette.SemanticRed,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = RailBitePalette.SemanticGreen,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = RailBitePalette.SemanticGreen,
            )
        }
    }
}

/** Flat square veg / non-veg symbol matching FSSAI food safety guidelines. */
@Composable
fun VegMark(
    isVeg: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (isVeg) RailBitePalette.VegGreen else RailBitePalette.NonVegRed
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White)
            .border(1.dp, tint, RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint),
        )
    }
}

/** Flat pill category filter chip with ≥44 dp touch target (solid cyan when selected per Stitch sketch). */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(RailBiteRadii.Pill),
        color = if (selected) RailBitePalette.CyanPrimary else RailBitePalette.SurfaceCard,
        contentColor = if (selected) Color.White else RailBitePalette.TextPrimary,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) RailBitePalette.CyanPrimary else RailBitePalette.BorderSubtle,
        ),
        shadowElevation = 0.dp,
        modifier = modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

