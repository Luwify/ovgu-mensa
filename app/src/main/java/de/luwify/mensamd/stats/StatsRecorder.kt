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
 * marks new payments for matching to the day's menu ([snapshot]). Runs in [scope] (the app's), so a scan from the popup
 * is stored even when the popup closes right away. A scan never touches the network: the matching, which may have to
 * load menus, waits for [matchPending]. Never logs card data.
 */
class StatsRecorder(
    val store: StatsStore,
    private val settings: Flow<CardStatsSettings>,
    private val mealContext: suspend () -> MealContext,
    private val snapshot: suspend (LocalDate) -> DayMenu,
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

    /** Records a scan in the app scope. The result lands in [last] and the deferred as soon as the scan is stored. */
    fun recordAsync(seq: Long, balance: Int, lastDebit: Int?, fingerprint: String?): Deferred<RecordResult> = scope.async {
        val result = storeScan(balance, lastDebit, fingerprint)
        _last.value = ScanRecord(seq, result)
        result
    }

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
        val ids = data.pendingMatch.toSet()
        if (ids.isEmpty()) return@withLock
        if (config.detailedActive) matchPayments(data.events.filter { it.id in ids })
        store.update { it.copy(pendingMatch = it.pendingMatch - ids) }
    }

    /** Payments that just got known (from a pending scan or an answered question): match them when detailed statistics are on. */
    suspend fun matchPayments(payments: List<CardEvent>) {
        if (!settings.first().detailedActive) return
        val dated = payments.filter { it.kind == EventKind.PAYMENT && it.dateKnown && it.meal == null }
        if (dated.isEmpty()) return
        val context = mealContext()
        dated.groupBy { it.date }.forEach { (date, list) ->
            val menu = try {
                snapshot(date)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                DayMenu(emptyList(), MensaCatalog.all.map { it.id })
            }
            store.update { data ->
                var next = data
                list.filter { p -> next.events.any { it.id == p.id } }.forEach { payment ->
                    val plan = MealMatcher.plan(payment.amount, menu.dishes, context.defaultMensaId, context.priceGroup)
                    // Saved without asking only with every Mensa's menu at hand (a missing one might have the same
                    // price) and when both scans were on the same day: with the previous scan on an earlier day the
                    // payment may also have been on that day, after that scan.
                    val single = (plan as? MealMatcher.Plan.Choose)?.candidates?.singleOrNull()
                        ?.takeIf { menu.missing.isEmpty() && StatsOps.sameDayAsPrevious(next, payment.scanId) }
                    next = if (single != null) StatsOps.setMeal(next, payment.id, single.toRecord())
                    else StatsOps.askMeals(next, listOf(payment), menu.dishes, menu.missing)
                }
                next
            }
        }
    }
}
