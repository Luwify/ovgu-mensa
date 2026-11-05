package de.luwify.mensamd.data.settings

import de.luwify.mensamd.stats.KnownAmounts
import java.time.LocalTime

/**
 * The card statistics settings (gear on the card screen). [detailed] (matching payments to dishes) only works with
 * [enabled]; the reminder too. Amounts are thousandths of a euro, like the card.
 */
data class CardStatsSettings(
    val enabled: Boolean = false,
    val detailed: Boolean = false,
    val reminder: Boolean = false,
    val reminderTime: LocalTime = DEFAULT_REMINDER_TIME,
    val topUps: List<Int> = DEFAULT_TOP_UPS,
    val pfand: List<Int> = DEFAULT_PFAND,
) {
    val detailedActive: Boolean get() = enabled && detailed
    val reminderActive: Boolean get() = enabled && reminder
    val known: KnownAmounts get() = KnownAmounts(topUps, pfand)

    companion object {
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(15, 0)
        val DEFAULT_TOP_UPS: List<Int> = KnownAmounts.DEFAULT_TOP_UPS
        val DEFAULT_PFAND: List<Int> = KnownAmounts.DEFAULT_PFAND
    }
}
