package de.luwify.mensamd.ui

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.MenuDay

/**
 * Filter keys are tag keys. `vegan` and `vegetarisch` are inclusive (show only matching dishes,
 * and vegetarian also lets vegan dishes through); every other key hides dishes carrying that tag.
 */
object DishFilters {
    const val VEGAN = "vegan"
    const val VEGETARIAN = "vegetarisch"

    /** Chip order; keys we don't know yet follow alphabetically. */
    private val ORDER = listOf(VEGAN, VEGETARIAN, "schwein", "rind", "gefluegel", "fisch", "knoblauch", "alkohol", "suppe")

    fun matches(dish: Dish, active: Set<String>): Boolean {
        if (active.isEmpty()) return true
        val tags = dish.tags.mapTo(HashSet()) { it.key }
        return active.all { filter ->
            when (filter) {
                VEGAN -> VEGAN in tags
                VEGETARIAN -> VEGETARIAN in tags || VEGAN in tags
                else -> filter !in tags
            }
        }
    }

    /** Keeps every day, even when all its dishes are filtered out (the UI says "no matching dishes"). */
    fun apply(days: List<MenuDay>, active: Set<String>): List<MenuDay> =
        if (active.isEmpty()) days else days.map { day -> day.copy(dishes = day.dishes.filter { matches(it, active) }) }

    /** [label] is the site's alt text, used only for keys we have no own strings for. */
    data class Option(val key: String, val label: String)

    /**
     * One option per tag actually found in [days], plus any active filter whose tag isn't on this
     * page, so a persisted filter can always be switched off. Vegetarian is offered when there are
     * vegetarian or vegan dishes, since it matches both.
     */
    fun options(days: List<MenuDay>, active: Set<String>): List<Option> {
        val labels = LinkedHashMap<String, String>()
        for (day in days) for (dish in day.dishes) for (tag in dish.tags) labels.putIfAbsent(tag.key, tag.label)
        if (VEGAN in labels && VEGETARIAN !in labels) labels[VEGETARIAN] = VEGETARIAN
        for (key in active) labels.putIfAbsent(key, key)
        return labels.map { Option(it.key, it.value) }.sortedWith(
            compareBy<Option>({ ORDER.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.key }),
        )
    }
}
