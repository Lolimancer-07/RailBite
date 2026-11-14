package com.railbite.customer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.model.MenuItem
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.ui.ProductEmoji
import com.railbite.core.ui.VegDot
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
    val diners by vm.diners.collectAsState()

    Box(modifier.fillMaxSize()) {
        Column {
            // ---- Header ----
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("RailBite", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onEditSeat) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = onEditSeat,
                            label = {
                                Text(
                                    seat?.chip ?: "Set your seat",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                labelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Delivery in ~10 mins",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // ---- Search ----
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search chai, biryani, water…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // ---- Category chips ----
            CategoryRow(
                categories = state.categories,
                selected = state.selectedCategory,
                onSelect = vm::selectCategory,
            )

            // ---- Menu grid ----
            if (state.loading) {
                LoadingSkeleton(rows = 6, modifier = Modifier.padding(16.dp))
            } else {
                val products = state.filtered
                if (products.isEmpty()) {
                    EmptyState(
                        emoji = "🍽️",
                        title = if (state.menu.isEmpty()) "No menu yet" else "Nothing matches",
                        subtitle = if (state.menu.isEmpty()) state.error ?: "Pull to refresh when the server is up"
                        else "Try a different search or category",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
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

        // ---- Floating cart bar ----
        val count = cartLines.values.sumOf { it.qty }
        AnimatedVisibility(
            visible = count > 0,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
        ) {
            Button(
                onClick = onOpenCart,
                shape = RoundedCornerShape(18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    "$count item${if (count == 1) "" else "s"} · ${Money.rupees(graph.cart.totalPaise)}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(12.dp))
                Text("View Cart →", color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        // active order pill (if any) under header
        ActiveOrderPill(graph = graph, modifier = Modifier.align(Alignment.TopCenter), onOpenOrder = onOpenOrder)
    }
}

@Composable
private fun CategoryRow(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = {
            items(listOf<String?>(null) + categories) { cat ->
                FilterChipLike(
                    label = cat ?: "All",
                    selected = selected == cat,
                    onClick = { onSelect(cat) },
                )
            }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipLike(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun ProductCard(
    item: MenuItem,
    qty: Int,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
) {
    val soldOut = item.stock <= 0
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // emoji "photo" tile
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(ProductEmoji.forItem(item.name, item.category), style = MaterialTheme.typography.headlineLarge)
                if (soldOut) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        Text(
                            "Sold out",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                VegDot(isVeg = item.isVeg)
                Spacer(Modifier.width(4.dp))
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("₹${Money.rupees(item.mrpPaise)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (soldOut) {
                Text("Back soon", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (qty == 0) {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Text("ADD", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    FilledIconButton(
                        onClick = onDecrement,
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text("−", fontWeight = FontWeight.Bold) }
                    Text(
                        "$qty",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FilledIconButton(
                        onClick = onAdd,
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text("+", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Small floating pill that appears when there is an active order being tracked. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ActiveOrderPill(
    graph: com.railbite.customer.CustomerGraph,
    modifier: Modifier = Modifier,
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
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.padding(top = 88.dp),
        shadowElevation = 4.dp,
    ) {
        Text(
            "🛵 Track order · ${status?.let { Statuses.label(it) } ?: "live"}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
