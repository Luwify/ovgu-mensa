package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The stored file (or a backup) was written by a newer app version: it is never overwritten or downgraded. */
class StatsTooNewException(val version: Int) : Exception("statistics version $version is newer than ${StatsMigrations.CURRENT}")

/**
 * Schema versions of [StatsData]. Any change of the stored shape: raise [CURRENT], add the step `STEPS[old]`
 * (old -> old + 1, on the raw JSON) and a test in StatsMigrationsTest. Never drop or rename a stored field without a
 * step. Version 1 = the first release of the statistics (2026-11). Version 2 = dishes paid by weight (`SnapDish.byWeight`,
 * `EatenDish.grams`) and `MealQuestion.missingMensen`: all optional, nothing to convert. Version 3 = `StatsData.pendingMatch`:
 * optional, nothing to convert. Version 4 = `EatenDish.own`, `KnownDish.mensaId`/`own`: a stored dish
 * without a Mensa and without tags (named by hand, or a menu dish from the old known-dish list) becomes `own`, so its
 * missing tags count as "no information" and not as "not vegan". Version 5 = the open item `first_debit`
 * ([OpenItem.FirstDebit]): a new item type, nothing to convert (an older app can't read it, so the version guards it).
 * Version 6 = extras with their own amount ([ExtraItem]): a meal's `extras` (keys) and their shared `extrasAmount` become
 * one item per key, the total split evenly in whole cents (the first gets what doesn't divide); a total without a key
 * becomes "Sonstiges". Also new in 6, optional: `KnownDish.byWeight`/`per100` (a hand-named dish paid by weight).
 * Version 7 = `CardEvent.payments` (a payment standing for at least two), `EatenDish.dessert`, `KnownDish.dessert`:
 * desserts are dishes now. The dessert extras of a meal (`@dessert`, `@pudding`, `@rote_gruetze`) become hand-named dessert
 * dishes with the same amount (and weight); "Pudding" and "Rote Grütze" are also saved as dessert dishes by weight, so
 * the search offers them (the generic "Dessert" is built in). Also new in 7, optional: `StatsData.firstDebitDone`.
 */
object StatsMigrations {
    const val CURRENT = 7

    /** n -> n + 1. */
    private val STEPS: Map<Int, (JsonObject) -> JsonObject> = mapOf(1 to { it }, 2 to { it }, 3 to ::markUnknownDishesOwn, 4 to { it }, 5 to ::extrasWithAmounts, 6 to ::dessertsAsDishes)

    /** 3 -> 4: see the class comment. Every other part of the document is kept as it is. */
    private fun markUnknownDishesOwn(obj: JsonObject): JsonObject {
        val events = obj["events"] as? JsonArray ?: return obj
        fun JsonObject.noTags() = (this["tags"] as? JsonArray)?.isEmpty() != false
        fun JsonObject.noMensa() = this["mensaId"].let { it == null || it is JsonNull }
        fun dish(d: JsonElement): JsonElement =
            if (d is JsonObject && d.noMensa() && d.noTags()) JsonObject(d + ("own" to JsonPrimitive(true))) else d
        fun event(e: JsonElement): JsonElement {
            val meal = (e as? JsonObject)?.get("meal") as? JsonObject ?: return e
            val dishes = meal["dishes"] as? JsonArray ?: return e
            return JsonObject(e + ("meal" to JsonObject(meal + ("dishes" to JsonArray(dishes.map(::dish))))))
        }
        return JsonObject(obj + ("events" to JsonArray(events.map(::event))))
    }

