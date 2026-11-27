package com.railbite.customer.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.model.MenuItem
import com.railbite.core.search.MatchRange
import com.railbite.core.search.SearchMatch
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.customer.CustomerGraph
import com.railbite.customer.ui.components.FloatingCartBar
import com.railbite.customer.ui.components.FoodAssets
import com.railbite.customer.ui.components.ItemQuickViewSheet
import com.railbite.customer.ui.components.PricePill
import com.railbite.customer.ui.components.ProductAddAction
import com.railbite.customer.ui.components.VegMark
import com.railbite.customer.ui.onboarding.flatTextFieldColors
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii
import com.railbite.customer.ui.theme.RailBiteSelectedBorder
import kotlinx.coroutines.delay

private data class CategoryShortcut(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val SEARCH_CATEGORIES = listOf(
    CategoryShortcut("Beverages", "Chai, coffee & water", Icons.Filled.LocalCafe),
    CategoryShortcut("Snacks", "Samosa, puffs & chips", Icons.Filled.Fastfood),
    CategoryShortcut("Lunch", "Biryani, sandwiches", Icons.Filled.LunchDining),
    CategoryShortcut("Dinner", "Hot meals & rolls", Icons.Filled.DinnerDining),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    graph: CustomerGraph,
    onBack: () -> Unit,
    onOpenCart: () -> Unit,
) {
    val vm = viewModel(key = "customer-search") { SearchViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val cartLines by vm.cartLines.collectAsState()
    val seat by vm.seatInfo.seat.collectAsState(initial = null)

    var inspectedItem by remember { mutableStateOf<MenuItem?>(null) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val cartCount = cartLines.values.sumOf { it.qty }
    val bottomClearance = if (cartCount > 0) 96.dp else 24.dp

    Scaffold(
        containerColor = RailBitePalette.Canvas,
        topBar = {
            StickySearchHeader(
                query = state.query,
                lanLive = state.lanLive,
                focusRequester = focusRequester,
                onBack = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    onBack()
                },
                onQueryChange = vm::onQueryChange,
                onClearQuery = vm::clearQuery,
                onSubmitSearch = {
                    vm.submitSearch()
                    focusManager.clearFocus()
                    keyboard?.hide()
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            if (state.loading) {
                LoadingSkeleton(
                    rows = 6,
                    modifier = Modifier.padding(RailBiteMetrics.ScreenPadding),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = RailBiteMetrics.ScreenPadding,
                        end = RailBiteMetrics.ScreenPadding,
                        top = 12.dp,
                        bottom = bottomClearance,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!state.isActiveSearch) {
                        // STATE A: Pre-Search Discovery
                        if (state.recentSearches.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                RecentSearchesSection(
                                    queries = state.recentSearches,
                                    onSelect = { q ->
                                        vm.applyRecentSearch(q)
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                    },
                                    onRemove = vm::removeRecentSearch,
                                    onClearAll = vm::clearRecentSearches,
                                )
                            }
                        }

                        if (state.trending.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                TrendingSection(
                                    title = "Trending on This Train",
                                    items = state.trending,
                                    cartQuantities = cartLines.mapValues { it.value.qty },
                                    onInspect = { inspectedItem = it },
                                    onAdd = vm::addToCart,
                                    onDecrement = vm::decrement,
                                )
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            BrowseCategorySection(
                                onSelectCategory = { label ->
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    vm.selectCategoryShortcut(label)
                                },
                            )
                        }
                    } else {
                        // STATE B & C: Quick Filters + Autocomplete + Live Results / No Results
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            QuickFiltersRow(
                                selectedCategory = state.selectedCategory,
                                inStockOnly = state.inStockOnly,
                                maxPricePaise = state.maxPricePaise,
                                sortByPriceAsc = state.sortByPriceAsc,
                                onClearCategory = vm::clearCategoryFilter,
                                onToggleInStock = vm::toggleInStockOnly,
                                onToggleUnder50 = vm::toggleUnder50,
                                onToggleUnder100 = vm::toggleUnder100,
                                onToggleSortPrice = vm::toggleSortPriceLowHigh,
                            )
                        }

                        if (state.query.isNotBlank() && state.suggestions.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AutocompleteSuggestionsRow(
                                    suggestions = state.suggestions,
                                    onSelect = { suggestion ->
                                        vm.applySuggestion(suggestion)
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                    },
                                )
                            }
                        }

                        if (state.results.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptySearchStateCard(
                                    query = state.query,
                                    selectedCategory = state.selectedCategory,
                                    hasActiveFilters = state.hasActiveFilters,
                                    onClearFilters = vm::clearAllFilters,
                                    onClearQuery = vm::clearQuery,
                                )
                            }

                            if (state.trending.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    TrendingSection(
                                        title = "Popular right now",
                                        items = state.trending,
                                        cartQuantities = cartLines.mapValues { it.value.qty },
                                        onInspect = { inspectedItem = it },
                                        onAdd = vm::addToCart,
                                        onDecrement = vm::decrement,
                                    )
                                }
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "${state.results.size} item${if (state.results.size == 1) "" else "s"} found",
                                        style = RailBiteNumericType.PriceMedium,
                                        color = RailBitePalette.TextPrimary,
                                    )
                                    if (state.hasActiveFilters) {
                                        TextButton(onClick = vm::clearAllFilters) {
                                            Text("Clear filters", color = RailBitePalette.CyanPrimary)
                                        }
                                    }
                                }
                            }

                            items(
                                items = state.results,
                                key = { it.item.id },
                            ) { match ->
                                val item = match.item
                                SearchResultCard(
                                    match = match,
                                    qty = cartLines[item.id]?.qty ?: 0,
                                    onInspect = {
                                        vm.onInspectItem(item)
                                        inspectedItem = item
                                    },
                                    onAdd = { vm.addToCart(item) },
                                    onDecrement = { vm.decrement(item.id) },
                                )
                            }
                        }
                    }
                }
            }

            // Sticky Floating Cart Bar cleanly padded above IME & system navigation bar
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
                    onOpenCart = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onOpenCart()
                    },
                )
            }
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
private fun StickySearchHeader(
    query: String,
    lanLive: Boolean,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSubmitSearch: () -> Unit,
) {
    Surface(
        color = RailBitePalette.SurfaceCard,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = RailBitePalette.TextPrimary,
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = "Search chai, pani, biriyani, snacks…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RailBitePalette.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = RailBitePalette.CyanPrimary,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = onClearQuery,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = RailBitePalette.TextSecondary,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(RailBiteRadii.Pill),
                    colors = flatTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .padding(end = 8.dp)
                        .focusRequester(focusRequester),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RailBiteMetrics.ScreenPadding)
                    .padding(bottom = 8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Pill),
                    color = if (lanLive) RailBitePalette.SemanticGreenTint else RailBitePalette.CyanTint,
                    border = BorderStroke(
                        1.dp,
                        if (lanLive) RailBitePalette.SemanticGreenBorder else RailBitePalette.CyanBorder,
                    ),
                ) {
                    Text(
                        text = if (lanLive) "Live Pantry" else "Offline Catalog",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lanLive) RailBitePalette.SemanticGreen else RailBitePalette.CyanPressed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                Text(
                    text = "IRCTC Pantry MRP • Instant Berth Search",
                    style = MaterialTheme.typography.labelSmall,
                    color = RailBitePalette.TextSecondary,
                )
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearchesSection(
    queries: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Recent Searches",
                style = MaterialTheme.typography.titleMedium,
                color = RailBitePalette.TextPrimary,
            )
            Surface(
                onClick = onClearAll,
                shape = RoundedCornerShape(RailBiteRadii.Pill),
                color = Color.Transparent,
                modifier = Modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = "Clear all",
                        style = MaterialTheme.typography.labelLarge,
                        color = RailBitePalette.CyanPrimary,
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            queries.forEach { q ->
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Pill),
                    color = RailBitePalette.SurfaceCard,
                    border = RailBiteCardBorder,
                    shadowElevation = 0.dp,
                    modifier = Modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clickable { onSelect(q) }
                                .padding(vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = RailBitePalette.TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = q,
                                style = MaterialTheme.typography.labelLarge,
                                color = RailBitePalette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { onRemove(q) },
                            modifier = Modifier
                                .size(38.dp)
                                .semantics { contentDescription = "Remove $q" },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = RailBitePalette.TextMuted,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendingSection(
    title: String,
    items: List<MenuItem>,
    cartQuantities: Map<String, Int>,
    onInspect: (MenuItem) -> Unit,
    onAdd: (MenuItem) -> Unit,
    onDecrement: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Control),
                color = RailBitePalette.CyanTint,
                border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = RailBitePalette.CyanPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = RailBitePalette.TextPrimary,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items, key = { it.id }) { item ->
                TrendingProductCard(
                    item = item,
                    qty = cartQuantities[item.id] ?: 0,
                    onInspect = { onInspect(item) },
                    onAdd = { onAdd(item) },
                    onDecrement = { onDecrement(item.id) },
                )
            }
        }
    }
}

