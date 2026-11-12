package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import java.time.LocalDate

/** What a scan did to the statistics. */
sealed interface RecordResult {
    /** The scan was stored. [baseline] = the first scan of this card (nothing to compare with). */
    data class Recorded(
        val scanId: Long,
        val baseline: Boolean,
        val newEvents: List<CardEvent>,
        val newQuestions: List<OpenItem>,
    ) : RecordResult

    /** Same balance and last debit as the previous scan on the same day: nothing new, not stored again. */
    data object Unchanged : RecordResult

    /** Another card than the registered one: shown, never stored. */
    data object OtherCard : RecordResult

    /** Statistics on, but no card registered yet: the card screen offers to register this one. */
    data object NoCard : RecordResult

    /** Statistics off (or the file is from a newer app version). */
    data object Off : RecordResult
}

/** The state changes of the statistics, pure: each takes [StatsData] and returns the next one. */
object StatsOps {
    fun register(data: StatsData, salt: String, fingerprint: String, now: Long): StatsData =
        data.copy(card = RegisteredCard(salt, CardIdentity.hash(salt, fingerprint), now))

    /** The recorded scans, events and answers stay; only new scans stop until a card is registered again. */
    fun unregister(data: StatsData): StatsData = data.copy(card = null)

    /**
     * Records one scan of the registered card: stores it and derives what happened since the previous scan of the
     * same card. Certain events are stored, unclear ones become open questions.
     */
    fun recordScan(
        data: StatsData,
        fingerprint: String,
        at: Long,
        date: LocalDate,
        balance: Int,
        lastDebit: Int?,
        known: KnownAmounts,
    ): Pair<StatsData, RecordResult> {
        val card = data.card ?: return data to RecordResult.NoCard
        if (!CardIdentity.matches(card, fingerprint)) return data to RecordResult.OtherCard
        val previous = data.scans.lastOrNull { it.card == card.hash }
        if (previous != null && previous.date == date && previous.balance == balance && previous.lastDebit == lastDebit) {
            return data to RecordResult.Unchanged
        }
        var nextId = data.nextId
        val scan = Scan(nextId++, at, date, card.hash, balance, lastDebit)
        if (previous == null) {
            // The card's last debit happened before the statistics knew the card: offered as a payment of a day to pick.
            val first = lastDebit?.takeIf { it > 0 }?.let { listOf(OpenItem.FirstDebit(nextId++, scan.id, date, amount = it)) }.orEmpty()
            return data.copy(scans = data.scans + scan, open = data.open + first, nextId = nextId) to
                RecordResult.Recorded(scan.id, true, emptyList(), first)
        }
        val dateKnown = BalanceDiff.dateKnown(previous.date, date)
        val events = mutableListOf<CardEvent>()
        val questions = mutableListOf<OpenItem>()
        when (val outcome = BalanceDiff.derive(previous.balance, previous.lastDebit, balance, lastDebit, known)) {
            BalanceDiff.Outcome.Nothing -> Unit
            is BalanceDiff.Outcome.Events -> outcome.events.forEach { (kind, amount) ->
                events += CardEvent(nextId++, scan.id, kind, amount, date, dateKnown)
            }
            is BalanceDiff.Outcome.AskSeveral ->
                questions += OpenItem.SeveralPayments(nextId++, scan.id, date, dateKnown, outcome.amount, outcome.lastDebit)
            is BalanceDiff.Outcome.AskCredit ->
                questions += OpenItem.CreditAndPayment(nextId++, scan.id, date, dateKnown, outcome.delta, outcome.lastDebit, outcome.suggested)
        }
        val next = data.copy(scans = data.scans + scan, events = data.events + events, open = data.open + questions, nextId = nextId)
        return next to RecordResult.Recorded(scan.id, false, events, questions)
    }

    /** Several payments: one payment ([parts] of size 1) or a split; parts must add up. Returns the new payments. */
    fun resolveSeveral(data: StatsData, itemId: Long, parts: List<Int>): Pair<StatsData, List<CardEvent>> {
        val item = data.open.firstOrNull { it.id == itemId } as? OpenItem.SeveralPayments ?: return data to emptyList()
        require(parts.size == 1 && parts.single() == item.amount || BalanceDiff.validSplit(item.amount, parts)) { "parts must add up" }
        var nextId = data.nextId
        val events = parts.map { CardEvent(nextId++, item.scanId, EventKind.PAYMENT, it, item.date, item.dateKnown) }
        return data.copy(events = data.events + events, open = data.open - item, nextId = nextId) to events
    }

    /**
     * "Letzte Abbuchung erfassen?" on a card's first scan: [date] null = "Nicht erfassen" (the question goes away),
     * otherwise a payment of the last debit on [date] (the user's choice, so known). Returns the new payments.
     */
    fun resolveFirstDebit(data: StatsData, itemId: Long, date: LocalDate?): Pair<StatsData, List<CardEvent>> {
        val item = data.open.firstOrNull { it.id == itemId } as? OpenItem.FirstDebit ?: return data to emptyList()
        if (date == null) return data.copy(open = data.open - item) to emptyList()
        val payment = CardEvent(data.nextId, item.scanId, EventKind.PAYMENT, item.amount, date, dateKnown = true)
        return data.copy(events = data.events + payment, open = data.open - item, nextId = data.nextId + 1) to listOf(payment)
    }

