package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites

/**
 * Matches a payment to that day's menu (detailed statistics). Pure. A payment can be one dish, the same dish twice or
 * two different dishes of one Mensa, at the user's price group. Every Mensa with a menu that day counts. Dishes paid by
 * weight ([SnapDish.byWeight]) have no fixed price, so they never match exactly and are never saved automatically: the
 * question screen offers them for whatever is left ([MealDraft]). Only a single candidate overall may be saved without
 * asking ([single]).
 */
object MealMatcher {
    /** [count] times [dish] at [price] each (mills). */
    data class Pick(val dish: SnapDish, val count: Int, val price: Int)

    data class Candidate(val mensaId: String, val picks: List<Pick>) {
        val total: Int get() = picks.sumOf { it.count * it.price }

        fun toRecord(): MealRecord = MealRecord(dishes = picks.map { it.toEaten() })
    }

    /**
     * The prices of [dish] for price group [group] in mills. The group's price when the menu says which column is
     * which (a dish with fewer prices, like a cake with one, costs its first one, as the menu shows it); otherwise
     * (PIER 16 lists two unnamed prices) every listed price, since any of them could be the user's.
     */
    fun prices(dish: SnapDish, group: Int): List<Int> =
        if (dish.priceGroupsKnown) listOfNotNull((dish.prices.getOrNull(group) ?: dish.prices.firstOrNull())?.times(10))
        else dish.prices.distinct().map { it * 10 }

    /**
     * How many grams [amount] buys at [per100] (both mills), to the nearest gram (integer arithmetic). Only for showing:
     * a weight worked out from a price is never stored (see [EatenDish.per100]).
     */
    fun estimateGrams(amount: Int, per100: Int): Int =
        ((amount.toLong() * 100 + per100 / 2) / per100).toInt().coerceAtLeast(1)

    /** The one candidate for [amount] among all of [dishes], or null when there is none or more than one. */
    fun single(amount: Int, dishes: List<SnapDish>, group: Int): Candidate? = candidates(amount, dishes, group).singleOrNull()

    /** Every combination that costs exactly [amount], per Mensa in the order of [dishes]: singles, doubles, pairs. */
    fun candidates(amount: Int, dishes: List<SnapDish>, group: Int): List<Candidate> {
        val byMensa = dishes.groupBy { it.mensaId } // keeps first-seen order
        return byMensa.flatMap { (mensaId, list) ->
            // A dish listed twice (lunch and dinner) at the same price is one choice.
            val priced = list.filterNot { it.byWeight }.flatMap { d -> prices(d, group).map { d to it } }
                .distinctBy { (d, p) -> Favorites.normalize(d.name.de) to p }
            val singles = priced.filter { it.second == amount }.map { Candidate(mensaId, listOf(Pick(it.first, 1, it.second))) }
            val doubles = priced.filter { it.second * 2 == amount }.map { Candidate(mensaId, listOf(Pick(it.first, 2, it.second))) }
            val pairs = buildList {
                for (i in priced.indices) for (j in i + 1 until priced.size) {
                    val (a, pa) = priced[i]
                    val (b, pb) = priced[j]
                    if (pa + pb == amount && Favorites.normalize(a.name.de) != Favorites.normalize(b.name.de)) {
                        add(Candidate(mensaId, listOf(Pick(a, 1, pa), Pick(b, 1, pb))))
                    }
                }
            }
            singles + doubles + pairs
        }
    }

    private fun Pick.toEaten() = EatenDish(Favorites.normalize(dish.name.de), dish.name, dish.mensaId, price, count, dish.tags, own = dish.own)
}
