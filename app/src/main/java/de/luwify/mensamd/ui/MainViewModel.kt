package de.luwify.mensamd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.luwify.mensamd.AppContainer
import de.luwify.mensamd.data.settings.ViewPrefs
import de.luwify.mensamd.model.Mensa
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.model.resolveOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val prefs = combine(
        container.settings.activeFilters,
        container.settings.excludedCodes,
        container.settings.priceGroup,
        container.settings.compactMode,
    ) { tags, codes, priceGroup, compact -> ViewPrefs(tags, codes, priceGroup, compact) }

    val screen: StateFlow<AppScreen> = combine(
        container.settings.defaultMensaId,
        container.settings.mensaOrder,
        prefs,
    ) { defaultId, order, prefs ->
        val default = defaultId?.let(MensaCatalog::byId)
        if (default == null) AppScreen.Picker else AppScreen.Main(resolveOrder(order), default.id, prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppScreen.Loading)

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

    fun toggleFilter(key: String) {
        viewModelScope.launch { container.settings.toggleFilter(key) }
    }

    /**
     * Forces a fetch (retry button, pull-to-refresh). Runs in the ViewModel scope so leaving the
     * screen doesn't cancel it; the returned job completes when the attempt is over, success or not.
     */
    fun refresh(id: String): Job = viewModelScope.launch { container.menuRepository.refresh(id) }
}
