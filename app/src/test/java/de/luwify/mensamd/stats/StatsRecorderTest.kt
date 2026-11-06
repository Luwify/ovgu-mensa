package de.luwify.mensamd.stats

import de.luwify.mensamd.data.settings.CardStatsSettings
import de.luwify.mensamd.model.LocalizedText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

class StatsRecorderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val zone = ZoneId.of("Europe/Berlin")
    private val fp = CardIdentity.fingerprint(byteArrayOf(4, 1, 2, 3, 4, 5, 6))
    private var now = LocalDateTime.of(2026, 11, 5, 13, 0)
    private val menu = listOf(SnapDish("unten", LocalizedText("Seelachs"), listOf(310, 520, 620)))

    private fun recorder(scope: CoroutineScope, dishes: List<SnapDish> = menu): StatsRecorder = StatsRecorder(
        store = StatsStore(File(tmp.root, "stats.json"), Dispatchers.Unconfined),
        settings = MutableStateFlow(CardStatsSettings(enabled = true, detailed = true)),
        mealContext = { MealContext("unten", 0) },
        snapshot = { dishes },
        scope = scope,
        clock = { now.atZone(zone).toInstant().toEpochMilli() },
        zone = { zone },
    )

    @Test fun singleMatchIsSavedOnlyWhenBothScansAreOnTheSameDay() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val r = recorder(scope)
            r.register(fp, 10_000, 0, null)
            now = now.plusHours(1)
            r.record(6_900, 3_100, fp) // same day: saved directly
            assertEquals("Seelachs", r.store.load().events.single().meal?.dishes?.single()?.name?.de)
            now = now.plusDays(1)
            r.record(3_800, 3_100, fp) // previous scan yesterday: asked
            val data = r.store.load()
            assertNull(data.events.last().meal)
            assertTrue(data.open.single() is OpenItem.MealQuestion)
        } finally {
            scope.cancel()
        }
    }

    @Test fun aMatchInAnotherMensaTooIsAskedNotSaved() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val both = listOf(
                SnapDish("unten", LocalizedText("Portion Pommes Frites"), listOf(200, 380, 520)),
                SnapDish("oben", LocalizedText("Chili sin carne"), listOf(200, 380, 520)),
            )
            val r = recorder(scope, both)
            r.register(fp, 10_000, 0, null)
            now = now.plusHours(1)
            r.record(8_000, 2_000, fp)
            val data = r.store.load()
            assertNull(data.events.single().meal)
            assertEquals(both, (data.open.single() as OpenItem.MealQuestion).dishes)
        } finally {
            scope.cancel()
        }
    }

    @Test fun registeringTheSameCardAgainKeepsItsRegistration() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val r = recorder(scope)
            r.register(fp, 10_000, 0, null)
            val card = r.store.load().card
            r.setRegistering(true)
            now = now.plusHours(1)
            val result = r.record(6_900, 3_100, fp)
            assertEquals(card, r.store.load().card)
            assertTrue((result as RecordResult.Recorded).newEvents.isNotEmpty()) // not a new baseline
        } finally {
            scope.cancel()
        }
    }
}
