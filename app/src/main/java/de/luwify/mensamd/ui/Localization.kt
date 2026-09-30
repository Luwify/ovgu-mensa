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
