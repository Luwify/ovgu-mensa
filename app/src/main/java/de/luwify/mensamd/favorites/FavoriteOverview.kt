package de.luwify.mensamd.favorites

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.Tag
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * What the app keeps about a favorite besides its key ([Favorites.key]): the name as the site writes it and the tags,
 * so the overview can show a favorite that is on no loaded menu. Saved when the heart is tapped and refreshed whenever
 * the overview finds the dish on a menu (favorites from before 1.1.0 get theirs that way).
 */
@Serializable
data class FavoriteInfo(val name: LocalizedText, val tags: List<Tag>) {
    companion object {
        fun of(dish: Dish) = FavoriteInfo(dish.name, dish.tags)
    }
}

/** Where a favorite is next: [date] null means a fixed (undated) menu such as PIER 16's, where it is always offered. */
data class NextServing(val mensaId: String, val date: LocalDate?, val dish: Dish)

/** One row of the favorites overview. [next] null: on no loaded menu right now. */
data class FavoriteEntry(val key: String, val name: LocalizedText, val tags: List<Tag>, val next: NextServing?)

/** The favorites overview: where each favorite is next, and the order of the list. Pure Kotlin. */
object FavoriteOverview {
    /**
     * Every favorite with its next serving in [menus] (Mensa id + page, in tab order; null pages = nothing cached).
     * Only days the menu itself would show count: from [today] on, no weekends, so a tap always finds the day.
     * The earliest date wins; on the same date the Mensa that comes first in [menus]. A fixed menu counts only when
     * no dated menu has the dish. Name and tags come from that serving, else from [saved], else the key.
     *
     * Order: dated servings by date (then Mensa order, then name), then fixed-menu servings, then the rest; within
     * the last two groups and on ties by name in [language], compared with [names] (a locale collator in the app).
     */
    fun entries(
        favorites: Set<String>,
        menus: List<Pair<String, MenuPage?>>,
        today: LocalDate,
        saved: Map<String, FavoriteInfo>,
        language: String,
        names: Comparator<in String>,
    ): List<FavoriteEntry> {
        if (favorites.isEmpty()) return emptyList()
        val next = nextServings(favorites, menus, today)
        val mensaOrder = menus.withIndex().associate { (i, m) -> m.first to i }
        val entries = favorites.map { key ->
            val serving = next[key]
            val info = serving?.dish?.let(FavoriteInfo::of) ?: saved[key]
            FavoriteEntry(key, info?.name ?: LocalizedText(key.replaceFirstChar { it.uppercaseChar() }), info?.tags.orEmpty(), serving)
        }
        val byName = compareBy(names) { e: FavoriteEntry -> if (language == "de") e.name.de else e.name.en }
        return entries.sortedWith(
            compareBy<FavoriteEntry> { e -> e.next.let { if (it == null) 2 else if (it.date == null) 1 else 0 } }
                .thenBy { it.next?.date }
                .thenBy { e -> e.next?.let { mensaOrder[it.mensaId] } }
                .then(byName)
                .thenBy { it.key },
        )
    }

    /** The next serving per favorite key (see [entries]); keys on no menu are missing. */
    fun nextServings(favorites: Set<String>, menus: List<Pair<String, MenuPage?>>, today: LocalDate): Map<String, NextServing> {
        val result = HashMap<String, NextServing>()
        // All shown days of all Mensen, earliest first; sortedBy is stable, so equal dates keep the Mensa order.
        val days = menus.flatMap { (mensaId, page) ->
            page?.days.orEmpty().filter { it.date >= today && !it.date.isWeekend }.map { mensaId to it }
        }.sortedBy { it.second.date }
        for ((mensaId, day) in days) {
            for (dish in day.dishes) {
                val key = Favorites.key(dish)
                if (key in favorites && key !in result) result[key] = NextServing(mensaId, day.date, dish)
            }
        }
        for ((mensaId, page) in menus) {
            page?.fixed.orEmpty().flatMap { it.dishes }.forEach { dish ->
                val key = Favorites.key(dish)
                if (key in favorites && key !in result) result[key] = NextServing(mensaId, null, dish)
            }
        }
        return result
    }

    /** Saved infos that are missing or outdated for favorites found on a menu: what the app should store. */
    fun infoUpdates(entries: List<FavoriteEntry>, saved: Map<String, FavoriteInfo>): Map<String, FavoriteInfo> =
        entries.mapNotNull { e -> e.next?.let { e.key to FavoriteInfo.of(it.dish) } }
            .filter { (key, info) -> saved[key] != info }
            .toMap()

    private val LocalDate.isWeekend get() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
}
