package de.luwify.mensamd.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Two DataStores on one file in one process crash ("multiple DataStores active") or lose writes.
 * Both activities (main and card popup) must get the settings from the one AppContainer.
 */
class SingleDataStoreTest {
    private val root = listOf("src/main/java", "app/src/main/java").map(::File).first { it.isDirectory }
    private val sources = root.walkTopDown().filter { it.extension == "kt" }.map { it.name to it.readText() }.toList()

    @Test
    fun eachDataStoreIsDeclaredOnce_andNoneIsCreatedByHand() {
        // `settings` (SettingsRepository) and `updates` (the updater's own store, UpdateSettings): one declaration each.
        val names = sources.flatMap { (_, text) ->
            Regex("""\bpreferencesDataStore\(\s*name\s*=\s*"([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()
        }
        assertEquals(sources.sumOf { Regex("""\bpreferencesDataStore\(""").findAll(it.second).count() }, names.size)
        assertEquals(listOf("settings", "updates"), names.sorted())
        assertEquals(0, sources.sumOf { Regex("""PreferenceDataStoreFactory""").findAll(it.second).count() })
    }

    @Test
    fun onlyTheContainerBuildsTheRepository() {
        val users = sources.filter { Regex("""\bsettingsRepository\(""").containsMatchIn(it.second) }.map { it.first }.toSortedSet()
        assertEquals(sortedSetOf("AppContainer.kt", "SettingsRepository.kt"), users)
    }
}
