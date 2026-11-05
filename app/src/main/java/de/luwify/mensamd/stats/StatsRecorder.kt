package de.luwify.mensamd.stats

import de.luwify.mensamd.data.settings.CardStatsSettings
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

/** What matching needs from the app: the default Mensa (tried first) and the user's price group. */
data class MealContext(val defaultMensaId: String?, val priceGroup: Int)

/**
 * Turns card scans into statistics: checks the card, stores the scan, derives events and, with detailed statistics,
 * matches new payments to the day's menu ([snapshot]). Runs in [scope] (the app's), so a scan from the popup is
 * stored even when the popup closes right away. Never logs card data.
 */
class StatsRecorder(
    val store: StatsStore,
    private val settings: Flow<CardStatsSettings>,
    private val mealContext: suspend () -> MealContext,
    private val snapshot: suspend (LocalDate) -> List<SnapDish>,
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

    /** Records a scan in the app scope; the result also lands in [last]. */
    fun recordAsync(seq: Long, balance: Int, lastDebit: Int?, fingerprint: String?): Deferred<RecordResult> = scope.async {
        val result = record(balance, lastDebit, fingerprint)
        _last.value = ScanRecord(seq, result)
        result
    }

    suspend fun record(balance: Int, lastDebit: Int?, fingerprint: String?): RecordResult = scanLock.withLock {
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
            next
        }
        (result as? RecordResult.Recorded)?.let { matchPayments(it.newEvents, config) }
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

    /** Payments that just got known (from a scan or an answered question): match them when detailed statistics are on. */
    suspend fun matchPayments(payments: List<CardEvent>, config: CardStatsSettings? = null) {
        val c = config ?: settings.first()
        if (!c.detailedActive) return
        val dated = payments.filter { it.kind == EventKind.PAYMENT && it.dateKnown && it.meal == null }
        if (dated.isEmpty()) return
        val context = mealContext()
        dated.groupBy { it.date }.forEach { (date, list) ->
            val dishes = runCatching { snapshot(date) }.getOrDefault(emptyList())
            store.update { data ->
                var next = data
                list.filter { p -> next.events.any { it.id == p.id } }.forEach { payment ->
                    val plan = MealMatcher.plan(payment.amount, dishes, context.defaultMensaId, context.priceGroup)
                    // Saved without asking only when both scans were on the same day: with the previous scan on an
                    // earlier day the payment may also have been on that day, after that scan.
                    val single = (plan as? MealMatcher.Plan.Choose)?.candidates?.singleOrNull()
                        ?.takeIf { StatsOps.sameDayAsPrevious(next, payment.scanId) }
                    next = if (single != null) StatsOps.setMeal(next, payment.id, single.toRecord())
                    else StatsOps.askMeals(next, listOf(payment), dishes)
                }
                next
            }
        }
    }
}
