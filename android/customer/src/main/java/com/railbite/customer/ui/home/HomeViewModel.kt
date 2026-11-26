package com.railbite.customer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.model.MenuItem
import com.railbite.core.util.Validators
import com.railbite.customer.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MenuSortOption(val label: String, val subtitle: String) {
    DEFAULT("Recommended", "Pantry express shelf order"),
    PRICE_LOW_HIGH("Price: Low to High", "Budget drinks & quick snacks first"),
    PRICE_HIGH_LOW("Price: High to Low", "Hearty meals & combos first"),
    NAME_AZ("Name: A to Z", "Alphabetical catalog order"),
}

data class HomeUiState(
    val loading: Boolean = true,
    val menu: List<MenuItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val search: String = "",
    val vegOnly: Boolean = false,
    val inStockOnly: Boolean = false,
    val maxPricePaise: Int? = null,
    val sortBy: MenuSortOption = MenuSortOption.DEFAULT,
    val error: String? = null,
) {
    val activeFilterCount: Int
        get() = (if (vegOnly) 1 else 0) +
            (if (inStockOnly) 1 else 0) +
            (if (maxPricePaise != null) 1 else 0)

    val filtered: List<MenuItem>
        get() = applyCriteria(
            items = menu,
            selectedCategory = selectedCategory,
            search = search,
            vegOnly = vegOnly,
            inStockOnly = inStockOnly,
            maxPricePaise = maxPricePaise,
            sortBy = sortBy,
        )

    fun previewFilteredCount(
        vegOnly: Boolean,
        inStockOnly: Boolean,
        maxPricePaise: Int?,
    ): Int = applyCriteria(
        items = menu,
        selectedCategory = selectedCategory,
        search = search,
        vegOnly = vegOnly,
        inStockOnly = inStockOnly,
        maxPricePaise = maxPricePaise,
        sortBy = sortBy,
    ).size

    companion object {
        private fun applyCriteria(
            items: List<MenuItem>,
            selectedCategory: String?,
            search: String,
            vegOnly: Boolean,
            inStockOnly: Boolean,
            maxPricePaise: Int?,
            sortBy: MenuSortOption,
        ): List<MenuItem> {
            var list = items
            selectedCategory?.let { cat ->
                list = list.filter { it.category.equals(cat, ignoreCase = true) }
            }
            if (search.isNotBlank()) {
                val q = search.trim()
                list = list.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.category.contains(q, ignoreCase = true)
                }
            }
            if (vegOnly) {
                list = list.filter { it.isVeg }
            }
            if (inStockOnly) {
                list = list.filter { it.stock > 0 }
            }
            maxPricePaise?.let { cap ->
                list = list.filter { it.mrpPaise <= cap }
            }
            return when (sortBy) {
                MenuSortOption.DEFAULT -> list
                MenuSortOption.PRICE_LOW_HIGH -> list.sortedBy { it.mrpPaise }
                MenuSortOption.PRICE_HIGH_LOW -> list.sortedByDescending { it.mrpPaise }
                MenuSortOption.NAME_AZ -> list.sortedBy { it.name.lowercase() }
            }
        }
    }
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Cart lines keyed by item id — drives ADD <-> stepper state. */
    val cartLines = container.cart.lines
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val seatInfo: com.railbite.core.data.SeatSnapshot = com.railbite.core.data.SeatSnapshot(container.settings)

    val diners = container.settings.diners.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.menu.isEmpty(), error = null) }
            try {
                val menu = container.client.api().menu().filter { it.track == "READY" }
                // Cache for offline browsing (cloud-first app; the mesh relay reuses this).
                runCatching {
                    container.settings.setMenuCache(
                        com.railbite.core.mesh.MeshStore.json
                            .encodeToString(
                                kotlinx.serialization.builtins.ListSerializer(MenuItem.serializer()),
                                menu,
                            ),
                    )
                }
                val categories = menu.map { it.category }.distinct()
                _state.update {
                    it.copy(
                        loading = false,
                        menu = menu,
                        categories = categories,
                        selectedCategory = if (categories.isEmpty()) it.selectedCategory
                        else it.selectedCategory?.takeIf { c -> categories.contains(c) },
                    )
                }
            } catch (e: Exception) {
                // Offline: serve the last fetched menu so browsing/cart keep working.
                val cached = runCatching {
                    com.railbite.core.mesh.MeshStore.json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(MenuItem.serializer()),
                        container.settings.menuCache.first(),
                    )
                }.getOrDefault(emptyList()).filter { it.track == "READY" }
                val categories = cached.map { it.category }.distinct()
                _state.update {
                    it.copy(
                        loading = false,
                        menu = cached,
                        categories = categories,
                        selectedCategory = if (categories.isEmpty()) it.selectedCategory
                        else it.selectedCategory?.takeIf { c -> categories.contains(c) },
                        error = if (cached.isEmpty()) "Cannot reach server — try again when you have internet"
                        else "No internet — showing the saved menu",
                    )
                }
            }
        }
    }

    fun selectCategory(category: String?) = _state.update { it.copy(selectedCategory = category) }
    fun setSearch(value: String) = _state.update { it.copy(search = value) }

    fun setVegOnly(enabled: Boolean) = _state.update { it.copy(vegOnly = enabled) }
    fun setInStockOnly(enabled: Boolean) = _state.update { it.copy(inStockOnly = enabled) }
    fun setMaxPricePaise(cap: Int?) = _state.update { it.copy(maxPricePaise = cap) }
    fun setSort(sort: MenuSortOption) = _state.update { it.copy(sortBy = sort) }

    fun applyFilters(vegOnly: Boolean, inStockOnly: Boolean, maxPricePaise: Int?) {
        _state.update {
            it.copy(
                vegOnly = vegOnly,
                inStockOnly = inStockOnly,
                maxPricePaise = maxPricePaise,
            )
        }
    }

    fun resetFilters() {
        _state.update {
            it.copy(
                vegOnly = false,
                inStockOnly = false,
                maxPricePaise = null,
            )
        }
    }

    /** Updates passenger PNR, coach, and berth into local settings from Home's SeatSetupBottomSheet. */
    fun updateSeat(
        pnr: String,
        coach: String,
        berthText: String,
        onSaved: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val cleanPnr = pnr.filter(Char::isDigit).take(10)
        val cleanCoach = coach.trim().uppercase()
        val berth = berthText.trim().toIntOrNull() ?: 0
        if (!Validators.isValidPnr(cleanPnr) || !Validators.isValidCoach(cleanCoach) || !Validators.isValidBerth(berth)) {
            onError("Enter a valid 10-digit PNR, Coach (e.g. B2), and Berth (1–100)")
            return
        }
        viewModelScope.launch {
            runCatching {
                container.settings.setSeat(cleanPnr, cleanCoach, berth, diners.value)
                container.settings.setOnboardingDone()
                container.mqtt.ensureConnected()
            }.onSuccess {
                onSaved()
            }.onFailure { err ->
                onError(err.message ?: "Could not save seat details")
            }
        }
    }

    fun addToCart(item: MenuItem) = container.cart.add(item)
    fun decrement(itemId: String) = container.cart.decrement(itemId)

    fun cartCount(): Int = container.cart.itemCount
    fun cartTotalPaise(): Int = container.cart.totalPaise
}
