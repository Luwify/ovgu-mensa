package de.luwify.mensamd.favorites

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.parser.MenuPageParser
import java.text.Normalizer
import java.util.Locale

/**
 * A favorite is the normalized German dish name: the same dish is recognized on every day and in every Mensa,
 * by exact match of that key only (no fuzzy matching). Pure Kotlin.
 */
object Favorites {
    /**
     * Also the no-break spaces, which a plain `\s` misses on the JVM. Spelled out instead of the `(?U)` flag:
     * Android's ICU regex engine rejects that flag (crash at class init).
     */
    private val WHITESPACE = Regex("""[\s\u00A0\u2007\u202F]+""")

    /**
     * Lowercase, whitespace collapsed (incl. nbsp), leading "label:" badges removed (the parser already strips
     * them from dish names; this keeps the key stable if one slips through), punctuation trimmed at both ends.
     * Punctuation inside the name stays: "Spaghetti, Tomatensoße" and "Spaghetti Tomatensoße" are different dishes.
     */
    fun normalize(name: String): String {
        val text = Normalizer.normalize(name, Normalizer.Form.NFC).replace(WHITESPACE, " ").trim()
        return MenuPageParser.splitBadges(text).second
            .lowercase(Locale.GERMAN)
            .trim { !it.isLetterOrDigit() }
            .replace(WHITESPACE, " ")
    }

    fun key(dish: Dish): String = normalize(dish.name.de)

    fun isFavorite(dish: Dish, favorites: Set<String>): Boolean = favorites.isNotEmpty() && key(dish) in favorites
}
