package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import java.text.Normalizer
import java.util.Locale

/**
 * "Vorhandenes Gericht wählen": a search over every dish the app knows, that day's first. Words match anywhere in the
 * name (either language), ignoring case and umlauts: "gemuse", "gemuese" and "Gemüse" all find "Gemüse". Pure.
 */
object DishSearch {
    /**
     * A dish to pick. [price] in mills, null when the menu shows none; [mensaId] null for hand-named dishes. [byWeight]:
     * paid by weight, [price] is per 100 g (null for one known only from an earlier meal: that portion's price says nothing).
     */
    data class Hit(
        val key: String,
        val name: LocalizedText,
        val tags: List<Tag>,
        val price: Int?,
        val mensaId: String?,
        val onDay: Boolean,
        val byWeight: Boolean = false,
        /** A hand-named dish: see [EatenDish.own]. */
        val own: Boolean = false,
    ) {
        private val plain = fold(Favorites.normalize(name.de) + " " + Favorites.normalize(name.en))
        private val collapsed = collapse(plain)

        internal fun matches(word: String) = word in plain || collapse(word) in collapsed

        /** The weight a payment of [amount] buys of a dish paid by weight, else null. */
        fun grams(amount: Int): Int? = price?.takeIf { byWeight && it > 0 }?.let { MealMatcher.estimateGrams(amount, it) }
    }

    private val MARKS = Regex("""\p{Mn}+""")
    private val SPELLED_UMLAUT = Regex("[aou]e")
    private val SEPARATORS = Regex("""[\s   ]+""")

    /** Lowercase, without accents and umlaut dots, ß as ss. */
    internal fun fold(text: String): String =
        MARKS.replace(Normalizer.normalize(text.lowercase(Locale.GERMAN).replace("ß", "ss"), Normalizer.Form.NFD), "")

    /** "gemuese" to "gemuse": the spelled-out umlaut. Used on both sides, next to the plain text (see [Hit.matches]). */
    private fun collapse(folded: String) = SPELLED_UMLAUT.replace(folded) { it.value.take(1) }

    /**
     * Everything to search: the [day]'s dishes first (in that order, so the default Mensa leads), then the [menus] of
     * all days, the [events]' dishes and the [custom] ones by name. A dish once per Mensa; without a Mensa only if no
     * menu or meal names one.
     */
    fun hits(day: List<SnapDish>, menus: List<SnapDish>, events: List<CardEvent>, custom: List<KnownDish>, group: Int): List<Hit> {
        fun SnapDish.hit(onDay: Boolean) =
            Hit(Favorites.normalize(name.de), name, tags, MealMatcher.prices(this, group).firstOrNull(), mensaId, onDay, byWeight)
        val eaten = events.mapNotNull { it.meal }.flatMap { it.dishes }.asReversed() // the latest price wins
            .map { Hit(it.key, it.name, it.tags, it.price.takeIf { _ -> it.grams == null }, it.mensaId, false, it.grams != null, it.own) }
        val named = custom.map { Hit(it.key, it.name, it.tags, it.price, it.mensaId, false, own = it.own) }
        val all = (day.map { it.hit(true) } + menus.map { it.hit(false) } + eaten + named).distinctBy { it.key to it.mensaId }
        val withMensa = all.filter { it.mensaId != null }.map { it.key }.toSet()
        val (today, rest) = all.filterNot { it.mensaId == null && it.key in withMensa }.partition { it.onDay }
        return today + rest.sortedBy { fold(it.name.de) }
    }

    /** The [hits] that contain every word of [query], in their order. */
    fun search(hits: List<Hit>, query: String): List<Hit> {
        val words = query.split(SEPARATORS).map { w -> fold(w).trim { !it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        return hits.filter { hit -> words.all(hit::matches) }
    }
}
