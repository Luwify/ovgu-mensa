package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsOpsTest {
    private val known = KnownAmounts(KnownAmounts.DEFAULT_TOP_UPS, emptyList())
    private val fp = CardIdentity.fingerprint(byteArrayOf(4, 1, 2, 3, 4, 5, 6))
    private val other = CardIdentity.fingerprint(byteArrayOf(4, 9, 9, 9, 9, 9, 9))
    private val day = LocalDate.of(2026, 11, 5)
    private val registered = StatsOps.register(StatsData(), "00112233445566778899aabbccddeeff", fp, 0)

    /** A first scan with its "Letzte Abbuchung erfassen?" question dropped (most tests are about what follows). */
    private fun baseline(scanned: Pair<StatsData, RecordResult>): StatsData =
        scanned.first.copy(open = scanned.first.open.filterNot { it is OpenItem.FirstDebit })

    private fun scan(data: StatsData, balance: Int, debit: Int?, date: LocalDate = day, card: String = fp) =
        StatsOps.recordScan(data, card, 0, date, balance, debit, known)

    /** The wrong automatic match of 2026-11-06: corrected in place, the payment itself stays as it was. */
    @Test fun correctingAMealKeepsThePayment() {
        val pommes = SnapDish("unten", LocalizedText("Portion Pommes Frites"), listOf(200, 380, 520))
        val chili = SnapDish("oben", LocalizedText("Chili sin carne"), listOf(200, 380, 520))
        val (first, _) = scan(registered, 10_000, 0)
        val (paid, _) = scan(first, 8_000, 2_000)
        val payment = paid.events.single()
        fun asPayment(d: SnapDish) = MealRecord(listOf(EatenDish(Favorites.normalize(d.name.de), d.name, d.mensaId, payment.amount)))
        val wrong = StatsOps.setMeal(paid, payment.id, asPayment(pommes))
        val fixed = StatsOps.setMeal(wrong, payment.id, asPayment(chili))
        val event = fixed.events.single()
        assertEquals(payment.copy(meal = event.meal), event)
        assertEquals(listOf("oben" to "chili sin carne"), event.meal!!.dishes.map { it.mensaId to it.key })
        assertEquals(paid.scans, fixed.scans)
    }

    @Test fun nothingWithoutCard() = assertEquals(RecordResult.NoCard, scan(StatsData(), 1_000, 0).second)

    @Test fun otherCardIsNotStored() {
        val (data, result) = scan(registered, 1_000, 0, card = other)
        assertEquals(RecordResult.OtherCard, result)
        assertTrue(data.scans.isEmpty())
    }

    @Test fun uidIsNeverStored() {
        val (data, _) = scan(registered, 1_000, 0)
        val text = statsJson.encodeToString(StatsData.serializer(), data)
        assertFalse(text.contains(fp))
        assertEquals(registered.card!!.hash, data.scans.single().card)
    }

    @Test fun firstScanIsBaselineThenPayment() {
        val (a, r1) = scan(registered, 10_000, 3_100)
        assertTrue((r1 as RecordResult.Recorded).baseline)
        val (b, r2) = scan(a, 4_400, 5_600)
        val events = (r2 as RecordResult.Recorded).newEvents
        assertEquals(listOf(EventKind.PAYMENT to 5_600), events.map { it.kind to it.amount })
        assertTrue(events.single().dateKnown)
        assertEquals(b.events, events)
    }

    @Test fun repeatedIdenticalScanSameDayIsIgnored() {
        val a = baseline(scan(registered, 10_000, 3_100))
        assertEquals(RecordResult.Unchanged, scan(a, 10_000, 3_100).second)
        // The next day it is stored (the reminder needs to know the card was scanned that day).
        assertTrue(scan(a, 10_000, 3_100, day.plusDays(1)).second is RecordResult.Recorded)
    }

    @Test fun severalPaymentsSplitOrOne() {
        val a = baseline(scan(registered, 10_000, 1_000))
        val (b, r) = scan(a, 4_400, 2_500)
        val q = (r as RecordResult.Recorded).newQuestions.single() as OpenItem.SeveralPayments
        assertEquals(5_600, q.amount)
        val (split, payments) = StatsOps.resolveSeveral(b, q.id, listOf(3_100, 2_500))
        assertEquals(listOf(3_100, 2_500), payments.map { it.amount })
        assertTrue(split.open.isEmpty())
        val (one, single) = StatsOps.resolveSeveral(b, q.id, listOf(5_600))
        assertEquals(listOf(5_600), single.map { it.amount })
        assertEquals(1, one.events.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun splitThatDoesNotAddUpIsRejected() {
        val a = baseline(scan(registered, 10_000, 1_000))
        val (b, r) = scan(a, 4_400, 2_500)
        StatsOps.resolveSeveral(b, (r as RecordResult.Recorded).newQuestions.single().id, listOf(3_000, 2_500))
    }

    @Test fun unknownIncreaseResolvedAsTopUpPlusPayment() {
        val a = baseline(scan(registered, 5_370, 5_600))
        val (b, r) = scan(a, 19_770, 5_600)
        val q = (r as RecordResult.Recorded).newQuestions.single() as OpenItem.CreditAndPayment
        val (c, payments) = StatsOps.resolveMovements(b, q.id, listOf(Movement(EventKind.TOP_UP, 20_000, day), Movement(EventKind.PAYMENT, 5_600, day)))
        assertEquals(listOf(EventKind.TOP_UP to 20_000, EventKind.PAYMENT to 5_600), c.events.map { it.kind to it.amount })
        assertEquals(5_600, payments.single().amount)
        assertTrue(c.open.isEmpty())
        val (d, _) = StatsOps.resolveMovements(b, q.id, listOf(Movement(EventKind.TOP_UP, 14_400, day)))
        assertEquals(listOf(EventKind.TOP_UP to 14_400), d.events.map { it.kind to it.amount })
    }

    @Test fun reRegisteredCardStartsNewChain() {
        val (a, _) = scan(registered, 10_000, 0)
        val other = StatsOps.register(a, "ffeeddccbbaa99887766554433221100", this.other, 1)
        val (b, r) = StatsOps.recordScan(other, this.other, 0, day, 50_000, 0, known)
        assertTrue((r as RecordResult.Recorded).baseline)
        assertTrue(b.events.isEmpty())
    }

    @Test fun skippedWeekdayMakesDateUnknown() {
        val (a, _) = scan(registered, 10_000, 0, day.minusDays(3)) // Monday
        val (_, r) = scan(a, 6_900, 3_100) // Thursday
        assertFalse((r as RecordResult.Recorded).newEvents.single().dateKnown)
    }

    @Test fun mealQuestionsAndAnswers() {
        val (a, _) = scan(registered, 10_000, 0)
        val (b, r) = scan(a, 6_900, 3_100)
        val payment = (r as RecordResult.Recorded).newEvents.single()
        val asked = StatsOps.askMeals(b, listOf(payment), emptyList())
        val q = asked.open.single() as OpenItem.MealQuestion
        assertEquals(payment.id, q.eventId)
        assertEquals(asked, StatsOps.askMeals(asked, listOf(payment), emptyList())) // never asked twice
        val meal = MealRecord(listOf(EatenDish("x", LocalizedText("X"), "unicampus-unten", 3_100)))
        val answered = StatsOps.setMeal(asked, payment.id, meal)
        assertEquals(meal, answered.events.single().meal)
        assertTrue(answered.open.isEmpty())
        val otherDay = StatsOps.answerMeal(asked, payment.id, meal, payment.date.minusDays(1))
        assertEquals(payment.date.minusDays(1) to true, otherDay.events.single().let { it.date to it.dateKnown })
        assertTrue(otherDay.open.isEmpty())
    }

    @Test fun deleteAndRestoreEvent() {
        val (a, _) = scan(registered, 10_000, 0)
        val (b, _) = scan(a, 6_900, 3_100)
        val event = b.events.single()
        val deleted = StatsOps.deleteEvent(b, event.id)
        assertTrue(deleted.events.isEmpty())
        assertEquals(b.events, StatsOps.restoreEvent(deleted, event).events)
    }

    @Test fun unregisterKeepsHistory() {
        val (a, _) = scan(registered, 10_000, 0)
        val u = StatsOps.unregister(a)
        assertNull(u.card)
        assertEquals(a.scans, u.scans)
    }

    @Test fun reassignPfandBecomesATopUpQuestion() {
        val k = KnownAmounts(KnownAmounts.DEFAULT_TOP_UPS, KnownAmounts.DEFAULT_PFAND)
        val a = baseline(StatsOps.recordScan(registered, fp, 0, day, 5_000, 3_100, k))
        val (b, r) = StatsOps.recordScan(a, fp, 0, day, 5_330, 3_100, k)
        val pfand = (r as RecordResult.Recorded).newEvents.single()
        assertEquals(EventKind.PFAND, pfand.kind)
        val (c, id) = StatsOps.reassignCredit(b, pfand.id)
        assertTrue(c.events.isEmpty())
        val q = c.open.single() as OpenItem.CreditAndPayment
        assertEquals(id, q.id)
        assertEquals(330, q.delta)
        assertEquals(3_100, q.lastDebit)
        // Answered as a 10 € top-up and two payments (the last one the card's last debit).
        val (d, payments) = StatsOps.resolveMovements(
            c, q.id, listOf(Movement(EventKind.TOP_UP, 10_000, day), Movement(EventKind.PAYMENT, 6_570, day), Movement(EventKind.PAYMENT, 3_100, day)),
        )
        assertEquals(listOf(EventKind.TOP_UP to 10_000, EventKind.PAYMENT to 6_570, EventKind.PAYMENT to 3_100), d.events.map { it.kind to it.amount })
        assertEquals(2, payments.size)
        assertTrue(d.open.isEmpty())
        // Or back to a Pfand return after all.
        val (e, none) = StatsOps.resolveMovements(c, q.id, listOf(Movement(EventKind.PFAND, 330, day)))
        assertEquals(listOf(EventKind.PFAND to 330), e.events.map { it.kind to it.amount })
        assertTrue(none.isEmpty())
    }

    @Test fun reassignPfandWithItsPaymentKeepsTheNetChange() {
        val k = KnownAmounts(KnownAmounts.DEFAULT_TOP_UPS, KnownAmounts.DEFAULT_PFAND)
        val a = baseline(StatsOps.recordScan(registered, fp, 0, day, 10_000, 3_100, k))
        // 0.50 € bottles back, 5.60 € lunch: -5.10 €
        val (b, _) = StatsOps.recordScan(a, fp, 0, day, 4_900, 5_600, k)
        assertEquals(listOf(EventKind.PFAND to 500, EventKind.PAYMENT to 5_600), b.events.map { it.kind to it.amount })
        val withMeal = StatsOps.askMeals(b, listOf(b.events[1]), emptyList())
        val (c, _) = StatsOps.reassignCredit(withMeal, b.events[0].id)
        assertEquals(-5_100, (c.open.single() as OpenItem.CreditAndPayment).delta) // the meal question went with it
        assertTrue(c.events.isEmpty())
    }

    @Test fun reassignOnlyForPfand() {
        val (a, _) = scan(registered, 10_000, 0)
        val (b, _) = scan(a, 6_900, 3_100)
        assertEquals(b to null, StatsOps.reassignCredit(b, b.events.single().id))
    }

    private val vegan = Tag("vegan", "Vegan")
    private val fish = Tag("fisch", "Fisch")

    /** A payment of 3,20 € with a hand-named dish saved without Mensa and tags, as the app did before. */
    private fun withShawarma(): Pair<StatsData, Long> {
        val (first, _) = scan(registered, 10_000, 0)
        val (paid, _) = scan(first, 6_800, 3_200)
        val payment = paid.events.single()
        val (named, eaten) = StatsOps.addCustomDish(paid, " Plantbased Chicken-Shawarma ", 3_200)
        return StatsOps.setMeal(named, payment.id, MealRecord(dishes = listOf(eaten))) to payment.id
    }

    @Test fun aNewDishIsSavedWithMensaAndTagsAndOfferedNextTime() {
        val (paid, id) = withShawarma()
        val (named, eaten) = StatsOps.addCustomDish(paid, "Wrap", 3_000, "unicampus-unten", listOf(vegan))
        assertEquals(EatenDish("wrap", LocalizedText("Wrap"), "unicampus-unten", 3_000, 1, listOf(vegan), null, own = true), eaten)
        val saved = named.customDishes.single { it.key == "wrap" }
        assertEquals("unicampus-unten", saved.mensaId)
        assertEquals(listOf(vegan), saved.tags)
        val hit = MealDraft.options(0, emptyList(), false, emptyList(), named.events, named.customDishes, emptyList(), null, 0).single { it.key == "wrap" }
        assertEquals("unicampus-unten" to listOf(vegan), hit.mensaId to hit.tags)
        assertTrue(hit.own)
        assertEquals(id, paid.events.single().id)
    }

    @Test fun editingAHandNamedDishFixesTheEntryAndTheSavedDish() {
        val (data, id) = withShawarma()
        assertFalse(data.events.single().meal!!.dishes.single().tagsKnown)
        val fixed = StatsOps.editDish(data, id, 0, "Plantbased Chicken-Shawarma", "unicampus-oben", listOf(vegan))
        val dish = fixed.events.single().meal!!.dishes.single()
        assertEquals("unicampus-oben" to listOf(vegan), dish.mensaId to dish.tags)
        assertTrue(dish.tagsKnown)
        assertEquals(3_200, dish.price)
        val saved = fixed.customDishes.single()
        assertEquals("unicampus-oben" to listOf(vegan), saved.mensaId to saved.tags)
        assertEquals(3_200, saved.price)
        assertEquals(data.events.single().amount, fixed.events.single().amount)
    }

    @Test fun renamingAHandNamedDishRenamesItEverywhere() {
        val (data, id) = withShawarma()
        // The same dish eaten on another payment.
        val other = data.events.single().copy(id = 99, meal = data.events.single().meal)
        val twice = data.copy(events = data.events + other)
        val fixed = StatsOps.editDish(twice, id, 0, "Shawarma Bowl", null, listOf(fish))
        assertEquals(listOf("shawarma bowl", "shawarma bowl"), fixed.events.map { it.meal!!.dishes.single().key })
        assertEquals(listOf("shawarma bowl"), fixed.customDishes.map { it.key })
        assertEquals(listOf(listOf(fish), listOf(fish)), fixed.events.map { it.meal!!.dishes.single().tags })
    }

    @Test fun editingAMenuDishChangesThatPaymentOnlyAndKeepsItsName() {
        val (first, _) = scan(registered, 10_000, 0)
        val (paid, _) = scan(first, 6_800, 3_200)
        val payment = paid.events.single()
        val menu = EatenDish("curry", LocalizedText("Curry"), "unicampus-unten", 3_200, 1, emptyList())
        val twoMeals = StatsOps.setMeal(paid, payment.id, MealRecord(listOf(menu)))
            .let { it.copy(events = it.events + it.events.single().copy(id = 50)) }
        val fixed = StatsOps.editDish(twoMeals, payment.id, 0, "Ignored", "herrenkrug", listOf(vegan))
        val (changed, untouched) = fixed.events.map { it.meal!!.dishes.single() }
        assertEquals(Triple("curry", "herrenkrug", listOf(vegan)), Triple(changed.key, changed.mensaId, changed.tags))
        assertEquals(menu, untouched)
        assertTrue(fixed.customDishes.isEmpty())
        assertTrue(changed.tagsKnown)
    }

    @Test fun editingNothingChangesNothing() {
        val (data, id) = withShawarma()
        assertEquals(data, StatsOps.editDish(data, id, 3, "X", null, emptyList()))
        assertEquals(data, StatsOps.editDish(data, 12345, 0, "X", null, emptyList()))
    }

    @Test fun clearingTheTagsOfAHandNamedDishMakesThemUnknownAgain() {
        val (data, id) = withShawarma()
        val tagged = StatsOps.editDish(data, id, 0, "Plantbased Chicken-Shawarma", null, listOf(vegan))
        val cleared = StatsOps.editDish(tagged, id, 0, "Plantbased Chicken-Shawarma", null, emptyList())
        assertFalse(cleared.events.single().meal!!.dishes.single().tagsKnown)
    }

    /** The first scan offers the card's last debit as a payment of a day the user picks; "Nicht erfassen" drops it. */
    @Test fun firstScanOffersTheLastDebit() {
        val (a, r) = scan(registered, 10_000, 3_100)
        val q = (r as RecordResult.Recorded).newQuestions.single() as OpenItem.FirstDebit
        assertEquals(3_100, q.amount)
        assertEquals(day, q.date)
        assertFalse(q.dateKnown)
        assertEquals(listOf(q), a.open)
        val monday = day.minusDays(3)
        val (b, payments) = StatsOps.resolveFirstDebit(a, q.id, monday)
        val p = payments.single()
        assertEquals(Triple(EventKind.PAYMENT, 3_100, monday), Triple(p.kind, p.amount, p.date))
        assertTrue(p.dateKnown)
        assertEquals(a.scans.single().id, p.scanId)
        assertTrue(b.open.isEmpty())
        // The next scan compares with the first one as before: unchanged debit, lower balance = one new payment.
        val (_, r2) = scan(b, 7_500, 2_500)
        assertEquals(listOf(EventKind.PAYMENT to 2_500), (r2 as RecordResult.Recorded).newEvents.map { it.kind to it.amount })

        val (skipped, none) = StatsOps.resolveFirstDebit(a, q.id, null)
        assertTrue(none.isEmpty())
        assertTrue(skipped.open.isEmpty())
        assertTrue(skipped.events.isEmpty())
    }

    @Test fun firstScanWithoutALastDebitAsksNothing() {
        assertTrue(scan(registered, 10_000, 0).first.open.isEmpty())
        assertTrue(scan(registered, 10_000, null).first.open.isEmpty())
    }

    /** Two payments between scans: the balance fell by more than the last debit, so one or a split is asked. */
    @Test fun twoPaymentsBetweenScansAreAskedAndSplit() {
        val a = baseline(scan(registered, 20_000, 1_000))
        val (b, r) = scan(a, 14_400, 2_500) // paid 3,10 then 2,50
        val q = (r as RecordResult.Recorded).newQuestions.single() as OpenItem.SeveralPayments
        assertEquals(5_600 to 2_500, q.amount to q.lastDebit)
        assertTrue(r.newEvents.isEmpty())
        val (split, payments) = StatsOps.resolveSeveral(b, q.id, listOf(3_100, 2_500))
        assertEquals(listOf(3_100, 2_500), payments.map { it.amount })
        assertTrue(payments.all { it.kind == EventKind.PAYMENT && it.dateKnown })
        assertTrue(split.open.isEmpty())
        val (one, single) = StatsOps.resolveSeveral(b, q.id, listOf(5_600))
        assertEquals(listOf(5_600), single.map { it.amount })
        assertEquals(1, one.events.size)
    }

    /** Movements that don't add up to the change are rejected. */
    @Test(expected = IllegalArgumentException::class)
    fun movementsMustAddUp() {
        val a = baseline(scan(registered, 5_000, 1_000))
        val (b, _) = scan(a, 9_400, 2_500)
        val q = b.open.single() as OpenItem.CreditAndPayment
        StatsOps.resolveMovements(b, q.id, listOf(Movement(EventKind.TOP_UP, 10_000, day), Movement(EventKind.PAYMENT, 2_500, day)))
    }

    /**
     * The case of 2026-11-24: 1,05 € paid in the evening (no scan), the next day 20 € topped up and 0,15 € Pfand back, then
     * scanned: +19,10 € with a new last debit of 1,05 €. The first suggestion is exactly what happened; the payment moves
     * to the evening before, and the meal question follows for it on that day.
     */
    @Test fun paymentTopUpAndPfandOnTwoDays() {
        val k = KnownAmounts(KnownAmounts.DEFAULT_TOP_UPS, KnownAmounts.DEFAULT_PFAND)
        val monday = day.minusDays(3) // Mon 2026-11-02
        val tuesday = monday.plusDays(1)
        val a = baseline(StatsOps.recordScan(registered, fp, 0, monday, 4_000, 3_100, k))
        val (b, r) = StatsOps.recordScan(a, fp, 0, tuesday, 23_100, 1_050, k)
        val q = (r as RecordResult.Recorded).newQuestions.single() as OpenItem.CreditAndPayment
        assertEquals(19_100, q.delta)
        val first = CreditDraft.suggestions(q.delta, q.lastDebit, CreditDraft.debitChanged(b, q.scanId), k, tuesday).first()
        assertEquals(listOf(EventKind.PAYMENT to 1_050, EventKind.TOP_UP to 20_000, EventKind.PFAND to 150), first.movements.map { it.kind to it.amount })
        assertEquals(listOf(monday, tuesday), CreditDraft.days(StatsOps.previousScanDate(b, q.scanId), q.date))
        val answer = first.movements.map { if (it.kind == EventKind.PAYMENT) it.copy(day = monday, dayChosen = true) else it }
        val (c, payments) = StatsOps.resolveMovements(b, q.id, answer)
        assertEquals(
            listOf(Triple(EventKind.PAYMENT, 1_050, monday), Triple(EventKind.TOP_UP, 20_000, tuesday), Triple(EventKind.PFAND, 150, tuesday)),
            c.events.map { Triple(it.kind, it.amount, it.date) },
        )
        assertTrue(c.events.all { it.dateKnown }) // no weekday between the scans; the payment's day was set
        assertEquals(listOf(1_050 to monday), payments.map { it.amount to it.date })
        val asked = StatsOps.askMeals(c, payments, emptyList())
        assertEquals(monday, (asked.open.single() as OpenItem.MealQuestion).date)
        // Drink 0,90 € + Pfand 0,15 €: only the drink is spending.
        val meal = MealRecord(extras = listOf(ExtraItem(Extras.DRINK, 900), ExtraItem(Extras.PFAND, 150)))
        val done = StatsOps.setMeal(asked, payments.single().id, meal)
        val paid = done.events.first { it.kind == EventKind.PAYMENT }
        assertEquals(150 to 900, paid.pfandPaid to paid.spent)
    }

    /** A movement whose day was left alone keeps the question's (un)known day. */
    @Test fun untouchedDaysKeepTheQuestionsDateKnown() {
        val a = baseline(scan(registered, 5_000, 1_000, day.minusDays(3))) // Monday
        val (b, _) = scan(a, 9_400, 2_500) // Thursday: a weekday skipped
        val q = b.open.single() as OpenItem.CreditAndPayment
        assertFalse(q.dateKnown)
        val (c, _) = StatsOps.resolveMovements(
            b, q.id, listOf(Movement(EventKind.TOP_UP, 10_000, day), Movement(EventKind.PAYMENT, 3_100, day.minusDays(1), dayChosen = true), Movement(EventKind.PAYMENT, 2_500, day)),
        )
        assertEquals(listOf(false, true, false), c.events.map { it.dateKnown })
    }

    @Test fun spreadingKeepsAtLeastAsManyPayments() {
        val event = CardEvent(1, 1, EventKind.PAYMENT, 5_600, day, false, payments = 3)
        val data = StatsData(events = listOf(event), nextId = 2)
        val meal = MealRecord(other = true)
        val two = StatsOps.splitPayment(data, 1, listOf(Triple(day.minusDays(1), 3_100, meal), Triple(day, 2_500, meal)))
        assertEquals(listOf(2, 1), two.events.map { it.payments })
        val one = StatsOps.splitPayment(data.copy(events = listOf(event.copy(payments = 2))), 1, listOf(Triple(day.minusDays(1), 3_100, meal), Triple(day, 2_500, meal)))
        assertEquals(listOf(1, 1), one.events.map { it.payments })
    }

    /** A card registered before the offer existed: its first scan's debit is offered later, once, until answered. */
    @Test fun theFirstDebitIsOfferedUntilAnswered() {
        val (a, _) = scan(registered, 10_000, 3_100)
        val old = a.copy(open = emptyList()) // as before 1.2.3: no question was made
        val (b, _) = scan(old, 6_900, 3_100, day.plusDays(1))
        val offered = StatsOps.offerFirstDebit(b)
        val q = offered.open.single() as OpenItem.FirstDebit
        assertEquals(3_100 to day, q.amount to q.date)
        assertEquals(offered, StatsOps.offerFirstDebit(offered)) // never twice
        // Recorded: a payment from the first scan; not offered again.
        val (recorded, _) = StatsOps.resolveFirstDebit(offered, q.id, day)
        val forgotten = recorded.copy(firstDebitDone = emptyList())
        assertEquals(forgotten, StatsOps.offerFirstDebit(forgotten))
        // Declined: remembered.
        val (declined, _) = StatsOps.resolveFirstDebit(offered, q.id, null)
        assertEquals(listOf(registered.card!!.hash), declined.firstDebitDone)
        assertEquals(declined, StatsOps.offerFirstDebit(declined))
        // Nothing to offer without a debit or a registered card.
        assertEquals(b.copy(scans = b.scans.map { it.copy(lastDebit = 0) }), StatsOps.offerFirstDebit(b.copy(scans = b.scans.map { it.copy(lastDebit = 0) })))
        assertEquals(StatsOps.unregister(b), StatsOps.offerFirstDebit(StatsOps.unregister(b)))
    }
}
