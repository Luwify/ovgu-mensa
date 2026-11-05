package de.luwify.mensamd.stats

import de.luwify.mensamd.stats.BalanceDiff.Outcome
import de.luwify.mensamd.stats.EventKind.PAYMENT
import de.luwify.mensamd.stats.EventKind.PFAND
import de.luwify.mensamd.stats.EventKind.TOP_UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BalanceDiffTest {
    private val known = KnownAmounts(KnownAmounts.DEFAULT_TOP_UPS, KnownAmounts.DEFAULT_PFAND)

    private fun derive(oldBalance: Int, oldDebit: Int?, newBalance: Int, newDebit: Int?) =
        BalanceDiff.derive(oldBalance, oldDebit, newBalance, newDebit, known)

    @Test fun unchangedIsNothing() = assertEquals(Outcome.Nothing, derive(5_370, 5_600, 5_370, 5_600))

    @Test fun downByLastDebitIsOnePayment() =
        assertEquals(Outcome.Events(listOf(PAYMENT to 3_100)), derive(10_000, 5_600, 6_900, 3_100))

    @Test fun samePaymentTwiceInARowIsStillOnePayment() =
        assertEquals(Outcome.Events(listOf(PAYMENT to 3_100)), derive(10_000, 3_100, 6_900, 3_100))

    @Test fun downByMoreIsSeveralPayments() =
        assertEquals(Outcome.AskSeveral(5_600, 2_500), derive(10_000, 1_000, 4_400, 2_500))

    @Test fun cardWithoutLastDebitCountsOnePayment() =
        assertEquals(Outcome.Events(listOf(PAYMENT to 4_400)), derive(10_000, null, 5_600, null))

    @Test fun upByKnownTopUp() = assertEquals(Outcome.Events(listOf(TOP_UP to 20_000)), derive(5_370, 5_600, 25_370, 5_600))

    @Test fun upByAPfandSum() = assertEquals(Outcome.Events(listOf(PFAND to 330)), derive(5_370, 5_600, 5_700, 5_600))

    @Test fun upByUnknownAmountAsksForTopUpPlusPayment() =
        assertEquals(Outcome.AskCredit(14_400, 5_600, null), derive(5_370, 5_600, 19_770, 5_600))

    @Test fun upWithChangedDebitIsAskedWithTheTopUpPreselected() =
        // 20 € top-up, 5.60 € lunch: +14.40 €, and the last debit changed to 5.60 €: asked, 20 € suggested.
        assertEquals(Outcome.AskCredit(14_400, 5_600, 20_000), derive(5_370, 3_100, 19_770, 5_600))

    @Test fun upWithChangedDebitButUnknownCreditAsks() =
        assertEquals(Outcome.AskCredit(14_000, 5_600, null), derive(5_370, 3_100, 19_370, 5_600))

    @Test fun upByTopUpAmountWithChangedDebitIsAsked() =
        // +10 € but the last debit changed: a payment happened too, so not a plain 10 € top-up (20 € suggested).
        assertEquals(Outcome.AskCredit(10_000, 10_000, 20_000), derive(5_000, 3_100, 15_000, 10_000))

    @Test fun upByAPfandSumWithChangedDebitIsAskedNotPfand() =
        // +0.33 € looks like Pfand, but the last debit changed: top-up + payment, asked (3.43 € is no top-up amount).
        assertEquals(Outcome.AskCredit(330, 3_100, null), derive(5_000, 2_500, 5_330, 3_100))

    @Test fun upByAPfandSumWithUnchangedDebitIsPfand() =
        assertEquals(Outcome.Events(listOf(PFAND to 650)), derive(5_000, 3_100, 5_650, 3_100))

    @Test fun upByATopUpAmountWithUnchangedDebitIsATopUp() =
        assertEquals(Outcome.Events(listOf(TOP_UP to 10_000)), derive(5_000, 3_100, 15_000, 3_100))

    @Test fun upByNeitherWithUnchangedDebitIsAsked() {
        assertEquals(Outcome.AskCredit(7_000, 3_100, null), derive(5_000, 3_100, 12_000, 3_100)) // over 5 €, no top-up
        assertEquals(Outcome.AskCredit(170, 3_100, null), derive(5_000, 3_100, 5_170, 3_100)) // 0.17 €: no bottle sum
    }

    @Test fun firstScanWithoutAnOldDebitCountsAsUnchanged() =
        assertEquals(Outcome.Events(listOf(PFAND to 250)), derive(5_000, null, 5_250, 3_100))

    @Test fun downByLessThanLastDebitIsCreditPlusPayment() =
        // Two bottles back (0.25 + 0.25), then a 5.60 € lunch: -5.10 €.
        assertEquals(Outcome.Events(listOf(PFAND to 500, PAYMENT to 5_600)), derive(10_000, 3_100, 4_900, 5_600))

    @Test fun downByLessWithUnknownCreditAsks() =
        // Down 3 € with a last debit of 8.10 €: 5.10 € were credited, over 5 € and no top-up amount.
        assertEquals(Outcome.AskCredit(-3_000, 8_100, null), derive(10_000, 3_100, 7_000, 8_100))

    @Test fun zeroChangeWithChangedDebitIsCreditPlusPayment() {
        // 1.50 € Pfand back and a 1.50 € coffee: a Pfand sum, so automatic.
        assertEquals(Outcome.Events(listOf(PFAND to 1_500, PAYMENT to 1_500)), derive(10_000, 3_100, 10_000, 1_500))
        // Over 5 €: not a Pfand return, so asked.
        assertEquals(Outcome.AskCredit(0, 5_510, null), derive(10_000, 3_100, 10_000, 5_510))
    }

    @Test fun creditOptionsLeaveAPositivePayment() {
        assertEquals(listOf(20_000 to TOP_UP, 30_000 to TOP_UP, 50_000 to TOP_UP), BalanceDiff.creditOptions(14_400, known))
        assertEquals(KnownAmounts.DEFAULT_TOP_UPS.map { it to TOP_UP }, BalanceDiff.creditOptions(-3_000, known))
    }

    @Test fun pfandSumsOfAnyBottles() {
        val k = KnownAmounts(KnownAmounts.DEFAULT_TOP_UPS, KnownAmounts.DEFAULT_PFAND)
        // single bottles, several of one kind, mixed
        listOf(80, 150, 250, 160, 500, 330, 650, 230, 480, 5_000).forEach { assertEquals("$it", PFAND, k.creditKind(it)) }
        // not a sum of 0.08 / 0.15 / 0.25, or over 5 €
        // 0.67 € is the largest amount no combination makes; 0.68 € and everything up to 5 € in whole cents is one.
        assertEquals(PFAND, k.creditKind(680))
        listOf(5, 10, 70, 90, 100, 170, 670, 5_010, 5_250).forEach { assertEquals("$it", null, k.creditKind(it)) }
        assertTrue(k.isPfandSum(5_000))
        assertFalse(k.isPfandSum(0))
        assertFalse(k.isPfandSum(-80))
    }

    @Test fun topUpWinsOverAnEqualPfandSum() {
        val k = KnownAmounts(listOf(5_000, 10_000), KnownAmounts.DEFAULT_PFAND)
        assertEquals(TOP_UP, k.creditKind(5_000)) // 20 x 0.25 €, but also a top-up amount
        assertEquals(PFAND, k.creditKind(4_750))
        assertEquals(listOf(5_000 to TOP_UP, 10_000 to TOP_UP), k.credits)
    }

    @Test fun editedPfandListAndEmptyList() {
        assertEquals(PFAND, KnownAmounts(emptyList(), listOf(100)).creditKind(300))
        assertEquals(null, KnownAmounts(emptyList(), listOf(100)).creditKind(250))
        assertEquals(null, KnownAmounts(emptyList(), emptyList()).creditKind(250))
    }

    @Test fun dateKnownSameDayNextDayAndOverWeekend() {
        val thu = LocalDate.of(2026, 11, 5)
        assertTrue(BalanceDiff.dateKnown(thu, thu))
        assertTrue(BalanceDiff.dateKnown(thu, thu.plusDays(1))) // Friday
        assertTrue(BalanceDiff.dateKnown(thu.plusDays(1), thu.plusDays(4))) // Friday -> Monday
        assertFalse(BalanceDiff.dateKnown(thu, thu.plusDays(4))) // Thursday -> Monday: Friday skipped
        assertFalse(BalanceDiff.dateKnown(thu.minusDays(3), thu)) // Monday -> Thursday
    }

    @Test fun splitMustAddUp() {
        assertTrue(BalanceDiff.validSplit(5_600, listOf(3_100, 2_500)))
        assertFalse(BalanceDiff.validSplit(5_600, listOf(3_100, 2_400)))
        assertFalse(BalanceDiff.validSplit(5_600, listOf(5_600)))
        assertFalse(BalanceDiff.validSplit(5_600, listOf(6_600, -1_000)))
    }
}
