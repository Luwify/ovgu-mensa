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
        /**
         * Shares of the servings whose tags are known ([EatenDish.tagsKnown]), 0..1; vegetarian includes vegan (like the
         * filter chip). Null when no serving has tag information.
         */
        val veganShare: Double?,
        val vegetarianShare: Double?,
        /** Servings without tag information: not part of the shares. */
        val unknownTags: Int,
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
        val known = eaten.filter { it.tagsKnown }
        val knownServings = known.sumOf { it.count }
        fun share(test: (EatenDish) -> Boolean) = if (knownServings == 0) null else known.filter(test).sumOf { it.count }.toDouble() / knownServings
        return Summary(
            top = top,
            byMensa = byMensa,
            servings = servings,
            veganShare = share { d -> d.tags.any { it.key == VEGAN } },
            vegetarianShare = share { d -> d.tags.any { it.key == VEGAN || it.key == VEGETARIAN } },
            unknownTags = servings - knownServings,
            averagePerMeal = if (meals.isEmpty()) null else (meals.sumOf { it.amount.toLong() } / meals.size).toInt(),
            mealPayments = meals.size,
        )
    }

    /** One dish of one payment's meal ([index] in its dishes), for lists that let the user fix it. */
    data class DishRef(val eventId: Long, val index: Int, val date: java.time.LocalDate, val dish: EatenDish)

    /** Every dish eaten without a known Mensa, newest payment first (the "Unbekannte Mensa" list). */
    fun withoutMensa(events: List<CardEvent>): List<DishRef> = events.asReversed()
        .filter { it.kind == EventKind.PAYMENT }
        .flatMap { e -> e.meal?.dishes.orEmpty().mapIndexedNotNull { i, d -> if (d.mensaId == null) DishRef(e.id, i, e.date, d) else null } }

    /**
     * Every dish eaten or saved by hand, one entry per key (the latest), sorted by name: for days without a menu. A dish
     * paid by weight has no price to reuse, so it isn't one of them.
     */
    fun knownDishes(events: List<CardEvent>, custom: List<KnownDish>): List<KnownDish> {
        val fromHistory = events.mapNotNull { it.meal }.flatMap { it.dishes }.filter { it.grams == null }
            .map { KnownDish(it.key, it.name, it.price, it.tags, it.mensaId, it.own) }
        return (fromHistory + custom).associateBy { it.key }.values.sortedBy { it.name.de.lowercase() }
    }
}
