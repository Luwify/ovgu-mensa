package de.luwify.mensamd.ui

import androidx.annotation.StringRes
import de.luwify.mensamd.R

/** The tabs of the settings page, in display order. */
enum class SettingsTab(@StringRes val label: Int) {
    /** Everything but the card: price group, layout, theme, favorites, notifications, version, backup, about. */
    GENERAL(R.string.settings_tab_general),

    /** The card statistics: statistics, detailed statistics, "Meine Karte", reminder, amounts, delete. */
    CARD(R.string.settings_tab_card);

    companion object {
        /** The tabs shown: the card tab only on a phone that can read the card (like the card icon in the top bar). */
        fun shown(cardAvailable: Boolean): List<SettingsTab> = if (cardAvailable) entries else listOf(GENERAL)

        /** The page index of [tab] among [shown]; a tab that isn't shown falls back to the first page. */
        fun pageOf(tab: SettingsTab, shown: List<SettingsTab>): Int = shown.indexOf(tab).coerceAtLeast(0)
    }
}

/** What the overlays in `MensaRoot` do when a card is held while the app is open (reader mode). */
data class ScanRoute(val showCard: Boolean, val showSettings: Boolean)

object SettingsNav {
    /**
     * A card scan starts. While "Karte registrieren" waits for this card, the settings stay on top (the card tab shows
     * the registration) and the card screen keeps whatever it was. Otherwise the scan's result must be seen: the card
     * screen opens and the settings close (they lie above the card screen).
     */
    fun onScan(showCard: Boolean, showSettings: Boolean, registering: Boolean): ScanRoute =
        if (showSettings && registering) ScanRoute(showCard = showCard, showSettings = true)
        else ScanRoute(showCard = true, showSettings = false)
}
