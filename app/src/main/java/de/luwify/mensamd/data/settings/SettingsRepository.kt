package de.luwify.mensamd.data.settings

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

    private val excludedCodesKey = stringPreferencesKey("excluded_codes")

    /** Additive/allergen codes whose dishes are hidden; shared by all Mensen. */
    val excludedCodes: Flow<Set<String>> = store.data.map { prefs ->
        prefs[excludedCodesKey]?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
    }

    /** Selects or deselects several codes at once (a family like a1-a6), atomically. */
    suspend fun setCodesExcluded(codes: Set<String>, excluded: Boolean) {
        store.edit { prefs ->
            val current = prefs[excludedCodesKey]?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
            prefs[excludedCodesKey] = (if (excluded) current + codes else current - codes).joinToString(",")
        }
    }

    suspend fun clearExcludedCodes() {
        store.edit { it.remove(excludedCodesKey) }
    }

    private val priceGroupKey = intPreferencesKey("price_group")

    /** Index into the legend's price columns (0 = students, the default). */
    val priceGroup: Flow<Int> = store.data.map { (it[priceGroupKey] ?: 0).coerceAtLeast(0) }

    suspend fun setPriceGroup(index: Int) {
        store.edit { it[priceGroupKey] = index.coerceAtLeast(0) }
    }

    private val compactKey = booleanPreferencesKey("compact_mode")

    /** Compact dish cards instead of the normal ones; applies to all Mensen. */
    val compactMode: Flow<Boolean> = store.data.map { it[compactKey] ?: false }

    suspend fun setCompactMode(compact: Boolean) {
        store.edit { it[compactKey] = compact }
    }

    private val themeKey = stringPreferencesKey("theme_mode")

    /** System colors (default) or the own Soft palette. */
    val themeMode: Flow<ThemeMode> = store.data.map { ThemeMode.fromKey(it[themeKey]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.key }
    }
}
