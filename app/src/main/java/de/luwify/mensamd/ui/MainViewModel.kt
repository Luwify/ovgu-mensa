package de.luwify.mensamd.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.luwify.mensamd.AppContainer
import de.luwify.mensamd.data.settings.CustomThemeSpec
import de.luwify.mensamd.data.settings.ThemeChoice
import de.luwify.mensamd.data.settings.ThemeMode
import de.luwify.mensamd.data.settings.ViewPrefs
import de.luwify.mensamd.model.Mensa
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.model.resolveOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import de.luwify.mensamd.favorites.FavoriteNotifications
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

sealed interface AppScreen {
    /** Settings not read from DataStore yet: render nothing (a few ms), no spinner. */
    data object Loading : AppScreen

    /** No (valid) default Mensa stored: first run. */
    data object Picker : AppScreen

    /**
     * [mensas] is in the user's tab order; [defaultId] is independent of that order; [prefs] are
     * the filters and price group shared by all Mensen. They ride along here so the first frame is
     * already filtered, not unfiltered for a moment.
     */
    data class Main(val mensas: List<Mensa>, val defaultId: String, val prefs: ViewPrefs) : AppScreen
}

/** The "Benachrichtigungen" part of the filter sheet. */
data class NotifySettings(val enabled: Boolean, val watched: Set<String>, val time: LocalTime)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val prefs = combine(
        container.settings.activeFilters,
        container.settings.excludedCodes,
        container.settings.priceGroup,
        container.settings.compactMode,
        container.settings.favorites,
    ) { tags, codes, priceGroup, compact, favorites -> ViewPrefs(tags, codes, priceGroup, compact, favorites) }
        .combine(container.settings.filterOrder) { prefs, order -> prefs.copy(filterOrder = order) }
        .combine(container.settings.legendaryFavorites) { prefs, legendary -> prefs.copy(legendary = legendary) }

    val screen: StateFlow<AppScreen> = combine(
        container.settings.defaultMensaId,
        container.settings.mensaOrder,
        prefs,
    ) { defaultId, order, prefs ->
        val default = defaultId?.let(MensaCatalog::byId)
        if (default == null) AppScreen.Picker else AppScreen.Main(resolveOrder(order), default.id, prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppScreen.Loading)

    /** The custom theme while the user is still dragging the hue slider: shown live, saved on release. */
    private val customDraft = MutableStateFlow<CustomThemeSpec?>(null)

    /** Null until DataStore answered: the activity draws nothing until then, so there is no theme flash. */
    val theme: StateFlow<ThemeChoice?> = combine(
        container.settings.themeMode,
        container.settings.customTheme,
        customDraft,
    ) { mode, custom, draft -> ThemeChoice(mode, draft ?: custom) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val notifySettings: StateFlow<NotifySettings?> = combine(
        container.settings.notificationsEnabled,
        container.settings.notifyMensen,
        container.settings.notifyTime,
    ) { enabled, watched, time -> NotifySettings(enabled, watched, time) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether the permission explanation was shown already; null until known (then nothing is asked). */
    val permissionExplained: StateFlow<Boolean?> = container.settings.notificationPermissionExplained
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val permissionRequested: StateFlow<Boolean?> = container.settings.notificationPermissionRequested
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _openRequest = MutableStateFlow<String?>(null)

    /** A Mensa tab to switch to (from a notification tap); consumed by the main screen. */
    val openRequest: StateFlow<String?> = _openRequest.asStateFlow()

    init {
        // Off the main thread: the first WorkManager call initializes it.
        viewModelScope.launch(Dispatchers.Default) {
            var lastTime: LocalTime? = null
            combine(container.settings.notificationsEnabled, container.settings.favorites, container.settings.notifyTime) { on, favorites, time ->
                (on && favorites.isNotEmpty()) to time
            }
                .distinctUntilChanged()
                .collect { (active, time) ->
                    // App start keeps a pending job; a new time (picked while the app runs) replaces it.
                    container.syncFavoriteSchedule(active, time, timeChanged = lastTime != null && lastTime != time)
                    lastTime = time
                }
        }
    }

    private val menus = ConcurrentHashMap<String, StateFlow<MenuUiState?>>()

    /**
     * The menu of one Mensa. Upstream (cache read + background refresh) only runs while a page
     * collects it, i.e. for the visible tab and the neighbors the pager keeps composed; it stops
     * 5 s after the last collector leaves, and the last value stays for instant re-display.
     */
    fun menuFor(id: String): StateFlow<MenuUiState?> = menus.getOrPut(id) {
        container.menuRepository.observeMenu(id)
            .map { it.toUiState() }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    fun pickDefault(id: String) {
        viewModelScope.launch { container.settings.setDefaultMensaId(id) }
    }

    fun setOrder(ids: List<String>) {
        viewModelScope.launch { container.settings.setMensaOrder(ids) }
    }

    /** Only ever called from the sheet's theme chips; logged so an unexpected change leaves a trace. */
    fun setThemeMode(mode: ThemeMode) {
        Log.i("MensaTheme", "user selected theme $mode")
        viewModelScope.launch { container.settings.setThemeMode(mode) }
    }

    /**
     * Changes the custom theme. [commit] false (slider moving) only updates the live preview; true saves it. The
     * draft is dropped only after the save, so the theme never jumps back for a frame.
     */
    fun setCustomTheme(spec: CustomThemeSpec, commit: Boolean = true) {
        customDraft.value = spec
        if (commit) {
            viewModelScope.launch {
                container.settings.setCustomTheme(spec)
                if (customDraft.value == spec) customDraft.value = null
            }
        }
    }

    fun setLegendaryFavorites(on: Boolean) {
        viewModelScope.launch { container.settings.setLegendaryFavorites(on) }
    }

    fun setCompactMode(compact: Boolean) {
        viewModelScope.launch { container.settings.setCompactMode(compact) }
    }

    fun setPriceGroup(index: Int) {
        viewModelScope.launch { container.settings.setPriceGroup(index) }
    }

    fun setCodesExcluded(codes: Set<String>, excluded: Boolean) {
        viewModelScope.launch { container.settings.setCodesExcluded(codes, excluded) }
    }

    fun clearExcludedCodes() {
        viewModelScope.launch { container.settings.clearExcludedCodes() }
    }

    fun toggleFavorite(key: String) {
        viewModelScope.launch { container.settings.toggleFavorite(key) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settings.setNotificationsEnabled(enabled) }
    }

    fun setNotifyMensa(id: String, watched: Boolean) {
        viewModelScope.launch { container.settings.setNotifyMensa(id, watched) }
    }

    fun markPermissionExplained() {
        viewModelScope.launch { container.settings.setNotificationPermissionExplained() }
    }

    fun markPermissionRequested() {
        viewModelScope.launch { container.settings.setNotificationPermissionRequested() }
    }

    fun canPostNotifications(): Boolean = container.canPostNotifications()

    fun postTestNotification(mensaId: String) {
        val today = FavoriteNotifications.announcesToday(notifySettings.value?.time ?: FavoriteNotifications.DEFAULT_RUN_AT)
        container.postTestNotification(mensaId, today)
    }

    fun setNotifyTime(time: LocalTime) {
        viewModelScope.launch { container.settings.setNotifyTime(time) }
    }

    fun requestOpen(mensaId: String) {
        _openRequest.value = mensaId
    }

    fun consumeOpenRequest() {
        _openRequest.value = null
    }

    /** The chips of one page after a drag; merged into the stored order (chips of other pages keep their place). */
    fun reorderFilters(visibleKeys: List<String>) {
        viewModelScope.launch { container.settings.reorderFilters(visibleKeys, DishFilters::mergeOrder) }
    }

    fun toggleFilter(key: String) {
        viewModelScope.launch { container.settings.toggleFilter(key) }
    }

    /**
     * Forces a fetch (retry button, pull-to-refresh). Runs in the ViewModel scope so leaving the
     * screen doesn't cancel it; the returned job completes when the attempt is over, success or not.
     */
    fun refresh(id: String): Job = viewModelScope.launch { container.menuRepository.refresh(id) }
}
