package de.luwify.mensamd.backup

import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.stats.CardEvent
import de.luwify.mensamd.stats.EatenDish
import de.luwify.mensamd.stats.EventKind
import de.luwify.mensamd.stats.KnownDish
import de.luwify.mensamd.stats.MealRecord
import de.luwify.mensamd.stats.OpenItem
import de.luwify.mensamd.stats.RegisteredCard
import de.luwify.mensamd.stats.Scan
import de.luwify.mensamd.stats.StatsData
import de.luwify.mensamd.stats.StatsMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupFormatTest {
    private val day = LocalDate.of(2026, 11, 5)
    private val stats = StatsData(
        card = RegisteredCard("00112233445566778899aabbccddeeff", "abc123", 42),
        scans = listOf(Scan(1, 10, day, "abc123", 5_370, 5_600)),
        events = listOf(
            CardEvent(
                2, 1, EventKind.PAYMENT, 5_600, day, true,
                MealRecord(listOf(EatenDish("seelachs", LocalizedText("Seelachs", "Pollock"), "unicampus-unten", 3_100)), listOf("@salad", "Smoothie"), 2_500),
            ),
        ),
        open = listOf(OpenItem.SeveralPayments(3, 1, day, true, 5_600, 2_500)),
        customExtras = listOf("Smoothie"),
        customDishes = listOf(KnownDish("bowl", LocalizedText("Bowl"), 4_000)),
        nextId = 4,
    )
    private val settings = mapOf(
        "default_mensa_id" to SettingValue.Text("unicampus-unten"),
        "price_group" to SettingValue.IntValue(1),
        "stats_enabled" to SettingValue.Bool(true),
        "favorites" to SettingValue.Texts(setOf("seelachs, dillsauce", "curry")),
        "favorite_info" to SettingValue.Text("""{"curry":{"name":{"de":"Curry","en":"Curry"},"tags":[]}}"""),
        "some_long" to SettingValue.LongValue(5L),
    )

    @Test fun roundTrip() {
        val backup = Backup(settings, stats, "de", appVersion = "1.2.0")
        val text = BackupFormat.encode(backup, Instant.parse("2026-11-05T12:00:00Z"))
        val r = BackupFormat.decode(text) as BackupResult.Ok
        assertEquals(settings, r.backup.settings)
        assertEquals(stats, r.backup.stats)
        assertEquals("de", r.backup.language)
        assertEquals("2026-11-05T12:00:00Z", r.backup.exportedAt)
        assertEquals("1.2.0", r.backup.appVersion)
    }

    @Test fun deviceStateIsNeverBackedUp() {
        val text = BackupFormat.encode(Backup(settings + ("notified_favorites" to SettingValue.Texts(setOf("x"))), stats, null))
        assertFalse(text.contains("notified_favorites"))
    }

    @Test fun notABackup() {
        assertEquals(BackupResult.NotABackup, BackupFormat.decode("""{"format":"stundenplanmd-backup","version":1}"""))
        assertEquals(BackupResult.NotABackup, BackupFormat.decode("[]"))
        assertTrue(BackupFormat.decode("{nope") is BackupResult.Broken)
    }

    @Test fun newerEnvelopeOrStatsAreTooNew() {
        assertEquals(BackupResult.TooNew, BackupFormat.decode("""{"format":"mensamd-backup","version":${BackupFormat.VERSION + 1}}"""))
        val newerStats = """{"format":"mensamd-backup","version":1,"settings":{},"stats":{"version":${StatsMigrations.CURRENT + 1}}}"""
        assertEquals(BackupResult.TooNew, BackupFormat.decode(newerStats))
    }

    @Test fun unknownSettingTypeIsSkipped() {
        val text = """{"format":"mensamd-backup","version":1,"settings":{"a":{"type":"bytes","value":"x"},"b":{"type":"bool","value":true}},"stats":{"version":1}}"""
        val r = BackupFormat.decode(text) as BackupResult.Ok
        assertEquals(mapOf("b" to SettingValue.Bool(true)), r.backup.settings)
        assertEquals(StatsData(), r.backup.stats)
    }

    @Test fun fileName() = assertEquals("mensamd-backup-2026-11-05.json", BackupFormat.fileName(day))
}
