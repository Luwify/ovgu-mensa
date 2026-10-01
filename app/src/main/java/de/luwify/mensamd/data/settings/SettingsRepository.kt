package de.luwify.mensamd.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import de.luwify.mensamd.favorites.FavoriteNotifications
import java.time.LocalDate
import java.time.LocalTime

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

    private val filterOrderKey = stringPreferencesKey("filter_order")

    /** The user's filter chip order (keys); empty until they first drag a chip. Keys never contain commas. */
    val filterOrder: Flow<List<String>> = store.data.map { prefs ->
        prefs[filterOrderKey]?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
    }

    /** Applies a drag on one page ([visibleNew]) to the stored order, atomically (see `DishFilters.mergeOrder`). */
    suspend fun reorderFilters(visibleNew: List<String>, merge: (List<String>, List<String>) -> List<String>) {
        store.edit { prefs ->
            val stored = prefs[filterOrderKey]?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
            prefs[filterOrderKey] = merge(stored, visibleNew).joinToString(",")
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

    private val customBaseKey = stringPreferencesKey("custom_theme_base")
    private val customHueKey = intPreferencesKey("custom_theme_hue")
    private val customGlowKey = booleanPreferencesKey("custom_theme_glow")

    /** The user's own theme (used when the theme mode is CUSTOM). */
    val customTheme: Flow<CustomThemeSpec> = store.data.map { prefs ->
        CustomThemeSpec(
            base = CustomBase.fromKey(prefs[customBaseKey]),
            hue = (prefs[customHueKey] ?: CustomThemeSpec.DEFAULT_HUE).mod(360),
            glow = prefs[customGlowKey] ?: false,
        )
    }

    suspend fun setCustomTheme(spec: CustomThemeSpec) {
        store.edit {
            it[customBaseKey] = spec.base.key
            it[customHueKey] = spec.hue.mod(360)
            it[customGlowKey] = spec.glow
        }
    }

    private val favoritesKey = stringSetPreferencesKey("favorites")

    /**
     * Favorite dishes as normalized German names (see `Favorites`); global for all Mensen. A string set, not a
     * joined string: names contain commas. Stays on the device and is never logged.
     */
    val favorites: Flow<Set<String>> = store.data.map { it[favoritesKey].orEmpty() }

    /** Atomic toggle; returns true if [key] is a favorite afterwards. */
    suspend fun toggleFavorite(key: String): Boolean {
        var added = false
        store.edit { prefs ->
            val current = prefs[favoritesKey].orEmpty()
            added = key !in current
            prefs[favoritesKey] = if (added) current + key else current - key
        }
        return added
    }

    private val notifyEnabledKey = booleanPreferencesKey("notify_enabled")

    /** Daily favorites notifications; on by default (they need favorites and the system permission anyway). */
    val notificationsEnabled: Flow<Boolean> = store.data.map { it[notifyEnabledKey] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.edit { it[notifyEnabledKey] = enabled }
    }

    private val notifyMinuteKey = intPreferencesKey("notify_minute_of_day")

    /** When the favorites notification comes (default 18:00). Before noon it announces the same day. */
    val notifyTime: Flow<LocalTime> = store.data.map { prefs ->
        prefs[notifyMinuteKey]?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime.of(it / 60, it % 60) }
            ?: FavoriteNotifications.DEFAULT_RUN_AT
    }

    suspend fun setNotifyTime(time: LocalTime) {
        store.edit { it[notifyMinuteKey] = time.hour * 60 + time.minute }
    }

    private val notifyMensenKey = stringSetPreferencesKey("notify_mensen")

    /** Mensa ids the daily job checks. Until the user changes it: only the default Mensa. */
    val notifyMensen: Flow<Set<String>> = store.data.map { prefs ->
        prefs[notifyMensenKey] ?: setOfNotNull(prefs[defaultMensaKey])
    }

    suspend fun setNotifyMensa(id: String, watched: Boolean) {
        store.edit { prefs ->
            val current = prefs[notifyMensenKey] ?: setOfNotNull(prefs[defaultMensaKey])
            prefs[notifyMensenKey] = if (watched) current + id else current - id
        }
    }

    private val notifiedKey = stringSetPreferencesKey("notified_favorites")

    /** "date|mensaId|favorite" entries already announced, so nothing is announced twice. */
    suspend fun notifiedFavorites(): Set<String> = store.data.first()[notifiedKey].orEmpty()

    /** Adds [entries] and drops what [prune] rejects (entries for past days), in one atomic edit. */
    suspend fun recordNotified(entries: Set<String>, today: LocalDate, prune: (Set<String>, LocalDate) -> Set<String>) {
        store.edit { prefs -> prefs[notifiedKey] = prune(prefs[notifiedKey].orEmpty() + entries, today) }
    }

    private val permissionExplainedKey = booleanPreferencesKey("notify_permission_explained")

    /** Whether the notification permission was already explained and asked for (asked once, on the first favorite). */
    val notificationPermissionExplained: Flow<Boolean> = store.data.map { it[permissionExplainedKey] ?: false }

    suspend fun setNotificationPermissionExplained() {
        store.edit { it[permissionExplainedKey] = true }
    }

    private val permissionRequestedKey = booleanPreferencesKey("notify_permission_requested")

    /** Whether the system permission dialog was launched before: after that, a denial may be permanent. */
    val notificationPermissionRequested: Flow<Boolean> = store.data.map { it[permissionRequestedKey] ?: false }

    suspend fun setNotificationPermissionRequested() {
        store.edit { it[permissionRequestedKey] = true }
    }
}
