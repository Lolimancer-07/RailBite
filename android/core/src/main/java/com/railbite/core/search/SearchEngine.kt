package com.railbite.core.search

import com.railbite.core.model.MenuItem

data class SearchCriteria(
    val query: String = "",
    val category: String? = null,
    val inStockOnly: Boolean = false,
    val maxPricePaise: Int? = null,
    val sortByPriceAsc: Boolean = false,
)

data class MatchRange(
    val start: Int,
    val endExclusive: Int,
)

data class SearchMatch(
    val item: MenuItem,
    val score: Int,
    val nameHighlightRanges: List<MatchRange>,
)

/**
 * Pure, high-speed offline pantry search engine designed to execute on `Dispatchers.Default`:
 * - Exact & prefix scoring prioritized above substring/category matches
 * - Built-in Indian Railway pantry synonym map (`tea`/`garam` -> Chai, `pani` -> Water, etc.)
 * - Damerau-Levenshtein typo tolerance (`dist <= 1` for 4–6 chars, `dist <= 2` for 7+ chars)
 * - Autocomplete item-name completions and cyan highlight match ranges
 */
object SearchEngine {

    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val MULTI_SPACE = Regex("\\s+")

    /** Curated Indian Railways pantry synonym lookup. */
    private val SYNONYM_MAP: Map<String, List<String>> = mapOf(
        "tea" to listOf("chai", "masala chai"),
        "garam" to listOf("chai", "coffee", "samosa", "bonda"),
        "cha" to listOf("chai"),
        "adrak" to listOf("masala chai"),
        "chai" to listOf("tea", "masala"),
        "cofe" to listOf("coffee"),
        "cofi" to listOf("coffee"),
        "kaapi" to listOf("filter coffee"),
        "nescafe" to listOf("coffee"),
        "pani" to listOf("water", "rail neer"),
        "paani" to listOf("water", "rail neer"),
        "water" to listOf("rail neer", "packaged water"),
        "bottle" to listOf("water", "rail neer"),
        "mineral" to listOf("water", "rail neer"),
        "neer" to listOf("water", "rail neer"),
        "railneer" to listOf("rail neer"),
        "biriyani" to listOf("biryani"),
        "rice" to listOf("biryani", "meals"),
        "pulao" to listOf("biryani"),
        "pulav" to listOf("biryani"),
        "chawal" to listOf("biryani"),
        "biskut" to listOf("biscuits", "parle"),
        "biscut" to listOf("biscuits", "parle"),
        "biskuit" to listOf("biscuits", "parle"),
        "cookie" to listOf("biscuits", "parle"),
        "cookies" to listOf("biscuits", "parle"),
        "glucose" to listOf("parle-g biscuits"),
        "parle" to listOf("biscuits"),
        "nasta" to listOf("snacks", "samosa", "puff", "chips", "bonda"),
        "nashta" to listOf("snacks", "samosa", "puff", "chips", "bonda"),
        "snack" to listOf("snacks", "samosa", "puff", "chips"),
        "namkeen" to listOf("chips", "snacks"),
        "thali" to listOf("meals", "biryani", "sandwich", "frankie"),
        "khana" to listOf("meals", "biryani", "sandwich", "frankie"),
        "lunch" to listOf("meals", "biryani", "sandwich", "frankie"),
        "dinner" to listOf("meals", "biryani", "frankie", "noodles"),
        "meal" to listOf("meals", "biryani", "sandwich"),
        "food" to listOf("meals", "biryani"),
        "roti" to listOf("frankie", "meals"),
        "cold" to listOf("cola", "buttermilk", "beverages"),
        "thanda" to listOf("cola", "buttermilk", "beverages"),
        "soda" to listOf("cola"),
        "coke" to listOf("cola"),
        "pepsi" to listOf("cola"),
        "drink" to listOf("cola", "buttermilk", "beverages", "water"),
        "chaas" to listOf("buttermilk"),
        "chach" to listOf("buttermilk"),
        "lassi" to listOf("buttermilk"),
        "mattha" to listOf("buttermilk"),
        "maggi" to listOf("noodles", "cup noodles"),
        "maggie" to listOf("noodles", "cup noodles"),
        "noodle" to listOf("cup noodles"),
        "wrap" to listOf("frankie"),
        "roll" to listOf("frankie"),
        "nonveg" to listOf("chicken"),
        "bread" to listOf("sandwich"),
        "toast" to listOf("sandwich"),
        "sweet" to listOf("cake", "honey"),
        "mithai" to listOf("cake", "honey"),
        "pastry" to listOf("puff", "cake"),
        "breakfast" to listOf("bonda", "cake", "chai", "coffee"),
        "morning" to listOf("breakfast", "chai", "coffee"),
    )

