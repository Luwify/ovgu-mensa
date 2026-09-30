package de.luwify.mensamd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.luwify.mensamd.AppContainer
import de.luwify.mensamd.model.Mensa
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.model.resolveOrder
import kotlinx.coroutines.Dispatchers
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

    /** [mensas] is in the user's tab order; [defaultId] is independent of that order. */
    data class Main(val mensas: List<Mensa>, val defaultId: String) : AppScreen
}

class MainViewModel(private val container: AppContainer) : ViewModel() {
    val screen: StateFlow<AppScreen> = combine(
        container.settings.defaultMensaId,
        container.settings.mensaOrder,
    ) { defaultId, order ->
        val default = defaultId?.let(MensaCatalog::byId)
        if (default == null) AppScreen.Picker else AppScreen.Main(resolveOrder(order), default.id)
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

    /** For the retry button; pull-to-refresh comes in step B. */
    fun refresh(id: String) {
        viewModelScope.launch { container.menuRepository.refresh(id) }
    }
}