@Composable
private fun TrendingProductCard(
    item: MenuItem,
    qty: Int,
    onInspect: () -> Unit,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
) {
    val soldOut = item.stock <= 0
    val meta = remember(item.id) { FoodAssets.metaFor(item) }
    val popularLabel = item.orderCount?.takeIf { it > 0 }?.let { "$it ordered" } ?: "Popular"

    Card(
        onClick = onInspect,
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = if (qty > 0 && !soldOut) RailBiteSelectedBorder else RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.width(168.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
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

                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Pill),
                    color = RailBitePalette.CyanTint,
                    border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                ) {
                    Text(
                        text = popularLabel,
                        style = RailBiteNumericType.MetaMono,
                        color = RailBitePalette.CyanPressed,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }

                PricePill(
                    paise = item.mrpPaise,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                VegMark(isVeg = item.isVeg)
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (soldOut) RailBitePalette.TextMuted else RailBitePalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (soldOut) "Sold Out" else "IRCTC MRP",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (soldOut) RailBitePalette.SemanticRed else RailBitePalette.SemanticGreen,
                    maxLines = 1,
                )
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
}

@Composable
private fun BrowseCategorySection(
    onSelectCategory: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Text(
            text = "Browse by Category",
            style = MaterialTheme.typography.titleMedium,
            color = RailBitePalette.TextPrimary,
        )

        SEARCH_CATEGORIES.chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                pair.forEach { cat ->
                    Surface(
                        onClick = { onSelectCategory(cat.title) },
                        shape = RoundedCornerShape(RailBiteRadii.Card),
                        color = RailBitePalette.SurfaceCard,
                        border = RailBiteCardBorder,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 68.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(RailBiteRadii.Button),
                                color = RailBitePalette.CyanTint,
                                border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                                modifier = Modifier.size(38.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = cat.icon,
                                        contentDescription = null,
                                        tint = RailBitePalette.CyanPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = cat.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = RailBitePalette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = cat.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RailBitePalette.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickFiltersRow(
    selectedCategory: String?,
    inStockOnly: Boolean,
    maxPricePaise: Int?,
    sortByPriceAsc: Boolean,
    onClearCategory: () -> Unit,
    onToggleInStock: () -> Unit,
    onToggleUnder50: () -> Unit,
    onToggleUnder100: () -> Unit,
    onToggleSortPrice: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (selectedCategory != null) {
            item {
                SearchFilterPill(
                    label = "$selectedCategory ×",
                    selected = true,
                    onClick = onClearCategory,
                )
            }
        }
        item {
            SearchFilterPill(
                label = "In Stock",
                selected = inStockOnly,
                onClick = onToggleInStock,
            )
        }
        item {
            SearchFilterPill(
                label = "Under ₹50",
                selected = maxPricePaise == 5_000,
                onClick = onToggleUnder50,
            )
        }
        item {
            SearchFilterPill(
                label = "Under ₹100",
                selected = maxPricePaise == 10_000,
                onClick = onToggleUnder100,
            )
        }
        item {
            SearchFilterPill(
                label = "Sort: Price Low–High",
                selected = sortByPriceAsc,
                onClick = onToggleSortPrice,
            )
        }
    }
}

@Composable
private fun SearchFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(RailBiteRadii.Pill),
        color = if (selected) RailBitePalette.CyanPrimary else RailBitePalette.SurfaceCard,
        contentColor = if (selected) Color.White else RailBitePalette.TextPrimary,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) RailBitePalette.CyanPrimary else RailBitePalette.BorderSubtle,
        ),
        shadowElevation = 0.dp,
        modifier = Modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            )
        }
    }
}

