package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites

/**
 * Matches a payment to that day's menu (detailed statistics). Pure. A payment can be one dish, the same dish twice or
 * two different dishes of one Mensa, at the user's price group. Every Mensa with a menu that day counts, the default
 * Mensa only comes first in the list. Too many candidates: the user first says where they ate ([Plan.AskWhere]).
 * Dishes paid by weight ([SnapDish.byWeight]) have no fixed price, so they never match exactly and are never saved
 * automatically: they are offered for whatever amount is left ([weightDishes]), with the weight it would buy.
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

    /** A dish paid by weight with its price per 100 g for the user's group (mills). */
    data class WeightDish(val dish: SnapDish, val per100: Int) {
        /** Identifies the dish within a day's menu. */
        val id: String get() = dish.mensaId + "|" + Favorites.normalize(dish.name.de)

        /** About how many grams [amount] buys, to the nearest 10 g. */
        fun grams(amount: Int): Int = estimateGrams(amount, per100)

        /** What [grams] cost (mills). */
        fun cost(grams: Int): Int = ((grams.toLong() * per100 + 50) / 100).toInt()

        /** The portion that cost [amount]. */
        fun eaten(amount: Int, grams: Int = grams(amount)) = EatenDish(
            Favorites.normalize(dish.name.de), dish.name, dish.mensaId, amount, 1, dish.tags, grams,
        )

        /** The whole payment of [amount] was this dish. */
        fun toRecord(amount: Int) = MealRecord(dishes = listOf(eaten(amount)))
    }

    /** A dish paid by weight in a mixed payment: [grams] as entered, or null: it takes all the money that is left. */
    data class WeightPick(val weight: WeightDish, val grams: Int? = null)

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

    /** About how many grams [amount] buys at [per100] (both mills), to the nearest 10 g. */
    fun estimateGrams(amount: Int, per100: Int): Int =
        ((amount.toLong() * 100 / per100 + 5) / 10 * 10).toInt().coerceAtLeast(10)

    /** The dishes paid by weight in [dishes] (of [where], if given), each once per Mensa, the default Mensa's first. */
    fun weightDishes(dishes: List<SnapDish>, defaultId: String?, group: Int, where: Set<String>? = null): List<WeightDish> =
        dishes.filter { it.byWeight && (where == null || it.mensaId in where) }
            .mapNotNull { d -> prices(d, group).firstOrNull()?.takeIf { it > 0 }?.let { WeightDish(d, it) } }
            .distinctBy { it.id }
            .sortedBy { it.dish.mensaId != defaultId }

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
     * The rest of the day for a correction list: every dish of [dishes] (of [where], if given) that isn't already one
     * of the [matched] candidates on its own, once per Mensa, the default Mensa's first. Dishes paid by weight have
     * their own list ([weightDishes]).
     */
    fun others(matched: List<Candidate>, dishes: List<SnapDish>, defaultId: String?, where: Set<String>? = null): List<SnapDish> {
        val shown = matched.filter { it.picks.size == 1 }.map { it.mensaId to Favorites.normalize(it.picks.single().dish.name.de) }.toSet()
        return dishes.filter { !it.byWeight && (where == null || it.mensaId in where) }
            .distinctBy { it.mensaId to Favorites.normalize(it.name.de) }
            .filterNot { (it.mensaId to Favorites.normalize(it.name.de)) in shown }
            .sortedBy { it.mensaId != defaultId }
    }

    /** The user says [dish] was this payment of [amount], whatever the menu asks for it. */
    fun asPayment(dish: SnapDish, amount: Int): MealRecord =
        MealRecord(dishes = listOf(EatenDish(Favorites.normalize(dish.name.de), dish.name, dish.mensaId, amount, 1, dish.tags)))

    /**
     * Real dishes plus extras: the [picks] with their prices, then the [weights]: one with a weight costs that, one
     * without takes all that is left (at most one can). What remains goes to [extras] as a total. Null when the dishes
     * cost more than the payment, nothing is left for the dish without a weight, or money is left over without an
     * extra to put it on.
     */
    fun withExtras(amount: Int, picks: List<Pick>, extras: List<String>, weights: List<WeightPick> = emptyList()): MealRecord? {
        val (weighed, open) = weights.partition { it.grams != null }
        if (open.size > 1) return null
        val portions = weighed.map { it.weight.eaten(it.weight.cost(it.grams!!), it.grams) }
        val left = amount - picks.sumOf { it.count * it.price } - portions.sumOf { it.price }
        val taking = open.singleOrNull()?.weight
        val rest = if (taking != null) 0 else left
        if (left < 0 || (taking != null && left == 0) || (rest > 0 && extras.isEmpty())) return null
        if (picks.isEmpty() && weights.isEmpty() && extras.isEmpty()) return null
        val dishes = picks.map { it.toEaten() } + portions + listOfNotNull(taking?.eaten(left))
        return MealRecord(dishes = dishes, extras = extras.distinct(), extrasAmount = rest)
    }

    private fun Pick.toEaten() = EatenDish(Favorites.normalize(dish.name.de), dish.name, dish.mensaId, price, count, dish.tags)
}
