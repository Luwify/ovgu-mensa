package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag

/** The meals overview (detailed statistics), from the payments' [MealRecord]s. Pure. */
object MealSummary {
    /** A dish eaten [count] times (servings); [name]/[tags] from the latest time, [lastPrice] in mills. */
    data class DishCount(val key: String, val name: LocalizedText, val tags: List<Tag>, val count: Int, val lastPrice: Int)

    data class Summary(
        val top: List<DishCount>,
        /** Servings per Mensa id (null = unknown, e.g. hand-named dishes), most first. */
        val byMensa: List<Pair<String?, Int>>,
        val servings: Int,
        /** Shares of servings, 0..1; vegetarian includes vegan (like the filter chip). Null without servings. */
        val veganShare: Double?,
        val vegetarianShare: Double?,
        /** Payments with a meal (not "Sonstiges"): their average amount; null without any. */
        val averagePerMeal: Int?,
        val mealPayments: Int,
    )

    private const val VEGAN = "vegan"
    private const val VEGETARIAN = "vegetarisch"

    fun summarize(events: List<CardEvent>, topCount: Int = 10): Summary {
        val meals = events.filter { it.kind == EventKind.PAYMENT && it.meal != null && !it.meal.other }
        val eaten = meals.flatMap { it.meal!!.dishes }
        val servings = eaten.sumOf { it.count }
        val top = eaten.groupBy { it.key }.map { (key, list) ->
            val latest = list.last()
            DishCount(key, latest.name, latest.tags, list.sumOf { it.count }, latest.price)
        }.sortedWith(compareByDescending<DishCount> { it.count }.thenBy { it.name.de.lowercase() }).take(topCount)
        val byMensa = eaten.groupBy { it.mensaId }.map { (id, list) -> id to list.sumOf { it.count } }.sortedByDescending { it.second }
        fun share(test: (EatenDish) -> Boolean) = if (servings == 0) null else eaten.filter(test).sumOf { it.count }.toDouble() / servings
        return Summary(
            top = top,
            byMensa = byMensa,
            servings = servings,
            veganShare = share { d -> d.tags.any { it.key == VEGAN } },
            vegetarianShare = share { d -> d.tags.any { it.key == VEGAN || it.key == VEGETARIAN } },
            averagePerMeal = if (meals.isEmpty()) null else (meals.sumOf { it.amount.toLong() } / meals.size).toInt(),
            mealPayments = meals.size,
        )
    }

    /** Every dish eaten or saved by hand, one entry per key (the latest), sorted by name: for days without a menu. */
    fun knownDishes(events: List<CardEvent>, custom: List<KnownDish>): List<KnownDish> {
        val fromHistory = events.mapNotNull { it.meal }.flatMap { it.dishes }.map { KnownDish(it.key, it.name, it.price, it.tags) }
        return (fromHistory + custom).associateBy { it.key }.values.sortedBy { it.name.de.lowercase() }
    }
}
