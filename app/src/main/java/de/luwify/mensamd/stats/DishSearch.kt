package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.LocalizedText
import java.text.Normalizer
import java.util.Locale

/**
 * Text matching for the question screen's search ([MealDraft.search]). Words match anywhere in the name (either
 * language), ignoring case and umlauts: "gemuse", "gemuese" and "Gemüse" all find "Gemüse". Pure.
 */
object DishSearch {
    /** A name prepared for matching. */
    class Text(name: LocalizedText) {
        private val plain = fold(Favorites.normalize(name.de) + " " + Favorites.normalize(name.en))
        private val collapsed = collapse(plain)

        /** [word] (from [words]) occurs in the name. */
        fun contains(word: String): Boolean = word in plain || collapse(word) in collapsed

        /** The name (or one of its words) starts with [word]. */
        fun startsWith(word: String): Boolean {
            val c = collapse(word)
            return plain.split(' ').any { it.startsWith(word) } || collapsed.split(' ').any { it.startsWith(c) }
        }
    }

    private val MARKS = Regex("""\p{Mn}+""")
    private val SPELLED_UMLAUT = Regex("[aou]e")
    private val SEPARATORS = Regex("""[\s   ]+""")

    /** Lowercase, without accents and umlaut dots, ß as ss. */
    internal fun fold(text: String): String =
        MARKS.replace(Normalizer.normalize(text.lowercase(Locale.GERMAN).replace("ß", "ss"), Normalizer.Form.NFD), "")

    /** "gemuese" to "gemuse": the spelled-out umlaut. Used on both sides, next to the plain text (see [Text.contains]). */
    private fun collapse(folded: String) = SPELLED_UMLAUT.replace(folded) { it.value.take(1) }

    /** The words of a query, folded, without punctuation at their ends. */
    fun words(query: String): List<String> =
        query.split(SEPARATORS).map { w -> fold(w).trim { !it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
}
