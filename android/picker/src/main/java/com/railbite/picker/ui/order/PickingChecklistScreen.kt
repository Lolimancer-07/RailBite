package com.railbite.picker.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.railbite.core.model.Order
import com.railbite.core.model.OrderItem
import com.railbite.core.util.Money
import com.railbite.picker.ui.components.PickerErrorBanner
import com.railbite.picker.ui.components.PickerFoodAssets
import com.railbite.picker.ui.components.PickerPrimaryButton
import com.railbite.picker.ui.components.PickerSeatPill
import com.railbite.picker.ui.components.PickerSectionCard
import com.railbite.picker.ui.components.PickerStatusTag
import com.railbite.picker.ui.components.shortOrderTag
import com.railbite.picker.ui.theme.PickerCardBorder
import com.railbite.picker.ui.theme.PickerMetrics
import com.railbite.picker.ui.theme.PickerNumericType
import com.railbite.picker.ui.theme.PickerRadii
import com.railbite.picker.ui.theme.RailBitePickerPalette
import kotlin.math.abs

private val GROUP_DISPLAY_ORDER = listOf("BEVERAGES", "WATER", "MEALS", "BREAKFAST", "SNACKS")

private data class ItemRackGroup(
    val meta: PickerFoodAssets.RackMeta,
    val items: List<OrderItem>,
)

private fun formatSlaTimer(slaRemainingSeconds: Int): Pair<String, Boolean> {
    val overdue = slaRemainingSeconds < 0
    val absSec = abs(slaRemainingSeconds)
    val mins = absSec / 60
    val secs = absSec % 60
    val formatted = "%02d:%02d".format(mins, secs)
    return if (overdue) "+$formatted OVER" to true else "$formatted SLA" to false
}

/**
 * Stitch Screen 4 (`7001963e`): Picking Checklist & Packed Action.
 * Sticky seat/SLA strip, unread customer message banner, category-grouped items with
 * local drawables, oversized cyan `×N` quantity badges, 48.dp touch checkboxes, and
 * 56.dp sticky bottom action unlocking "Packed — Start Delivery →".
 */
