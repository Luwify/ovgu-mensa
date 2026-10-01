package de.luwify.mensamd.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun defaultMensa_isNullUntilSet_thenPersists() = runBlocking {
        val file = File(tmp.root, "settings.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            val repo = SettingsRepository(store)
            assertNull(repo.defaultMensaId.first())
            repo.setDefaultMensaId("herrenkrug")
            assertEquals("herrenkrug", repo.defaultMensaId.first())
        } finally {
            scope.shutDown()
        }
        // A fresh store on the same file sees the value (it really hit the disk).
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo2 = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope2) { file })
            assertEquals("herrenkrug", repo2.defaultMensaId.first())
        } finally {
            scope2.shutDown()
        }
    }

    @Test
    fun mensaOrder_isEmptyUntilSet_thenPersistsInOrder() = runBlocking {
        val file = File(tmp.root, "order.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(emptyList<String>(), repo.mensaOrder.first())
            repo.setMensaOrder(listOf("stendal", "herrenkrug", "kellercafe"))
            assertEquals(listOf("stendal", "herrenkrug", "kellercafe"), repo.mensaOrder.first())
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun filters_toggleOnAndOff_andPersist() = runBlocking {
        val file = File(tmp.root, "filters.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(emptySet<String>(), repo.activeFilters.first())
            repo.toggleFilter("vegan")
            repo.toggleFilter("schwein")
            assertEquals(setOf("vegan", "schwein"), repo.activeFilters.first())
            repo.toggleFilter("vegan")
            assertEquals(setOf("schwein"), repo.activeFilters.first())
            repo.toggleFilter("schwein")
            assertEquals(emptySet<String>(), repo.activeFilters.first())
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun excludedCodes_andPriceGroup_persist() = runBlocking {
        val file = File(tmp.root, "view.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(emptySet<String>(), repo.excludedCodes.first())
            assertEquals(0, repo.priceGroup.first())

            repo.setCodesExcluded(setOf("a1", "a2", "g"), true)
            repo.setCodesExcluded(setOf("a2"), false)
            assertEquals(setOf("a1", "g"), repo.excludedCodes.first())
            repo.setPriceGroup(2)
            assertEquals(2, repo.priceGroup.first())

            repo.clearExcludedCodes()
            assertEquals(emptySet<String>(), repo.excludedCodes.first())
            assertEquals(2, repo.priceGroup.first()) // clearing codes leaves the price group alone
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun compactMode_defaultsOff_andPersists() = runBlocking {
        val file = File(tmp.root, "compact.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(false, repo.compactMode.first())
            repo.setCompactMode(true)
            assertEquals(true, repo.compactMode.first())
            repo.setCompactMode(false)
            assertEquals(false, repo.compactMode.first())
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun themeMode_defaultsToSystem_andPersists() = runBlocking {
        val file = File(tmp.root, "theme.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
            repo.setThemeMode(ThemeMode.SOFT)
            assertEquals(ThemeMode.SOFT, repo.themeMode.first())
            assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("garbage")) // unknown stored value falls back
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun favorites_areAStringSet_soNamesWithCommasSurvive_andToggleIsAtomic() = runBlocking {
        val file = File(tmp.root, "fav.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(emptySet<String>(), repo.favorites.first())
            assertEquals(true, repo.toggleFavorite("reis, gemüse"))
            assertEquals(true, repo.toggleFavorite("schnitzel"))
            assertEquals(setOf("reis, gemüse", "schnitzel"), repo.favorites.first())
            assertEquals(false, repo.toggleFavorite("schnitzel"))
            assertEquals(setOf("reis, gemüse"), repo.favorites.first())
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun notifyMensen_defaultToTheDefaultMensa_untilChanged() = runBlocking {
        val file = File(tmp.root, "notify.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(true, repo.notificationsEnabled.first())
            assertEquals(emptySet<String>(), repo.notifyMensen.first())
            repo.setDefaultMensaId("herrenkrug")
            assertEquals(setOf("herrenkrug"), repo.notifyMensen.first())
            repo.setNotifyMensa("stendal", true)
            assertEquals(setOf("herrenkrug", "stendal"), repo.notifyMensen.first())
            // once chosen, a new default Mensa doesn't change the watched ones
            repo.setDefaultMensaId("kellercafe")
            assertEquals(setOf("herrenkrug", "stendal"), repo.notifyMensen.first())
            repo.setNotifyMensa("herrenkrug", false)
            assertEquals(setOf("stendal"), repo.notifyMensen.first())
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun notified_isRecordedAndPruned() = runBlocking {
        val file = File(tmp.root, "notified.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            val prune = de.luwify.mensamd.favorites.FavoriteNotifications::prune
            repo.recordNotified(setOf("2026-10-02|a|x"), java.time.LocalDate.of(2026, 10, 1), prune)
            assertEquals(setOf("2026-10-02|a|x"), repo.notifiedFavorites())
            repo.recordNotified(setOf("2026-10-06|a|y"), java.time.LocalDate.of(2026, 10, 5), prune)
            assertEquals(setOf("2026-10-06|a|y"), repo.notifiedFavorites())
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun filterOrder_isEmptyUntilDragged_thenMergedAndPersisted() = runBlocking {
        val file = File(tmp.root, "chips.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(emptyList<String>(), repo.filterOrder.first())
            val merge = de.luwify.mensamd.ui.DishFilters::mergeOrder
            repo.reorderFilters(listOf("vegan", ":kuchen", ":favoriten"), merge)
            repo.reorderFilters(listOf(":favoriten", "vegan"), merge)
            assertEquals(listOf(":favoriten", ":kuchen", "vegan"), repo.filterOrder.first())
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun notifyTime_defaultsTo18_thenPersists() = runBlocking {
        val file = File(tmp.root, "time.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(java.time.LocalTime.of(18, 0), repo.notifyTime.first())
            repo.setNotifyTime(java.time.LocalTime.of(7, 45))
            assertEquals(java.time.LocalTime.of(7, 45), repo.notifyTime.first())
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun customTheme_defaults_thenPersists() = runBlocking {
        val file = File(tmp.root, "custom.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(CustomThemeSpec(), repo.customTheme.first())
            repo.setCustomTheme(CustomThemeSpec(CustomBase.BLACK, 0x12AB34, glow = true))
            assertEquals(CustomThemeSpec(CustomBase.BLACK, 0x12AB34, glow = true), repo.customTheme.first())
            repo.setThemeMode(ThemeMode.CUSTOM)
            assertEquals(ThemeMode.CUSTOM, repo.themeMode.first())
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun customTheme_oldHueOnlySetting_becomesThatSwatchColor() = runBlocking {
        val file = File(tmp.root, "legacy.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            store.edit { it[androidx.datastore.preferences.core.intPreferencesKey("custom_theme_hue")] = 265 }
            val repo = SettingsRepository(store)
            assertEquals(CustomThemeSpec.DEFAULT_ACCENT, repo.customTheme.first().accent) // 265 = the violet default
            store.edit { it[androidx.datastore.preferences.core.intPreferencesKey("custom_theme_hue")] = 0 }
            assertEquals(0xD03939, repo.customTheme.first().accent) // red swatch
            // saving the new format drops the old key
            repo.setCustomTheme(CustomThemeSpec(accent = 0x112233))
            assertEquals(null, store.data.first()[androidx.datastore.preferences.core.intPreferencesKey("custom_theme_hue")])
        } finally {
            scope.shutDown()
        }
    }

    @Test
    fun customOverrides_encodeDecodeAndPersist() = runBlocking {
        val o = mapOf(ColorRole.CARD to 0x1A1A1A, ColorRole.BACKGROUND to 0x000000, ColorRole.FAVORITE to 0xFF6B9D)
        val text = CustomThemeSpec.encodeOverrides(o)
        assertEquals("bg=000000,card=1A1A1A,heart=FF6B9D", text)
        assertEquals(o, CustomThemeSpec.decodeOverrides(text))
        // broken or unknown entries are skipped, not fatal
        assertEquals(mapOf(ColorRole.CARD to 0x123456), CustomThemeSpec.decodeOverrides("card=123456,nope=FFFFFF,bg=12,tag=ZZZZZZ,,x"))
        assertEquals(emptyMap<ColorRole, Int>(), CustomThemeSpec.decodeOverrides(null))

        val file = File(tmp.root, "overrides.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            repo.setCustomTheme(CustomThemeSpec(overrides = o))
            assertEquals(o, repo.customTheme.first().overrides)
            repo.setCustomTheme(CustomThemeSpec())
            assertEquals(emptyMap<ColorRole, Int>(), repo.customTheme.first().overrides)
        } finally {
            scope.shutDown()
        }
    }
}
