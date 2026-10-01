package de.luwify.mensamd.ui

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.isDinner
import java.time.LocalDate

/** One row of the lazy menu list. Keys are built here once, so nothing hashes dishes at scroll time. */
sealed class ListRow(val key: String) {
    class Header(val date: LocalDate) : ListRow("header-$date")
    class SubHeader(val date: LocalDate, val dinner: Boolean) : ListRow("sub-${if (dinner) "dinner" else "lunch"}-$date")
    class DishRow(val dish: Dish, val date: LocalDate, key: String) : ListRow(key)
    class NoMatch(date: LocalDate) : ListRow("empty-$date")
    class SubDivider(date: LocalDate) : ListRow("subdivider-$date")
    class DayDivider(date: LocalDate) : ListRow("divider-$date")
    class Footer(val updatedAt: Long, val failed: Boolean) : ListRow("footer")
}

/**
 * Flattens days into rows. A day that has dinner dishes is split into a lunch part and a dinner
 * part, each with a small sub-header and a divider between them. Days without dinner get no
 * sub-headers at all (they would only add noise), and the lunch sub-header only appears when
 * there is a dinner part to tell it apart from.
 */
fun buildRows(days: List<MenuDay>, updatedAt: Long?, failed: Boolean): List<ListRow> = buildList {
    days.forEachIndexed { index, day ->
        add(ListRow.Header(day.date))
        if (day.dishes.isEmpty()) {
            // Everything on this day is filtered out: keep the day visible and say why.
            add(ListRow.NoMatch(day.date))
        } else {
            val (dinner, lunch) = day.dishes.partition { it.isDinner }
            val seen = HashMap<String, Int>()
            fun addDishes(list: List<Dish>) = list.forEach { dish ->
                val base = "${day.date}|${dish.name.de}"
                val n = seen.merge(base, 1, Int::plus)!!
                add(ListRow.DishRow(dish, day.date, if (n == 1) base else "$base#$n"))
            }
            if (lunch.isNotEmpty() && dinner.isNotEmpty()) add(ListRow.SubHeader(day.date, dinner = false))
            addDishes(lunch)
            if (dinner.isNotEmpty()) {
                if (lunch.isNotEmpty()) add(ListRow.SubDivider(day.date))
                add(ListRow.SubHeader(day.date, dinner = true))
                addDishes(dinner)
            }
        }
        if (index < days.lastIndex) add(ListRow.DayDivider(day.date))
    }
    if (updatedAt != null) add(ListRow.Footer(updatedAt, failed))
}
