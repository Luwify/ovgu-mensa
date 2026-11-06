package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites

/**
 * Matches a payment to that day's menu (detailed statistics). Pure. A payment can be one dish, the same dish twice or
 * two different dishes of one Mensa, at the user's price group. Every Mensa with a menu that day counts, the default
 * Mensa only comes first in the list. Too many candidates: the user first says where they ate ([Plan.AskWhere]).
 */
object MealMatcher {
    /** More candidates than this: ask where first. */
    const val MAX_CHOICES = 5

    /** [count] times [dish] at [price] each (mills). */
    data class Pick(val dish: SnapDish, val count: Int, val price: Int)

    data class Candidate(val mensaId: String, val picks: List<Pick>) {
        val total: Int get() = picks.sumOf { it.count * it.price }

        fun toRecord(): MealRecord = MealRecord(dishes = picks.map { it.toEaten() })
    }

    sealed interface Plan {
        /** Where did you eat? [mensaIds] = every Mensa with dishes that day (default first); [preselected] ticked. */
        data class AskWhere(val mensaIds: List<String>, val preselected: Set<String>) : Plan

        /** Pick one ([candidates] has one entry: certain enough to save without asking). */
        data class Choose(val candidates: List<Candidate>) : Plan

        data object NoMatch : Plan
    }

    /**
     * The prices of [dish] for price group [group] in mills. The group's price when the menu says which column is
     * which (a dish with fewer prices, like a cake with one, costs its first one, as the menu shows it); otherwise
     * (PIER 16 lists two unnamed prices) every listed price, since any of them could be the user's.
     */
    fun prices(dish: SnapDish, group: Int): List<Int> =
        if (dish.priceGroupsKnown) listOfNotNull((dish.prices.getOrNull(group) ?: dish.prices.firstOrNull())?.times(10))
        else dish.prices.distinct().map { it * 10 }

    /** Every combination that costs exactly [amount], per Mensa in the order of [dishes]: singles, doubles, pairs. */
    fun candidates(amount: Int, dishes: List<SnapDish>, group: Int): List<Candidate> {
        val byMensa = dishes.groupBy { it.mensaId } // keeps first-seen order
        return byMensa.flatMap { (mensaId, list) ->
            // A dish listed twice (lunch and dinner) at the same price is one choice.
            val priced = list.flatMap { d -> prices(d, group).map { d to it } }
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

    /**
     * What to show for a payment of [amount]: all candidates of every Mensa in [dishes] (only one candidate overall may
     * be saved without asking), the default Mensa's first. [where] = the Mensen the user said they ate at (null: not
     * asked yet).
     */
    fun plan(amount: Int, dishes: List<SnapDish>, defaultId: String?, group: Int, where: Set<String>? = null): Plan {
        val found = candidates(amount, dishes.filter { where == null || it.mensaId in where }, group)
            .sortedBy { it.mensaId != defaultId } // stable: otherwise the order of [dishes]
        return when {
            found.isEmpty() -> Plan.NoMatch
            where != null || found.size <= MAX_CHOICES -> Plan.Choose(found)
            else -> {
                val ids = dishes.map { it.mensaId }.distinct().sortedBy { it != defaultId }
                Plan.AskWhere(ids, found.map { it.mensaId }.filter { it == defaultId }.toSet())
            }
        }
    }

    /**
     * Real dishes plus extras: the [picks] with their prices, the rest of [amount] goes to [extras] as a total. Null
     * when the dishes cost more than the payment, or money is left over without an extra to put it on.
     */
    fun withExtras(amount: Int, picks: List<Pick>, extras: List<String>): MealRecord? {
        val rest = amount - picks.sumOf { it.count * it.price }
        if (rest < 0 || (rest > 0 && extras.isEmpty()) || (picks.isEmpty() && extras.isEmpty())) return null
        return MealRecord(dishes = picks.map { it.toEaten() }, extras = extras.distinct(), extrasAmount = rest)
    }

    private fun Pick.toEaten() = EatenDish(Favorites.normalize(dish.name.de), dish.name, dish.mensaId, price, count, dish.tags)
}
