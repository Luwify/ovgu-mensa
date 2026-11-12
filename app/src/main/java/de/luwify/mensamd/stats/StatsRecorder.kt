package de.luwify.mensamd.stats

import de.luwify.mensamd.data.settings.CardStatsSettings
import de.luwify.mensamd.model.MensaCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The result of the scan with sequence number [seq] (see `CardScanState.Success.seq`). */
data class ScanRecord(val seq: Long, val result: RecordResult)

/** What matching needs from the app: the default Mensa (listed first) and the user's price group. */
data class MealContext(val defaultMensaId: String?, val priceGroup: Int)

/**
 * Turns card scans into statistics: checks the card, stores the scan, derives events and, with detailed statistics,
 * marks new payments for matching to the day's menu. Runs in [scope] (the app's), so a scan from the popup is stored
 * even when the popup closes right away. A scan from the popup never touches the network: the matching waits for
 * [matchPending] (main UI or statistics screen open, or a scan in the open app).
 *
 * The matching never waits for the network: it decides with the menus the app has ([cachedMenu]) and asks right away;
 * Mensen whose menu of that day is missing are then loaded in the background ([loadMenu], [loading]) and the open
 * questions of that day get the new dishes. Saved without asking is still only a single candidate with every menu known.
 * Never logs card data.
 */
