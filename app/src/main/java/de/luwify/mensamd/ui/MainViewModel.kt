package de.luwify.mensamd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.luwify.mensamd.AppContainer
import de.luwify.mensamd.model.Mensa
import de.luwify.mensamd.model.MensaCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppScreen {
    /** Default Mensa id not read from DataStore yet: render nothing (a few ms), no spinner. */
    data object Loading : AppScreen

    /** No (valid) default Mensa stored: first run. */
    data object Picker : AppScreen

    data class Menu(val mensa: Mensa, val isDefault: Boolean) : AppScreen
}

/** UI state together with the Mensa it belongs to, so a stale menu never shows under a new title. */
data class MenuForMensa(val mensaId: String, val ui: MenuUiState)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val container: AppContainer) : ViewModel() {
    /** Mensa picked in the top bar for this session only; null means "the default". */
    private val viewing = MutableStateFlow<String?>(null)

    val screen: StateFlow<AppScreen> = combine(container.settings.defaultMensaId, viewing) { defaultId, viewingId ->
        val default = defaultId?.let(MensaCatalog::byId)
        when {
            default == null -> AppScreen.Picker
            else -> {
                val current = viewingId?.let(MensaCatalog::byId) ?: default
                AppScreen.Menu(current, isDefault = current.id == default.id)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppScreen.Loading)

    val menu: StateFlow<MenuForMensa?> = screen
        .map { (it as? AppScreen.Menu)?.mensa?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else container.menuRepository.observeMenu(id).map { MenuForMensa(id, it.toUiState()) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pickDefault(id: String) {
        viewing.value = null
        viewModelScope.launch { container.settings.setDefaultMensaId(id) }
    }

    fun switchTo(id: String) {
        viewing.value = id
    }

    fun makeCurrentTheDefault() {
        val current = (screen.value as? AppScreen.Menu)?.mensa ?: return
        pickDefault(current.id)
    }

    /** For the retry button; pull-to-refresh comes in step B. */
    fun refresh() {
        val current = (screen.value as? AppScreen.Menu)?.mensa ?: return
        viewModelScope.launch { container.menuRepository.refresh(current.id) }
    }
}
