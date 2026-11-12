package de.luwify.mensamd.stats

import de.luwify.mensamd.data.settings.CardStatsSettings
import de.luwify.mensamd.model.LocalizedText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test fun aCardTapLoadsNoMenus_theNextVisitMatches() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            var loads = 0
            val make = {
                StatsRecorder(
                    store = StatsStore(File(tmp.root, "stats.json"), Dispatchers.Unconfined),
                    settings = MutableStateFlow(CardStatsSettings(enabled = true, detailed = true)),
                    mealContext = { MealContext("unten", 0) },
                    snapshot = {
                        loads++
                        DayMenu(menu)
                    },
                    scope = scope,
                    clock = { now.atZone(zone).toInstant().toEpochMilli() },
                    zone = { zone },
                )
            }
            val r = make()
            r.register(fp, 10_000, 0, null)
            now = now.plusHours(1)

            val result = withTimeout(5_000) { r.recordAsync(7, 6_900, 3_100, fp).await() }
            assertEquals(1, (result as RecordResult.Recorded).newEvents.size)
            assertEquals(7L, r.last.value?.seq)
            val payment = r.store.load().events.single()
            assertEquals(3_100, payment.amount)
            assertNull(payment.meal)
            assertEquals(0, loads)
            assertEquals(listOf(payment.id), r.store.load().pendingMatch)

            // Survives the process: the popup's scan is matched when the app is opened later.
            val later = make()
            later.matchPending()
            assertEquals(1, loads)
            val saved = later.store.load()
            assertEquals("Seelachs", saved.events.single().meal!!.dishes.single().name.de)
            assertEquals(emptyList<Long>(), saved.pendingMatch)

            later.matchPending() // nothing left: no second load
            assertEquals(1, loads)
        } finally {
            scope.cancel()
        }
    }

    @Test fun theCardIdIsWantedOnlyWithTheStatisticsOn() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val settings = MutableStateFlow(CardStatsSettings(enabled = false))
            val r = StatsRecorder(
                store = StatsStore(File(tmp.root, "stats.json"), Dispatchers.Unconfined),
                settings = settings,
                mealContext = { MealContext("unten", 0) },
                snapshot = { DayMenu(menu) },
                scope = scope,
            )
            assertEquals(false, r.wantsCardId())
            assertEquals(RecordResult.Off, r.recordAsync(1, 5_000, null, null).await())
            settings.value = CardStatsSettings(enabled = true)
            assertEquals(true, r.wantsCardId())
        } finally {
            scope.cancel()
        }
    }

    @Test fun withoutDetailedStatisticsNothingWaits_andWhatWaitedIsDropped() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val settings = MutableStateFlow(CardStatsSettings(enabled = true, detailed = true))
            var loads = 0
            val r = StatsRecorder(
                store = StatsStore(File(tmp.root, "stats.json"), Dispatchers.Unconfined),
                settings = settings,
                mealContext = { MealContext("unten", 0) },
                snapshot = {
                    loads++
                    DayMenu(menu)
                },
                scope = scope,
                clock = { now.atZone(zone).toInstant().toEpochMilli() },
                zone = { zone },
            )
            r.register(fp, 10_000, 0, null)
            now = now.plusHours(1)
            r.recordAsync(1, 6_900, 3_100, fp).await()
            assertEquals(1, r.store.load().pendingMatch.size)

            settings.value = CardStatsSettings(enabled = true, detailed = false)
            r.matchPending()
            assertEquals(0, loads)
            assertEquals(emptyList<Long>(), r.store.load().pendingMatch)

            now = now.plusHours(1)
            r.recordAsync(2, 3_800, 3_100, fp).await()
            assertEquals(emptyList<Long>(), r.store.load().pendingMatch)
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

    /**
     * Two payments between scans, the last one the price of a dish: the split puts the last debit in its own part, which
     * matches that dish alone (saved: one candidate, same day); the other part is asked.
     */
    @Test fun twoPaymentsWhereTheLastIsADishPrice() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val dishes = listOf(
                SnapDish("unten", LocalizedText("Linseneintopf"), listOf(250, 400, 500)),
                SnapDish("unten", LocalizedText("Seelachs"), listOf(330, 520, 620)),
            )
            val r = recorder(scope, dishes)
            r.register(fp, 20_000, 0, null)
            now = now.plusHours(1)
            val result = r.record(14_400, 2_500, fp) as RecordResult.Recorded // 3,10 + 2,50
            val q = result.newQuestions.single() as OpenItem.SeveralPayments
            var payments = emptyList<CardEvent>()
            r.store.update { d -> StatsOps.resolveSeveral(d, q.id, listOf(3_100, 2_500)).also { payments = it.second }.first }
            r.matchPayments(payments)
            val data = r.store.load()
            val (first, second) = data.events
            assertNull(first.meal) // 3,10: no dish costs that
            assertEquals("Linseneintopf", second.meal?.dishes?.single()?.name?.de)
            assertEquals(first.id, (data.open.single() as OpenItem.MealQuestion).eventId)
        } finally {
            scope.cancel()
        }
    }
}
