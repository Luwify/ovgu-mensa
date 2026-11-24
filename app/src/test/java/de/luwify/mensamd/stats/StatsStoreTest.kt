package de.luwify.mensamd.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
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

    @Test fun aVersion2FileLoadsWithNothingPending() {
        val data = decodeStats(statsJson.parseToJsonElement("""{"version":2,"nextId":5}""") as JsonObject)
        assertEquals(StatsMigrations.CURRENT, data.version)
        assertEquals(emptyList<Long>(), data.pendingMatch)
        assertEquals(5L, data.nextId)
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

    /** Version 3 -> 4: a dish without Mensa and tags is a hand-named one (no tag information), the others stay as they were. */
    @Test fun aVersion3FileMarksDishesWithoutMensaAndTagsAsOwn() {
        val v3 = """{"version":3,"nextId":9,"customDishes":[{"key":"shawarma","name":{"de":"Shawarma","en":"Shawarma"},"price":3200}],
            "events":[
              {"id":1,"scanId":1,"kind":"payment","amount":3200,"date":"2026-11-09","dateKnown":true,
               "meal":{"dishes":[{"key":"shawarma","name":{"de":"Shawarma","en":"Shawarma"},"price":3200}]}},
              {"id":2,"scanId":2,"kind":"payment","amount":4400,"date":"2026-11-09","dateKnown":true,
               "meal":{"dishes":[{"key":"burger","name":{"de":"Burger","en":"Burger"},"mensaId":"unicampus-oben","price":4400},
                                 {"key":"curry","name":{"de":"Curry","en":"Curry"},"price":3100,"tags":[{"key":"vegan","label":"Vegan"}]}]}},
              {"id":3,"scanId":3,"kind":"top_up","amount":20000,"date":"2026-11-09","dateKnown":true}]}"""
        val data = decodeStats(statsJson.parseToJsonElement(v3) as JsonObject)
        assertEquals(StatsMigrations.CURRENT, data.version)
        val (shawarma) = data.events[0].meal!!.dishes
        assertTrue(shawarma.own)
        assertFalse(shawarma.tagsKnown)
        val (burger, curry) = data.events[1].meal!!.dishes
        assertFalse(burger.own) // a menu dish without tags is known to carry none
        assertFalse(curry.own) // tags but no Mensa (old known-dish list): its tags stay information
        assertTrue(curry.tagsKnown)
        assertEquals(9L, data.nextId)
        assertEquals(3, data.events.size)
        assertTrue(data.customDishes.single().own)
        assertEquals(null, data.customDishes.single().mensaId)
    }

    @Test fun aMealOfAnOlderFileWithoutDishesIsKept() {
        val v3 = """{"version":3,"events":[{"id":1,"scanId":1,"kind":"payment","amount":900,"date":"2026-11-09","dateKnown":true,"meal":{"other":true}}]}"""
        val data = decodeStats(statsJson.parseToJsonElement(v3) as JsonObject)
        assertTrue(data.events.single().meal!!.other)
    }

    @Test fun theNewFieldsSurviveAWriteAndRead() {
        val dish = EatenDish("wrap", de.luwify.mensamd.model.LocalizedText("Wrap"), "kellercafe", 3_000, 1, listOf(de.luwify.mensamd.model.Tag("vegan", "Vegan")), null, own = true)
        val data = StatsData(
            events = listOf(CardEvent(1, 1, EventKind.PAYMENT, 3_000, java.time.LocalDate.of(2026, 11, 9), true, MealRecord(listOf(dish)))),
            customDishes = listOf(KnownDish("wrap", dish.name, 3_000, dish.tags, "kellercafe")),
        )
        assertEquals(data.copy(version = StatsMigrations.CURRENT), decodeStats(encodeStats(data)))
    }

    /** Version 4 -> 5: only a new open item type; a version 4 file loads unchanged, and the new item survives a write and read. */
    @Test fun aVersion4FileLoadsAndTheFirstDebitQuestionRoundTrips() {
        val v4 = """{"version":4,"nextId":3,"scans":[{"id":1,"at":0,"date":"2026-11-09","card":"h","balance":10000,"lastDebit":3100}],
            "open":[{"type":"several","id":2,"scanId":1,"date":"2026-11-09","dateKnown":true,"amount":5600,"lastDebit":2500}]}"""
        val data = decodeStats(statsJson.parseToJsonElement(v4) as JsonObject)
        assertEquals(StatsMigrations.CURRENT, data.version)
        assertEquals(OpenItem.SeveralPayments(2, 1, java.time.LocalDate.of(2026, 11, 9), true, 5_600, 2_500), data.open.single())
        val withFirst = data.copy(open = data.open + OpenItem.FirstDebit(3, 1, java.time.LocalDate.of(2026, 11, 9), amount = 3_100), nextId = 4)
        assertEquals(withFirst, decodeStats(encodeStats(withFirst)))
        assertTrue(encodeStats(withFirst).toString().contains("\"first_debit\""))
    }

    /** Version 5 -> 6: the extras' shared total becomes one amount per extra (whole cents, the first gets the remainder). */
    @Test fun aVersion5MealSplitsItsExtrasTotal() {
        val v5 = """{"version":5,"nextId":5,"events":[
            {"id":1,"scanId":1,"kind":"payment","amount":5250,"date":"2026-11-12","dateKnown":true,
             "meal":{"dishes":[{"key":"seelachs","name":{"de":"Seelachs","en":"Pollock"},"mensaId":"unicampus-unten","price":3100}],
                     "extras":["@salad","@drink","Smoothie"],"extrasAmount":2150}},
            {"id":2,"scanId":1,"kind":"payment","amount":900,"date":"2026-11-12","dateKnown":true,"meal":{"other":true}},
            {"id":3,"scanId":1,"kind":"payment","amount":4000,"date":"2026-11-12","dateKnown":true,
             "meal":{"dishes":[{"key":"x","name":{"de":"X","en":"X"},"price":3000}],"extrasAmount":1000}},
            {"id":4,"scanId":1,"kind":"top_up","amount":20000,"date":"2026-11-12","dateKnown":true}]}"""
        val data = decodeStats(statsJson.parseToJsonElement(v5) as JsonObject)
        assertEquals(StatsMigrations.CURRENT, data.version)
        val meal = data.events[0].meal!!
        assertEquals(listOf(ExtraItem("@salad", 730), ExtraItem("@drink", 710), ExtraItem("Smoothie", 710)), meal.extras)
        assertEquals(5_250, meal.dishes.sumOf { it.price * it.count } + meal.extras.sumOf { it.amount })
        assertEquals("seelachs", meal.dishes.single().key)
        assertEquals(MealRecord(other = true), data.events[1].meal)
        assertEquals(listOf(ExtraItem(Extras.OTHER, 1_000)), data.events[2].meal!!.extras) // a total without a key
        assertNull(data.events[3].meal)
        assertEquals(data, decodeStats(encodeStats(data)))
        assertFalse(encodeStats(data).toString().contains("extrasAmount"))
    }

    /** Version 6 -> 7: the dessert extras become hand-named dessert dishes; Pudding and Rote Grütze are saved for the search. */
    @Test fun dessertExtrasBecomeDessertDishes() {
        val v6 = """{"version":6,"nextId":5,"customDishes":[{"key":"bagel","name":{"de":"Bagel","en":"Bagel"},"price":3200}],"events":[
            {"id":1,"scanId":1,"kind":"payment","amount":3400,"date":"2026-11-12","dateKnown":true,
             "meal":{"dishes":[{"key":"seelachs","name":{"de":"Seelachs","en":"Pollock"},"price":2500}],
                     "extras":[{"key":"@pudding","amount":500,"grams":180},{"key":"@salad","amount":400}]}},
            {"id":2,"scanId":1,"kind":"payment","amount":900,"date":"2026-11-13","dateKnown":true,
             "meal":{"extras":[{"key":"@rote_gruetze","amount":600},{"key":"@dessert","amount":300}]}}]}"""
        val data = decodeStats(statsJson.parseToJsonElement(v6) as JsonObject)
        assertEquals(StatsMigrations.CURRENT, data.version)
        val first = data.events[0].meal!!
        assertEquals(listOf(ExtraItem(Extras.SALAD, 400)), first.extras)
        val pudding = first.dishes.last()
        // Then 7 -> 8: the worked-out weight (180 g) is dropped, the saved Pudding has no price per 100 g: the usual 0,60 €.
        assertEquals(listOf("pudding", 500, null, 600, true, true), listOf(pudding.key, pudding.price, pudding.grams, pudding.per100, pudding.own, pudding.dessert))
        val second = data.events[1].meal!!
        assertTrue(second.extras.isEmpty())
        assertEquals(listOf("Rote Grütze" to 600, "Dessert" to 300), second.dishes.map { it.name.de to it.price })
        assertTrue(second.dishes.all { it.dessert })
        // Saved for the search: Pudding and Rote Grütze (the generic Dessert is built in), by weight, as desserts.
        assertEquals(listOf("bagel", "pudding", "rote grütze"), data.customDishes.map { it.key })
        assertTrue(data.customDishes.drop(1).all { it.byWeight && it.dessert })
        // The sums still match the payments.
        for (e in data.events) assertEquals(e.amount, e.meal!!.dishes.sumOf { it.price * it.count } + e.meal!!.extras.sumOf { it.amount })
        assertEquals(data, decodeStats(encodeStats(data)))
    }

    /**
     * Version 7 -> 8: weighed dishes keep the amount paid and get a price per 100 g instead of the old rounded weight:
     * the dessert's saved one, else 0,60 €; a fixed-price dessert none; a menu dish the round price its weight came from.
     */
    @Test fun weightsBecomePricesPer100g() {
        val v7 = """{"version":7,"nextId":5,"customDishes":[
            {"key":"obstsalat","name":{"de":"Obstsalat","en":"Obstsalat"},"price":1000,"byWeight":true,"per100":600,"dessert":true,"own":true},
            {"key":"rote grütze","name":{"de":"Rote Grütze","en":"Rote Grütze"},"price":600,"byWeight":true,"dessert":true,"own":true},
            {"key":"pudding","name":{"de":"Pudding","en":"Pudding"},"price":750,"byWeight":true,"per100":750,"dessert":true,"own":true},
            {"key":"kuchen","name":{"de":"Kuchen","en":"Kuchen"},"price":1200,"dessert":true,"own":true}],"events":[
            {"id":1,"scanId":1,"kind":"payment","amount":1970,"date":"2026-11-20","dateKnown":true,
             "meal":{"dishes":[{"key":"obstsalat","name":{"de":"Obstsalat","en":"Obstsalat"},"price":1000,"grams":170,"own":true,"dessert":true},
                               {"key":"rote grütze","name":{"de":"Rote Grütze","en":"Rote Grütze"},"price":970,"grams":160,"own":true,"dessert":true}]}},
            {"id":2,"scanId":1,"kind":"payment","amount":4150,"date":"2026-11-21","dateKnown":true,
             "meal":{"dishes":[{"key":"farfalle","name":{"de":"Farfalle","en":"Farfalle"},"mensaId":"unicampus-unten","price":2350,"grams":290},
                               {"key":"dessert","name":{"de":"Dessert","en":"Dessert"},"price":300,"own":true,"dessert":true},
                               {"key":"pudding","name":{"de":"Pudding","en":"Pudding"},"price":300,"grams":40,"own":true,"dessert":true},
                               {"key":"kuchen","name":{"de":"Kuchen","en":"Kuchen"},"price":1200,"own":true,"dessert":true}]}}]}"""
        val data = decodeStats(statsJson.parseToJsonElement(v7) as JsonObject)
        assertEquals(StatsMigrations.CURRENT, data.version)
        val (obst, gruetze) = data.events[0].meal!!.dishes
        assertEquals(listOf(1_000, 600, 167), listOf(obst.price, obst.per100, obst.weight))
        assertEquals(listOf(970, 600, 162), listOf(gruetze.price, gruetze.per100, gruetze.weight))
        val (farfalle, dessert, pudding, kuchen) = data.events[1].meal!!.dishes
        assertEquals(800, farfalle.per100) // 0,80 € and 0,81 € both gave "290 g": the round one
        assertEquals(600, dessert.per100)
        assertEquals(750, pudding.per100) // its saved price
        assertNull(kuchen.per100)
        assertTrue(data.events.flatMap { it.meal!!.dishes }.all { it.grams == null })
        // The amounts paid are untouched.
        for (e in data.events) assertEquals(e.amount, e.meal!!.dishes.sumOf { it.price * it.count })
        assertEquals(data, decodeStats(encodeStats(data)))
        // Reopened in the question screen: 0,60 € and the weight to the gram.
        val items = MealDraft.fromRecord(data.events[0].meal, 1_970, data.customDishes)
        assertEquals(listOf(600 to 167, 600 to 162), items.map { it.per100 to it.grams })
    }

    /**
     * Version 8 -> 9: nothing to convert. An open "top-up and payment?" question of a version 8 file (the shape 1.3.0 wrote)
     * loads unchanged and is answered with the movements of "Was ist passiert?"; a meal with Pfand survives a write and read.
     */
    @Test fun aVersion8CreditQuestionIsAnsweredWithMovements() {
        val v8 = """{"version":8,"nextId":4,"card":{"salt":"s","hash":"h","since":0},"scans":[
            {"id":1,"at":0,"date":"2026-11-23","card":"h","balance":4000,"lastDebit":3100},
            {"id":2,"at":1,"date":"2026-11-24","card":"h","balance":23100,"lastDebit":1050}],
            "open":[{"type":"credit","id":3,"scanId":2,"date":"2026-11-24","dateKnown":true,"delta":19100,"lastDebit":1050}]}"""
        val data = decodeStats(statsJson.parseToJsonElement(v8) as JsonObject)
        assertEquals(9, StatsMigrations.CURRENT)
        assertEquals(StatsMigrations.CURRENT, data.version)
        val q = data.open.single() as OpenItem.CreditAndPayment
        assertEquals(OpenItem.CreditAndPayment(3, 2, LocalDate.of(2026, 11, 24), true, 19_100, 1_050), q)
        val monday = LocalDate.of(2026, 11, 23)
        val (answered, payments) = StatsOps.resolveMovements(
            data, q.id,
            listOf(
                Movement(EventKind.PAYMENT, 1_050, monday, dayChosen = true),
                Movement(EventKind.TOP_UP, 20_000, q.date),
                Movement(EventKind.PFAND, 150, q.date),
            ),
        )
        assertTrue(answered.open.isEmpty())
        assertEquals(monday, payments.single().date)
        val withMeal = StatsOps.setMeal(answered, payments.single().id, MealRecord(extras = listOf(ExtraItem(Extras.DRINK, 900), ExtraItem(Extras.PFAND, 150))))
        assertEquals(withMeal, decodeStats(encodeStats(withMeal)))
        assertTrue(encodeStats(withMeal).toString().contains("\"@pfand\""))
    }

}
