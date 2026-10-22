package de.luwify.mensamd.ui

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.FixedSection
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.isDinner
import java.time.LocalDate

/** One row of the lazy menu list. Keys are built here once, so nothing hashes dishes at scroll time. */
sealed class ListRow(val key: String) {
    class Header(val date: LocalDate) : ListRow("header-$date")
    class SubHeader(val date: LocalDate, val dinner: Boolean) : ListRow("sub-${if (dinner) "dinner" else "lunch"}-$date")
    /** [date] is null for dishes of a fixed (undated) menu. */
    class DishRow(val dish: Dish, val date: LocalDate?, key: String) : ListRow(key) {
        /** Computed once per row, not at every recomposition of the card. */
        val favoriteKey: String by lazy(LazyThreadSafetyMode.NONE) { Favorites.key(dish) }
    }
    class NoMatch(id: String) : ListRow("empty-$id")
    class SubDivider(date: LocalDate) : ListRow("subdivider-$date")
    class DayDivider(date: LocalDate) : ListRow("divider-$date")
    class Footer(val updatedAt: Long, val failed: Boolean, val fromFallback: Boolean = false) : ListRow("footer")

    /** The single "Festes Angebot" label that replaces the day headers of a fixed menu. */
    data object FixedHeader : ListRow("fixed-header")
    class SectionTitle(index: Int, val title: LocalizedText) : ListRow("section-$index")
    class SectionDivider(index: Int) : ListRow("section-divider-$index")
}

/**
 * Flattens days into rows. A day that has dinner dishes is split into a lunch part and a dinner
 * part, each with a small sub-header and a divider between them. Days without dinner get no
 * sub-headers at all (they would only add noise), and the lunch sub-header only appears when
 * there is a dinner part to tell it apart from.
 */
fun buildRows(days: List<MenuDay>, updatedAt: Long?, failed: Boolean, fromFallback: Boolean = false): List<ListRow> = buildList {
    days.forEachIndexed { index, day ->
        add(ListRow.Header(day.date))
        if (day.dishes.isEmpty()) {
            // Everything on this day is filtered out: keep the day visible and say why.
            add(ListRow.NoMatch(day.date.toString()))
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
    if (updatedAt != null) add(ListRow.Footer(updatedAt, failed, fromFallback))
}

/**
 * Rows of a fixed (undated) menu: one "Festes Angebot" label, then each section with its optional
 * title, dishes (or "no matching dishes" when filters removed them all) and a divider between sections.
 */
fun buildFixedRows(sections: List<FixedSection>, updatedAt: Long?, failed: Boolean, fromFallback: Boolean = false): List<ListRow> = buildList {
    add(ListRow.FixedHeader)
    sections.forEachIndexed { index, section ->
        section.title?.let { add(ListRow.SectionTitle(index, it)) }
        val seen = HashMap<String, Int>()
        for (dish in section.dishes) {
            val base = "fixed-$index|${dish.name.de}"
            val n = seen.merge(base, 1, Int::plus)!!
            add(ListRow.DishRow(dish, null, if (n == 1) base else "$base#$n"))
        }
        if (section.dishes.isEmpty()) add(ListRow.NoMatch("fixed-$index"))
        if (index < sections.lastIndex) add(ListRow.SectionDivider(index))
    }
    if (updatedAt != null) add(ListRow.Footer(updatedAt, failed, fromFallback))
}
