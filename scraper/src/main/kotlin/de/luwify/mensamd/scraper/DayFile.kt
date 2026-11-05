package de.luwify.mensamd.scraper

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Mensa
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.model.isCakeOfTheDay
import de.luwify.mensamd.model.isDinner
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * `data/YYYY/MM/DD/<mensa>.json`. Holds no time of the fetch: the same menu must give the same bytes, so a rerun changes
 * nothing. Raise [SCHEMA] for a change that readers can't ignore.
 */
@Serializable
data class DayFile(
    val schema: Int = SCHEMA,
    val date: String,
    val mensa: MensaRef,
    /** A list without dates (PIER 16): a snapshot of what was on offer that day, not a menu of that day. */
    val fixed: Boolean,
    /** [SOURCE_MAIN], or [SOURCE_FALLBACK] for the Studentenwerk's test server. */
    val source: String,
    val dishes: List<DishEntry>,
) {
    companion object {
        const val SCHEMA = 1
        const val SOURCE_MAIN = "main"
        const val SOURCE_FALLBACK = "fallback"
    }
}

@Serializable
data class MensaRef(val id: String, val name: LocalizedText)

@Serializable
data class DishEntry(
    /** The app's favorites key: the same dish has the same key on every day and in every Mensa. */
    val key: String,
    val name: LocalizedText,
    /** Labels the site puts in front of a name ("Save the meal", "Aktionstheke", ...), outermost first. */
    val labels: List<LocalizedText>,
    /** Cents per group ([PRICE_GROUPS]). Empty when the page shows none or doesn't say which group a price is for. */
    val prices: Map<String, Int>,
    /** Cents that can't be matched to a group (PIER 16 sometimes lists only two). */
    val unassignedPrices: List<Int>,
    val tags: List<Tag>,
    /** Additive and allergen codes as printed, e.g. "1", "a1". Facts only: the legend's texts are not copied. */
    val codes: List<String>,
    val dinner: Boolean,
    val cake: Boolean,
    /** The heading a dish of a fixed list stands under, if any. */
    val section: LocalizedText? = null,
)

/** The order of [Dish.prices] on every page with three prices. */
val PRICE_GROUPS = listOf("students", "staff", "guests")

fun Mensa.toRef() = MensaRef(id, name)

fun Dish.toEntry(section: LocalizedText? = null): DishEntry {
    val assigned = priceGroupsKnown && prices.size == PRICE_GROUPS.size
    return DishEntry(
        key = Favorites.key(this),
        name = name,
        labels = badges,
        prices = if (assigned) PRICE_GROUPS.zip(prices).toMap() else emptyMap(),
        unassignedPrices = if (assigned) emptyList() else prices,
        tags = tags,
        codes = codes,
        dinner = isDinner,
        cake = isCakeOfTheDay,
        section = section,
    )
}

/** Where a day's file lives below the data directory. */
fun dayPath(date: LocalDate, mensaId: String): String =
    "%04d/%02d/%02d/%s.json".format(date.year, date.monthValue, date.dayOfMonth, mensaId)
