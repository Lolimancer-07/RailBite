package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
 * Cyan `- 1 +` pill stepper enforcing ≥44 dp touch targets for moving train coaches.
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
) {
    val bgColor = if (outlinedStyle) RailBitePalette.SurfaceCard else RailBitePalette.CyanPrimary
    val contentColor = if (outlinedStyle) RailBitePalette.TextPrimary else Color.White
    val border = if (outlinedStyle) BorderStroke(1.dp, RailBitePalette.BorderSubtle) else null

    Surface(
        shape = RoundedCornerShape(RailBiteRadii.Button),
        color = bgColor,
        contentColor = contentColor,
        border = border,
        shadowElevation = 0.dp,
        modifier = modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(RailBiteMetrics.TouchTargetMin)
                    .semantics { contentDescription = "Decrease quantity of $itemName" },
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            Text(
                text = "$quantity",
                style = RailBiteNumericType.PriceMedium,
                color = contentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            IconButton(
                onClick = onAdd,
                enabled = canIncrement,
                modifier = Modifier
                    .size(RailBiteMetrics.TouchTargetMin)
                    .semantics { contentDescription = "Increase quantity of $itemName" },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = if (canIncrement) contentColor else contentColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Flat `ADD` CTA morphing into [QuantityStepper] when qty > 0, or dimmed `Sold Out` state.
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
                shape = RoundedCornerShape(RailBiteRadii.Button),
                color = RailBitePalette.SemanticRedTint,
                contentColor = RailBitePalette.SemanticRed,
                border = BorderStroke(1.dp, RailBitePalette.SemanticRedBorder),
                shadowElevation = 0.dp,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = RailBiteMetrics.TouchTargetMin),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Sold Out",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        qty <= 0 -> {
            Surface(
                onClick = onAdd,
                shape = RoundedCornerShape(RailBiteRadii.Button),
                color = RailBitePalette.SurfaceCard,
                contentColor = RailBitePalette.CyanPrimary,
                border = BorderStroke(1.dp, RailBitePalette.BorderSubtle),
                shadowElevation = 0.dp,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = RailBiteMetrics.TouchTargetMin),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
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
                onAdd = onAdd,
                onRemove = onDecrement,
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}

