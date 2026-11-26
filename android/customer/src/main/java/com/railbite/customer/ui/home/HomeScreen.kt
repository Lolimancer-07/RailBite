package com.railbite.customer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.model.MenuItem
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.util.Statuses
import com.railbite.customer.ui.components.CategoryChip
import com.railbite.customer.ui.components.FloatingCartBar
import com.railbite.customer.ui.components.FoodProductCard
import com.railbite.customer.ui.components.ItemQuickViewSheet
import com.railbite.customer.ui.components.MrpBadge
import com.railbite.customer.ui.components.SeatHeaderBar
import com.railbite.customer.ui.onboarding.flatTextFieldColors
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

@Composable
fun HomeScreen(
    graph: com.railbite.customer.CustomerGraph,
    modifier: Modifier = Modifier,
    onOpenCart: () -> Unit,
    onEditSeat: () -> Unit,
    onOpenSettings: () -> Unit = onEditSeat,
    onOpenOrder: (String) -> Unit,
) {
    val vm = viewModel(key = "home") { HomeViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val cartLines by vm.cartLines.collectAsState()
    val seat by vm.seatInfo.seat.collectAsState(initial = null)
    val mqttConnected by graph.mqtt.connected.collectAsState()
    var inspectedItem by remember { mutableStateOf<MenuItem?>(null) }

    val online = mqttConnected || (state.error == null && !state.loading)

    Box(
        modifier
            .fillMaxSize()
            .background(RailBitePalette.Canvas),
    ) {
        Column(Modifier.fillMaxSize()) {
            SeatHeaderBar(
                seatLabel = seat?.chip ?: "Set your seat",
                online = online,
                onSeatClick = onEditSeat,
                onSettingsClick = onOpenSettings,
            )

            // Active order live tracker pill banner
            ActiveOrderBanner(graph = graph, onOpenOrder = onOpenOrder)

            // Offline cached menu notice (non-blocking)
            state.error?.takeIf { state.menu.isNotEmpty() }?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    color = RailBitePalette.SemanticAmberTint,
                    border = BorderStroke(1.dp, RailBitePalette.SemanticAmberBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePalette.SemanticAmber,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = vm::refresh) {
                            Text("Retry", color = RailBitePalette.CyanPrimary)
                        }
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                placeholder = {
                    Text(
                        "Search chai, biryani, samosa, water…",
                        color = RailBitePalette.TextMuted,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = RailBitePalette.TextSecondary,
                    )
                },
                trailingIcon = {
                    if (state.search.isNotEmpty()) {
                        IconButton(onClick = { vm.setSearch("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = RailBitePalette.TextSecondary,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(RailBiteRadii.Button),
                colors = flatTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 10.dp),
            )

            // Category pills row
            CategoryRow(
                categories = state.categories,
                selected = state.selectedCategory,
                onSelect = vm::selectCategory,
            )

            val products = state.filtered

            if (!state.loading && products.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 8.dp),
                ) {
                    Text(
                        text = "Pantry Express",
                        style = MaterialTheme.typography.titleMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                    Text(
                        text = "  (${products.size} item${if (products.size == 1) "" else "s"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    MrpBadge(label = "IRCTC Regulated")
                }
            }

            // Menu 2-column grid
            if (state.loading) {
                LoadingSkeleton(rows = 6, modifier = Modifier.padding(RailBiteMetrics.ScreenPadding))
            } else if (products.isEmpty()) {
                EmptyState(
                    title = if (state.menu.isEmpty()) "No menu available yet" else "Nothing matches",
                    subtitle = if (state.menu.isEmpty()) {
                        state.error ?: "Tap Retry when the pantry server is reachable"
                    } else {
                        "Try a different search keyword or switch category"
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = RailBiteMetrics.ScreenPadding,
                        end = RailBiteMetrics.ScreenPadding,
                        top = 4.dp,
                        bottom = 110.dp, // clearance for FloatingCartBar
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    items(products, key = { it.id }) { item ->
                        FoodProductCard(
                            item = item,
                            qty = cartLines[item.id]?.qty ?: 0,
                            onInspect = { inspectedItem = item },
                            onAdd = { vm.addToCart(item) },
                            onDecrement = { vm.decrement(item.id) },
                        )
                    }
                }
            }
        }

        // Sticky high-contrast FloatingCartBar
        val count = cartLines.values.sumOf { it.qty }
        AnimatedVisibility(
            visible = count > 0,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 12.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            FloatingCartBar(
                itemCount = count,
                totalPaise = graph.cart.totalPaise,
                seatSummary = seat?.chip,
                onOpenCart = onOpenCart,
            )
        }
    }

    inspectedItem?.let { item ->
        val currentQty = cartLines[item.id]?.qty ?: 0
        ItemQuickViewSheet(
            item = item,
            currentCartQty = currentQty,
            seatLabel = seat?.chip,
            onDismiss = { inspectedItem = null },
            onApplyQuantity = { targetQty ->
                val delta = targetQty - currentQty
                if (delta > 0) {
                    repeat(delta) { vm.addToCart(item) }
                } else if (delta < 0) {
                    repeat(-delta) { vm.decrement(item.id) }
                }
            },
        )
    }
}

@Composable
private fun CategoryRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = RailBiteMetrics.ScreenPadding),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        items(listOf<String?>(null) + categories) { cat ->
            CategoryChip(
                label = cat ?: "All",
                selected = selected == cat,
                onClick = { onSelect(cat) },
            )
        }
    }
}

/** Inline banner shown while an order is live; tap opens Live Tracking. */
@Composable
private fun ActiveOrderBanner(
    graph: com.railbite.customer.CustomerGraph,
    onOpenOrder: (String) -> Unit,
) {
    val orderId by graph.activeOrderId.collectAsState(initial = "")
    val statusMap by graph.mqtt.orderStatuses.collectAsState()
    if (orderId.isBlank()) return
    val status = statusMap[orderId]?.status
    if (status != null && Statuses.isTerminal(status)) return

    Surface(
        onClick = { onOpenOrder(orderId) },
        color = RailBitePalette.CyanTint,
        contentColor = RailBitePalette.CyanPressed,
        border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
        shape = RoundedCornerShape(RailBiteRadii.Button),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 8.dp)
            .heightIn(min = RailBiteMetrics.TouchTargetMin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.LocalShipping,
                contentDescription = null,
                tint = RailBitePalette.CyanPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Live order • ${status?.let { Statuses.label(it) } ?: "Tracking active"}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Pill),
                color = RailBitePalette.CyanPrimary,
                contentColor = Color.White,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Track", style = MaterialTheme.typography.labelSmall)
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}
