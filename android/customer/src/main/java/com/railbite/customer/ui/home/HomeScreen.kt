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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.mesh.TransportMode
import com.railbite.core.model.MenuItem
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.util.Statuses
import com.railbite.customer.ui.components.AccountMenuSheet
import com.railbite.customer.ui.components.CategoryChip
import com.railbite.customer.ui.components.FilterBottomSheet
import com.railbite.customer.ui.components.FloatingCartBar
import com.railbite.customer.ui.components.FoodProductCard
import com.railbite.customer.ui.components.ItemQuickViewSheet
import com.railbite.customer.ui.components.LocalBleActionRequester
import com.railbite.customer.ui.components.SeatSetupBottomSheet
import com.railbite.customer.ui.components.SortBottomSheet
import com.railbite.customer.ui.components.StorefrontTopRow
import com.railbite.customer.ui.components.StorefrontUtilityRow
import com.railbite.customer.ui.components.TransportIndicatorPill
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    graph: com.railbite.customer.CustomerGraph,
    modifier: Modifier = Modifier,
    onOpenCart: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    val vm = viewModel(key = "home") { HomeViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val cartLines by vm.cartLines.collectAsState()
    val seat by vm.seatInfo.seat.collectAsState(initial = null)
    val mqttConnected by graph.mqtt.connected.collectAsState()
    val transportStatus by graph.transport.status.collectAsState()
    val requestBleAction = LocalBleActionRequester.current
    val session by graph.authStore.session.collectAsState()
    val serverUrl by graph.settings.serverUrl.collectAsState(initial = "")

    var inspectedItem by remember { mutableStateOf<MenuItem?>(null) }
    var showSeatSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val online = mqttConnected || transportStatus.lanReachable
    val cartCount = cartLines.values.sumOf { it.qty }
    val products = state.filtered

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RailBitePalette.Canvas,
        bottomBar = {
            StorefrontBottomBar(
                activeFilterCount = state.activeFilterCount,
                selectedSort = state.sortBy,
                onHomeClick = {
                    focusManager.clearFocus()
                    scope.launch { gridState.animateScrollToItem(0) }
                },
                onFilterClick = { showFilterSheet = true },
                onSortClick = { showSortSheet = true },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            Column(Modifier.fillMaxSize()) {
                // Wireframe Top Row: RailBite brand box + live server dot on left, tappable SeatInfoBox on right
                StorefrontTopRow(
                    online = online,
                    pnr = seat?.pnr.orEmpty(),
                    coach = seat?.coach.orEmpty(),
                    berth = seat?.berth,
                    onBrandClick = onOpenSettings,
                    onSeatBoxClick = { showSeatSheet = true },
                )

                // Slim live transport pill (LAN Connected / BLE Mesh Offline (Active) / BLE Off)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RailBiteMetrics.ScreenPadding)
                        .padding(bottom = 8.dp),
                ) {
                    TransportIndicatorPill(
                        status = transportStatus,
                        onClick = {
                            if (transportStatus.mode == TransportMode.BLE_DISABLED) {
                                requestBleAction()
                            } else {
                                onOpenSettings()
                            }
                        },
                    )
                    if (transportStatus.mode == TransportMode.BLE_MESH_ACTIVE) {
                        Text(
                            text = "Offline instant delivery",
                            style = MaterialTheme.typography.labelSmall,
                            color = RailBitePalette.CyanPressed,
                        )
                    }
                }

                // Wireframe Utility Row: SearchPill + AccountIconButton + CartIconButton
                StorefrontUtilityRow(
                    search = state.search,
                    onSearchChange = vm::setSearch,
                    cartCount = cartCount,
                    onAccountClick = { showAccountSheet = true },
                    onCartClick = onOpenCart,
                )

                Spacer(Modifier.size(10.dp))

                // Category Pills Row (wired directly to real server categories)
                CategoryRow(
                    categories = state.categories,
                    selected = state.selectedCategory,
                    onSelect = vm::selectCategory,
                )

                // Active order banner when a live order is underway
                ActiveOrderBanner(graph = graph, onOpenOrder = onOpenOrder)

                // Active filter/sort feedback pill or offline notice
                if (state.activeFilterCount > 0 || state.sortBy != MenuSortOption.DEFAULT) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 4.dp),
                    ) {
                        val summary = buildList {
                            if (state.vegOnly) add("Pure Veg")
                            if (state.inStockOnly) add("In Stock")
                            state.maxPricePaise?.let { add("≤₹${it / 100}") }
                            if (state.sortBy != MenuSortOption.DEFAULT) add(state.sortBy.label)
                        }.joinToString(" • ")
                        Text(
                            text = "${products.size} items • $summary",
                            style = MaterialTheme.typography.labelSmall,
                            color = RailBitePalette.CyanPressed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                vm.resetFilters()
                                vm.setSort(MenuSortOption.DEFAULT)
                            },
                        ) {
                            Text("Reset", color = RailBitePalette.CyanPrimary)
                        }
                    }
                } else {
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
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
                }

                // 2-Column Food Product Grid
                if (state.loading) {
                    LoadingSkeleton(
                        rows = 6,
                        modifier = Modifier.padding(RailBiteMetrics.ScreenPadding),
                    )
                } else if (products.isEmpty()) {
                    EmptyState(
                        title = if (state.menu.isEmpty()) "No menu available yet" else "No matching items",
                        subtitle = if (state.menu.isEmpty()) {
                            state.error ?: "Tap Retry when the pantry server is reachable"
                        } else {
                            "Clear filters or try another category/search"
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(
                            start = RailBiteMetrics.ScreenPadding,
                            end = RailBiteMetrics.ScreenPadding,
                            top = 8.dp,
                            bottom = if (cartCount > 0) 92.dp else 18.dp,
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

            // Optional slim floating "View Cart" pill directly above the Home|Filter|Sort bottom bar
            AnimatedVisibility(
                visible = cartCount > 0,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 10.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                FloatingCartBar(
                    itemCount = cartCount,
                    totalPaise = graph.cart.totalPaise,
                    seatSummary = seat?.chip,
                    onOpenCart = onOpenCart,
                )
            }
        }
    }

    // Bottom Sheet 1: Seat Setup / Edit PNR & Berth
    if (showSeatSheet) {
        SeatSetupBottomSheet(
            initialPnr = seat?.pnr.orEmpty(),
            initialCoach = seat?.coach.orEmpty(),
            initialBerth = seat?.berth,
            onDismiss = { showSeatSheet = false },
            onOpenSettings = onOpenSettings,
            onSave = vm::updateSeat,
        )
    }

    // Bottom Sheet 2: Filter Options (Pure Veg / In Stock / Price Cap)
    if (showFilterSheet) {
        FilterBottomSheet(
            state = state,
            onDismiss = { showFilterSheet = false },
            onApply = vm::applyFilters,
            onReset = vm::resetFilters,
        )
    }

    // Bottom Sheet 3: Sort Options (Price Low-High / High-Low / Name)
    if (showSortSheet) {
        SortBottomSheet(
            selectedSort = state.sortBy,
            onDismiss = { showSortSheet = false },
            onSelectSort = vm::setSort,
        )
    }

    // Bottom Sheet 4: Account / Orders / Settings Quick Sheet
    if (showAccountSheet) {
        AccountMenuSheet(
            userLabel = session?.user?.phone ?: session?.user?.name.orEmpty(),
            online = online,
            serverUrl = serverUrl,
            onDismiss = { showAccountSheet = false },
            onOpenOrders = onOpenOrders,
            onEditSeat = { showSeatSheet = true },
            onOpenSettings = onOpenSettings,
            onSignOut = {
                scope.launch { runCatching { graph.auth.logout() } }
            },
        )
    }

    // Bottom Sheet 5: Food Item Quick-View Detail Sheet
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

/**
 * Wireframe Bottom Bar (`acf3d694` + `ad2f85d4`):
 * `Home` icon button on left, `Filter` button in middle, `Sort` button on right, with `navigationBarsPadding()`.
 */
@Composable
private fun StorefrontBottomBar(
    activeFilterCount: Int,
    selectedSort: MenuSortOption,
    onHomeClick: () -> Unit,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
) {
    Surface(
        color = RailBitePalette.SurfaceCard,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 10.dp),
            ) {
                // Left: Home Icon Button (44.dp+ touch target, cyan highlighted per sketch)
                Surface(
                    onClick = onHomeClick,
                    shape = RoundedCornerShape(RailBiteRadii.ChipBox),
                    color = RailBitePalette.CyanTint,
                    border = BorderStroke(1.dp, RailBitePalette.CyanPrimary),
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(width = 52.dp, height = RailBiteMetrics.TouchTargetMin),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Scroll to top of menu",
                            tint = RailBitePalette.CyanPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Middle: Filter button (opens FilterBottomSheet)
                Surface(
                    onClick = onFilterClick,
                    shape = RoundedCornerShape(RailBiteRadii.ChipBox),
                    color = if (activeFilterCount > 0) RailBitePalette.CyanTint else RailBitePalette.SurfaceCard,
                    border = if (activeFilterCount > 0) {
                        BorderStroke(1.dp, RailBitePalette.CyanPrimary)
                    } else {
                        RailBiteCardBorder
                    },
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RailBiteMetrics.TouchTargetMin),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = null,
                            tint = if (activeFilterCount > 0) RailBitePalette.CyanPrimary else RailBitePalette.DarkSlate,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "Filter",
                            style = MaterialTheme.typography.labelLarge,
                            color = RailBitePalette.TextPrimary,
                        )
                        if (activeFilterCount > 0) {
                            Spacer(Modifier.size(6.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(RailBitePalette.CyanPrimary, CircleShape),
                            ) {
                                Text(
                                    text = "$activeFilterCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }

                // Right: Sort button (opens SortBottomSheet)
                val sortActive = selectedSort != MenuSortOption.DEFAULT
                Surface(
                    onClick = onSortClick,
                    shape = RoundedCornerShape(RailBiteRadii.ChipBox),
                    color = if (sortActive) RailBitePalette.CyanTint else RailBitePalette.SurfaceCard,
                    border = if (sortActive) {
                        BorderStroke(1.dp, RailBitePalette.CyanPrimary)
                    } else {
                        RailBiteCardBorder
                    },
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RailBiteMetrics.TouchTargetMin),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = RailBitePalette.CyanPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "Sort",
                            style = MaterialTheme.typography.labelLarge,
                            color = RailBitePalette.TextPrimary,
                        )
                        Spacer(Modifier.size(4.dp))
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = RailBitePalette.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
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

@Composable
private fun ActiveOrderBanner(
    graph: com.railbite.customer.CustomerGraph,
    onOpenOrder: (String) -> Unit,
) {
    val orderId by graph.activeOrderId.collectAsState(initial = "")
    val statusMap by graph.mqtt.orderStatuses.collectAsState()
    if (orderId.isBlank()) return
    val meshEntry by graph.container.meshSender.observeEntry(orderId).collectAsState(initial = null)
    val status = meshEntry?.ack ?: statusMap[orderId]?.status
    if (status != null && Statuses.isTerminal(status)) return

    Surface(
        onClick = { onOpenOrder(orderId) },
        color = RailBitePalette.CyanTint,
        contentColor = RailBitePalette.CyanPressed,
        border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
        shape = RoundedCornerShape(RailBiteRadii.Button),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 6.dp)
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
