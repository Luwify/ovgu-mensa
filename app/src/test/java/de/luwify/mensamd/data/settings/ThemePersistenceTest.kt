package de.luwify.mensamd.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The stored theme must only ever change when the user picks one. These tests cover every way the
 * repository could change it by itself: other writes, restarts, reads, and unknown stored values.
 */
class ThemePersistenceTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Opens the file like a fresh process would, runs [block], and closes the store again. */
    private fun <T> withStore(file: File, block: suspend (SettingsRepository) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            block(SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun everyOtherSetting_leavesTheThemeAlone() {
        val file = File(tmp.root, "a.preferences_pb")
        withStore(file) { it.setThemeMode(ThemeMode.AMOLED) }
        withStore(file) { repo ->
            repo.setDefaultMensaId("herrenkrug")
            repo.setMensaOrder(listOf("stendal", "kellercafe"))
            repo.toggleFilter("vegan")
            repo.toggleFilter("vegan")
            repo.setCodesExcluded(setOf("a1", "g"), true)
            repo.clearExcludedCodes()
            repo.setPriceGroup(2)
            repo.setCompactMode(true)
            repo.setCompactMode(false)
            assertEquals(ThemeMode.AMOLED, repo.themeMode.first())
        }
    }

    @Test
    fun theme_survivesManyRestarts_forEveryMode() {
        for (mode in ThemeMode.entries) {
            val file = File(tmp.root, "restart-${mode.key}.preferences_pb")
            withStore(file) { it.setThemeMode(mode) }
            repeat(5) { assertEquals(mode, withStore(file) { repo -> repo.themeMode.first() }) }
        }
    }

    @Test
    fun readingNeverWritesTheFile() {
        val file = File(tmp.root, "readonly.preferences_pb")
        withStore(file) { it.setThemeMode(ThemeMode.SOFT) }
        val before = file.readBytes()
        repeat(3) { withStore(file) { repo -> repo.themeMode.first(); repo.compactMode.first(); repo.activeFilters.first() } }
        assertArrayEquals("a read-only session rewrote the preferences file", before, file.readBytes())
    }

    @Test
    fun unknownStoredValue_showsAsSystem_butIsNotRewritten() {
        val file = File(tmp.root, "garbage.preferences_pb")
        withStore(file) { repo ->
            repo.setThemeMode(ThemeMode.SOFT)
        }
        // pretend a newer/older app version stored something we don't know
        withStore(file) { repo ->
            val field = SettingsRepository::class.java.getDeclaredField("store").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val store = field.get(repo) as androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
            store.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    set(androidx.datastore.preferences.core.stringPreferencesKey("theme_mode"), "from-the-future")
                }
            }
        }
        val before = file.readBytes()
        assertEquals(ThemeMode.SYSTEM, withStore(file) { it.themeMode.first() })
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun everyModeHasAUniqueKey_andKeysRoundTrip() {
        assertEquals(ThemeMode.entries.size, ThemeMode.entries.map { it.key }.toSet().size)
        for (mode in ThemeMode.entries) assertEquals(mode, ThemeMode.fromKey(mode.key))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
    }

    @Test
    fun sourceCheck_onlyUserActionPathsWriteTheTheme() {
        fun src(vararg candidates: String) = candidates.map(::File).first { it.isDirectory }
        val root = src("src/main/java", "app/src/main/java")
        val users = root.walkTopDown().filter { it.extension == "kt" && it.readText().contains("setThemeMode") }
            .map { it.name }.toSortedSet()
        // repository (the single writer), the ViewModel wrapper, and the screen wiring of the sheet's chips
        assertEquals(sortedSetOf("MainScreen.kt", "MainViewModel.kt", "SettingsRepository.kt"), users)

        val repoText = File(root, "de/luwify/mensamd/data/settings/SettingsRepository.kt").readText()
        val writes = Regex("""\[themeKey\]\s*=""").findAll(repoText).count()
        assertEquals("exactly one place writes the theme key", 1, writes)
    }
}
