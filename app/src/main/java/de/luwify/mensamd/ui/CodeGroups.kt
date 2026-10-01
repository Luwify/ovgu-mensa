package de.luwify.mensamd.ui

import androidx.compose.ui.state.ToggleableState
import de.luwify.mensamd.model.CodeCategory
import de.luwify.mensamd.model.LegendCode

/**
 * [family] is the letter shared by a run of numbered allergens (a1-a6 gluten, h1-h9 nuts) when
 * there are at least two; such a group gets a header that selects all of them. Otherwise null.
 */
data class ExcludeGroup(val family: String?, val codes: List<LegendCode>)

data class ExcludeSection(val category: CodeCategory, val groups: List<ExcludeGroup>)

private val FAMILY = Regex("^([a-z])\\d+$")

/**
 * Arranges the parsed legend for the exclude list: allergens first (numbered ones grouped into
 * families), then additives, then other codes. Built from the legend, so it follows the site.
 */
fun codeSections(codes: List<LegendCode>): List<ExcludeSection> =
    listOf(CodeCategory.ALLERGEN, CodeCategory.ADDITIVE, CodeCategory.OTHER).mapNotNull { category ->
        val inCategory = codes.filter { it.category == category }
        if (inCategory.isEmpty()) return@mapNotNull null
        val groups = if (category == CodeCategory.ALLERGEN) {
            inCategory.groupBy { FAMILY.matchEntire(it.code)?.groupValues?.get(1) ?: it.code }.map { (key, list) ->
                if (list.size >= 2 && FAMILY.matches(list[0].code)) ExcludeGroup(key, list) else ExcludeGroup(null, list)
            }
        } else {
            listOf(ExcludeGroup(null, inCategory))
        }
        ExcludeSection(category, groups)
    }

fun groupState(codes: List<LegendCode>, excluded: Set<String>): ToggleableState {
    val selected = codes.count { it.code in excluded }
    return when (selected) {
        0 -> ToggleableState.Off
        codes.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}

/** Tapping a family header: select everything unless everything is already selected. */
fun groupToggleSelects(codes: List<LegendCode>, excluded: Set<String>): Boolean = groupState(codes, excluded) != ToggleableState.On
