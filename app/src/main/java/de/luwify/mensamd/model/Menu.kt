package de.luwify.mensamd.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** A text in both site languages. [en] falls back to [de] when the page has no English text. */
@Serializable
data class LocalizedText(val de: String, val en: String = de)

/**
 * Everything one Mensa page yields. A page is either dated ([days], most Mensen) or a fixed list
 * without dates ([fixed], PIER 16), never both. [legend] is the one found on the page, or for fixed
 * pages the one borrowed from another Mensa (PIER 16 has none of its own).
 */
@Serializable
data class MenuPage(
    val days: List<MenuDay>,
    val legend: Legend,
    val fixed: List<FixedSection> = emptyList(),
) {
    /** No dishes at all: a closed Mensa, an empty or a changed page. */
    val isBlank: Boolean get() = days.isEmpty() && fixed.none { it.dishes.isNotEmpty() }
}

/** A part of a fixed menu (e.g. "Frühstück"); [title] is null for the untitled list. */
@Serializable
data class FixedSection(val title: LocalizedText?, val dishes: List<Dish>)

@Serializable
data class MenuDay(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val dishes: List<Dish>,
)

@Serializable
data class Dish(
    val name: LocalizedText,
    /** Leading "Save the meal:", "Aktionstheke:", "Pastabuffet:" ... labels, outermost first. */
    val badges: List<LocalizedText>,
    /** Cents in legend order (students, staff, guests). Empty when the page shows no price. */
    val prices: List<Int>,
    val tags: List<Tag>,
    /** Additive/allergen/other codes without parentheses, e.g. "1", "a1", "g", "Q". */
    val codes: List<String>,
    val co2Grams: Int?,
    val waterLiters: Int?,
    /**
     * False when [prices] can't be matched to the legend's price groups (PIER 16 sometimes lists only
     * two prices and doesn't say which groups). Such prices are shown without group names.
     */
    val priceGroupsKnown: Boolean = true,
)

/**
 * Dinner dishes carry an "Abendessen im ..." prefix on the site ("Abendessen im Café Latte",
 * "Abendessen im oberen Speisesaal"; English "Dinner at/in ..."). This is the only place that knows.
 */
val Dish.isDinner: Boolean get() = badges.any { it.de.startsWith("Abendessen", ignoreCase = true) }

/** A tag icon. [key] is the icon file name (`mensasym_<key>.png`) and is the only stable identifier. */
@Serializable
data class Tag(val key: String, val label: String)

@Serializable
enum class CodeCategory { ADDITIVE, ALLERGEN, OTHER }

/** 1-10 additives, a1-n allergens, anything else (A, P, Q, ...) other. Same rule the site's legend follows. */
fun codeCategory(code: String): CodeCategory = when {
    code.firstOrNull()?.isDigit() == true -> CodeCategory.ADDITIVE
    code.firstOrNull()?.isLowerCase() == true -> CodeCategory.ALLERGEN
    else -> CodeCategory.OTHER
}

@Serializable
data class LegendCode(val code: String, val category: CodeCategory, val text: LocalizedText)

/** A headed free-text paragraph of the legend, e.g. "Save the meal:" or the CO2e/water methodology. */
@Serializable
data class LegendNote(val title: LocalizedText, val text: LocalizedText)

@Serializable
data class Legend(
    /** Names of the price columns, e.g. students | staff | guests. */
    val priceGroups: List<LocalizedText>,
    val codes: List<LegendCode>,
    val notes: List<LegendNote>,
) {
    companion object {
        val EMPTY = Legend(emptyList(), emptyList(), emptyList())
    }
}
