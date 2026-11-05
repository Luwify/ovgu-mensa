package de.luwify.mensamd.ui

import android.net.Uri
import android.util.Log
import de.luwify.mensamd.backup.Backup
import de.luwify.mensamd.backup.BackupFormat
import de.luwify.mensamd.backup.BackupResult
import de.luwify.mensamd.backup.SettingValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import de.luwify.mensamd.favorites.FavoriteEntry
import de.luwify.mensamd.favorites.FavoriteInfo
import de.luwify.mensamd.favorites.FavoriteNotifications
import de.luwify.mensamd.favorites.FavoriteOverview
import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.Dish
import kotlinx.coroutines.flow.Flow
import java.text.Collator
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
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
/** Open this Mensa, and scroll to [date] when given. */
data class OpenRequest(val mensaId: String, val date: LocalDate? = null)

/** The outcome of a backup action, shown as a snackbar on the settings page. */
sealed interface BackupMessage {
    data object Exported : BackupMessage
    data object Failed : BackupMessage
    data object NotABackup : BackupMessage
    data object TooNew : BackupMessage

    /** The statistics file is from a newer app version: nothing was replaced. */
    data object StatsReadOnly : BackupMessage

    /** Restored; [previous] is what was replaced (the undo). */
    data class Restored(val previous: Backup) : BackupMessage
    data object Undone : BackupMessage
}

/** What a backup holds, for the confirmation dialog. */
data class BackupSummary(val favorites: Int, val scans: Int, val events: Int, val cardRegistered: Boolean, val exportedAt: String?) {
    companion object {
        fun of(b: Backup) = BackupSummary(
            (b.settings["favorites"] as? SettingValue.Texts)?.value?.size ?: 0,
            b.stats.scans.size, b.stats.events.size, b.stats.card != null, b.exportedAt,
        )
    }
}

/** A backup file read and waiting for the user's confirmation; [now] summarizes what it would replace. */
data class PendingBackup(val backup: Backup, val incoming: BackupSummary, val now: BackupSummary)

