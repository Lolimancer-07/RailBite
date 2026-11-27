package com.railbite.core.data

import android.content.Context
import com.railbite.core.mesh.MeshStore
import com.railbite.core.model.MenuItem
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Bundled deterministic pantry catalog backed by `assets/default_menu.json` and DataStore.
 *
 * Guarantees immediate offline menu rendering in Customer and deterministic item lookup by
 * numeric ID (`1..16`) in Picker without ever hitting the network on first launch.
 */
object OfflineMenuCatalog {

    private const val ASSET_NAME = "default_menu.json"
    private val SERVER_ID_NUM_REGEX = Regex("^10000000-0000-0000-0000-000000000(\\d{3})$")

    @Serializable
    data class CatalogEntry(
        val numericId: Int,
        val id: String,
        val name: String,
        val category: String,
        val track: String = "READY",
        val mrpPaise: Int,
        val stock: Int = 100,
        val prepMinutes: Int = 0,
        val isVeg: Boolean = true,
        val active: Boolean = true,
    ) {
        fun toMenuItem(): MenuItem = MenuItem(
            id = id,
            name = name,
            category = category,
            track = track,
            mrpPaise = mrpPaise,
            stock = stock,
            prepMinutes = prepMinutes,
            isVeg = isVeg,
            active = active,
        )
    }

    @Volatile
    private var bundledCache: List<CatalogEntry>? = null

    /** Parses `assets/default_menu.json` safely once per process. */
    fun bundledEntries(context: Context): List<CatalogEntry> {
        bundledCache?.let { return it }
        val jsonText = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        val parsed = parseBundledJson(jsonText)
        bundledCache = parsed
        return parsed
    }

    /** Pure parser exposed for JVM unit tests. */
    fun parseBundledJson(rawJson: String): List<CatalogEntry> = runCatching {
        MeshStore.json.decodeFromString(ListSerializer(CatalogEntry.serializer()), rawJson)
    }.getOrDefault(emptyList())

    /** Seeds DataStore's `menuCache` from `default_menu.json` on first offline launch. */
    suspend fun seedIfNeeded(context: Context, settings: RailBiteSettings): List<MenuItem> {
        val existingRaw = settings.menuCache.first()
        if (existingRaw.isNotBlank()) {
            val cached = runCatching {
                MeshStore.json.decodeFromString(ListSerializer(MenuItem.serializer()), existingRaw)
            }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) return cached
        }
        val bundled = bundledEntries(context).map { it.toMenuItem() }
        if (bundled.isNotEmpty()) {
            runCatching { saveMenuCache(settings, bundled) }
        }
        return bundled
    }

    suspend fun saveMenuCache(settings: RailBiteSettings, items: List<MenuItem>) {
        settings.setMenuCache(
            MeshStore.json.encodeToString(ListSerializer(MenuItem.serializer()), items),
        )
    }

    /**
     * Resolves a server/catalog string ID (`"10000000-...-0001"` or `"1"`) to its fixed
     * 1-byte numeric BLE ID (`1..255`).
     */
    fun numericIdFor(context: Context, itemId: String, itemName: String? = null): Int? {
        itemId.trim().toIntOrNull()?.takeIf { it in 1..255 }?.let { return it }
        SERVER_ID_NUM_REGEX.matchEntire(itemId.trim())?.let { match ->
            match.groupValues[1].toIntOrNull()?.takeIf { it in 1..255 }?.let { return it }
        }
        val entries = bundledEntries(context)
        entries.firstOrNull { it.id.equals(itemId, ignoreCase = true) }?.let { return it.numericId }
        if (!itemName.isNullOrBlank()) {
            entries.firstOrNull { it.name.equals(itemName.trim(), ignoreCase = true) }?.let {
                return it.numericId
            }
        }
        return null
    }

    /**
     * Looks up item metadata for a decoded BLE numeric ID, preferring DataStore's refreshed
     * menu cache if available and falling back cleanly to `assets/default_menu.json`.
     */
    suspend fun resolveByNumericId(
        context: Context,
        settings: RailBiteSettings,
        numericId: Int,
    ): CatalogEntry {
        val entries = bundledEntries(context)
        val bundled = entries.firstOrNull { it.numericId == numericId }
        val cachedItems = runCatching {
            val raw = settings.menuCache.first()
            if (raw.isBlank()) emptyList()
            else MeshStore.json.decodeFromString(ListSerializer(MenuItem.serializer()), raw)
        }.getOrDefault(emptyList())

        val matchedCached = cachedItems.firstOrNull { item ->
            numericIdFor(context, item.id, item.name) == numericId
        }
        if (matchedCached != null) {
            return CatalogEntry(
                numericId = numericId,
                id = matchedCached.id,
                name = matchedCached.name,
                category = matchedCached.category,
                track = matchedCached.track,
                mrpPaise = matchedCached.mrpPaise,
                stock = matchedCached.stock,
                prepMinutes = matchedCached.prepMinutes,
                isVeg = matchedCached.isVeg,
                active = matchedCached.active,
            )
        }
        return bundled ?: CatalogEntry(
            numericId = numericId,
            id = "10000000-0000-0000-0000-${numericId.toString().padStart(12, '0')}",
            name = "Item #$numericId",
            category = "SNACKS",
            mrpPaise = 1500,
        )
    }
}

