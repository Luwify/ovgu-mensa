package de.luwify.mensamd.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

fun settingsRepository(context: Context) = SettingsRepository(context.applicationContext.settingsDataStore)

class SettingsRepository(private val store: DataStore<Preferences>) {
    private val defaultMensaKey = stringPreferencesKey("default_mensa_id")

    /** Null until the user picked one: that is what triggers the first-run picker. */
    val defaultMensaId: Flow<String?> = store.data.map { it[defaultMensaKey] }

    suspend fun setDefaultMensaId(id: String) {
        store.edit { it[defaultMensaKey] = id }
    }

    private val orderKey = stringPreferencesKey("mensa_order")

    /**
     * The user's custom tab order as Mensa ids, empty if never reordered. May contain ids that no
     * longer exist or miss new ones: feed it through `resolveOrder`.
     */
    val mensaOrder: Flow<List<String>> = store.data.map { prefs ->
        prefs[orderKey]?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
    }

    suspend fun setMensaOrder(ids: List<String>) {
        store.edit { it[orderKey] = ids.joinToString(",") }
    }

    private val filtersKey = stringPreferencesKey("dish_filters")

    /** Active filter keys (tag keys); shared by all Mensen. Empty when nothing is active. */
    val activeFilters: Flow<Set<String>> = store.data.map { prefs ->
        prefs[filtersKey]?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
    }

    /** Atomic read-modify-write so two quick taps never lose one. */
    suspend fun toggleFilter(key: String) {
        store.edit { prefs ->
            val current = prefs[filtersKey]?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
            val next = if (key in current) current - key else current + key
            prefs[filtersKey] = next.joinToString(",")
        }
    }
}
