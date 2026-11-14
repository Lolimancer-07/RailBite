package com.railbite.customer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.model.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val menu: List<MenuItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val search: String = "",
    val error: String? = null,
) {
    val filtered: List<MenuItem>
        get() {
            var list = menu
            selectedCategory?.let { cat -> list = list.filter { it.category == cat } }
            if (search.isNotBlank()) {
                val q = search.trim()
                list = list.filter { it.name.contains(q, ignoreCase = true) }
            }
            return list
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
                _state.update {
                    it.copy(
                        loading = false,
                        error = "Cannot reach server — showing cached data" ,
                    )
                }
            }
        }
    }

    fun selectCategory(category: String?) = _state.update { it.copy(selectedCategory = category) }
    fun setSearch(value: String) = _state.update { it.copy(search = value) }

    fun addToCart(item: MenuItem) = container.cart.add(item)
    fun decrement(itemId: String) = container.cart.decrement(itemId)

    fun cartCount(): Int = container.cart.itemCount
    fun cartTotalPaise(): Int = container.cart.totalPaise
}
