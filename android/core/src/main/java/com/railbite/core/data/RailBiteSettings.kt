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
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "railbite_settings")

/** Everything both apps persist locally: server URL, seat, picker login, online flag. */
class RailBiteSettings(private val context: Context) {

    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val pnr = stringPreferencesKey("pnr")
        val coach = stringPreferencesKey("coach")
        val berth = intPreferencesKey("berth")
        val diners = intPreferencesKey("diners")
        val staffId = stringPreferencesKey("staff_id")
        val staffName = stringPreferencesKey("staff_name")
        val staffToken = stringPreferencesKey("staff_token")
        val staffZone = stringPreferencesKey("staff_zone")
        val online = booleanPreferencesKey("picker_online")
        val onboardingDone = booleanPreferencesKey("customer_onboarding_done")
        val activeOrderId = stringPreferencesKey("customer_active_order_id")
        val pickerOrderId = stringPreferencesKey("picker_current_order_id")
        val howItWorksShown = booleanPreferencesKey("how_it_works_shown")
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { it[Keys.serverUrl] ?: "" }
    val pnr: Flow<String> = context.dataStore.data.map { it[Keys.pnr] ?: "" }
    val coach: Flow<String> = context.dataStore.data.map { it[Keys.coach] ?: "" }
    val berth: Flow<Int> = context.dataStore.data.map { it[Keys.berth] ?: 0 }
    val diners: Flow<Int> = context.dataStore.data.map { it[Keys.diners] ?: 1 }
    val staffId: Flow<String> = context.dataStore.data.map { it[Keys.staffId] ?: "" }
    val staffName: Flow<String> = context.dataStore.data.map { it[Keys.staffName] ?: "" }
    val staffToken: Flow<String> = context.dataStore.data.map { it[Keys.staffToken] ?: "" }
    val staffZone: Flow<String> = context.dataStore.data.map { it[Keys.staffZone] ?: "" }
    val online: Flow<Boolean> = context.dataStore.data.map { it[Keys.online] ?: false }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboardingDone] ?: false }
    val activeOrderId: Flow<String> = context.dataStore.data.map { it[Keys.activeOrderId] ?: "" }
    val pickerOrderId: Flow<String> = context.dataStore.data.map { it[Keys.pickerOrderId] ?: "" }
    val howItWorksShown: Flow<Boolean> = context.dataStore.data.map { it[Keys.howItWorksShown] ?: false }

    suspend fun setServerUrl(value: String) = context.dataStore.edit { it[Keys.serverUrl] = value.trim() }
    suspend fun setSeat(pnr: String, coach: String, berth: Int, diners: Int) = context.dataStore.edit {
        it[Keys.pnr] = pnr.trim()
        it[Keys.coach] = coach.trim().uppercase()
        it[Keys.berth] = berth
        it[Keys.diners] = diners
    }

    suspend fun setDiners(value: Int) = context.dataStore.edit { it[Keys.diners] = value }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboardingDone] = true }
    suspend fun setStaffLogin(staffId: String, name: String, token: String, zone: String) =
        context.dataStore.edit {
            it[Keys.staffId] = staffId
            it[Keys.staffName] = name
            it[Keys.staffToken] = token
            it[Keys.staffZone] = zone
        }

    suspend fun clearStaff() = context.dataStore.edit {
        it.remove(Keys.staffId)
        it.remove(Keys.staffName)
        it.remove(Keys.staffToken)
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
}
