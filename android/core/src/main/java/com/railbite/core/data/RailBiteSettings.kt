package com.railbite.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "railbite_settings")

/** Everything both apps persist locally: server URL, seat, picker login, online flag. */
class RailBiteSettings(
    private val context: Context,
    /** Build-time baked server (cloud). Used whenever no custom override is stored. */
    private val defaultServerUrl: String = "",
) {

    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val mqttWsUrl = stringPreferencesKey("mqtt_ws_url")
        val trainNumber = stringPreferencesKey("train_number")
        val activeTrainNumber = stringPreferencesKey("active_train_number")
        val pnr = stringPreferencesKey("pnr")
        val coach = stringPreferencesKey("coach")
        val berth = intPreferencesKey("berth")
        val diners = intPreferencesKey("diners")
        val staffId = stringPreferencesKey("staff_id")
        val staffName = stringPreferencesKey("staff_name")
        val staffZone = stringPreferencesKey("staff_zone")
        val online = booleanPreferencesKey("picker_online")
        val onboardingDone = booleanPreferencesKey("customer_onboarding_done")
        val activeOrderId = stringPreferencesKey("customer_active_order_id")
        val pickerOrderId = stringPreferencesKey("picker_current_order_id")
        val howItWorksShown = booleanPreferencesKey("how_it_works_shown")
        // Bluetooth-mesh relay (offline feature; cloud remains the default path)
        val meshNodeId = stringPreferencesKey("mesh_node_id")
        val meshRelayEnabled = booleanPreferencesKey("mesh_relay_enabled")
        val meshSinkEnabled = booleanPreferencesKey("mesh_sink_enabled")
        val menuCache = stringPreferencesKey("menu_cache")
        val meshKey = stringPreferencesKey("mesh_key")
        val recentSearches = stringPreferencesKey("recent_searches")
        val trendingCache = stringPreferencesKey("trending_cache")
    }

    /** Effective server URL: stored override if set, otherwise the baked cloud default. */
    val serverUrl: Flow<String> = context.dataStore.data.map {
        it[Keys.serverUrl]?.takeIf { v -> v.isNotBlank() } ?: defaultServerUrl
    }

    /** Raw stored override ("" when none) — for the advanced settings UIs. */
    val serverUrlStored: Flow<String> = context.dataStore.data.map { it[Keys.serverUrl] ?: "" }
    /** Optional custom MQTT WebSocket URL ("" = derived automatically from [serverUrl]). */
    val mqttWsUrl: Flow<String> = context.dataStore.data.map { it[Keys.mqttWsUrl] ?: "" }
    val trainNumber: Flow<String> = context.dataStore.data.map { it[Keys.trainNumber] ?: "" }
    val activeTrainNumber: Flow<String> = context.dataStore.data.map {
        it[Keys.activeTrainNumber]?.takeIf { v -> v.isNotBlank() } ?: "12345"
    }
    val pnr: Flow<String> = context.dataStore.data.map { it[Keys.pnr] ?: "" }
    val coach: Flow<String> = context.dataStore.data.map { it[Keys.coach] ?: "" }
    val berth: Flow<Int> = context.dataStore.data.map { it[Keys.berth] ?: 0 }
    val diners: Flow<Int> = context.dataStore.data.map { it[Keys.diners] ?: 1 }
    val staffId: Flow<String> = context.dataStore.data.map { it[Keys.staffId] ?: "" }
    val staffName: Flow<String> = context.dataStore.data.map { it[Keys.staffName] ?: "" }
    val staffZone: Flow<String> = context.dataStore.data.map { it[Keys.staffZone] ?: "" }
    val online: Flow<Boolean> = context.dataStore.data.map { it[Keys.online] ?: false }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboardingDone] ?: false }
    val activeOrderId: Flow<String> = context.dataStore.data.map { it[Keys.activeOrderId] ?: "" }
    val pickerOrderId: Flow<String> = context.dataStore.data.map { it[Keys.pickerOrderId] ?: "" }
    val howItWorksShown: Flow<Boolean> = context.dataStore.data.map { it[Keys.howItWorksShown] ?: false }
    val meshNodeId: Flow<String> = context.dataStore.data.map { it[Keys.meshNodeId] ?: "" }
    // Mesh relay/sink default ON: offline ordering over Bluetooth is the product's
    // core field scenario — the toggles remain in Settings as explicit opt-outs.
    val meshRelayEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.meshRelayEnabled] ?: true }
    val meshSinkEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.meshSinkEnabled] ?: true }
    val menuCache: Flow<String> = context.dataStore.data.map { it[Keys.menuCache] ?: "" }
    val trendingCache: Flow<String> = context.dataStore.data.map { it[Keys.trendingCache] ?: "" }
    val recentSearches: Flow<List<String>> = context.dataStore.data.map {
        com.railbite.core.search.RecentSearchHistory.decode(it[Keys.recentSearches])
    }
    val meshKey: Flow<String> = context.dataStore.data.map {
        it[Keys.meshKey]?.takeIf { v -> v.isNotBlank() } ?: com.railbite.core.mesh.BlePacketCodec.RAILBITE_MESH_KEY
    }

    /** Stable mesh node id (origin/hops identity) — generated on first use. */
    suspend fun ensureMeshNodeId(): String {
        val existing = context.dataStore.data.first()[Keys.meshNodeId]
        if (!existing.isNullOrBlank()) return existing
        val fresh = java.util.UUID.randomUUID().toString().take(8)
        context.dataStore.edit { it[Keys.meshNodeId] = fresh }
        return fresh
    }

    suspend fun setMeshRelayEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.meshRelayEnabled] = value }

    suspend fun setMeshSinkEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.meshSinkEnabled] = value }

    suspend fun setMenuCache(rawJson: String) = context.dataStore.edit {
        if (rawJson.isBlank()) it.remove(Keys.menuCache) else it[Keys.menuCache] = rawJson
    }

    suspend fun setServerUrl(value: String) = context.dataStore.edit { it[Keys.serverUrl] = value.trim() }
    suspend fun setMqttWsUrl(value: String) = context.dataStore.edit { it[Keys.mqttWsUrl] = value.trim() }
    suspend fun setTrainNumber(value: String) =
        context.dataStore.edit { it[Keys.trainNumber] = value.trim() }

    suspend fun setActiveTrainNumber(value: String) =
        context.dataStore.edit { it[Keys.activeTrainNumber] = value.trim() }

    suspend fun setSeat(
        pnr: String,
        coach: String,
        berth: Int,
        diners: Int,
        trainNumber: String? = null,
    ) = context.dataStore.edit {
        if (trainNumber != null) {
            it[Keys.trainNumber] = trainNumber.trim()
        }
        it[Keys.pnr] = pnr.trim()
        it[Keys.coach] = coach.trim().uppercase()
        it[Keys.berth] = berth
        it[Keys.diners] = diners
    }

    suspend fun setDiners(value: Int) = context.dataStore.edit { it[Keys.diners] = value }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboardingDone] = true }
    suspend fun setStaffLogin(
        staffId: String,
        name: String,
        zone: String,
        trainNumber: String? = null,
    ) = context.dataStore.edit {
        it[Keys.staffId] = staffId
        it[Keys.staffName] = name
        it[Keys.staffZone] = zone
        if (!trainNumber.isNullOrBlank()) {
            it[Keys.activeTrainNumber] = trainNumber.trim()
        }
    }

    suspend fun clearStaff() = context.dataStore.edit {
        it.remove(Keys.staffId)
        it.remove(Keys.staffName)
        it.remove(Keys.staffZone)
        it.remove(Keys.online)
        it.remove(Keys.pickerOrderId)
    }

    suspend fun setOnline(value: Boolean) = context.dataStore.edit { it[Keys.online] = value }
    suspend fun setActiveOrderId(orderId: String) = context.dataStore.edit {
        if (orderId.isEmpty()) it.remove(Keys.activeOrderId) else it[Keys.activeOrderId] = orderId
    }

    suspend fun setPickerOrderId(orderId: String) = context.dataStore.edit {
        if (orderId.isEmpty()) it.remove(Keys.pickerOrderId) else it[Keys.pickerOrderId] = orderId
    }

    suspend fun setHowItWorksShown() = context.dataStore.edit { it[Keys.howItWorksShown] = true }

    suspend fun setMeshKey(value: String) = context.dataStore.edit {
        val clean = value.trim()
        if (clean.isEmpty()) it.remove(Keys.meshKey) else it[Keys.meshKey] = clean
    }

    suspend fun setTrendingCache(rawJson: String) = context.dataStore.edit {
        if (rawJson.isBlank()) it.remove(Keys.trendingCache) else it[Keys.trendingCache] = rawJson
    }

    suspend fun addRecentSearch(rawQuery: String) = context.dataStore.edit { prefs ->
        val current = com.railbite.core.search.RecentSearchHistory.decode(prefs[Keys.recentSearches])
        val next = com.railbite.core.search.RecentSearchHistory.addQuery(current, rawQuery)
        prefs[Keys.recentSearches] = com.railbite.core.search.RecentSearchHistory.encode(next)
    }

    suspend fun removeRecentSearch(query: String) = context.dataStore.edit { prefs ->
        val current = com.railbite.core.search.RecentSearchHistory.decode(prefs[Keys.recentSearches])
        val next = com.railbite.core.search.RecentSearchHistory.removeQuery(current, query)
        if (next.isEmpty()) prefs.remove(Keys.recentSearches)
        else prefs[Keys.recentSearches] = com.railbite.core.search.RecentSearchHistory.encode(next)
    }

    suspend fun clearRecentSearches() = context.dataStore.edit {
        it.remove(Keys.recentSearches)
    }
}
