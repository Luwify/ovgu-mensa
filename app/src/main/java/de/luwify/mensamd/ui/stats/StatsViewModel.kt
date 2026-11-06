package de.luwify.mensamd.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.luwify.mensamd.AppContainer
import de.luwify.mensamd.data.settings.CardStatsSettings
import de.luwify.mensamd.favorites.FavoriteInfo
import de.luwify.mensamd.stats.CardEvent
import de.luwify.mensamd.stats.EatenDish
import de.luwify.mensamd.stats.EventKind
import de.luwify.mensamd.stats.MealContext
import de.luwify.mensamd.stats.MealRecord
import de.luwify.mensamd.stats.StatsData
import de.luwify.mensamd.stats.StatsOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/** The card statistics screens (card settings, overview, questions). Everything goes through the store's lock. */
class StatsViewModel(private val container: AppContainer) : ViewModel() {
    private val store = container.statsStore
    private val recorder = container.statsRecorder
    private val settingsRepo = container.settings

    val settings: StateFlow<CardStatsSettings?> = settingsRepo.cardStats.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val data: StateFlow<StatsData?> = store.data
    val problem = store.problem
    val lastRecord = recorder.last
    val registering = recorder.registering
    val favorites: StateFlow<Set<String>> = settingsRepo.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Default Mensa (listed first when matching) and price group, for the meal questions. */
    val mealContext: StateFlow<MealContext?> = combine(settingsRepo.defaultMensaId, settingsRepo.priceGroup) { id, group ->
        MealContext(id, group)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { store.load() }
    }

    private fun edit(block: suspend () -> Unit) {
        // The app scope: an answer is saved even if the screen closes right after the tap.
        container.appScope.launch { block() }
    }

    fun setEnabled(on: Boolean) = edit {
        settingsRepo.setStatsEnabled(on)
        if (!on) recorder.setRegistering(false)
    }

    fun setDetailed(on: Boolean) = edit { settingsRepo.setStatsDetailed(on) }
    fun setReminder(on: Boolean) = edit { settingsRepo.setCardReminder(on) }
    fun setReminderTime(time: LocalTime) = edit { settingsRepo.setCardReminderTime(time) }
    fun setTopUps(amounts: List<Int>) = edit { settingsRepo.setTopUpAmounts(amounts) }
    fun setPfand(amounts: List<Int>) = edit { settingsRepo.setPfandAmounts(amounts) }

    fun setRegistering(on: Boolean) = recorder.setRegistering(on)

    /** Registers the card of a scan that was just shown ([seq]) and stores that scan as its first one. */
    fun registerScanned(fingerprint: String, balance: Int, lastDebit: Int?, seq: Long) = edit {
        recorder.register(fingerprint, balance, lastDebit, seq)
    }

    fun unregister() = edit { store.update(StatsOps::unregister) }

    fun clearHistory() = edit { store.update(StatsOps::clearHistory) }

    fun resolveSeveral(itemId: Long, parts: List<Int>) = edit {
        var payments = emptyList<CardEvent>()
        store.update { d -> StatsOps.resolveSeveral(d, itemId, parts).also { payments = it.second }.first }
        recorder.matchPayments(payments)
    }

    fun resolveCredit(itemId: Long, credit: Int?, kind: EventKind) = edit {
        var payments = emptyList<CardEvent>()
        store.update { d -> StatsOps.resolveCredit(d, itemId, credit, kind).also { payments = it.second }.first }
        recorder.matchPayments(payments)
    }

    fun dismiss(itemId: Long) = edit { store.update { StatsOps.dismiss(it, itemId) } }

    private val _focusQuestion = MutableStateFlow<Long?>(null)

    /** A question the statistics screen should open right away (after "Anders zuordnen"). */
    val focusQuestion: StateFlow<Long?> = _focusQuestion.asStateFlow()

    fun consumeFocusQuestion() {
        _focusQuestion.value = null
    }

    /** "Anders zuordnen" on a Pfand return: it becomes a "top-up + payment?" question, which then opens. */
    fun reassignCredit(eventId: Long) = edit {
        var id: Long? = null
        store.update { d -> StatsOps.reassignCredit(d, eventId).also { id = it.second }.first }
        _focusQuestion.value = id
    }

    fun setMeal(eventId: Long, meal: MealRecord?) = edit { store.update { StatsOps.setMeal(it, eventId, meal) } }

    fun markDateUnknown(eventId: Long) = edit { store.update { StatsOps.markDateUnknown(it, eventId) } }

    /** "Als neues Gericht speichern": the dish is kept for later searches and set as the payment's meal. */
    fun saveNewDish(eventId: Long, name: String, price: Int) = edit {
        store.update { d ->
            val (next, eaten) = StatsOps.addCustomDish(d, name, price)
            StatsOps.setMeal(next, eventId, MealRecord(dishes = listOf(eaten)))
        }
    }

    fun deleteEvent(event: CardEvent) = edit { store.update { StatsOps.deleteEvent(it, event.id) } }
    fun restoreEvent(event: CardEvent) = edit { store.update { StatsOps.restoreEvent(it, event) } }

    fun addCustomExtra(name: String) = edit { store.update { StatsOps.addCustomExtra(it, name) } }
    fun removeCustomExtra(name: String) = edit { store.update { StatsOps.removeCustomExtra(it, name) } }

    /** The heart in the meals overview: same favorite key and info as on a menu card. */
    fun toggleFavorite(dish: EatenDish) = edit {
        settingsRepo.toggleFavorite(dish.key, FavoriteInfo(dish.name, dish.tags))
    }
}