data class NotifySettings(val enabled: Boolean, val watched: Set<String>, val time: LocalTime)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    /** The in-app updater (process-wide, see [AppContainer.updates]). */
    val updates = container.updates

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
        container.settings.cardGlow,
    ) { mode, custom, draft, glow -> ThemeChoice(mode, draft ?: custom, glow ?: (mode == ThemeMode.AMOLED)) }
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

    private val _openRequest = MutableStateFlow<OpenRequest?>(null)

    /** A Mensa tab (and maybe a day) to switch to, from a notification or the favorites overview; consumed by the main screen. */
    val openRequest: StateFlow<OpenRequest?> = _openRequest.asStateFlow()

    private val _dayRequest = MutableStateFlow<OpenRequest?>(null)

    /** A day to scroll to once that Mensa's list is shown (set by the main screen from [openRequest]). */
    val dayRequest: StateFlow<OpenRequest?> = _dayRequest.asStateFlow()

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
        // The card reminder runs only while the statistics and the reminder are on.
        viewModelScope.launch(Dispatchers.Default) {
            var lastTime: LocalTime? = null
            container.settings.cardStats.map { it.reminderActive to it.reminderTime }
                .distinctUntilChanged()
                .collect { (active, time) ->
                    container.syncCardReminder(active, time, timeChanged = lastTime != null && lastTime != time)
                    lastTime = time
                }
        }
    }

    private val _openCard = MutableStateFlow(false)

    /** The card reminder was tapped: the card screen opens (consumed by `MensaRoot`). */
    val openCard: StateFlow<Boolean> = _openCard.asStateFlow()

    fun requestOpenCard() {
        _openCard.value = true
    }

    fun consumeOpenCard() {
        _openCard.value = false
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

    fun setCardGlow(on: Boolean) {
        viewModelScope.launch { container.settings.setCardGlow(on) }
    }

    /** The user's banner choice (null = never chosen); the settings combine it with "installed". */
    val stundenplanBannerHidden: StateFlow<Boolean?> =
        container.settings.stundenplanBannerHidden.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val companion get() = container.companion

    fun setStundenplanBannerHidden(hidden: Boolean?) {
        viewModelScope.launch { container.settings.setStundenplanBannerHidden(hidden) }
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

    /** The heart on a card: also stores the dish's name and tags for the favorites overview. */
    fun toggleFavorite(dish: Dish) {
        viewModelScope.launch { container.settings.toggleFavorite(Favorites.key(dish), FavoriteInfo.of(dish)) }
    }

    /** Removal from the favorites overview; [restoreFavorite] is its undo. */
    fun removeFavorite(entry: FavoriteEntry) {
        viewModelScope.launch {
            if (entry.key in container.settings.favorites.first()) container.settings.toggleFavorite(entry.key)
        }
    }

    fun restoreFavorite(entry: FavoriteEntry) {
        viewModelScope.launch { container.settings.addFavorite(entry.key, FavoriteInfo(entry.name, entry.tags)) }
    }

    /**
     * The favorites overview over the cached menus of [mensaIds] (tab order), names sorted for [language]. Never
     * fetches. Name/tag infos found on a menu are stored on the way (see [FavoriteOverview.infoUpdates]).
     */
    fun favoriteOverview(mensaIds: List<String>, today: LocalDate, language: String): Flow<List<FavoriteEntry>> {
        val names = Collator.getInstance(Locale.forLanguageTag(language))
        return combine(
            container.menuRepository.observeCached(mensaIds),
            container.settings.favorites,
            container.settings.favoriteInfo,
        ) { menus, favorites, saved ->
            val entries = FavoriteOverview.entries(favorites, menus.map { it.first to it.second.page }, today, saved, language, names)
            val updates = FavoriteOverview.infoUpdates(entries, saved)
            if (updates.isNotEmpty()) viewModelScope.launch { container.settings.saveFavoriteInfo(updates) }
            entries
        }.flowOn(Dispatchers.Default)
    }

    private val _backupMessages = Channel<BackupMessage>(Channel.BUFFERED)
    val backupMessages: Flow<BackupMessage> = _backupMessages.receiveAsFlow()

    private val _pendingBackup = MutableStateFlow<PendingBackup?>(null)
    val pendingBackup: StateFlow<PendingBackup?> = _pendingBackup.asStateFlow()

    /** Writes everything (settings incl. favorites, statistics, language) as one backup file to [uri]. */
    fun exportBackup(uri: Uri) {
        container.appScope.launch(Dispatchers.IO) {
            val ok = runCatching { container.writeDocument(uri, BackupFormat.encode(container.backups.current())) }.getOrDefault(false)
            _backupMessages.send(if (ok) BackupMessage.Exported else BackupMessage.Failed)
        }
    }

    /** Reads a backup file; nothing is replaced before the user confirms. */
    fun readBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val r = container.readDocument(uri)?.let(BackupFormat::decode)) {
                is BackupResult.Ok -> _pendingBackup.value = PendingBackup(r.backup, BackupSummary.of(r.backup), BackupSummary.of(container.backups.current()))
                BackupResult.NotABackup -> _backupMessages.send(BackupMessage.NotABackup)
                BackupResult.TooNew -> _backupMessages.send(BackupMessage.TooNew)
                null, is BackupResult.Broken -> _backupMessages.send(BackupMessage.Failed)
            }
        }
    }

    fun cancelBackup() {
        _pendingBackup.value = null
    }

    fun confirmBackup() {
        val pending = _pendingBackup.value ?: return
        _pendingBackup.value = null
        container.appScope.launch(Dispatchers.IO) {
            val before = container.backups.current()
            val message = try {
                container.backups.restore(pending.backup)
                BackupMessage.Restored(before)
            } catch (e: IllegalStateException) {
                BackupMessage.StatsReadOnly
            } catch (e: Exception) {
                BackupMessage.Failed
            }
            _backupMessages.send(message)
        }
    }

    fun undoBackup(previous: Backup) {
        container.appScope.launch(Dispatchers.IO) {
            val ok = runCatching { container.backups.restore(previous) }.isSuccess
            _backupMessages.send(if (ok) BackupMessage.Undone else BackupMessage.Failed)
        }
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

    fun requestOpen(mensaId: String, date: LocalDate? = null) {
        _openRequest.value = OpenRequest(mensaId, date)
    }

    /** The pager is on the requested Mensa; a requested day is handed on to that Mensa's list. */
    fun consumeOpenRequest() {
        _dayRequest.value = _openRequest.value?.takeIf { it.date != null }
        _openRequest.value = null
    }

    fun consumeDayRequest() {
        _dayRequest.value = null
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