    fun search(
        items: List<MenuItem>,
        criteria: SearchCriteria,
    ): List<SearchMatch> {
        val normalizedQuery = criteria.query.trim().replace(MULTI_SPACE, " ").lowercase()

        var base = items.asSequence()
            .filter { it.active }
            .filter { item -> criteria.category == null || matchesCategory(item, criteria.category) }
            .filter { item -> !criteria.inStockOnly || item.stock > 0 }
            .filter { item -> criteria.maxPricePaise == null || item.mrpPaise <= criteria.maxPricePaise }
            .toList()

        val matches = if (normalizedQuery.isEmpty()) {
            base.map { SearchMatch(item = it, score = 100, nameHighlightRanges = emptyList()) }
        } else {
            val tokens = normalizedQuery.split(" ").filter { it.isNotEmpty() }
            val strippedQuery = normalizedQuery.replace(" ", "")
            base.mapNotNull { item ->
                val score = scoreItem(item, normalizedQuery, strippedQuery, tokens)
                if (score <= 0) {
                    null
                } else {
                    SearchMatch(
                        item = item,
                        score = score,
                        nameHighlightRanges = findHighlightRanges(item.name, normalizedQuery, tokens),
                    )
                }
            }
        }

        return if (criteria.sortByPriceAsc) {
            matches.sortedWith(
                compareBy<SearchMatch> { it.item.mrpPaise }
                    .thenByDescending { it.item.stock > 0 }
                    .thenByDescending { it.score }
                    .thenBy { it.item.name.lowercase() },
            )
        } else {
            matches.sortedWith(
                compareByDescending<SearchMatch> { it.score }
                    .thenByDescending { it.item.stock > 0 }
                    .thenBy { it.item.trendingRank ?: 99 }
                    .thenBy { it.item.name.lowercase() },
            )
        }
    }

    /** Suggests compact autocomplete completion labels while typing 1+ characters. */
    fun autocompleteSuggestions(
        items: List<MenuItem>,
        query: String,
        limit: Int = 6,
    ): List<String> {
        val q = query.trim().replace(MULTI_SPACE, " ").lowercase()
        if (q.isEmpty()) return emptyList()
        return search(items, SearchCriteria(query = q))
            .map { it.item.name }
            .filter { !it.equals(q, ignoreCase = true) }
            .distinct()
            .take(limit.coerceAtLeast(1))
    }

    fun matchesCategory(item: MenuItem, shortcutOrCategory: String): Boolean {
        val filter = shortcutOrCategory.trim().uppercase()
        val itemCat = item.category.trim().uppercase()
        if (filter.isEmpty() || filter == "ALL") return true
        if (itemCat == filter) return true
        return when (filter) {
            "BEVERAGES" -> itemCat == "BEVERAGES" || itemCat == "WATER"
            "SNACKS" -> itemCat == "SNACKS" || itemCat == "BREAKFAST"
            "LUNCH", "DINNER" -> itemCat == "MEALS"
            else -> false
        }
    }

