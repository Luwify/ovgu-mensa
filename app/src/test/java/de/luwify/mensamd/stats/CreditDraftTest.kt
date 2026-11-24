package de.luwify.mensamd.stats

import de.luwify.mensamd.stats.EventKind.PAYMENT
import de.luwify.mensamd.stats.EventKind.PFAND
import de.luwify.mensamd.stats.EventKind.TOP_UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CreditDraftTest {
    private val known = KnownAmounts(KnownAmounts.DEFAULT_TOP_UPS, KnownAmounts.DEFAULT_PFAND)
    private val tue = LocalDate.of(2026, 11, 24)

    private fun suggest(delta: Int, lastDebit: Int?, changed: Boolean?) =
        CreditDraft.suggestions(delta, lastDebit, changed, known, tue).map { s -> s.movements.map { it.kind to it.amount } }

    @Test fun restAndCompleteness() {
        val list = listOf(Movement(PAYMENT, 1_050, tue), Movement(TOP_UP, 20_000, tue))
        assertEquals(18_950, CreditDraft.net(list))
        assertEquals(150, CreditDraft.rest(19_100, list)) // a credit of 0,15 € is missing
        assertFalse(CreditDraft.complete(19_100, list))
        assertTrue(CreditDraft.complete(19_100, list + Movement(PFAND, 150, tue)))
        assertEquals(-1_050, CreditDraft.rest(18_950 - 1_050, list)) // a payment is missing
        assertFalse(CreditDraft.complete(0, emptyList()))
        assertFalse(CreditDraft.complete(0, listOf(Movement(TOP_UP, 0, tue))))
    }

    /** The case that started it: +19,10 € with a new last debit of 1,05 €. */
    @Test fun lastDebitPlusTopUpPlusPfandComesFirst() {
        val s = suggest(19_100, 1_050, true)
        assertEquals(listOf(PAYMENT to 1_050, TOP_UP to 20_000, PFAND to 150), s.first())
        assertTrue(CreditDraft.suggestions(19_100, 1_050, true, known, tue).first().usesLastDebit)
        assertTrue(s.size <= CreditDraft.MAX_SUGGESTIONS)
        // A payment happened, so nothing without one is offered; and the last one was 1,05 €, so no single 0,90 € payment.
        assertTrue(s.all { list -> list.any { it.first == PAYMENT } })
        assertTrue(s.none { it == listOf(TOP_UP to 20_000, PAYMENT to 900) })
        assertEquals(listOf(TOP_UP to 30_000, PAYMENT to 9_850, PAYMENT to 1_050), s[1])
    }

    /** Mixed case 2: 10 € topped up, lunch 3,10 € and coffee 1,20 € (the last debit): +5,70 €. */
    @Test fun topUpAndTwoPaymentsSplitsOffTheLastDebit() {
        val s = suggest(5_700, 1_200, true)
        assertEquals(listOf(TOP_UP to 10_000, PAYMENT to 3_100, PAYMENT to 1_200), s.first())
    }

    /** Mixed case 3: 0,50 € Pfand back and 5,60 € lunch (the last debit): −5,10 €. */
    @Test fun pfandAndAPaymentWithADrop() {
        val s = suggest(-5_100, 5_600, true)
        assertEquals(listOf(PAYMENT to 5_600, PFAND to 500), s.first())
    }

    /** The last debit didn't change: a top-up plus Pfand without a payment first. */
    @Test fun withoutAPaymentFirstWhenTheLastDebitStayed() {
        val s = suggest(20_150, 3_100, false)
        assertEquals(listOf(TOP_UP to 20_000, PFAND to 150), s.first())
    }

    @Test fun noDuplicatesAndOnlyPositiveAmounts() {
        val all = CreditDraft.suggestions(7_000, 3_000, true, known, tue, max = 50)
        val keys = all.map { s -> s.movements.map { it.kind to it.amount }.sortedWith(compareBy({ it.first }, { it.second })) }
        assertEquals(keys.distinct(), keys)
        assertTrue(all.flatMap { it.movements }.all { it.amount > 0 })
        all.forEach { assertTrue(CreditDraft.complete(7_000, it.movements)) }
    }

    @Test fun daysFromThePreviousScan() {
        assertEquals(listOf(tue), CreditDraft.days(null, tue))
        assertEquals(listOf(tue), CreditDraft.days(tue, tue))
        assertEquals(listOf(tue.minusDays(1), tue), CreditDraft.days(tue.minusDays(1), tue))
        // From Friday to Tuesday: Friday, Monday, Tuesday (the weekend left out).
        assertEquals(listOf(tue.minusDays(4), tue.minusDays(1), tue), CreditDraft.days(tue.minusDays(4), tue))
    }

    @Test fun prefillOnlyWithTheRightSign() {
        assertEquals(150, CreditDraft.prefill(PFAND, 150))
        assertNull(CreditDraft.prefill(PFAND, 19_100)) // more than one Pfand return
        assertEquals(19_100, CreditDraft.prefill(TOP_UP, 19_100))
        assertNull(CreditDraft.prefill(PAYMENT, 150))
        assertEquals(1_050, CreditDraft.prefill(PAYMENT, -1_050))
    }

    @Test fun debitChangedFromTheScanChain() {
        val d = LocalDate.of(2026, 11, 23)
        val data = StatsData(scans = listOf(Scan(1, 0, d, "h", 4_000, 3_100), Scan(2, 0, tue, "h", 23_100, 1_050), Scan(3, 0, tue, "h", 23_100, 1_050)))
        assertNull(CreditDraft.debitChanged(data, 1))
        assertEquals(true, CreditDraft.debitChanged(data, 2))
        assertEquals(false, CreditDraft.debitChanged(data, 3))
    }
}
