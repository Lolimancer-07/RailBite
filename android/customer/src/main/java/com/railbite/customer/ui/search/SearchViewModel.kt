package com.railbite.customer.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.data.OfflineMenuCatalog
import com.railbite.core.data.SeatSnapshot
import com.railbite.core.model.MenuItem
import com.railbite.core.search.RecentSearchHistory
import com.railbite.core.search.SearchCriteria
import com.railbite.core.search.SearchEngine
import com.railbite.core.search.SearchMatch
import com.railbite.customer.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SearchUiState(
    val query: String = "",
    val selectedCategory: String? = null,
    val inStockOnly: Boolean = false,
    val maxPricePaise: Int? = null,
    val sortByPriceAsc: Boolean = false,
    val loading: Boolean = true,
    val lanLive: Boolean = false,
    val results: List<SearchMatch> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val trending: List<MenuItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
) {
    val isActiveSearch: Boolean
        get() = query.isNotBlank() || selectedCategory != null || inStockOnly || maxPricePaise != null || sortByPriceAsc

    val hasActiveFilters: Boolean
        get() = selectedCategory != null || inStockOnly || maxPricePaise != null || sortByPriceAsc
}

private data class QueryInput(
    val text: String = "",
    val immediate: Boolean = false,
)

private data class FilterSnapshot(
    val category: String? = null,
    val inStockOnly: Boolean = false,
    val maxPricePaise: Int? = null,
    val sortByPriceAsc: Boolean = false,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {

    private val _queryInput = MutableStateFlow(QueryInput())
    private val _filters = MutableStateFlow(FilterSnapshot())
    private val _menu = MutableStateFlow<List<MenuItem>>(emptyList())

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val cartLines = container.cart.lines
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val seatInfo = SeatSnapshot(container.settings)

    init {
        loadCatalogAndTrending()

        // Observe persisted Recent Searches on IO
        viewModelScope.launch {
            container.settings.recentSearches
                .flowOn(Dispatchers.IO)
                .collect { recents ->
                    _state.update { it.copy(recentSearches = recents) }
                }
        }

        // Debounced (150ms) off-main-thread search & autocomplete computation
        val debouncedQuery = _queryInput
            .debounce { input -> if (input.immediate || input.text.isEmpty()) 0L else 150L }
            .map { it.text }
            .distinctUntilChanged()

        viewModelScope.launch {
            combine(debouncedQuery, _menu, _filters) { query, menu, f ->
                val criteria = SearchCriteria(
                    query = query,
                    category = f.category,
                    inStockOnly = f.inStockOnly,
                    maxPricePaise = f.maxPricePaise,
                    sortByPriceAsc = f.sortByPriceAsc,
                )
                val computedResults = SearchEngine.search(menu, criteria)
                val computedSuggestions = if (query.trim().isNotEmpty()) {
                    SearchEngine.autocompleteSuggestions(menu, query, limit = 6)
                } else {
                    emptyList()
                }
                computedResults to computedSuggestions
            }
                .flowOn(Dispatchers.Default)
                .collect { (results, suggestions) ->
                    _state.update { cur ->
                        cur.copy(
                            results = results,
                            suggestions = suggestions,
                        )
                    }
                }
        }
    }

    private fun loadCatalogAndTrending() {
        viewModelScope.launch(Dispatchers.IO) {
            val localMenu = OfflineMenuCatalog
                .seedIfNeeded(container.app, container.settings)
                .filter { it.track == "READY" }
            val localTrending = OfflineMenuCatalog.loadTrending(container.app, container.settings, limit = 6)

            _menu.value = localMenu
            _state.update {
                it.copy(
                    loading = false,
                    trending = localTrending,
                )
            }

            if (!container.transportRouter.checkLanReachable()) {
                return@launch
            }

            val menuDeferred = async {
                withTimeoutOrNull(1_500) {
                    runCatching { container.client.api().menu().filter { it.track == "READY" } }.getOrNull()
                }
            }
            val trendingDeferred = async {
                withTimeoutOrNull(1_500) {
                    runCatching { container.client.api().trendingMenu(6).filter { it.track == "READY" } }.getOrNull()
                }
            }

            val liveMenu = menuDeferred.await()
            val liveTrending = trendingDeferred.await()

            if (!liveMenu.isNullOrEmpty()) {
                runCatching { OfflineMenuCatalog.saveMenuCache(container.settings, liveMenu) }
                _menu.value = liveMenu
            }
            if (!liveTrending.isNullOrEmpty()) {
                runCatching { OfflineMenuCatalog.saveTrendingCache(container.settings, liveTrending) }
            }

            _state.update { cur ->
                cur.copy(
                    lanLive = liveMenu != null || liveTrending != null,
                    trending = if (!liveTrending.isNullOrEmpty()) liveTrending else cur.trending,
                )
            }
        }
    }

    fun onQueryChange(newValue: String) {
        _state.update { it.copy(query = newValue) }
        _queryInput.value = QueryInput(text = newValue, immediate = false)
    }

    fun clearQuery() {
        _state.update { it.copy(query = "") }
        _queryInput.value = QueryInput(text = "", immediate = true)
    }

    fun submitSearch(explicitQuery: String = _state.value.query) {
        val clean = RecentSearchHistory.normalize(explicitQuery)
        if (clean.isEmpty()) return
        _state.update { it.copy(query = clean) }
        _queryInput.value = QueryInput(text = clean, immediate = true)
        viewModelScope.launch(Dispatchers.IO) {
            container.settings.addRecentSearch(clean)
        }
    }

    fun applyRecentSearch(query: String) {
        val clean = RecentSearchHistory.normalize(query)
        if (clean.isEmpty()) return
        _state.update { it.copy(query = clean) }
        _queryInput.value = QueryInput(text = clean, immediate = true)
        viewModelScope.launch(Dispatchers.IO) {
            container.settings.addRecentSearch(clean)
        }
    }

    fun applySuggestion(suggestion: String) {
        submitSearch(suggestion)
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.settings.removeRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch(Dispatchers.IO) {
            container.settings.clearRecentSearches()
        }
    }

    fun selectCategoryShortcut(categoryLabel: String) {
        _filters.update { it.copy(category = categoryLabel) }
        _state.update { it.copy(selectedCategory = categoryLabel) }
    }

    fun clearCategoryFilter() {
        _filters.update { it.copy(category = null) }
        _state.update { it.copy(selectedCategory = null) }
    }

    fun toggleInStockOnly() {
        _filters.update { cur ->
            val next = !cur.inStockOnly
            _state.update { it.copy(inStockOnly = next) }
            cur.copy(inStockOnly = next)
        }
    }

    fun toggleUnder50() {
        _filters.update { cur ->
            val nextCap = if (cur.maxPricePaise == 5_000) null else 5_000
            _state.update { it.copy(maxPricePaise = nextCap) }
            cur.copy(maxPricePaise = nextCap)
        }
    }

    fun toggleUnder100() {
        _filters.update { cur ->
            val nextCap = if (cur.maxPricePaise == 10_000) null else 10_000
            _state.update { it.copy(maxPricePaise = nextCap) }
            cur.copy(maxPricePaise = nextCap)
        }
    }

    fun toggleSortPriceLowHigh() {
        _filters.update { cur ->
            val next = !cur.sortByPriceAsc
            _state.update { it.copy(sortByPriceAsc = next) }
            cur.copy(sortByPriceAsc = next)
        }
    }

    fun clearAllFilters() {
        _filters.value = FilterSnapshot()
        _state.update {
            it.copy(
                selectedCategory = null,
                inStockOnly = false,
                maxPricePaise = null,
                sortByPriceAsc = false,
            )
        }
    }

    fun onInspectItem(item: MenuItem) {
        val currentQuery = _state.value.query
        if (currentQuery.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                container.settings.addRecentSearch(currentQuery)
            }
        }
    }

    fun addToCart(item: MenuItem) = container.cart.add(item)

    fun decrement(itemId: String) = container.cart.decrement(itemId)
}