    private fun scoreItem(
        item: MenuItem,
        fullQuery: String,
        strippedQuery: String,
        tokens: List<String>,
    ): Int {
        val nameLower = item.name.lowercase()
        val nameNoSpaces = nameLower.replace(" ", "")
        val categoryLower = item.category.lowercase()
        val words = (nameLower.split(NON_ALNUM) + categoryLower.split(NON_ALNUM))
            .filter { it.length >= 2 }

        var score = 0

        // 1. Exact / Prefix matches on full name
        if (nameLower == fullQuery || nameNoSpaces == strippedQuery) {
            score = maxOf(score, 1000)
        } else if (nameLower.startsWith(fullQuery) || nameNoSpaces.startsWith(strippedQuery)) {
            score = maxOf(score, 850)
        }

        // Word-start prefix (e.g. "cha" -> "Masala Chai", "bir" -> "Packed Veg Biryani")
        if (score < 700 && words.any { it.startsWith(fullQuery) }) {
            score = maxOf(score, 700)
        }

        // 2. Substring contains in item name
        if (score < 550 && (nameLower.contains(fullQuery) || nameNoSpaces.contains(strippedQuery))) {
            score = maxOf(score, 550)
        }

        // Multi-word AND substring coverage (e.g. "veg biryani" inside "Packed Veg Biryani")
        if (tokens.size > 1 && tokens.all { tok -> nameLower.contains(tok) || categoryLower.contains(tok) }) {
            score = maxOf(score, 520 + (tokens.size * 15))
        }

        // 3. Direct category match
        if (categoryLower == fullQuery || categoryLower.contains(fullQuery)) {
            score = maxOf(score, 380)
        }

        // 4. Synonym lookup across full query and individual tokens
        val queryCandidates = (listOf(fullQuery, strippedQuery) + tokens).distinct()
        var synonymScore = 0
        for (qTok in queryCandidates) {
            val targets = SYNONYM_MAP[qTok].orEmpty()
            for (target in targets) {
                when {
                    nameLower.contains(target) -> synonymScore = maxOf(synonymScore, 320)
                    categoryLower.contains(target) -> synonymScore = maxOf(synonymScore, 240)
                }
            }
        }
        score = maxOf(score, synonymScore)

        // 5. Lightweight typo tolerance (Damerau-Levenshtein: swaps count as distance 1)
        if (score == 0) {
            var bestTypoScore = 0
            for (tok in queryCandidates) {
                val maxDist = maxEditDistanceFor(tok.length)
                if (maxDist <= 0) continue

                // Check whole stripped query against stripped name/words
                val nameWords = nameLower.split(NON_ALNUM).filter { it.length >= 3 } + nameNoSpaces
                for (w in nameWords) {
                    if (kotlin.math.abs(w.length - tok.length) > maxDist) continue
                    val d = damerauLevenshtein(tok, w)
                    if (d <= maxDist) {
                        val candidate = 220 - (d * 40)
                        bestTypoScore = maxOf(bestTypoScore, candidate)
                    }
                }
            }
            score = bestTypoScore
        }

        return score
    }

    fun maxEditDistanceFor(length: Int): Int = when {
        length < 4 -> 0
        length <= 6 -> 1
        else -> 2
    }

    /** Optimal String Alignment (Damerau-Levenshtein) edit distance. */
    fun damerauLevenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n

        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = minOf(best, dp[i - 2][j - 2] + 1)
                }
                dp[i][j] = best
            }
        }
        return dp[n][m]
    }

    fun findHighlightRanges(
        itemName: String,
        normalizedQuery: String,
        tokens: List<String> = normalizedQuery.split(" ").filter { it.isNotEmpty() },
    ): List<MatchRange> {
        if (normalizedQuery.isEmpty() || itemName.isEmpty()) return emptyList()
        val lower = itemName.lowercase()
        val ranges = mutableListOf<MatchRange>()

        fun collectAll(needle: String) {
            if (needle.isEmpty()) return
            var startIdx = 0
            while (startIdx < lower.length) {
                val idx = lower.indexOf(needle, startIdx)
                if (idx < 0) break
                ranges.add(MatchRange(idx, idx + needle.length))
                startIdx = idx + needle.length
            }
        }

        collectAll(normalizedQuery)
        if (ranges.isEmpty()) {
            tokens.filter { it.length >= 2 }.forEach { collectAll(it) }
        }
        if (ranges.isEmpty()) return emptyList()

        // Merge overlapping/adjacent ranges cleanly
        val sorted = ranges.sortedBy { it.start }
        val merged = mutableListOf<MatchRange>()
        for (cur in sorted) {
            val prev = merged.lastOrNull()
            if (prev != null && cur.start <= prev.endExclusive) {
                merged[merged.lastIndex] = MatchRange(prev.start, maxOf(prev.endExclusive, cur.endExclusive))
            } else {
                merged.add(cur)
            }
        }
        return merged
    }
}
