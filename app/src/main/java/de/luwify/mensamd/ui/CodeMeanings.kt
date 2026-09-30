package de.luwify.mensamd.ui

import de.luwify.mensamd.model.CodeCategory
import de.luwify.mensamd.model.LegendCode
import de.luwify.mensamd.model.codeCategory

/** One code of a dish with its meaning, or null text when the legend doesn't know the code. */
data class CodeMeaning(val code: String, val text: String?)

data class CodeGroup(val category: CodeCategory, val codes: List<CodeMeaning>)

/**
 * Groups a dish's codes by category (additives, allergens, other), each group in legend order.
 * Codes missing from the legend are kept, without text, and never dropped.
 */
fun groupCodes(dishCodes: List<String>, legend: Map<String, LegendCode>, language: String): List<CodeGroup> =
    dishCodes.distinct()
        .map { code -> CodeMeaning(code, legend[code]?.text?.forLanguage(language)) }
        .groupBy { codeCategory(it.code) }
        .map { (category, codes) -> CodeGroup(category, codes.sortedWith(codeOrder)) }
        .sortedBy { it.category }

private val codeOrder = compareBy<CodeMeaning>(
    { it.code.filter(Char::isLetter) },
    { it.code.filter(Char::isDigit).toIntOrNull() ?: 0 },
)