@Composable
fun PickingChecklistScreen(
    order: Order,
    state: ProgressUiState,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onTickItem: (String) -> Unit,
    onStartPickup: () -> Unit,
) {
    val items = order.items.orEmpty()
    val totalCount = items.size
    val totalQty = items.sumOf { it.qty }
    val pickedCount = items.count { state.checklist[it.itemId] == true }
    val allPacked = totalCount == 0 || pickedCount == totalCount
    val remainingCount = (totalCount - pickedCount).coerceAtLeast(0)
    val pct = if (totalCount == 0) 100 else ((pickedCount * 100) / totalCount).coerceIn(0, 100)
    val (slaLabel, slaOverdue) = formatSlaTimer(state.slaRemainingSeconds)

    val latestCustomerMsg = state.latestUnreadPreview
        ?: state.chatMessages.lastOrNull { it.senderRole.equals("CUSTOMER", ignoreCase = true) }?.body

    val groupedItems = remember(items) {
        val byKey = LinkedHashMap<String, Pair<PickerFoodAssets.RackMeta, MutableList<OrderItem>>>()
        items.forEach { item ->
            val meta = PickerFoodAssets.rackMetaFor(item.name ?: item.itemId, item.category)
            val entry = byKey.getOrPut(meta.groupKey) { meta to mutableListOf() }
            entry.second.add(item)
        }
        GROUP_DISPLAY_ORDER.mapNotNull { key ->
            byKey[key]?.let { (meta, list) -> ItemRackGroup(meta, list) }
        } + byKey.filterKeys { it !in GROUP_DISPLAY_ORDER }.values.map { (meta, list) ->
            ItemRackGroup(meta, list)
        }
    }

    Scaffold(
        containerColor = RailBitePickerPalette.Canvas,
        topBar = {
            Surface(color = RailBitePickerPalette.SurfaceCard) {
                Column(Modifier.statusBarsPadding()) {
                    // Top header row: Back + Title + Circular Message button with badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RailBitePickerPalette.TextPrimary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Picking Checklist",
                                style = MaterialTheme.typography.titleLarge,
                                color = RailBitePickerPalette.TextPrimary,
                            )
                            Text(
                                text = "${shortOrderTag(order.id)} • ${if (order.paymentMode == "UPI_QUEUED") "UPI Digital" else "Cash on Delivery"}",
                                style = PickerNumericType.MetaMono,
                                color = RailBitePickerPalette.TextSecondary,
                            )
                        }

                        Box(modifier = Modifier.padding(end = 6.dp)) {
                            Surface(
                                onClick = onOpenChat,
                                shape = CircleShape,
                                color = if (state.unreadChatCount > 0) {
                                    RailBitePickerPalette.CyanTint
                                } else {
                                    RailBitePickerPalette.Canvas
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (state.unreadChatCount > 0) {
                                        RailBitePickerPalette.CyanBorder
                                    } else {
                                        RailBitePickerPalette.BorderSubtle
                                    },
                                ),
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = "Quick Chat",
                                        tint = RailBitePickerPalette.CyanPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (state.unreadChatCount > 0) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(RailBitePickerPalette.SemanticRed),
                                ) {
                                    Text(
                                        text = state.unreadChatCount.coerceAtMost(9).toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)

                    // Sticky Seat + SLA Timer Context Strip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            PickerSeatPill(coach = order.coach, berth = order.berth)
                            Column {
                                Text(
                                    text = "$totalQty item${if (totalQty == 1) "" else "s"} • ${Money.rupees(order.totalPaise)}",
                                    style = PickerNumericType.PriceMedium,
                                    color = RailBitePickerPalette.TextPrimary,
                                )
                                Text(
                                    text = "PNR ${order.pnr}",
                                    style = PickerNumericType.MetaMono,
                                    color = RailBitePickerPalette.TextSecondary,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(PickerRadii.Pill),
                            color = if (slaOverdue) {
                                RailBitePickerPalette.SemanticRedTint
                            } else {
                                RailBitePickerPalette.SemanticAmberTint
                            },
                            border = BorderStroke(
                                1.dp,
                                if (slaOverdue) {
                                    RailBitePickerPalette.SemanticRedBorder
                                } else {
                                    RailBitePickerPalette.SemanticAmberBorder
                                },
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = if (slaOverdue) {
                                        RailBitePickerPalette.SemanticRed
                                    } else {
                                        RailBitePickerPalette.SemanticAmberInk
                                    },
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = slaLabel,
                                    style = PickerNumericType.BadgeMono,
                                    color = if (slaOverdue) {
                                        RailBitePickerPalette.SemanticRed
                                    } else {
                                        RailBitePickerPalette.SemanticAmberInk
                                    },
                                )
                            }
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)
                }
            }
        },
        bottomBar = {
            Surface(
                color = RailBitePickerPalette.SurfaceCard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = RailBitePickerPalette.BorderSubtle)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = PickerMetrics.ScreenPadding, vertical = 12.dp),
                    ) {
                        PickerPrimaryButton(
                            text = if (allPacked) {
                                "Packed — Start Delivery →"
                            } else {
                                "Pick $remainingCount more item${if (remainingCount == 1) "" else "s"}"
                            },
                            onClick = onStartPickup,
                            enabled = allPacked,
                            busy = state.busy,
                            containerColor = RailBitePickerPalette.CyanPrimary,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = PickerMetrics.ScreenPadding,
                end = PickerMetrics.ScreenPadding,
                top = 12.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Unread / Latest Customer Message Preview Bar
            if (!latestCustomerMsg.isNullOrBlank()) {
                item {
                    Surface(
                        onClick = onOpenChat,
                        shape = RoundedCornerShape(PickerRadii.Button),
                        color = RailBitePickerPalette.CyanTint,
                        border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = RailBitePickerPalette.CyanPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (state.unreadChatCount > 0) "NEW PASSENGER MESSAGE" else "PASSENGER NOTE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RailBitePickerPalette.CyanPressed,
                                )
                                Text(
                                    text = latestCustomerMsg,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = RailBitePickerPalette.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            PickerStatusTag(
                                text = "Reply",
                                bgColor = RailBitePickerPalette.SurfaceCard,
                                textColor = RailBitePickerPalette.CyanPressed,
                                borderColor = RailBitePickerPalette.CyanBorder,
                            )
                        }
                    }
                }
            }

            state.message?.let { msg ->
                item { PickerErrorBanner(message = msg) }
            }
            state.error?.let { err ->
                item { PickerErrorBanner(message = err) }
            }

            // Progress Card
            item {
                PickerSectionCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "$pickedCount of $totalCount picked",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RailBitePickerPalette.TextPrimary,
                            )
                            PickerStatusTag(
                                text = "$pct%",
                                bgColor = if (allPacked) {
                                    RailBitePickerPalette.SemanticGreenTint
                                } else {
                                    RailBitePickerPalette.CyanTint
                                },
                                textColor = if (allPacked) {
                                    RailBitePickerPalette.SemanticGreenInk
                                } else {
                                    RailBitePickerPalette.CyanPressed
                                },
                                borderColor = if (allPacked) {
                                    RailBitePickerPalette.SemanticGreenBorder
                                } else {
                                    RailBitePickerPalette.CyanBorder
                                },
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(PickerRadii.Pill))
                                .background(RailBitePickerPalette.ImageTile),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(PickerRadii.Pill))
                                    .background(
                                        if (allPacked) {
                                            RailBitePickerPalette.SemanticGreen
                                        } else {
                                            RailBitePickerPalette.CyanPrimary
                                        },
                                    ),
                            )
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    PickerSectionCard {
                        Text(
                            text = "No itemized lines returned — confirm package contents at pantry counter and tap Start Delivery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RailBitePickerPalette.TextSecondary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                groupedItems.forEach { group ->
                    item(key = "header-${group.meta.groupKey}") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Text(
                                text = group.meta.groupTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = RailBitePickerPalette.TextPrimary,
                            )
                            PickerStatusTag(
                                text = group.meta.rackZone,
                                bgColor = RailBitePickerPalette.SurfaceCard,
                                textColor = RailBitePickerPalette.TextSecondary,
                                borderColor = RailBitePickerPalette.BorderSubtle,
                            )
                        }
                    }

                    items(
                        items = group.items,
                        key = { "${group.meta.groupKey}-${it.itemId}" },
                    ) { item ->
                        val checked = state.checklist[item.itemId] == true
                        ChecklistItemCard(
                            item = item,
                            rackMeta = group.meta,
                            checked = checked,
                            onToggle = { onTickItem(item.itemId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemCard(
    item: OrderItem,
    rackMeta: PickerFoodAssets.RackMeta,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val itemName = item.name ?: item.itemId
    PickerSectionCard(
        containerColor = if (checked) {
            RailBitePickerPalette.SemanticGreenTint
        } else {
            RailBitePickerPalette.SurfaceCard
        },
        border = if (checked) {
            BorderStroke(1.dp, RailBitePickerPalette.SemanticGreenBorder)
        } else {
            PickerCardBorder
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(PickerMetrics.CardPadding),
        ) {
            // Local offline food category image tile
            Image(
                painter = painterResource(
                    id = PickerFoodAssets.drawableFor(itemName, item.category.orEmpty()),
                ),
                contentDescription = itemName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(PickerRadii.ImageTile))
                    .background(RailBitePickerPalette.ImageTile)
                    .border(1.dp, RailBitePickerPalette.BorderSubtle, RoundedCornerShape(PickerRadii.ImageTile)),
            )

            // Item name + storage rack + price
            Column(Modifier.weight(1f)) {
                Text(
                    text = itemName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = RailBitePickerPalette.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${rackMeta.storageTag} • ${Money.rupees(item.unitPricePaise * item.qty)}",
                    style = PickerNumericType.MetaMono,
                    color = RailBitePickerPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Oversized cyan ×N quantity badge
            Surface(
                shape = RoundedCornerShape(PickerRadii.Button),
                color = RailBitePickerPalette.CyanTint,
                border = BorderStroke(1.dp, RailBitePickerPalette.CyanBorder),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "×${item.qty}",
                        style = PickerNumericType.PriceLarge,
                        color = RailBitePickerPalette.CyanPressed,
                    )
                }
            }

            // 48.dp single-handed touch checkbox
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(PickerMetrics.ChecklistCheckbox)
                    .clip(RoundedCornerShape(PickerRadii.Button))
                    .background(
                        if (checked) {
                            RailBitePickerPalette.SemanticGreen
                        } else {
                            RailBitePickerPalette.SurfaceCard
                        },
                    )
                    .border(
                        width = if (checked) 0.dp else 1.5.dp,
                        color = if (checked) Color.Transparent else RailBitePickerPalette.BorderSubtle,
                        shape = RoundedCornerShape(PickerRadii.Button),
                    ),
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Picked",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

