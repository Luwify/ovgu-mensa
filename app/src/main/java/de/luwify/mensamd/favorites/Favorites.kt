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
    /** Unicode-aware: also nbsp, which a plain `\s` misses. */
    private val WHITESPACE = Regex("""(?U)\s+""")

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