@Composable
private fun AutocompleteSuggestionsRow(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(suggestions, key = { it }) { suggestion ->
            Surface(
                onClick = { onSelect(suggestion) },
                shape = RoundedCornerShape(RailBiteRadii.Pill),
                color = RailBitePalette.CyanTint,
                border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                shadowElevation = 0.dp,
                modifier = Modifier.heightIn(min = RailBiteMetrics.TouchTargetMin),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.NorthWest,
                        contentDescription = null,
                        tint = RailBitePalette.CyanPressed,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.labelLarge,
                        color = RailBitePalette.CyanPressed,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    match: SearchMatch,
    qty: Int,
    onInspect: () -> Unit,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
) {
    val item = match.item
    val soldOut = item.stock <= 0
    val meta = remember(item.id) { FoodAssets.metaFor(item) }
    val highlightedName = remember(item.name, match.nameHighlightRanges, soldOut) {
        buildHighlightedTitle(
            title = item.name,
            ranges = match.nameHighlightRanges,
            baseColor = if (soldOut) RailBitePalette.TextMuted else RailBitePalette.TextPrimary,
        )
    }

    Card(
        onClick = onInspect,
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = if (qty > 0 && !soldOut) RailBiteSelectedBorder else RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
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
                    text = highlightedName,
                    style = MaterialTheme.typography.titleSmall,
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = when {
                        soldOut -> "Sold Out"
                        qty > 0 -> "Qty:"
                        else -> "IRCTC MRP"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        soldOut -> RailBitePalette.SemanticRed
                        qty > 0 -> RailBitePalette.TextSecondary
                        else -> RailBitePalette.SemanticGreen
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 6.dp),
                )

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
}

@Composable
private fun EmptySearchStateCard(
    query: String,
    selectedCategory: String?,
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    onClearQuery: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        color = RailBitePalette.SurfaceCard,
        border = RailBiteCardBorder,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 22.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Pill),
                color = RailBitePalette.ImageTile,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SearchOff,
                        contentDescription = null,
                        tint = RailBitePalette.TextSecondary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            val label = query.trim().ifBlank { selectedCategory.orEmpty() }
            Text(
                text = if (label.isNotEmpty()) "No items matched \"$label\"" else "No matching items",
                style = MaterialTheme.typography.titleMedium,
                color = RailBitePalette.TextPrimary,
            )
            Text(
                text = "Try chai, pani, biriyani, samosa, or clear active price/stock filters.",
                style = MaterialTheme.typography.bodySmall,
                color = RailBitePalette.TextSecondary,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (hasActiveFilters) {
                    Button(
                        onClick = onClearFilters,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RailBitePalette.CyanPrimary,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Text("Clear filters", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (query.isNotBlank()) {
                    OutlinedButton(
                        onClick = onClearQuery,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        border = RailBiteCardBorder,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Text("Clear query", style = MaterialTheme.typography.labelLarge, color = RailBitePalette.TextPrimary)
                    }
                }
            }
        }
    }
}

private fun buildHighlightedTitle(
    title: String,
    ranges: List<MatchRange>,
    baseColor: Color,
): AnnotatedString {
    if (ranges.isEmpty()) {
        return AnnotatedString(title, spanStyle = SpanStyle(color = baseColor))
    }
    return buildAnnotatedString {
        append(title)
        addStyle(SpanStyle(color = baseColor), 0, title.length)
        for (r in ranges) {
            val start = r.start.coerceIn(0, title.length)
            val end = r.endExclusive.coerceIn(start, title.length)
            if (start < end) {
                addStyle(
                    SpanStyle(
                        color = RailBitePalette.CyanPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                    start,
                    end,
                )
            }
        }
    }
}
