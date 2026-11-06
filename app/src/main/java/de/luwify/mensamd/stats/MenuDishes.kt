package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.WeightPricing
import java.time.LocalDate

/** Menu pages as the statistics see them: [SnapDish]es, Mensa id attached. Pure. */
object MenuDishes {
    /** The dishes of [page] on [date]; a fixed list (PIER 16) counts for every day. */
    fun onDay(mensaId: String, page: MenuPage, date: LocalDate): List<SnapDish> =
        (page.days.firstOrNull { it.date == date }?.dishes.orEmpty() + page.fixed.flatMap { it.dishes }).map { it.snap(mensaId) }

    /** That day's dishes of all [pages] (Mensa id to page), in that order. */
    fun onDay(pages: List<Pair<String, MenuPage>>, date: LocalDate): List<SnapDish> =
        pages.flatMap { (id, page) -> onDay(id, page, date) }

    /** Every dish of all [pages], whatever the day. */
    fun all(pages: List<Pair<String, MenuPage>>): List<SnapDish> = pages.flatMap { (id, page) ->
        (page.days.flatMap { it.dishes } + page.fixed.flatMap { it.dishes }).map { it.snap(id) }
    }

    /**
     * A day's dishes for a meal question: [kept] (the menu saved with an open question, which outlives the page) plus
     * [cached] (what the app has now, all a correction of an answered payment can use), each dish once. Paid by weight
     * when either copy says so: a question kept from before the weight rules doesn't know.
     */
    fun merge(kept: List<SnapDish>, cached: List<SnapDish>): List<SnapDish> {
        fun SnapDish.id() = Triple(mensaId, Favorites.normalize(name.de), prices)
        val all = kept + cached
        val weighed = all.filter { it.byWeight }.mapTo(HashSet()) { it.id() }
        return all.distinctBy { it.id() }.map { if (it.id() in weighed) it.copy(byWeight = true) else it }
    }

    private fun Dish.snap(mensaId: String) =
        SnapDish(mensaId, name, prices, priceGroupsKnown, tags, byWeight = WeightPricing.isPer100g(mensaId, this))
}
