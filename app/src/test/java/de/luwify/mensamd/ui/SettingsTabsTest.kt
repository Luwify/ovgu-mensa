package de.luwify.mensamd.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTabsTest {
    @Test
    fun `both tabs with a card reader, general first`() {
        val shown = SettingsTab.shown(cardAvailable = true)
        assertEquals(listOf(SettingsTab.GENERAL, SettingsTab.CARD), shown)
        assertEquals(0, SettingsTab.pageOf(SettingsTab.GENERAL, shown))
        assertEquals(1, SettingsTab.pageOf(SettingsTab.CARD, shown))
    }

    @Test
    fun `without NFC only the general tab, a card request lands there`() {
        val shown = SettingsTab.shown(cardAvailable = false)
        assertEquals(listOf(SettingsTab.GENERAL), shown)
        assertEquals(0, SettingsTab.pageOf(SettingsTab.CARD, shown))
    }

    @Test
    fun `a scan while registering keeps the settings and leaves the card screen as it was`() {
        assertEquals(ScanRoute(showCard = false, showSettings = true), SettingsNav.onScan(showCard = false, showSettings = true, registering = true))
        assertEquals(ScanRoute(showCard = true, showSettings = true), SettingsNav.onScan(showCard = true, showSettings = true, registering = true))
    }

    @Test
    fun `any other scan shows the card screen and closes the settings`() {
        assertEquals(ScanRoute(showCard = true, showSettings = false), SettingsNav.onScan(showCard = false, showSettings = true, registering = false))
        assertEquals(ScanRoute(showCard = true, showSettings = false), SettingsNav.onScan(showCard = true, showSettings = true, registering = false))
        assertEquals(ScanRoute(showCard = true, showSettings = false), SettingsNav.onScan(showCard = false, showSettings = false, registering = true))
        assertEquals(ScanRoute(showCard = true, showSettings = false), SettingsNav.onScan(showCard = false, showSettings = false, registering = false))
    }
}
