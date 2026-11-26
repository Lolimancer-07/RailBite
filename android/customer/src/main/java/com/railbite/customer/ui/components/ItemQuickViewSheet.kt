package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.railbite.core.model.MenuItem
import com.railbite.core.util.Money
import com.railbite.customer.ui.theme.RailBiteBottomSheetShape
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

/**
 * Stitch Screen 3 Item Quick-View Bottom Sheet:
 * Material 3 `ModalBottomSheet` (`24.dp` top corners) with food hero tile, live stock pill,
 * delivery estimate to seat, highlights, inline `QuantityStepper`, and solid `#0891B2` CTA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemQuickViewSheet(
    item: MenuItem,
    currentCartQty: Int,
    seatLabel: String?,
    onDismiss: () -> Unit,
    onApplyQuantity: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val meta = remember(item.id) { FoodAssets.metaFor(item) }
    val soldOut = item.stock <= 0
    val maxAllowed = minOf(10, item.stock)
    var selectedQty by remember(item.id, currentCartQty) {
        mutableIntStateOf(
            when {
                soldOut -> 0
                currentCartQty > 0 -> currentCartQty.coerceAtMost(maxAllowed)
                else -> 1
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RailBiteBottomSheetShape,
        containerColor = RailBitePalette.SurfaceCard,
        scrimColor = RailBitePalette.DarkSlate.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(RailBiteRadii.Pill))
                    .background(RailBitePalette.BorderSubtle),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Hero 18.dp image band
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(RoundedCornerShape(RailBiteRadii.ImageTile))
                        .background(RailBitePalette.ImageTile),
                ) {
                    Image(
                        painter = painterResource(id = meta.imageRes),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    PricePill(
                        paise = item.mrpPaise,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(RailBiteRadii.Control),
                        color = RailBitePalette.SurfaceCard.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, RailBitePalette.BorderSubtle),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            VegMark(isVeg = item.isVeg)
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelMedium,
                                color = RailBitePalette.TextPrimary,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                    Text(
                        text = meta.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RailBitePalette.TextSecondary,
                    )
                }

                // Stock & delivery estimate flat rows
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    color = if (soldOut) RailBitePalette.SemanticRedTint else RailBitePalette.SemanticGreenTint,
                    border = BorderStroke(
                        1.dp,
                        if (soldOut) RailBitePalette.SemanticRedBorder else RailBitePalette.SemanticGreenBorder,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = if (soldOut) RailBitePalette.SemanticRed else RailBitePalette.SemanticGreen,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (soldOut) "Currently out of stock in Pantry Car" else "${item.stock} left in Pantry Car",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (soldOut) RailBitePalette.SemanticRed else RailBitePalette.SemanticGreen,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    color = RailBitePalette.Canvas,
                    border = RailBiteCardBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = RailBitePalette.CyanPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Column {
                            Text(
                                text = seatLabel?.let { "To $it" } ?: "Berth Delivery",
                                style = MaterialTheme.typography.labelSmall,
                                color = RailBitePalette.TextSecondary,
                            )
                            Text(
                                text = "${item.prepMinutes.coerceAtLeast(5)}–${(item.prepMinutes + 5).coerceAtLeast(10)} mins",
                                style = MaterialTheme.typography.labelLarge,
                                color = RailBitePalette.TextPrimary,
                            )
                        }
                    }
                }

                // Meal Highlights enclosure
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Card),
                    color = RailBitePalette.Canvas,
                    border = RailBiteCardBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = RailBitePalette.SemanticGreen,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Pantry Quality & Highlights",
                                style = MaterialTheme.typography.titleSmall,
                                color = RailBitePalette.TextPrimary,
                            )
                        }
                        meta.highlights.forEach { bullet ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = RailBitePalette.CyanPrimary,
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .size(12.dp),
                                )
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RailBitePalette.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)

            // Bottom sticky action bar: stepper + solid cyan CTA
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RailBitePalette.SurfaceCard)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                if (!soldOut) {
                    QuantityStepper(
                        quantity = selectedQty,
                        itemName = item.name,
                        outlinedStyle = true,
                        canIncrement = selectedQty < maxAllowed,
                        onAdd = { if (selectedQty < maxAllowed) selectedQty++ },
                        onRemove = {
                            val floor = if (currentCartQty > 0) 0 else 1
                            if (selectedQty > floor) selectedQty--
                        },
                        modifier = Modifier.width(128.dp),
                    )
                }

                val ctaLabel = when {
                    soldOut -> "Sold Out"
                    selectedQty == 0 && currentCartQty > 0 -> "Remove Item"
                    currentCartQty > 0 -> "Update Cart"
                    else -> "Add to Cart"
                }

                Button(
                    onClick = {
                        onApplyQuantity(selectedQty)
                        onDismiss()
                    },
                    enabled = !soldOut,
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedQty == 0 && currentCartQty > 0) {
                            RailBitePalette.SemanticRed
                        } else {
                            RailBitePalette.CyanPrimary
                        },
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(RailBiteMetrics.PrimaryButtonHeight),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = ctaLabel, style = MaterialTheme.typography.labelLarge)
                        if (!soldOut && selectedQty > 0) {
                            Text(
                                text = Money.rupees(item.mrpPaise * selectedQty),
                                style = RailBiteNumericType.PriceMedium,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
