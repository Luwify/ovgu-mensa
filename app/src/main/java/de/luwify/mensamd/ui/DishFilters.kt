package de.luwify.mensamd.ui

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.FixedSection
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.isCakeOfTheDay

/**
 * Tag filters and code exclusion. Filter keys are tag keys, plus [CAKE] and [FAVORITES], which are not tags (they
 * can't collide with one: tag keys never contain a colon). `vegan`, `vegetarisch`, [CAKE] and [FAVORITES] are
 * inclusive (show only matching dishes, and vegetarian also lets vegan dishes through); every other key hides
 * dishes carrying that tag.
 * Excluded codes hide every dish that lists any of them. A dish with no codes passes, which does
 * not mean it is free of allergens (the sheet says so).
 */
object DishFilters {
    const val VEGAN = "vegan"
    const val VEGETARIAN = "vegetarisch"

    /** The cake of the day (see `Dish.isCakeOfTheDay`). */
    const val CAKE = ":kuchen"

    /** Only the user's favorites (see `Favorites`). */
    const val FAVORITES = ":favoriten"

    /** Chip order; keys we don't know yet follow alphabetically. */
    private val ORDER = listOf(VEGAN, VEGETARIAN, CAKE, FAVORITES, "schwein", "rind", "gefluegel", "fisch", "knoblauch", "alkohol", "suppe")

    fun matches(dish: Dish, active: Set<String>, excludedCodes: Set<String> = emptySet(), favorites: Set<String> = emptySet()): Boolean {
        if (excludedCodes.isNotEmpty() && dish.codes.any { it in excludedCodes }) return false
        if (active.isEmpty()) return true
        val tags = dish.tags.mapTo(HashSet()) { it.key }
        return active.all { filter ->
            when (filter) {
                VEGAN -> VEGAN in tags
                VEGETARIAN -> VEGETARIAN in tags || VEGAN in tags
                CAKE -> dish.isCakeOfTheDay
                FAVORITES -> Favorites.isFavorite(dish, favorites)
                else -> filter !in tags
            }
        }
    }

    /** Keeps every day, even when all its dishes are filtered out (the UI says "no matching dishes"). */
    fun apply(
        days: List<MenuDay>,
        active: Set<String>,
        excludedCodes: Set<String> = emptySet(),
        favorites: Set<String> = emptySet(),
    ): List<MenuDay> =
        if (active.isEmpty() && excludedCodes.isEmpty()) days
        else days.map { day -> day.copy(dishes = day.dishes.filter { matches(it, active, excludedCodes, favorites) }) }

    /** Same filters over the sections of a fixed menu; sections are kept even when all their dishes are filtered out. */
    fun applySections(
        sections: List<FixedSection>,
        active: Set<String>,
        excludedCodes: Set<String> = emptySet(),
        favorites: Set<String> = emptySet(),
    ): List<FixedSection> =
        if (active.isEmpty() && excludedCodes.isEmpty()) sections
        else sections.map { it.copy(dishes = it.dishes.filter { d -> matches(d, active, excludedCodes, favorites) }) }

    /** [label] is the site's alt text, used only for keys we have no own strings for. */
    data class Option(val key: String, val label: String)

    /**
     * One option per tag actually found in [days], plus any active filter whose tag isn't on this
     * page, so a persisted filter can always be switched off. Vegetarian is offered when there are
     * vegetarian or vegan dishes, since it matches both; cake only when there is a cake of the day. Favorites is
     * always offered (also before the first favorite: the list then explains how to add one). [customOrder] is the
     * user's chip order (see [mergeOrder]).
     */
    fun options(
        days: List<MenuDay>,
        active: Set<String>,
        fixed: List<FixedSection> = emptyList(),
        customOrder: List<String> = emptyList(),
    ): List<Option> {
        val labels = LinkedHashMap<String, String>()
        for (day in days) for (dish in day.dishes) for (tag in dish.tags) labels.putIfAbsent(tag.key, tag.label)
        for (section in fixed) for (dish in section.dishes) for (tag in dish.tags) labels.putIfAbsent(tag.key, tag.label)
        if (VEGAN in labels && VEGETARIAN !in labels) labels[VEGETARIAN] = VEGETARIAN
        if (days.any { d -> d.dishes.any { it.isCakeOfTheDay } } || fixed.any { s -> s.dishes.any { it.isCakeOfTheDay } }) {
            labels[CAKE] = CAKE
        }
        labels[FAVORITES] = FAVORITES
        for (key in active) labels.putIfAbsent(key, key)
        // The user's order (long-press + drag) first; keys it doesn't know yet follow in the default order.
        val custom = customOrder.withIndex().associate { it.value to it.index }
        return labels.map { Option(it.key, it.value) }.sortedWith(
            compareBy<Option>(
                { custom[it.key] ?: Int.MAX_VALUE },
                { ORDER.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                { it.key },
            ),
        )
    }

    /**
     * The new stored chip order after the user dragged the chips of one page into [visibleNew]. Chips that are not
     * on this page keep their place in [stored]; the visible ones fill the visible slots in their new order, and
     * visible chips the stored order didn't know yet get slots at the end. Pure, so it is unit-tested.
     */
    fun mergeOrder(stored: List<String>, visibleNew: List<String>): List<String> {
        val base = stored.distinct() + visibleNew.filter { it !in stored }
        val visible = visibleNew.toSet()
        val queue = ArrayDeque(visibleNew)
        return base.map { if (it in visible) queue.removeFirst() else it }
    }
}
