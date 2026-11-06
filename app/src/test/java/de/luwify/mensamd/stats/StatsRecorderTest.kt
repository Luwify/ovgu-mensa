package de.luwify.mensamd.stats

import de.luwify.mensamd.data.settings.CardStatsSettings
import de.luwify.mensamd.model.LocalizedText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    private fun recorder(scope: CoroutineScope, dishes: List<SnapDish> = menu, missing: List<String> = emptyList()): StatsRecorder = StatsRecorder(
        store = StatsStore(File(tmp.root, "stats.json"), Dispatchers.Unconfined),
        settings = MutableStateFlow(CardStatsSettings(enabled = true, detailed = true)),
        mealContext = { MealContext("unten", 0) },
        snapshot = { DayMenu(dishes, missing) },
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

    @Test fun theScanResultDoesNotWaitForTheMenus() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val menusLoaded = CompletableDeferred<Unit>()
            val r = StatsRecorder(
                store = StatsStore(File(tmp.root, "stats.json"), Dispatchers.Unconfined),
                settings = MutableStateFlow(CardStatsSettings(enabled = true, detailed = true)),
                mealContext = { MealContext("unten", 0) },
                snapshot = {
                    menusLoaded.await()
                    DayMenu(menu)
                },
                scope = scope,
                clock = { now.atZone(zone).toInstant().toEpochMilli() },
                zone = { zone },
            )
            r.register(fp, 10_000, 0, null)
            now = now.plusHours(1)

            val result = withTimeout(5_000) { r.recordAsync(7, 6_900, 3_100, fp).await() } // the menus never came yet
            assertEquals(1, (result as RecordResult.Recorded).newEvents.size)
            assertEquals(7L, r.last.value?.seq)
            val payment = r.store.load().events.single()
            assertEquals(3_100, payment.amount)
            assertNull(payment.meal)

            menusLoaded.complete(Unit)
            val saved = withTimeout(5_000) { r.store.data.first { it?.events?.single()?.meal != null } }
            assertEquals("Seelachs", saved!!.events.single().meal!!.dishes.single().name.de)
        } finally {
            scope.cancel()
        }
    }

    @Test fun theSoupAtItsMenuPriceIsAskedNotSaved() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val soup = SnapDish("unicampus-unten", LocalizedText("Tagessuppe"), listOf(60, 80, 100), byWeight = true)
            val r = recorder(scope, menu + soup)
            r.register(fp, 10_000, 0, null)
            now = now.plusHours(1)
            r.record(9_400, 600, fp) // 0,60 €, the soup's price per 100 g
            val data = r.store.load()
            assertNull(data.events.single().meal)
            assertTrue(data.open.single() is OpenItem.MealQuestion)
        } finally {
            scope.cancel()
        }
    }

    @Test fun aSingleMatchIsAskedWhileAMensaIsMissing_andTheQuestionNamesIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val r = recorder(scope, missing = listOf("stendal", "herrenkrug"))
            r.register(fp, 10_000, 0, null)
            now = now.plusHours(1)
            r.record(6_900, 3_100, fp) // Seelachs would be saved with every menu at hand
            val data = r.store.load()
            assertNull(data.events.single().meal)
            val question = data.open.single() as OpenItem.MealQuestion
            assertEquals(listOf("stendal", "herrenkrug"), question.missingMensen)
            assertEquals(menu, question.dishes)
        } finally {
            scope.cancel()
        }
    }

    @Test fun aFailingSnapshotCountsAsAllMissing() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val r = StatsRecorder(
                store = StatsStore(File(tmp.root, "stats.json"), Dispatchers.Unconfined),
                settings = MutableStateFlow(CardStatsSettings(enabled = true, detailed = true)),
                mealContext = { MealContext("unten", 0) },
                snapshot = { error("no menus") },
                scope = scope,
                clock = { now.atZone(zone).toInstant().toEpochMilli() },
                zone = { zone },
            )
            r.register(fp, 10_000, 0, null)
            now = now.plusHours(1)
            r.record(6_900, 3_100, fp)
            assertTrue((r.store.load().open.single() as OpenItem.MealQuestion).missingMensen.isNotEmpty())
        } finally {
            scope.cancel()
        }
    }
}
