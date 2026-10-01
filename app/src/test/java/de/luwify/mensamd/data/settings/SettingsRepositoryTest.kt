package de.luwify.mensamd.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
}
