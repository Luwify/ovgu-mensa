package de.luwify.mensamd.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class StatsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(file: File) = StatsStore(file, Dispatchers.Unconfined) { 42 }

    private val sample = StatsData(
        scans = listOf(Scan(1, 10, LocalDate.of(2026, 11, 5), "h", 5_370, 5_600)),
        events = listOf(CardEvent(2, 1, EventKind.PAYMENT, 5_600, LocalDate.of(2026, 11, 5), true)),
        open = listOf(
            OpenItem.SeveralPayments(3, 1, LocalDate.of(2026, 11, 5), true, 5_600, 2_500),
            OpenItem.CreditAndPayment(4, 1, LocalDate.of(2026, 11, 5), false, -300, 5_600),
            OpenItem.MealQuestion(5, 1, LocalDate.of(2026, 11, 5), true, 2, 5_600, listOf(SnapDish("m", de.luwify.mensamd.model.LocalizedText("A"), listOf(310, 520, 620)))),
        ),
        customExtras = listOf("Smoothie"),
        nextId = 6,
    )

    @Test fun roundTripThroughTheFile() = runTest {
        val file = File(tmp.root, "stats.json")
        store(file).update { sample }
        assertEquals(sample, store(file).load())
    }

    @Test fun missingFileIsEmpty() = runTest {
        assertEquals(StatsData(), store(File(tmp.root, "none.json")).load())
    }

    @Test fun newerFileIsNeverWritten() = runTest {
        val file = File(tmp.root, "stats.json")
        val newer = """{"version":${StatsMigrations.CURRENT + 1},"scans":[],"future":true}"""
        file.writeText(newer)
        val s = store(file)
        assertEquals(StatsData(), s.load())
        assertTrue(s.problem.value is StatsProblem.TooNew)
        s.update { sample }
        assertEquals(newer, file.readText())
    }

    @Test fun brokenFileIsKeptAside() = runTest {
        val file = File(tmp.root, "stats.json")
        file.writeText("{not json")
        val s = store(file)
        assertEquals(StatsData(), s.load())
        assertEquals(StatsProblem.Broken("stats.json.broken-42"), s.problem.value)
        assertEquals("{not json", File(tmp.root, "stats.json.broken-42").readText())
    }

    @Test fun migrationStepsRunInOrder() {
        val steps: Map<Int, (JsonObject) -> JsonObject> = mapOf(
            1 to { o -> JsonObject(o + ("a" to JsonPrimitive(1))) },
            2 to { o -> JsonObject(o + ("b" to JsonPrimitive(2))) },
        )
        val out = StatsMigrations.migrate(JsonObject(mapOf("version" to JsonPrimitive(1))), steps, current = 3)
        assertEquals(JsonPrimitive(3), out["version"])
        assertEquals(JsonPrimitive(1), out["a"])
        assertEquals(JsonPrimitive(2), out["b"])
    }

    @Test(expected = StatsTooNewException::class)
    fun migrateRejectsNewer() {
        StatsMigrations.migrate(JsonObject(mapOf("version" to JsonPrimitive(StatsMigrations.CURRENT + 1))))
    }

    @Test fun cardHashIsStableAndSalted() {
        val fp = CardIdentity.fingerprint(byteArrayOf(4, 1, 2, 3, 4, 5, 6))
        assertEquals(fp, CardIdentity.fingerprint(byteArrayOf(4, 1, 2, 3, 4, 5, 6)))
        val a = CardIdentity.hash("00", fp)
        assertEquals(a, CardIdentity.hash("00", fp))
        assertTrue(a != CardIdentity.hash("01", fp))
        assertEquals(64, a.length)
    }

    @Test fun version1FileLoadsWithoutTheWeightFields() {
        val v1 = """{"version":1,"events":[{"id":1,"scanId":1,"kind":"payment","amount":3100,"date":"2026-11-05","dateKnown":true,
            "meal":{"dishes":[{"key":"curry","name":{"de":"Curry","en":"Curry"},"price":3100}]}}]}"""
        val data = decodeStats(statsJson.parseToJsonElement(v1) as JsonObject)
        assertEquals(StatsMigrations.CURRENT, encodeStats(data)["version"]!!.toString().toInt())
        assertEquals(null, data.events.single().meal!!.dishes.single().grams)
    }
}
