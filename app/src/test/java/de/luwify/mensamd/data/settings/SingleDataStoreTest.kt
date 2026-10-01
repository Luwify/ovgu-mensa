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
    fun exactlyOneDataStoreIsDeclared_andNoneIsCreatedByHand() {
        assertEquals(1, sources.sumOf { Regex("""\bpreferencesDataStore\(""").findAll(it.second).count() })
        assertEquals(0, sources.sumOf { Regex("""PreferenceDataStoreFactory""").findAll(it.second).count() })
    }

    @Test
    fun onlyTheContainerBuildsTheRepository() {
        val users = sources.filter { Regex("""\bsettingsRepository\(""").containsMatchIn(it.second) }.map { it.first }.toSortedSet()
        assertEquals(sortedSetOf("AppContainer.kt", "SettingsRepository.kt"), users)
    }
}
