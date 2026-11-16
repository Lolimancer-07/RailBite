package com.railbite.customer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.model.MenuItem
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.ui.ProductThumb
import com.railbite.core.ui.VegDot
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteFilterChip
import com.railbite.core.ui.components.RailBiteStepper
import com.railbite.core.ui.theme.Metrics
import com.railbite.core.ui.theme.Space
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    graph: com.railbite.customer.CustomerGraph,
    modifier: Modifier = Modifier,
    onOpenCart: () -> Unit,
    onEditSeat: () -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    val vm = viewModel(key = "home") { HomeViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val cartLines by vm.cartLines.collectAsState()
    val seat by vm.seatInfo.seat.collectAsState(initial = null)

    Box(modifier.fillMaxSize()) {
        Column {
            // ---- Header: white, ink text, flat ----
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RailBite",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onEditSeat) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                    modifier = Modifier.padding(top = Space.xs),
                ) {
                    SeatChip(seat?.chip ?: "Set your seat", onClick = onEditSeat)
                    Text(
                        "Delivery in ~10 mins",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Active order banner (inline under the header, tap to track)
            ActiveOrderBanner(graph = graph, onOpenOrder = onOpenOrder)

            // ---- Search ----
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                label = { Text("Search menu") },
                placeholder = { Text("Chai, biryani, water…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screen, vertical = Space.xs),
            )

            // ---- Category chips ----
            CategoryRow(
                categories = state.categories,
                selected = state.selectedCategory,
                onSelect = vm::selectCategory,
            )

            // ---- Menu grid ----
            if (state.loading) {
                LoadingSkeleton(rows = 6, modifier = Modifier.padding(Space.screen))
            } else {
                val products = state.filtered
                if (products.isEmpty()) {
                    EmptyState(
                        title = if (state.menu.isEmpty()) "No menu yet" else "Nothing matches",
                        subtitle = if (state.menu.isEmpty()) state.error ?: "Pull to refresh when the server is up"
                        else "Try a different search or category",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        verticalArrangement = Arrangement.spacedBy(Space.s),
                        contentPadding = PaddingValues(
                            start = Space.screen,
                            end = Space.screen,
                            top = Space.xs,
                            bottom = 96.dp, // clearance for the floating cart bar
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(products, key = { it.id }) { item ->
                            ProductCard(
                                item = item,
                                qty = cartLines[item.id]?.qty ?: 0,
                                onAdd = { vm.addToCart(item) },
                                onDecrement = { vm.decrement(item.id) },
                            )
                        }
                    }
                }
            }
        }

        // ---- Floating cart bar (56 dp, accent) ----
        val count = cartLines.values.sumOf { it.qty }
        AnimatedVisibility(
            visible = count > 0,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Space.screen, vertical = Space.m),
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
        ) {
            RailBiteButton(
                text = "View cart · ${Money.rupees(graph.cart.totalPaise)} · $count item${if (count == 1) "" else "s"}",
                onClick = onOpenCart,
            )
        }
    }
}

/** Seat selector: accent-tinted chip, ≥ 44 dp tall. */
@Composable
private fun SeatChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.heightIn(min = Metrics.tapTarget),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Space.s),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Category filter chips: platform-style pills, ≥ 44 dp tall. */
@Composable
private fun CategoryRow(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = Space.xs),
        content = {
            items(listOf<String?>(null) + categories) { cat ->
                RailBiteFilterChip(
                    label = cat ?: "All",
                    selected = selected == cat,
                    onClick = { onSelect(cat) },
                )
            }
        },
    )
}

/** Flat product tile: placeholder photo band, veg mark, name, ₹ price, 44 dp ADD. */
@Composable
fun ProductCard(
    item: MenuItem,
    qty: Int,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
) {
    val soldOut = item.stock <= 0
    Card(
        shape = MaterialTheme.shapes.large, // 12 dp tile
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            // Photo placeholder band (real photos slot in here when available)
            Box {
                ProductThumb(item.name, Modifier.fillMaxWidth().height(88.dp))
                if (soldOut) {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        Text(
                            "Sold out",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = Space.xs, vertical = 2.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                VegDot(isVeg = item.isVeg)
                Spacer(Modifier.width(6.dp))
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                Money.rupees(item.mrpPaise),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when {
                soldOut -> Button(
                    onClick = {},
                    enabled = false,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Metrics.tapTarget),
                ) { Text("Sold out", style = MaterialTheme.typography.labelLarge) }

                qty == 0 -> Button(
                    onClick = onAdd,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = Space.s, vertical = Space.xs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Metrics.tapTarget),
                ) { Text("ADD", style = MaterialTheme.typography.labelLarge) }

                else -> RailBiteStepper(
                    quantity = qty,
                    itemName = item.name,
                    onAdd = onAdd,
                    onRemove = onDecrement,
                )
            }
        }
    }
}

/** Banner shown while an order is being tracked; tap opens the tracking screen. */
@Composable
private fun ActiveOrderBanner(
    graph: com.railbite.customer.CustomerGraph,
    onOpenOrder: (String) -> Unit,
) {
    val orderId = graph.activeOrderId.collectAsState(initial = "")
    val statusMap by graph.mqtt.orderStatuses.collectAsState()
    val id = orderId.value
    if (id.isBlank()) return
    val status = statusMap[id]?.status
    if (status != null && Statuses.isTerminal(status)) return
    Surface(
        onClick = { onOpenOrder(id) },
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.tapTarget),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.xs),
        ) {
            Text(
                "Track order · ${status?.let { Statuses.label(it) } ?: "live"}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