    /**
     * A change no known amount explained. [credit] (with [kind]) chosen: that credit plus the payments of credit - delta.
     * Those are one payment when they don't exceed the card's last debit; more than it means several payments (the last
     * one was the last debit), which becomes a "several payments?" question like a plain decrease. [credit] null: only
     * the change itself, as a credit of [kind] (top-up or Pfand; delta > 0) or a payment (delta < 0); a zero change
     * stores nothing. Returns the new payments.
     */
    fun resolveCredit(data: StatsData, itemId: Long, credit: Int?, kind: EventKind = EventKind.TOP_UP): Pair<StatsData, List<CardEvent>> {
        val item = data.open.firstOrNull { it.id == itemId } as? OpenItem.CreditAndPayment ?: return data to emptyList()
        var nextId = data.nextId
        val events = when {
            credit != null -> {
                val paid = credit - item.delta
                require(kind != EventKind.PAYMENT && paid > 0) { "the payment must be positive" }
                val last = item.lastDebit?.takeIf { it > 0 }
                if (last != null && paid > last) {
                    val creditEvent = CardEvent(nextId++, item.scanId, kind, credit, item.date, item.dateKnown)
                    val several = OpenItem.SeveralPayments(nextId++, item.scanId, item.date, item.dateKnown, paid, last)
                    return data.copy(events = data.events + creditEvent, open = data.open - item + several, nextId = nextId) to emptyList()
                }
                listOf(
                    CardEvent(nextId++, item.scanId, kind, credit, item.date, item.dateKnown),
                    CardEvent(nextId++, item.scanId, EventKind.PAYMENT, paid, item.date, item.dateKnown),
                )
            }
            item.delta > 0 -> listOf(
                CardEvent(nextId++, item.scanId, if (kind == EventKind.PFAND) EventKind.PFAND else EventKind.TOP_UP, item.delta, item.date, item.dateKnown),
            )
            item.delta < 0 -> listOf(CardEvent(nextId++, item.scanId, EventKind.PAYMENT, -item.delta, item.date, item.dateKnown))
            else -> emptyList()
        }
        return data.copy(events = data.events + events, open = data.open - item, nextId = nextId) to events.filter { it.kind == EventKind.PAYMENT }
    }

    /**
     * "Anders zuordnen" on an automatic Pfand return: the events derived from that scan (the Pfand and a payment found
     * with it) are taken back and the change becomes a "top-up + payment?" question again, with the same net change.
     * Returns the new question's id (null when [eventId] is not a Pfand event).
     */
    fun reassignCredit(data: StatsData, eventId: Long): Pair<StatsData, Long?> {
        val event = data.events.firstOrNull { it.id == eventId && it.kind == EventKind.PFAND } ?: return data to null
        val taken = data.events.filter { it.scanId == event.scanId }
        val delta = taken.sumOf { if (it.kind == EventKind.PAYMENT) -it.amount else it.amount }
        val scan = data.scans.firstOrNull { it.id == event.scanId }
        val question = OpenItem.CreditAndPayment(data.nextId, event.scanId, event.date, event.dateKnown, delta, scan?.lastDebit)
        val takenIds = taken.map { it.id }.toSet()
        val next = data.copy(
            events = data.events.filterNot { it.id in takenIds },
            open = data.open.filterNot { it is OpenItem.MealQuestion && it.eventId in takenIds } + question,
            nextId = data.nextId + 1,
        )
        return next to question.id
    }

    /** Drops an open question without storing anything for it. */
    fun dismiss(data: StatsData, itemId: Long): StatsData = data.copy(open = data.open.filterNot { it.id == itemId })

    /** Adds meal questions for [payments] (detailed statistics), each with that day's menu [dishes] and its [missing] Mensen. */
    fun askMeals(data: StatsData, payments: List<CardEvent>, dishes: List<SnapDish>, missing: List<String> = emptyList()): StatsData {
        var nextId = data.nextId
        val asked = payments.filter { p -> p.kind == EventKind.PAYMENT && data.open.none { it is OpenItem.MealQuestion && it.eventId == p.id } }
            .map { OpenItem.MealQuestion(nextId++, it.scanId, it.date, it.dateKnown, it.id, it.amount, dishes, missing) }
        return if (asked.isEmpty()) data else data.copy(open = data.open + asked, nextId = nextId)
    }

    /** Remembers the dated, unmatched payments among [events] for the next visit to the app (see `StatsRecorder.matchPending`). */
    fun markForMatching(data: StatsData, events: List<CardEvent>): StatsData {
        val ids = events.filter { it.kind == EventKind.PAYMENT && it.dateKnown && it.meal == null }.map { it.id }
        return if (ids.isEmpty()) data else data.copy(pendingMatch = data.pendingMatch + ids)
    }

