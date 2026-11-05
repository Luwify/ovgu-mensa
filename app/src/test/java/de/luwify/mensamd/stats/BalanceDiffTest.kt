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
    private val known = KnownAmounts(KnownAmounts.DEFAULT_TOP_UPS, listOf(250, 2_000))

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

    @Test fun upByKnownPfand() = assertEquals(Outcome.Events(listOf(PFAND to 2_000)), derive(5_370, 5_600, 7_370, 5_600))

    @Test fun upByUnknownAmountAsksForTopUpPlusPayment() =
        assertEquals(Outcome.AskCredit(14_400, 5_600, null), derive(5_370, 5_600, 19_770, 5_600))

    @Test fun upWithChangedDebitIsTopUpPlusThatPayment() =
        // 20 € top-up, 5.60 € lunch: +14.40 €, and the last debit changed to 5.60 €.
        assertEquals(Outcome.Events(listOf(TOP_UP to 20_000, PAYMENT to 5_600)), derive(5_370, 3_100, 19_770, 5_600))

    @Test fun upWithChangedDebitButUnknownCreditAsks() =
        assertEquals(Outcome.AskCredit(14_000, 5_600, null), derive(5_370, 3_100, 19_370, 5_600))

    @Test fun upByTopUpAmountWithChangedDebitIsNotAPlainTopUp() =
        // +10 € but a 10 € payment happened too (last debit changed): a 20 € top-up.
        assertEquals(Outcome.Events(listOf(TOP_UP to 20_000, PAYMENT to 10_000)), derive(5_000, 3_100, 15_000, 10_000))

    @Test fun downByLessThanLastDebitIsCreditPlusPayment() =
        // Pfand 2 € back, then a 5.60 € lunch: -3.60 €.
        assertEquals(Outcome.Events(listOf(PFAND to 2_000, PAYMENT to 5_600)), derive(10_000, 3_100, 6_400, 5_600))

    @Test fun downByLessWithUnknownCreditAsks() =
        assertEquals(Outcome.AskCredit(-3_000, 5_600, null), derive(10_000, 3_100, 7_000, 5_600))

    @Test fun zeroChangeWithChangedDebitIsCreditPlusPayment() {
        // 2 € Pfand back and a 2 € payment: a known credit, so automatic.
        assertEquals(Outcome.Events(listOf(PFAND to 2_000, PAYMENT to 2_000)), derive(10_000, 3_100, 10_000, 2_000))
        assertEquals(Outcome.AskCredit(0, 1_500, null), derive(10_000, 3_100, 10_000, 1_500))
    }

    @Test fun creditOptionsLeaveAPositivePayment() {
        assertEquals(listOf(20_000 to TOP_UP, 30_000 to TOP_UP, 50_000 to TOP_UP), BalanceDiff.creditOptions(14_400, known))
        assertEquals(known.credits, BalanceDiff.creditOptions(-3_000, known))
    }

    @Test fun pfandEqualToATopUpCountsAsTopUp() {
        val k = KnownAmounts(listOf(10_000), listOf(10_000, 250))
        assertEquals(TOP_UP, k.creditKind(10_000))
        assertEquals(listOf(250 to PFAND, 10_000 to TOP_UP), k.credits)
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
