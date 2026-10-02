package nl.nulldevelopment.nfcappblocker.hceemulator.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Persists the tag presets and which one is active. */
class PresetStore(private val dataStore: DataStore<Preferences>) {

    /** Emits null until [ensureDefaults] has written a UUID for every preset. */
    val settings: Flow<EmulatorSettings?> = dataStore.data.map settings@{ prefs ->
        val presets = PRESETS.map { (id, label) ->
            val uuid = prefs[uuidKey(id)]?.let(UUID::fromString) ?: return@settings null
            TagPreset(id, label, uuid)
        }
        val active = prefs[ACTIVE_PRESET]?.takeIf { id -> presets.any { it.id == id } } ?: PRESETS.first().first
        EmulatorSettings(presets, active)
    }

    /** Gives each preset a random UUID the first time the app runs. */
    suspend fun ensureDefaults() {
        dataStore.edit { prefs ->
            PRESETS.forEach { (id, _) ->
                if (prefs[uuidKey(id)] == null) prefs[uuidKey(id)] = UUID.randomUUID().toString()
            }
        }
    }

    suspend fun setActivePreset(id: String) {
        dataStore.edit { it[ACTIVE_PRESET] = id }
    }

    suspend fun setUuid(id: String, uuid: UUID) {
        dataStore.edit { it[uuidKey(id)] = uuid.toString() }
    }

    private fun uuidKey(presetId: String) = stringPreferencesKey("preset_${presetId}_uuid")

    private companion object {
        val PRESETS = listOf("a" to "Tag A", "b" to "Tag B", "c" to "Tag C")
        val ACTIVE_PRESET = stringPreferencesKey("active_preset")
    }
}
