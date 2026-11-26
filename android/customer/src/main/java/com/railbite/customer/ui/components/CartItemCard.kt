package com.railbite.customer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.railbite.core.util.Money
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

/**
 * Stitch Screen 4 cart line card:
 * Individual `16.dp` `#FFFFFF` row card on `#F4F6F8` canvas with `18.dp` food thumbnail,
 * veg indicator, name, tabular line total, and cyan `QuantityStepper`.
 */
@Composable
fun CartItemCard(
    name: String,
    category: String,
    isVeg: Boolean,
    unitPricePaise: Int,
    qty: Int,
    maxStock: Int,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineTotalPaise = unitPricePaise * qty
    val imgRes = FoodAssets.drawableFor(name, category)

    Card(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(RailBiteRadii.ImageTile))
                    .background(RailBitePalette.ImageTile),
            ) {
                Image(
                    painter = painterResource(id = imgRes),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VegMark(isVeg = isVeg)
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = RailBitePalette.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = Money.rupees(lineTotalPaise),
                        style = RailBiteNumericType.PriceMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                    if (qty > 1) {
                        Text(
                            text = "(${Money.rupees(unitPricePaise)} × $qty)",
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePalette.TextSecondary,
                        )
                    }
                }
            }

            QuantityStepper(
                quantity = qty,
                itemName = name,
                canIncrement = qty < minOf(10, maxStock),
                onAdd = onAdd,
                onRemove = onDecrement,
                modifier = Modifier.width(122.dp),
            )
        }
    }
}