class StatsRecorder(
    val store: StatsStore,
    private val settings: Flow<CardStatsSettings>,
    private val mealContext: suspend () -> MealContext,
    /** That day's menu as far as the app has it, without fetching. */
    private val cachedMenu: suspend (LocalDate) -> DayMenu,
    /** That day's menu after loading what is missing (network). */
    private val loadMenu: suspend (LocalDate) -> DayMenu,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val newSalt: () -> String = { CardIdentity.newSalt() },
) {
    private val _last = MutableStateFlow<ScanRecord?>(null)

    /** The result of the latest scan; the card screen shows hints from it. */
    val last: StateFlow<ScanRecord?> = _last.asStateFlow()

    private val _registering = MutableStateFlow(false)

    /** "Karte registrieren" in the card settings: the next scan registers its card. */
    val registering: StateFlow<Boolean> = _registering.asStateFlow()

    fun setRegistering(on: Boolean) {
        _registering.value = on
    }

    /** Serializes scans, so two quick scans never derive from the same previous scan. */
    private val scanLock = Mutex()

    /** One [matchPending] at a time: the app start and the statistics screen can meet. */
    private val pendingLock = Mutex()

    /** Whether a successful read should look at the card's id at all: only to recognize or register a card, so only with the statistics on. */
    suspend fun wantsCardId(): Boolean = settings.first().enabled && !store.readOnly

    private val _loading = MutableStateFlow<Set<LocalDate>>(emptySet())

    /** Days whose missing menus are being loaded for open meal questions ("lädt weitere Mensen …"). */
    val loading: StateFlow<Set<LocalDate>> = _loading.asStateFlow()

    /** The meal question on screen: a background load updates its dishes but never answers it under the user's eyes. */
    val viewing = MutableStateFlow<Long?>(null)

    /**
     * Records a scan in the app scope. The result lands in [last] and the deferred as soon as the scan is stored.
     * [matchNow]: the app is open (reader mode), so its payments are matched right after (the popup passes false).
     */
    fun recordAsync(seq: Long, balance: Int, lastDebit: Int?, fingerprint: String?, matchNow: Boolean = false): Deferred<RecordResult> = scope.async {
        val result = storeScan(balance, lastDebit, fingerprint)
        _last.value = ScanRecord(seq, result)
        if (matchNow && result is RecordResult.Recorded) scope.launch { matchPending() }
        result
    }

    /** Whether new payments get a meal question (detailed statistics on). */
    suspend fun detailedActive(): Boolean = settings.first().detailedActive

    /** Stores the scan and matches its payments before returning. */
    suspend fun record(balance: Int, lastDebit: Int?, fingerprint: String?): RecordResult =
        storeScan(balance, lastDebit, fingerprint).also { matchPending() }

    private suspend fun storeScan(balance: Int, lastDebit: Int?, fingerprint: String?): RecordResult = scanLock.withLock {
        val config = settings.first()
        if (!config.enabled || store.readOnly) return@withLock RecordResult.Off
        if (fingerprint == null) return@withLock RecordResult.OtherCard // no id: can't be "my" card
        if (_registering.value) {
            _registering.value = false
            registerLocked(fingerprint)
        }
        val now = clock()
        val date = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
        var result: RecordResult = RecordResult.Off
        store.update { data ->
            val (next, r) = StatsOps.recordScan(data, fingerprint, now, date, balance, lastDebit, config.known)
            result = r
            if (config.detailedActive && r is RecordResult.Recorded) StatsOps.markForMatching(next, r.newEvents) else next
        }
        result
    }

    /**
     * Registers the card with [fingerprint] as "Meine Karte" and records [balance] as its first scan (when given). The
     * previous registration, if any, is replaced; its scans stay.
     */
    suspend fun register(fingerprint: String, balance: Int?, lastDebit: Int?, seq: Long?): RecordResult? = scanLock.withLock {
        registerLocked(fingerprint)
        if (balance == null) return@withLock null
        val now = clock()
        val date = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
        val config = settings.first()
        var result: RecordResult = RecordResult.Off
        store.update { data ->
            val (next, r) = StatsOps.recordScan(data, fingerprint, now, date, balance, lastDebit, config.known)
            result = r
            next
        }
        if (seq != null) _last.value = ScanRecord(seq, result)
        result
    }

    /** Registers [fingerprint]'s card; the card that is already registered keeps its registration (and its scans' chain). */
    private suspend fun registerLocked(fingerprint: String) {
        val current = store.load().card
        if (current != null && CardIdentity.matches(current, fingerprint)) return
        val salt = newSalt()
        store.update { StatsOps.register(it, salt, fingerprint, clock()) }
    }

    /**
     * Matches the payments scans left for later (a meal saved, or a question), loading their days' menus if needed. Called
     * when the main UI or the statistics screen opens, never from a card tap. A payment whose day has dropped off the
     * site by then counts its Mensa as missing, and the question names it.
     */
    suspend fun matchPending(): Unit = pendingLock.withLock {
        val config = settings.first()
        if (!config.enabled || store.readOnly) return@withLock
        val data = store.load()
        // Questions left with missing menus (the app closed while loading, or the site was down): tried again below.
        val today = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()
        val retry = data.open.filterIsInstance<OpenItem.MealQuestion>()
            .filter { it.missingMensen.isNotEmpty() && !it.date.isBefore(today.minusDays(RETRY_DAYS)) }
            .map { it.date }.distinct()
        val ids = data.pendingMatch.toSet()
        if (ids.isNotEmpty()) {
            if (config.detailedActive) matchPayments(data.events.filter { it.id in ids })
            store.update { it.copy(pendingMatch = it.pendingMatch - ids) }
        }
        if (config.detailedActive) retry.forEach(::loadInBackground)
    }

    /**
     * Payments that just got known (from a pending scan or an answered question): matched against the menus the app has
     * now, at once; missing menus follow in the background ([loadInBackground]). Only with detailed statistics.
     */
    suspend fun matchPayments(payments: List<CardEvent>) {
        if (!settings.first().detailedActive) return
        val dated = payments.filter { it.kind == EventKind.PAYMENT && it.dateKnown && it.meal == null }
        if (dated.isEmpty()) return
        val context = mealContext()
        dated.groupBy { it.date }.forEach { (date, list) ->
            val menu = safely { cachedMenu(date) } ?: DayMenu(emptyList(), MensaCatalog.all.map { it.id })
            store.update { data ->
                var next = data
                list.filter { p -> next.events.any { it.id == p.id } }.forEach { payment ->
                    val single = singleMatch(next, payment.scanId, payment.amount, menu, context)
                    next = if (single != null) StatsOps.setMeal(next, payment.id, single.toRecord())
                    else StatsOps.askMeals(next, listOf(payment), menu.dishes, menu.missing)
                }
                next
            }
            if (menu.missing.isNotEmpty()) loadInBackground(date)
        }
    }

    /**
     * The one candidate that may be saved without asking: only with every Mensa's menu at hand (a missing one might have
     * the same price) and when both scans were on the same day (with the previous scan on an earlier day the payment may
     * also have been on that day, after that scan).
     */
    private fun singleMatch(data: StatsData, scanId: Long, amount: Int, menu: DayMenu, context: MealContext): MealMatcher.Candidate? {
        if (menu.missing.isNotEmpty() || !StatsOps.sameDayAsPrevious(data, scanId)) return null
        val plan = MealMatcher.plan(amount, menu.dishes, context.defaultMensaId, context.priceGroup)
        return (plan as? MealMatcher.Plan.Choose)?.candidates?.singleOrNull()
    }

    /**
     * Loads the missing menus of [date] in the app scope (once at a time per day), then gives every open meal question of
     * that day the full menu. One that now has a single candidate is saved, unless it is on screen ([viewing]).
     */
    private fun loadInBackground(date: LocalDate) {
        val started = _loading.getAndUpdate { it + date }
        if (date in started) return
        scope.launch {
            try {
                val menu = safely { loadMenu(date) } ?: return@launch
                val context = mealContext()
                store.update { data ->
                    var next = data
                    next.open.filterIsInstance<OpenItem.MealQuestion>().filter { it.date == date }.forEach { q ->
                        val single = singleMatch(next, q.scanId, q.amount, menu, context)?.takeIf { viewing.value != q.id }
                        next = if (single != null) StatsOps.setMeal(next, q.eventId, single.toRecord())
                        else StatsOps.updateMealQuestion(next, q.id, menu.dishes, menu.missing)
                    }
                    next
                }
            } finally {
                _loading.update { it - date }
            }
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (_: Exception) {
        null
    }

    companion object {
        /** Open questions with missing menus are loaded again at the next visit only this many days back (the site drops past days). */
        const val RETRY_DAYS = 7L
    }
}
