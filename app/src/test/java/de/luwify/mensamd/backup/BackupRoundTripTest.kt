package de.luwify.mensamd.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.luwify.mensamd.data.settings.SettingsRepository
import de.luwify.mensamd.favorites.FavoriteInfo
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.stats.RegisteredCard
import de.luwify.mensamd.stats.Scan
import de.luwify.mensamd.stats.StatsData
import de.luwify.mensamd.stats.StatsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Settings + statistics through the real stores, the file format and back into fresh stores. */
class BackupRoundTripTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo(scope: CoroutineScope, name: String) =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "$name.preferences_pb") })

    @Test fun everythingSurvivesExportAndImport() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val a = repo(scope, "a")
            a.setDefaultMensaId("herrenkrug")
            a.setPriceGroup(2)
            a.toggleFavorite("seelachs", FavoriteInfo(LocalizedText("Seelachs"), emptyList()))
            a.setStatsEnabled(true)
            a.setCardReminderTime(LocalTime.of(14, 30))
            a.setPfandAmounts(listOf(250, 2_000))
            a.setNotificationPermissionExplained() // device state: not in the backup
            val statsA = StatsStore(File(tmp.root, "a-stats.json"), Dispatchers.IO)
            statsA.update { StatsData(card = RegisteredCard("00", "h", 1), scans = listOf(Scan(1, 1, LocalDate.of(2026, 11, 5), "h", 100)), nextId = 2) }
            val backup = BackupRepository(a, statsA, { "de" }, {}, "1.2.0").current()
            assertTrue("notify_permission_explained" !in backup.settings)
            val text = BackupFormat.encode(backup)

            val b = repo(scope, "b")
            b.setCompactMode(true) // replaced: not in the backup
            b.setNotificationPermissionExplained() // device state: kept
            val statsB = StatsStore(File(tmp.root, "b-stats.json"), Dispatchers.IO)
            var language: String? = null
            val restored = (BackupFormat.decode(text) as BackupResult.Ok).backup
            BackupRepository(b, statsB, { "system" }, { language = it }, null).restore(restored)

            assertEquals("herrenkrug", b.defaultMensaId.first())
            assertEquals(2, b.priceGroup.first())
            assertEquals(setOf("seelachs"), b.favorites.first())
            assertEquals("Seelachs", b.favoriteInfo.first().getValue("seelachs").name.de)
            val card = b.cardStats.first()
            assertTrue(card.enabled)
            assertEquals(LocalTime.of(14, 30), card.reminderTime)
            assertEquals(listOf(250, 2_000), card.pfand)
            assertEquals(false, b.compactMode.first())
            assertEquals(true, b.notificationPermissionExplained.first())
            assertEquals(statsA.load(), StatsStore(File(tmp.root, "b-stats.json"), Dispatchers.IO).load())
            assertEquals("de", language)
            assertEquals(backup.settings, b.exportAll())
        } finally {
            scope.cancel()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun aNewerStatisticsFileIsNeverReplaced() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val file = File(tmp.root, "stats.json").apply { writeText("""{"version":99}""") }
            BackupRepository(repo(scope, "c"), StatsStore(file, Dispatchers.IO), { "system" }, {}, null).restore(Backup(emptyMap(), StatsData(), null))
        } finally {
            scope.cancel()
        }
    }
}

class SettingsKeyTypesTest {
    /** Every key the repository declares is in KEY_TYPES with its type, so a mistyped backup value is dropped. */
    @Test fun keyTableMatchesTheSource() {
        val root = listOf("src/main/java", "app/src/main/java").map(::File).first { it.isDirectory }
        val text = File(root, "de/luwify/mensamd/data/settings/SettingsRepository.kt").readText()
        val names = mapOf("boolean" to "bool", "int" to "int", "string" to "string", "stringSet" to "strings")
        val declared = Regex("""\b(boolean|int|string|stringSet)PreferencesKey\("([a-z_]+)"\)""").findAll(text)
            .associate { it.groupValues[2] to names.getValue(it.groupValues[1]) }
        assertEquals(declared, de.luwify.mensamd.data.settings.KEY_TYPES)
    }

    @Test fun mistypedValueIsDropped() = runBlocking {
        val tmp = java.nio.file.Files.createTempDirectory("keys").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp, "s.preferences_pb") })
            repo.importAll(mapOf("price_group" to SettingValue.Text("1"), "future_key" to SettingValue.Text("x"), "compact_mode" to SettingValue.Bool(true)))
            assertEquals(0, repo.priceGroup.first())
            assertEquals(true, repo.compactMode.first())
            assertEquals(SettingValue.Text("x"), repo.exportAll()["future_key"])
        } finally {
            scope.cancel()
            tmp.deleteRecursively()
        }
    }
}