    /** 5 -> 6: see the class comment. */
    private fun extrasWithAmounts(obj: JsonObject): JsonObject = mapMeals(obj) { meal ->
        val keys = (meal["extras"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.distinct()
        val total = meal["extrasAmount"]?.jsonPrimitive?.intOrNull ?: 0
        val items = splitEvenly(total, keys.ifEmpty { if (total > 0) listOf(Extras.OTHER) else emptyList() })
            .map { (key, amount) -> JsonObject(mapOf("key" to JsonPrimitive(key), "amount" to JsonPrimitive(amount))) }
        JsonObject(meal - "extrasAmount" + ("extras" to JsonArray(items)))
    }

    /** The dessert extras before schema 7, as the dessert dishes they become: key to (German name, English name). */
    private val DESSERT_EXTRAS = mapOf(
        Extras.DESSERT to ("Dessert" to "Dessert"),
        Extras.PUDDING to ("Pudding" to "Pudding"),
        Extras.ROTE_GRUETZE to ("Rote Grütze" to "Rote Grütze"),
    )

    /** 6 -> 7: see the class comment. */
    private fun dessertsAsDishes(obj: JsonObject): JsonObject {
        val lastPrice = linkedMapOf<String, Int>() // extra key -> its latest amount, for the saved dishes
        val out = mapMeals(obj) { meal ->
            val extras = (meal["extras"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
            val (desserts, rest) = extras.partition { (it["key"] as? JsonPrimitive)?.contentOrNull in DESSERT_EXTRAS }
            if (desserts.isEmpty()) return@mapMeals meal
            val dishes = desserts.map { e ->
                val extra = e["key"]!!.jsonPrimitive.content
                val (de, en) = DESSERT_EXTRAS.getValue(extra)
                val amount = e["amount"]?.jsonPrimitive?.intOrNull ?: 0
                lastPrice[extra] = amount
                JsonObject(
                    buildMap {
                        put("key", JsonPrimitive(Favorites.normalize(de)))
                        put("name", JsonObject(mapOf("de" to JsonPrimitive(de), "en" to JsonPrimitive(en))))
                        put("price", JsonPrimitive(amount))
                        put("count", JsonPrimitive(1))
                        e["grams"]?.takeIf { it !is JsonNull }?.let { put("grams", it) }
                        put("own", JsonPrimitive(true))
                        put("dessert", JsonPrimitive(true))
                    },
                )
            }
            val before = (meal["dishes"] as? JsonArray).orEmpty()
            JsonObject(meal + ("dishes" to JsonArray(before + dishes)) + ("extras" to JsonArray(rest)))
        }
        val saved = (out["customDishes"] as? JsonArray).orEmpty()
        val savedKeys = saved.mapNotNull { (it as? JsonObject)?.get("key")?.jsonPrimitive?.contentOrNull }.toSet()
        val added = lastPrice.filterKeys { it != Extras.DESSERT }.mapNotNull { (extra, price) ->
            val (de, en) = DESSERT_EXTRAS.getValue(extra)
            val key = Favorites.normalize(de)
            if (key in savedKeys) null else JsonObject(
                mapOf(
                    "key" to JsonPrimitive(key), "name" to JsonObject(mapOf("de" to JsonPrimitive(de), "en" to JsonPrimitive(en))),
                    "price" to JsonPrimitive(price), "own" to JsonPrimitive(true), "byWeight" to JsonPrimitive(true), "dessert" to JsonPrimitive(true),
                ),
            )
        }
        return if (added.isEmpty()) out else JsonObject(out + ("customDishes" to JsonArray(saved + added)))
    }

    /** [total] mills over [keys] in whole cents; the first key gets the remainder. */
    internal fun splitEvenly(total: Int, keys: List<String>): List<Pair<String, Int>> {
        if (keys.isEmpty()) return emptyList()
        val cents = total / 10
        val each = cents / keys.size
        val first = total - each * 10 * (keys.size - 1)
        return keys.mapIndexed { i, k -> k to if (i == 0) first else each * 10 }
    }

    /** Applies [change] to every stored meal (events' `meal` objects); everything else stays as it is. */
    private fun mapMeals(obj: JsonObject, change: (JsonObject) -> JsonObject): JsonObject {
        val events = obj["events"] as? JsonArray ?: return obj
        return JsonObject(obj + ("events" to JsonArray(events.map { e ->
            val meal = (e as? JsonObject)?.get("meal") as? JsonObject
            if (meal == null) e else JsonObject(e + ("meal" to change(meal)))
        })))
    }

    fun versionOf(obj: JsonObject): Int = obj["version"]?.jsonPrimitive?.intOrNull ?: 1

    /** Brings [obj] to [CURRENT] step by step; throws [StatsTooNewException] for a newer file. */
    fun migrate(obj: JsonObject, steps: Map<Int, (JsonObject) -> JsonObject> = STEPS, current: Int = CURRENT): JsonObject {
        var version = versionOf(obj)
        if (version > current) throw StatsTooNewException(version)
        var out = obj
        while (version < current) {
            out = steps.getValue(version)(out)
            version++
        }
        return JsonObject(out + ("version" to JsonPrimitive(current)))
    }
}
