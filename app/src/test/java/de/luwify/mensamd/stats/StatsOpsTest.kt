package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalizedText
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

    private fun scan(data: StatsData, balance: Int, debit: Int?, date: LocalDate = day, card: String = fp) =
        StatsOps.recordScan(data, card, 0, date, balance, debit, known)

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
        val (a, _) = scan(registered, 10_000, 3_100)
        assertEquals(RecordResult.Unchanged, scan(a, 10_000, 3_100).second)
        // The next day it is stored (the reminder needs to know the card was scanned that day).
        assertTrue(scan(a, 10_000, 3_100, day.plusDays(1)).second is RecordResult.Recorded)
    }

    @Test fun severalPaymentsSplitOrOne() {
        val (a, _) = scan(registered, 10_000, 1_000)
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
        val (a, _) = scan(registered, 10_000, 1_000)
        val (b, r) = scan(a, 4_400, 2_500)
        StatsOps.resolveSeveral(b, (r as RecordResult.Recorded).newQuestions.single().id, listOf(3_000, 2_500))
    }

    @Test fun unknownIncreaseResolvedAsTopUpPlusPayment() {
        val (a, _) = scan(registered, 5_370, 5_600)
        val (b, r) = scan(a, 19_770, 5_600)
        val q = (r as RecordResult.Recorded).newQuestions.single() as OpenItem.CreditAndPayment
        val (c, payments) = StatsOps.resolveCredit(b, q.id, 20_000)
        assertEquals(listOf(EventKind.TOP_UP to 20_000, EventKind.PAYMENT to 5_600), c.events.map { it.kind to it.amount })
        assertEquals(5_600, payments.single().amount)
        val (d, _) = StatsOps.resolveCredit(b, q.id, null)
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
        val notToday = StatsOps.markDateUnknown(asked, payment.id)
        assertFalse(notToday.events.single().dateKnown)
        assertNull(notToday.events.single().meal)
    }

    @Test fun deleteAndRestoreEvent() {
        val (a, _) = scan(registered, 10_000, 0)
        val (b, _) = scan(a, 6_900, 3_100)
        val event = b.events.single()
        val deleted = StatsOps.deleteEvent(b, event.id)
        assertTrue(deleted.events.isEmpty())
        assertEquals(b.events, StatsOps.restoreEvent(deleted, event).events)
    }

    @Test fun customExtrasAreUniqueAndNeverBuiltInKeys() {
        var d = StatsOps.addCustomExtra(StatsData(), " Smoothie ")
        d = StatsOps.addCustomExtra(d, "smoothie")
        d = StatsOps.addCustomExtra(d, "@salad")
        assertEquals(listOf("Smoothie"), d.customExtras)
    }

    @Test fun unregisterKeepsHistory() {
        val (a, _) = scan(registered, 10_000, 0)
        val u = StatsOps.unregister(a)
        assertNull(u.card)
        assertEquals(a.scans, u.scans)
    }
}
