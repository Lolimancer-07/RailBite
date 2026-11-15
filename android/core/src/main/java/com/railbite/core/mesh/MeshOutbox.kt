package com.railbite.core.mesh

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Offline-relay store shared by both apps (separate DataStore file so the settings
 * store stays untouched):
 *  - customer: orders sent over the Bluetooth mesh that still need to reach the cloud
 *  - picker:   mesh orders received/completed offline, replayed to the cloud on reconnect
 */
private val Context.meshDataStore: DataStore<Preferences> by preferencesDataStore(name = "railbite_mesh")

object MeshStore {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

@Serializable
data class MeshEntry(
    /** Serialized payload — OrderCreateRequest JSON (items carry name/price for alerts). */
    val payload: String,
    val createdAt: String,
    /** Customer: PENDING | SYNCED. Picker: PENDING | ACCEPTED | DELIVERED. */
    val state: String = "PENDING",
    val attempts: Int = 0,
    /** Picker-only: 4-digit delivery code entered at handover (for cloud replay). */
    val deliveryCode: String? = null,
)

open class MeshEntryStore(context: Context, private val key: Preferences.Key<String>) {

    private val ds = context.meshDataStore

    val entries: Flow<List<MeshEntry>> = ds.data.map { decode(it[key]) }

    suspend fun all(): List<MeshEntry> = entries.first()

    suspend fun count(): Int = all().size

    /** Entries still waiting to reach the cloud (anything not SYNCED). */
    suspend fun pendingCount(): Int = all().count { it.state != "SYNCED" }

    suspend fun add(entry: MeshEntry) = write(all() + entry)

    suspend fun updateAll(updated: List<MeshEntry>) = write(updated)

    /** OrderCreateRequest/MeshOrderPayload JSON carries the order UUID in "orderId". */
    suspend fun markState(orderId: String, state: String, deliveryCode: String? = null) {
        val marker = "\"orderId\":\"$orderId\""
        val list = all().map {
            if (it.payload.contains(marker) && it.state != "SYNCED") {
                it.copy(state = state, deliveryCode = deliveryCode ?: it.deliveryCode)
            } else it
        }
        write(list)
    }

    private suspend fun write(list: List<MeshEntry>) = ds.edit {
        it[key] = MeshStore.json.encodeToString(ListSerializer(MeshEntry.serializer()), list)
    }

    private fun decode(raw: String?): List<MeshEntry> = try {
        if (raw.isNullOrBlank()) emptyList()
        else MeshStore.json.decodeFromString(ListSerializer(MeshEntry.serializer()), raw)
    } catch (_: Exception) {
        emptyList()
    }
}

/** Passenger side: orders broadcast over the mesh, waiting for the cloud. */
class MeshOutbox(context: Context) :
    MeshEntryStore(context, stringPreferencesKey("mesh_outbox"))

/** Pantry side: mesh orders received offline (authoritative syncer on reconnect). */
class MeshLedger(context: Context) :
    MeshEntryStore(context, stringPreferencesKey("mesh_ledger"))
