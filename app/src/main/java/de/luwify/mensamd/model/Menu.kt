package de.luwify.mensamd.model

import java.time.LocalDate

/** A text in both site languages. [en] falls back to [de] when the page has no English text. */
data class LocalizedText(val de: String, val en: String = de)

/** Everything one Mensa page yields: the published days plus the legend found on the same page. */
data class MenuPage(val days: List<MenuDay>, val legend: Legend)

data class MenuDay(val date: LocalDate, val dishes: List<Dish>)

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
)

/** A tag icon. [key] is the icon file name (`mensasym_<key>.png`) and is the only stable identifier. */
data class Tag(val key: String, val label: String)

enum class CodeCategory { ADDITIVE, ALLERGEN, OTHER }

data class LegendCode(val code: String, val category: CodeCategory, val text: LocalizedText)

/** A headed free-text paragraph of the legend, e.g. "Save the meal:" or the CO2e/water methodology. */
data class LegendNote(val title: LocalizedText, val text: LocalizedText)

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
