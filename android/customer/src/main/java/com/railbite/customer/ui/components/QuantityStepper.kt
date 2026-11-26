package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

/**
 * `- 1 +` stepper with ≥44 dp minimum touch targets.
 * Supports solid `#0891B2` style (Cart / Sheet) and Stitch-wireframe outlined pill style (Storefront).
 */
@Composable
fun QuantityStepper(
    quantity: Int,
    itemName: String,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    canIncrement: Boolean = true,
    outlinedStyle: Boolean = false,
    pillShape: Boolean = false,
) {
    val bgColor = if (outlinedStyle) RailBitePalette.SurfaceCard else RailBitePalette.CyanPrimary
    val contentColor = if (outlinedStyle) RailBitePalette.CyanPrimary else Color.White
    val digitColor = if (outlinedStyle) RailBitePalette.TextPrimary else Color.White
    val border = if (outlinedStyle) BorderStroke(1.dp, RailBitePalette.CyanPrimary) else null
    val shape = RoundedCornerShape(if (pillShape) RailBiteRadii.Pill else RailBiteRadii.Button)

    Surface(
        shape = shape,
        color = bgColor,
        contentColor = contentColor,
        border = border,
        shadowElevation = 0.dp,
        modifier = modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.defaultMinSize(minWidth = 96.dp),
        ) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .defaultMinSize(minWidth = 36.dp, minHeight = RailBiteMetrics.TouchTargetMin)
                    .semantics { contentDescription = "Decrease quantity of $itemName" },
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = "$quantity",
                style = RailBiteNumericType.PriceMedium,
                color = digitColor,
                textAlign = TextAlign.Center,
            )

            IconButton(
                onClick = onAdd,
                enabled = canIncrement,
                modifier = Modifier
                    .defaultMinSize(minWidth = 36.dp, minHeight = RailBiteMetrics.TouchTargetMin)
                    .semantics { contentDescription = "Increase quantity of $itemName" },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = if (canIncrement) contentColor else contentColor.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Compact bottom-right action inside Storefront `FoodProductCard` (matching wireframe Stitch `acf3d694`):
 * `+ ADD` cyan-bordered pill morphing into `- qty +` [QuantityStepper] (`44.dp` touch height), or dimmed `Sold Out`.
 */
@Composable
fun ProductAddAction(
    qty: Int,
    soldOut: Boolean,
    itemName: String,
    maxStock: Int,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        soldOut -> {
            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Pill),
                color = RailBitePalette.SemanticRedTint,
                contentColor = RailBitePalette.SemanticRed,
                border = BorderStroke(1.dp, RailBitePalette.SemanticRedBorder),
                shadowElevation = 0.dp,
                modifier = modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Sold Out",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        qty <= 0 -> {
            Surface(
                onClick = onAdd,
                shape = RoundedCornerShape(RailBiteRadii.Pill),
                color = RailBitePalette.SurfaceCard,
                contentColor = RailBitePalette.CyanPrimary,
                border = BorderStroke(1.dp, RailBitePalette.CyanPrimary),
                shadowElevation = 0.dp,
                modifier = modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = " ADD",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        else -> {
            QuantityStepper(
                quantity = qty,
                itemName = itemName,
                canIncrement = qty < minOf(10, maxStock),
                outlinedStyle = true,
                pillShape = true,
                onAdd = onAdd,
                onRemove = onDecrement,
                modifier = modifier,
            )
        }
    }
}