    /** Sets what payment [eventId] bought (null clears it) and closes its meal question. */
    fun setMeal(data: StatsData, eventId: Long, meal: MealRecord?): StatsData = data.copy(
        events = data.events.map { if (it.id == eventId && it.kind == EventKind.PAYMENT) it.copy(meal = meal) else it },
        open = data.open.filterNot { it is OpenItem.MealQuestion && it.eventId == eventId },
    )

    /** "Nicht an diesem Tag": the payment's day is unknown, so no matching; closes its meal question. */
    fun markDateUnknown(data: StatsData, eventId: Long): StatsData = data.copy(
        events = data.events.map { if (it.id == eventId) it.copy(dateKnown = false) else it },
        open = data.open.filterNot { it is OpenItem.MealQuestion && it.eventId == eventId },
    )

    /** Deletes an event and its open meal question. */
    fun deleteEvent(data: StatsData, eventId: Long): StatsData = data.copy(
        events = data.events.filterNot { it.id == eventId },
        open = data.open.filterNot { it is OpenItem.MealQuestion && it.eventId == eventId },
    )

    /** Undo of [deleteEvent]: puts the event back in order. */
    fun restoreEvent(data: StatsData, event: CardEvent): StatsData =
        if (data.events.any { it.id == event.id }) data else data.copy(events = (data.events + event).sortedBy { it.id })

    fun addCustomExtra(data: StatsData, name: String): StatsData {
        val clean = name.trim()
        if (clean.isEmpty() || Extras.isBuiltIn(clean) || data.customExtras.any { it.equals(clean, ignoreCase = true) }) return data
        return data.copy(customExtras = data.customExtras + clean)
    }

    fun removeCustomExtra(data: StatsData, name: String): StatsData = data.copy(customExtras = data.customExtras - name)

    /**
     * Saves a hand-named dish with its [mensaId] (null = unknown) and [tags] (none = no information), or updates the
     * saved one of the same name (price, Mensa, tags: its earlier meals follow, see [editDish]); returns it as eaten
     * once at [price].
     */
    fun addCustomDish(data: StatsData, name: String, price: Int, mensaId: String? = null, tags: List<Tag> = emptyList()): Pair<StatsData, EatenDish> {
        val clean = name.trim()
        val key = Favorites.normalize(clean)
        val dish = KnownDish(key, LocalizedText(clean), price, tags, mensaId)
        val list = data.customDishes.filterNot { it.key == key } + dish
        val earlier = data.events.map { e -> e.copy(meal = e.meal?.copy(dishes = e.meal.dishes.map { d -> if (d.own && d.key == key) d.copy(name = dish.name, mensaId = mensaId, tags = tags) else d })) }
        return data.copy(customDishes = list, events = earlier) to EatenDish(key, dish.name, mensaId, price, tags = tags, own = true)
    }

    /**
     * "Gericht bearbeiten": sets the Mensa ([mensaId], null = unknown) and [tags] of dish [index] of payment [eventId]'s
     * meal; the name too for a hand-named dish. A menu dish changes in that meal only. A hand-named dish is one dish
     * for the app, so every meal with it follows (and the saved dish used by the search); with no tag selected its tags
     * are "no information". Nothing changes for an unknown payment or index.
     */
    fun editDish(data: StatsData, eventId: Long, index: Int, name: String, mensaId: String?, tags: List<Tag>): StatsData {
        val event = data.events.firstOrNull { it.id == eventId && it.kind == EventKind.PAYMENT } ?: return data
        val meal = event.meal ?: return data
        val dish = meal.dishes.getOrNull(index) ?: return data
        if (!dish.own) {
            val dishes = meal.dishes.mapIndexed { i, d -> if (i == index) d.copy(mensaId = mensaId, tags = tags) else d }
            return data.copy(events = data.events.map { if (it.id == eventId) it.copy(meal = meal.copy(dishes = dishes)) else it })
        }
        val clean = name.trim().ifEmpty { dish.name.de }
        val key = Favorites.normalize(clean).ifEmpty { dish.key }
        val newName = if (clean == dish.name.de) dish.name else LocalizedText(clean)
        fun EatenDish.follow() = if (own && this.key == dish.key) copy(key = key, name = newName, mensaId = mensaId, tags = tags) else this
        val price = data.customDishes.firstOrNull { it.key == dish.key }?.price ?: dish.price
        return data.copy(
            events = data.events.map { e -> e.meal?.let { m -> e.copy(meal = m.copy(dishes = m.dishes.map { it.follow() })) } ?: e },
            customDishes = data.customDishes.filterNot { it.key == dish.key || it.key == key } + KnownDish(key, newName, price, tags, mensaId),
        )
    }

    /** Whether the scan [scanId] and the previous scan of the same card were on the same day. */
    fun sameDayAsPrevious(data: StatsData, scanId: Long): Boolean {
        val index = data.scans.indexOfFirst { it.id == scanId }
        if (index <= 0) return false
        val scan = data.scans[index]
        val previous = data.scans.subList(0, index).lastOrNull { it.card == scan.card } ?: return false
        return previous.date == scan.date
    }

    /** Deletes every scan, event and question (the registered card, custom extras and dishes stay). */
    fun clearHistory(data: StatsData): StatsData = data.copy(scans = emptyList(), events = emptyList(), open = emptyList())
}
