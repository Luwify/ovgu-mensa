package de.luwify.mensamd.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.luwify.mensamd.AppContainer
import de.luwify.mensamd.data.settings.CardStatsSettings
import de.luwify.mensamd.favorites.FavoriteInfo
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.stats.CardEvent
import de.luwify.mensamd.stats.EatenDish
import de.luwify.mensamd.stats.Movement
import de.luwify.mensamd.stats.MealContext
import de.luwify.mensamd.stats.MealDraft
import de.luwify.mensamd.stats.MealItem
import de.luwify.mensamd.stats.MealRecord
import de.luwify.mensamd.stats.OpenItem
import de.luwify.mensamd.stats.StatsData
import de.luwify.mensamd.stats.StatsOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** The card statistics screens (the settings' card tab, overview, questions). Everything goes through the store's lock. */
class StatsViewModel(private val container: AppContainer) : ViewModel() {
    private val store = container.statsStore
    private val recorder = container.statsRecorder
    private val settingsRepo = container.settings

    val settings: StateFlow<CardStatsSettings?> = settingsRepo.cardStats.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val data: StateFlow<StatsData?> = store.data
    val problem = store.problem
    val lastRecord = recorder.last
    val registering = recorder.registering

    /** Days whose missing menus are loading in the background (meal questions show "lädt weitere Mensen …"). */
    val loadingDays = recorder.loading

    /** The meal question on screen (null: none); a background load won't answer it while it is shown. */
    fun setViewing(questionId: Long?) {
        recorder.viewing.value = questionId
    }
    val favorites: StateFlow<Set<String>> = settingsRepo.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Default Mensa (listed first when matching) and price group, for the meal questions. */
    val mealContext: StateFlow<MealContext?> = combine(settingsRepo.defaultMensaId, settingsRepo.priceGroup) { id, group ->
        MealContext(id, group)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { store.load() }
    }

    /** What the app has of every Mensa's menu, for the meal questions and corrections; never fetches. */
    suspend fun cachedPages(): List<Pair<String, MenuPage>> = container.cachedPages()

    private fun edit(block: suspend () -> Unit) {
        // The app scope: an answer is saved even if the screen closes right after the tap.
        container.appScope.launch { block() }
    }

    fun setEnabled(on: Boolean) = edit {
        settingsRepo.setStatsEnabled(on)
        if (!on) recorder.setRegistering(false)
    }

    fun setDetailed(on: Boolean) = edit { settingsRepo.setStatsDetailed(on) }
    fun setHighContrast(on: Boolean) = edit { settingsRepo.setStatsHighContrast(on) }
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

    /** "Was ist passiert?": the movements are stored; with detailed statistics the first new payment's meal question opens. */
    fun resolveMovements(itemId: Long, movements: List<Movement>) = edit {
        var payments = emptyList<CardEvent>()
        store.update { d -> StatsOps.resolveMovements(d, itemId, movements).also { payments = it.second }.first }
        recorder.matchPayments(payments)
        val ids = payments.map { it.id }.toSet()
        store.load().open.firstOrNull { it is OpenItem.MealQuestion && it.eventId in ids }?.let { _focusQuestion.value = it.id }
    }

    /** "Letzte Abbuchung erfassen?": [date] null = not recorded. Recorded, its meal question opens right away (detailed statistics). */
    fun resolveFirstDebit(itemId: Long, date: LocalDate?) = edit {
        var payments = emptyList<CardEvent>()
        store.update { d -> StatsOps.resolveFirstDebit(d, itemId, date).also { payments = it.second }.first }
        recorder.matchPayments(payments)
        val ids = payments.map { it.id }.toSet()
        store.load().open.firstOrNull { it is OpenItem.MealQuestion && it.eventId in ids }?.let { _focusQuestion.value = it.id }
    }

    /** Opens question [id] in the statistics screen (from the card screen's note). */
    fun openQuestion(id: Long) {
        _focusQuestion.value = id
    }

    /** The statistics screen opened: a scan made while the app was open has its payment matched now. */
    fun matchPending() = edit { recorder.matchPending() }

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

    /**
     * The question screen's answer: [items] (adding up to the payment) become its meal; dishes named there are saved, a
     * chosen [date] dates the payment, items on several days split it.
     */
    fun saveItems(eventId: Long, items: List<MealItem>, date: LocalDate?) = edit { store.update { MealDraft.save(it, eventId, items, date) } }

    /** Changes the Mensa and tags (and the name of a hand-named dish) of dish [index] of a payment's meal. */
    fun editDish(eventId: Long, index: Int, name: String, mensaId: String?, tags: List<Tag>) = edit {
        store.update { StatsOps.editDish(it, eventId, index, name, mensaId, tags) }
    }

    fun deleteEvent(event: CardEvent) = edit { store.update { StatsOps.deleteEvent(it, event.id) } }
    fun restoreEvent(event: CardEvent) = edit { store.update { StatsOps.restoreEvent(it, event) } }

    /** The heart in the meals overview: same favorite key and info as on a menu card. */
    fun toggleFavorite(dish: EatenDish) = edit {
        settingsRepo.toggleFavorite(dish.key, FavoriteInfo(dish.name, dish.tags))
    }
}
