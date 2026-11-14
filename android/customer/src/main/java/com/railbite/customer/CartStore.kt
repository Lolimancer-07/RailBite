package com.railbite.customer

import com.railbite.core.model.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process cart. Keyed by menu item id; the line keeps a snapshot of the MenuItem
 * (name/MRP come from the server; the order POST carries ids + qty only).
 */
data class CartLine(val item: MenuItem, val qty: Int)

class CartStore {
    private val _lines = MutableStateFlow<Map<String, CartLine>>(emptyMap())
    val lines: StateFlow<Map<String, CartLine>> = _lines.asStateFlow()

    val itemCount: Int get() = _lines.value.values.sumOf { it.qty }
    val totalPaise: Int get() = _lines.value.values.sumOf { it.qty * it.item.mrpPaise }

    fun add(item: MenuItem) = _lines.update { m ->
        val cur = m[item.id]
        if (cur == null) {
            m + (item.id to CartLine(item, 1))
        } else if (cur.qty < 10) {
            m + (item.id to cur.copy(qty = cur.qty + 1))
        } else m
    }

    fun decrement(itemId: String) = _lines.update { m ->
        val cur = m[itemId] ?: return@update m
        if (cur.qty <= 1) m - itemId else m + (itemId to cur.copy(qty = cur.qty - 1))
    }

    fun clear() = _lines.update { emptyMap() }

    fun contains(itemId: String): Boolean = _lines.value.containsKey(itemId)
}
