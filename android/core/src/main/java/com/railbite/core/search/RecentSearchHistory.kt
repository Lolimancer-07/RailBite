package com.railbite.core.search

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Pure, deterministic recent-search list rules:
 * - Trims whitespace and collapses repeated internal spaces
 * - Case-insensitive deduplication keeping newest occurrence at index 0
 * - Capped strictly at [MAX_ITEMS] (`8`)
 */
object RecentSearchHistory {

    const val MAX_ITEMS = 8

    private val json = Json { ignoreUnknownKeys = true }

    fun normalize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    fun addQuery(
        current: List<String>,
        rawQuery: String,
        maxItems: Int = MAX_ITEMS,
    ): List<String> {
        val clean = normalize(rawQuery)
        if (clean.isEmpty()) return current
        val deduped = current.filterNot { it.equals(clean, ignoreCase = true) }
        return (listOf(clean) + deduped).take(maxItems.coerceAtLeast(1))
    }

    fun removeQuery(
        current: List<String>,
        query: String,
    ): List<String> {
        val clean = normalize(query)
        return current.filterNot { it.equals(clean, ignoreCase = true) }
    }

    fun encode(items: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), items.take(MAX_ITEMS))

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw).take(MAX_ITEMS)
        }.getOrDefault(emptyList())
    }
}
