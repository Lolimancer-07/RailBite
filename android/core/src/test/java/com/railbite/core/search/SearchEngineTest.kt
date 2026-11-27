package com.railbite.core.search

import com.railbite.core.model.MenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private val sampleMenu = listOf(
        item("1", "Masala Chai", "BEVERAGES", mrpPaise = 1500, stock = 200, trendingRank = 1),
        item("2", "Filter Coffee", "BEVERAGES", mrpPaise = 2000, stock = 200, trendingRank = 6),
        item("3", "Parle-G Biscuits", "SNACKS", mrpPaise = 1000, stock = 150, trendingRank = 5),
        item("4", "Potato Chips", "SNACKS", mrpPaise = 2000, stock = 0, trendingRank = 8),
        item("5", "Rail Neer Water (1L)", "WATER", mrpPaise = 1500, stock = 250, trendingRank = 4),
        item("6", "Samosa (2 pcs)", "SNACKS", mrpPaise = 3000, stock = 120, trendingRank = 3),
        item("7", "Veg Grilled Sandwich", "MEALS", mrpPaise = 4500, stock = 100, trendingRank = 7),
        item("8", "Packed Veg Biryani", "MEALS", mrpPaise = 9000, stock = 120, trendingRank = 2),
        item("9", "Cup Noodles", "MEALS", mrpPaise = 5500, stock = 80, trendingRank = 9),
        item("11", "Cola Can (330ml)", "BEVERAGES", mrpPaise = 4000, stock = 120, trendingRank = 11),
        item("12", "Masala Buttermilk", "BEVERAGES", mrpPaise = 1500, stock = 100, trendingRank = 12),
        item("13", "Packaged Water (500ml)", "WATER", mrpPaise = 1000, stock = 250, trendingRank = 13),
    )

    @Test
    fun `prefix matches rank ahead of substring and synonym matches`() {
        val customMenu = listOf(
            item("a", "Classic Chai Masala Mix", "SNACKS", mrpPaise = 2500, stock = 50),
            item("b", "Masala Chai", "BEVERAGES", mrpPaise = 1500, stock = 50),
            item("c", "Masala Buttermilk", "BEVERAGES", mrpPaise = 1500, stock = 50),
        )
        val results = SearchEngine.search(customMenu, SearchCriteria(query = "masala c"))
        assertEquals("Masala Chai", results.first().item.name)

        val masalaPrefix = SearchEngine.search(sampleMenu, SearchCriteria(query = "mas"))
        assertTrue(masalaPrefix.take(2).all { it.item.name.startsWith("Masala") })

        // Highlight ranges detect exact matched substring in Masala Chai
        val chaiMatch = SearchEngine.search(sampleMenu, SearchCriteria(query = "chai"))
            .first { it.item.name == "Masala Chai" }
        assertEquals(listOf(MatchRange(7, 11)), chaiMatch.nameHighlightRanges)
    }

    @Test
    fun `indian railway synonym lookup resolves tea pani and nashta`() {
        val teaNames = SearchEngine.search(sampleMenu, SearchCriteria(query = "tea")).map { it.item.name }
        assertTrue("tea should match Masala Chai", "Masala Chai" in teaNames)

        val paniNames = SearchEngine.search(sampleMenu, SearchCriteria(query = "pani")).map { it.item.name }
        assertTrue("pani should match Rail Neer Water", "Rail Neer Water (1L)" in paniNames)
        assertTrue("pani should match Packaged Water", "Packaged Water (500ml)" in paniNames)

        val nastaNames = SearchEngine.search(sampleMenu, SearchCriteria(query = "nasta")).map { it.item.name }
        assertTrue("nasta should match Samosa", "Samosa (2 pcs)" in nastaNames)
    }

    @Test
    fun `typo tolerance matches biriyani samsoa and cofee without false positives on gibberish`() {
        val biryani = SearchEngine.search(sampleMenu, SearchCriteria(query = "biriyani")).map { it.item.name }
        assertTrue("biriyani should match Packed Veg Biryani", "Packed Veg Biryani" in biryani)

        val samosa = SearchEngine.search(sampleMenu, SearchCriteria(query = "samsoa")).map { it.item.name }
        assertTrue("samsoa (transposition dist 1) should match Samosa (2 pcs)", "Samosa (2 pcs)" in samosa)

        val coffee = SearchEngine.search(sampleMenu, SearchCriteria(query = "cofee")).map { it.item.name }
        assertTrue("cofee (dist 1) should match Filter Coffee", "Filter Coffee" in coffee)

        val nonsense = SearchEngine.search(sampleMenu, SearchCriteria(query = "qzxxz"))
        assertTrue(nonsense.isEmpty())
    }

    @Test
    fun `combined filters in-stock under 50 sort low-high and category shortcuts work accurately`() {
        val filtered = SearchEngine.search(
            sampleMenu,
            SearchCriteria(
                query = "",
                inStockOnly = true,
                maxPricePaise = 5000,
                sortByPriceAsc = true,
            ),
        )
        assertTrue(filtered.isNotEmpty())
        assertFalse(filtered.any { it.item.stock <= 0 }) // Potato Chips (0 stock) excluded
        assertTrue(filtered.all { it.item.mrpPaise <= 5000 }) // Biryani/Noodles excluded
        val prices = filtered.map { it.item.mrpPaise }
        assertEquals(prices.sorted(), prices)

        val lunchItems = SearchEngine.search(sampleMenu, SearchCriteria(category = "Lunch"))
        assertTrue(lunchItems.any { it.item.name == "Packed Veg Biryani" })
        assertFalse(lunchItems.any { it.item.name == "Masala Chai" })
    }

    @Test
    fun `recent search history deduplicates case-insensitively and enforces 8-item cap`() {
        var history = emptyList<String>()
        val inputs = listOf(
            "Chai",
            "Samosa",
            "Biryani",
            "Water",
            "Coffee",
            "Chips",
            "Noodles",
            "Sandwich",
            "Biscuits",
        )
        inputs.forEach { history = RecentSearchHistory.addQuery(history, it) }

        assertEquals(8, history.size)
        assertEquals("Biscuits", history.first())
        assertFalse("Oldest item Chai should have been evicted", "Chai" in history)

        // Adding "samosa" (different case, extra whitespace) promotes it to front without duplicate
        history = RecentSearchHistory.addQuery(history, "  samosa  ")
        assertEquals(8, history.size)
        assertEquals("samosa", history.first())
        assertEquals(1, history.count { it.equals("Samosa", ignoreCase = true) })

        // Single removal
        history = RecentSearchHistory.removeQuery(history, "Coffee")
        assertEquals(7, history.size)
        assertFalse("Coffee" in history)
    }

    private fun item(
        id: String,
        name: String,
        category: String,
        mrpPaise: Int,
        stock: Int,
        trendingRank: Int = 99,
    ) = MenuItem(
        id = id,
        name = name,
        category = category,
        track = "READY",
        mrpPaise = mrpPaise,
        stock = stock,
        prepMinutes = 0,
        isVeg = true,
        active = true,
        trendingRank = trendingRank,
    )
}
