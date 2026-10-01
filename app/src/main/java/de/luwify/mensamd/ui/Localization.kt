package de.luwify.mensamd.ui

import androidx.annotation.StringRes
import de.luwify.mensamd.R
import de.luwify.mensamd.model.LocalizedText

/** German UI language gets German text, everything else English (the site only offers those two). */
fun LocalizedText.forLanguage(language: String): String = if (language == "de") de else en

/**
 * Our own de/en labels for the tag icons. The site's alt text is broken for some of them
 * ("enth lt Alkohol"), so it is only the fallback for keys we don't know yet.
 */
@StringRes
fun tagLabelRes(key: String): Int? = when (key) {
    "vegan" -> R.string.tag_vegan
    "vegetarisch" -> R.string.tag_vegetarisch
    "knoblauch" -> R.string.tag_knoblauch
    "gefluegel" -> R.string.tag_gefluegel
    "schwein" -> R.string.tag_schwein
    "fisch" -> R.string.tag_fisch
    "rind" -> R.string.tag_rind
    "alkohol" -> R.string.tag_alkohol
    "suppe" -> R.string.tag_suppe
    else -> null
}

/**
 * Chip text per filter key. Vegan/vegetarian say what they show, the others what they hide
 * ("No pork"). Null for keys we have no string for: the chip then uses the generic "No <site label>".
 */
@StringRes
fun filterLabelRes(key: String): Int? = when (key) {
    "vegan" -> R.string.tag_vegan
    "vegetarisch" -> R.string.tag_vegetarisch
    "schwein" -> R.string.filter_no_schwein
    "rind" -> R.string.filter_no_rind
    "gefluegel" -> R.string.filter_no_gefluegel
    "fisch" -> R.string.filter_no_fisch
    "knoblauch" -> R.string.filter_no_knoblauch
    "alkohol" -> R.string.filter_no_alkohol
    "suppe" -> R.string.filter_no_suppe
    else -> null
}

/** Own title for a family of numbered allergens (a1-a6, h1-h9); null falls back to the code range. */
@StringRes
fun codeFamilyRes(family: String): Int? = when (family) {
    "a" -> R.string.code_family_a
    "h" -> R.string.code_family_h
    else -> null
}
