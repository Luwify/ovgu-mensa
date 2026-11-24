package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
 * Version 8 = weighed dishes store their inputs, never a worked-out weight: `EatenDish.per100` (price per 100 g) is new
 * and `grams` means only a weight the user entered. Until 7 `grams` was always worked out from the price, rounded to 10 g,
 * so it is dropped: a dessert gets the price per 100 g saved with its hand-named dish (by weight), else the usual 0,60 €
 * ([MealDraft.DESSERT_PER_100]; a hand-named dessert with a fixed price gets none); any other weighed dish gets its
 * hand-named dish's price per 100 g, else the one price that old rounding came from (whole cents, a round one first),
 * the only source left for a menu dish. The amounts paid stay as they are.
 * Version 9 = Pfand paid with a drink, as the extra [Extras.PFAND] of a meal (not counted as spending), and "Was ist
 * passiert?" answering the open item `credit` with several movements on their own days: nothing to convert (an open
 * `credit` question keeps its shape and is answered with the new screen); the version keeps an older app, which would
 * count the Pfand as spending and show its key, from reading the file.
 */
object StatsMigrations {
    const val CURRENT = 9

    /** n -> n + 1. */
    private val STEPS: Map<Int, (JsonObject) -> JsonObject> = mapOf(1 to { it }, 2 to { it }, 3 to ::markUnknownDishesOwn, 4 to { it }, 5 to ::extrasWithAmounts, 6 to ::dessertsAsDishes, 7 to ::weightInputs, 8 to { it })

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

    /** 7 -> 8: see the class comment. */
    private fun weightInputs(obj: JsonObject): JsonObject {
        // Hand-named dishes: key -> (by weight, price per 100 g).
        val saved = (obj["customDishes"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().associate { d ->
            d["key"]?.jsonPrimitive?.contentOrNull to (((d["byWeight"] as? JsonPrimitive)?.booleanOrNull == true) to d["per100"]?.jsonPrimitive?.intOrNull)
        }
        fun dish(d: JsonElement): JsonElement {
            if (d !is JsonObject) return d
            val key = d["key"]?.jsonPrimitive?.contentOrNull
            val price = d["price"]?.jsonPrimitive?.intOrNull ?: 0
            val grams = d["grams"]?.jsonPrimitive?.intOrNull
            val dessert = (d["dessert"] as? JsonPrimitive)?.booleanOrNull == true
            val known = saved[key]
            val per100 = when {
                dessert -> if (known != null && !known.first) null else known?.second ?: MealDraft.DESSERT_PER_100
                grams != null -> known?.second ?: roundedFrom(price, grams)
                else -> null
            }
            val out = d - "grams"
            return JsonObject(if (per100 != null) out + ("per100" to JsonPrimitive(per100)) else out)
        }
        return mapMeals(obj) { meal ->
            val dishes = meal["dishes"] as? JsonArray ?: return@mapMeals meal
            JsonObject(meal + ("dishes" to JsonArray(dishes.map(::dish))))
        }
    }

    /**
     * The price per 100 g (mills) a weight saved until schema 7 came from: those weights were [price] at that price, rounded
     * to 10 g. Several whole-cent prices give the same rounded weight; a round one (10 cents, then 5) wins, then the one
     * closest to the quotient. Used only by the 7 -> 8 step.
     */
    internal fun roundedFrom(price: Int, grams: Int): Int? {
        if (price <= 0 || grams <= 0) return null
        fun oldGrams(p: Int) = ((price.toLong() * 100 / p + 5) / 10 * 10).toInt().coerceAtLeast(10)
        val exact = ((price.toLong() * 100 + grams / 2) / grams).toInt()
        val cents = (exact + 5) / 10 * 10
        return (-20..20).map { cents + it * 10 }.filter { it > 0 && oldGrams(it) == grams }
            .minWithOrNull(compareBy<Int>({ it % 100 != 0 }, { it % 50 != 0 }, { kotlin.math.abs(it - exact) })) ?: exact
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
