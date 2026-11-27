package com.railbite.core.mesh

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.railbite.core.util.Statuses
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.meshDataStore: DataStore<Preferences> by preferencesDataStore(name = "railbite_mesh")

object MeshStore {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

@Serializable
data class MeshEntry(
    val payload: String,
    val createdAt: String,
    /** Customer: PENDING | SYNCED | CANCELLED. Picker: PENDING | ACCEPTED | PACKING | OUT_FOR_DELIVERY | DELIVERED | REJECTED | SYNCED. */
    val state: String = "PENDING",
    val attempts: Int = 0,
    val deliveryCode: String? = null,
    /** Customer timeline ack: BROADCASTING -> ACCEPTED -> PACKING -> OUT_FOR_DELIVERY -> DELIVERED. */
    val ack: String? = null,
    /** Offline handover cash collected in paise (Picker ledger & sync). */
    val cashPaise: Int? = null,
    val captainId: String? = null,
    val etaMin: Int? = null,
    val hopCount: Int = 0,
    val lastRssi: Int? = null,
    val passengerConfirmed: Boolean = false,
)

open class MeshEntryStore(context: Context, private val key: Preferences.Key<String>) {

    private val ds = context.meshDataStore
    private val mutex = Mutex()

    val entries: Flow<List<MeshEntry>> = ds.data.map { decode(it[key]) }

    suspend fun all(): List<MeshEntry> = entries.first()

    suspend fun count(): Int = all().size

    /** Entries waiting to sync to edge-server (excludes CANCELLED / REJECTED / SYNCED). */
    suspend fun pendingCount(): Int = all().count {
        it.state != "SYNCED" && it.state != "REJECTED" && it.state != "CANCELLED"
    }

    suspend fun add(entry: MeshEntry) = mutex.withLock {
        write(all() + entry)
    }

    suspend fun addIfAbsent(orderId: String, entry: MeshEntry): Boolean = mutex.withLock {
        val current = all()
        val marker = "\"orderId\":\"$orderId\""
        if (current.any { it.payload.contains(marker) }) return@withLock false
        write(current + entry)
        true
    }

    suspend fun updateAll(updated: List<MeshEntry>) = mutex.withLock {
        write(updated)
    }

    suspend fun markState(
        orderId: String,
        state: String,
        deliveryCode: String? = null,
        cashPaise: Int? = null,
        captainId: String? = null,
    ) = mutex.withLock {
        val marker = "\"orderId\":\"$orderId\""
        val list = all().map {
            if (it.payload.contains(marker) && it.state != "SYNCED") {
                it.copy(
                    state = state,
                    deliveryCode = deliveryCode ?: it.deliveryCode,
                    cashPaise = cashPaise ?: it.cashPaise,
                    captainId = captainId ?: it.captainId,
                )
            } else {
                it
            }
        }
        write(list)
    }

    suspend fun entryOf(orderId: String): MeshEntry? =
        all().firstOrNull { it.payload.contains("\"orderId\":\"$orderId\"") }

    private val ackRank = mapOf(
        Statuses.BROADCASTING to 0,
        "REJECTED" to 0,
        Statuses.ACCEPTED to 1,
        Statuses.PACKING to 2,
        Statuses.OUT_FOR_DELIVERY to 3,
        Statuses.DELIVERED to 4,
    )

    suspend fun markAck(
        orderId: String,
        ack: String,
        captainId: String? = null,
        etaMin: Int? = null,
    ) = mutex.withLock {
        val newRank = ackRank[ack] ?: return@withLock
        val marker = "\"orderId\":\"$orderId\""
        write(
            all().map {
                if (!it.payload.contains(marker)) {
                    it
                } else {
                    val old = it.ack?.let { prev -> ackRank[prev] } ?: -1
                    if (newRank >= old) {
                        it.copy(
                            ack = ack,
                            captainId = captainId ?: it.captainId,
                            etaMin = etaMin ?: it.etaMin,
                        )
                    } else {
                        it
                    }
                }
            },
        )
    }

    suspend fun markPassengerConfirmed(orderId: String) = mutex.withLock {
        val marker = "\"orderId\":\"$orderId\""
        write(
            all().map {
                if (it.payload.contains(marker)) it.copy(passengerConfirmed = true) else it
            },
        )
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

/** Passenger side: offline BLE orders queued for LAN sync & live timeline state. */
class MeshOutbox(context: Context) :
    MeshEntryStore(context, stringPreferencesKey("mesh_outbox"))

/** Picker side: offline BLE orders received/fulfilled offline & queued for LAN sync. */
class MeshLedger(context: Context) :
    MeshEntryStore(context, stringPreferencesKey("mesh_ledger"))
