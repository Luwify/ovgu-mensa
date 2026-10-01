package de.luwify.mensamd.ui

/**
 * Glyph for a tag's small marker in compact cards. Plain Unicode emoji (no site icons, nothing
 * copied). Vegan is not here: it uses the drawn sunflower. Null means "no glyph, use a letter".
 */
fun tagMarkerGlyph(key: String): String? = when (key) {
    "vegetarisch" -> "🥕" // carrot
    "knoblauch" -> "🧄" // garlic (Unicode 12; falls back to a letter on older fonts)
    "gefluegel" -> "🐔" // chicken
    "schwein" -> "🐖" // pig
    "fisch" -> "🐟" // fish
    "rind" -> "🐄" // cow
    "alkohol" -> "🍷" // wine glass
    "suppe" -> "🍲" // pot of food
    else -> null
}

/** Fallback marker text: the first letter of the label, so unknown tags still get a marker. */
fun tagMarkerLetter(label: String, key: String): String =
    (label.trim().firstOrNull { it.isLetterOrDigit() } ?: key.firstOrNull() ?: '?').uppercase()
