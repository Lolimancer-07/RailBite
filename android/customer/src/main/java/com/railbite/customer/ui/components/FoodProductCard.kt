package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.railbite.core.model.MenuItem
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii
import com.railbite.customer.ui.theme.RailBiteSelectedBorder

/**
 * Stitch Screen 2 Storefront product tile:
 * `#FFFFFF` flat card with `1.dp` `#E2E8F0` border, `18.dp` food image frame,
 * floating dark-slate `PricePill`, "IRCTC MRP" label, and `ADD` <-> `QuantityStepper`.
 */
@Composable
fun FoodProductCard(
    item: MenuItem,
    qty: Int,
    onInspect: () -> Unit,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val soldOut = item.stock <= 0
    val meta = FoodAssets.metaFor(item)

    Card(
        onClick = onInspect,
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = if (qty > 0 && !soldOut) RailBiteSelectedBorder else RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Recessed 18.dp photo container with top-right floating PricePill & bottom-left pack tag
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(RailBiteRadii.ImageTile))
                    .background(RailBitePalette.ImageTile),
            ) {
                Image(
                    painter = painterResource(id = meta.imageRes),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (soldOut) 0.42f else 1f),
                )

                PricePill(
                    paise = item.mrpPaise,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )

                meta.packTag?.let { tag ->
                    Surface(
                        shape = RoundedCornerShape(RailBiteRadii.Control),
                        color = RailBitePalette.SurfaceCard.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, RailBitePalette.BorderSubtle),
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            VegMark(isVeg = item.isVeg)
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = RailBitePalette.TextPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (soldOut) RailBitePalette.TextMuted else RailBitePalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            MrpBadge(soldOut = soldOut)

            ProductAddAction(
                qty = qty,
                soldOut = soldOut,
                itemName = item.name,
                maxStock = item.stock,
                onAdd = onAdd,
                onDecrement = onDecrement,
            )
        }
    }
}

