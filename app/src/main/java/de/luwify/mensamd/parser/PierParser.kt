package de.luwify.mensamd.parser

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.FixedSection
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.Tag
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Parses the PIER 16 page. Unlike the other Mensen it is a hand-edited fixed list: no dates, no
 * legend, one `<tr>` per dish like
 *
 *     <strong>Name</strong> (3, 9,a1,j, k, rin)<br /> English text<br /> 4,20 l 5,90 l 7,30
 *
 * so this is best effort and tolerant: the English line is optional, the separator between prices is
 * whatever the editor typed, and headings (or a row with only a bold title) start a new section.
 * Never throws; a changed, empty or garbled page yields an empty [MenuPage].
 *
 * Codes in parentheses are typed by hand. A dish without a given code is NOT known to be free of
 * that allergen (the editor may have forgotten it), so allergen exclusion can only hide dishes that
 * state a code, never prove a dish safe.
 */
object PierParser {
    private val EMPTY = MenuPage(emptyList(), Legend.EMPTY, emptyList())

    private val PRICE = Regex("""(\d+),(\d{2})""")

    /** A line that is only prices: "4,20 l 5,90 l 7,30", "3,00 | 4,00 €" ... */
    private val PRICE_ONLY = Regex("""^\D{0,3}\d+,\d{2}(?:\s*\D{1,3}\s*\d+,\d{2}){0,3}\s*\D{0,3}$""")

    /** Prices at the end of a line that also holds the name (single-line hand edits). */
    private val TRAILING_PRICES = Regex("""\s(\d+,\d{2}(?:\s*\D{1,3}\s*\d+,\d{2}){0,3})\s*€?$""")

    private val PARENS = Regex("""\(([^()]*)\)""")
    private val CODE = Regex("""^(\d{1,2}|[a-n]\d?|[A-Z])$""")
    private val SHORT_TOKEN = Regex("""^[A-Za-z0-9]{1,4}$""")

    /** The page's own tag abbreviations, mapped to our tag keys where the meaning is clear. */
    private val KNOWN_TAGS = mapOf(
        "kno" to Tag("knoblauch", "Knoblauch"),
        "rin" to Tag("rind", "Rind"),
        "sch" to Tag("schwein", "Schwein"),
        "get" to Tag("gefluegel", "Geflügel"),
        "veg" to Tag("vegan", "Vegan"),
        "vgt" to Tag("vegetarisch", "Vegetarisch"),
    )

    fun parse(html: String): MenuPage = try {
        parseOrThrow(html)
    } catch (_: Exception) {
        EMPTY
    }

    private fun parseOrThrow(html: String): MenuPage {
        val root = Jsoup.parse(html).selectFirst("div.entry-content") ?: return EMPTY
        val sections = mutableListOf<FixedSection>()
        var title: LocalizedText? = null
        var dishes = mutableListOf<Dish>()
        fun flush() {
            if (dishes.isNotEmpty()) sections += FixedSection(title, dishes.toList())
            dishes = mutableListOf()
        }

        for (el in root.select("h2, h3, h4, tr, p, li")) {
            if (el.tagName().startsWith("h")) {
                val heading = normalizeText(el.text()).removeSuffix(":").trim()
                if (heading.isNotEmpty()) {
                    flush()
                    title = LocalizedText(heading)
                }
                continue
            }
            // A <p> or <li> inside a row/list item belongs to that one; don't read it twice.
            if (el.tagName() != "tr" && el.parents().any { it.tagName() == "tr" || it.tagName() == "li" }) continue
            val lines = linesOf(el)
            val dish = parseDish(el, lines)
            if (dish != null) {
                dishes += dish
            } else if (lines.size == 1 && el.selectFirst("strong, b") != null) {
                // A row with just a bold title and nothing else: a category header.
                val header = lines[0].removeSuffix(":").trim()
                if (header.isNotEmpty() && header.length <= 60) {
                    flush()
                    title = LocalizedText(header)
                }
            }
        }
        flush()
        return MenuPage(emptyList(), Legend.EMPTY, sections)
    }

    /** The visible text of an element split at <br> and cell/paragraph boundaries, trimmed, blanks dropped. */
    private fun linesOf(el: Element): List<String> {
        val copy = el.clone()
        copy.select("br").forEach { it.after(TextNode("\n")) }
        copy.select("td, th, p, div, li").forEach { it.appendChild(TextNode("\n")) }
        return copy.wholeText().split('\n').map(::normalizeText).filter { it.isNotEmpty() }
    }

    private fun parseDish(el: Element, lines: List<String>): Dish? {
        val name = el.selectFirst("strong, b")?.text()?.let(::normalizeText)?.removeSuffix(":")?.trim().orEmpty()
        if (name.isEmpty() || PRICE_ONLY.matches(name) || lines.isEmpty()) return null

        // Where the name sits, what follows it on that line, and the lines after it.
        val nameLine = lines.indexOfFirst { name in it }.takeIf { it >= 0 } ?: 0
        val afterName = lines[nameLine].substringAfter(name, "").trim()
        val rest = lines.drop(nameLine + 1)

        val codeSearch = listOf(afterName) + rest
        val group = codeSearch.firstNotNullOfOrNull { text ->
            PARENS.findAll(text).map { it.groupValues[1] }.firstOrNull { g ->
                val tokens = g.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                tokens.isNotEmpty() && tokens.all { SHORT_TOKEN.matches(it) }
            }
        }

        var prices = (rest.firstOrNull { PRICE_ONLY.matches(it) }
            ?: TRAILING_PRICES.find(lines[nameLine])?.groupValues?.get(1)
            ?: afterName.takeIf { PRICE_ONLY.matches(it) })
            ?.let { text -> PRICE.findAll(text).map { it.groupValues[1].toInt() * 100 + it.groupValues[2].toInt() }.toList() }
            .orEmpty()
        if (prices.size > 3) prices = prices.take(3)

        // A bold line with neither prices nor codes nor any other text is a header, not a dish.
        if (prices.isEmpty() && group == null && rest.isEmpty() && afterName.isEmpty()) return null

        val english = rest.firstOrNull { line ->
            !PRICE_ONLY.matches(line) && !(PARENS.matches(line) && group != null && line.contains(group))
        }

        val codes = mutableListOf<String>()
        val tags = LinkedHashMap<String, Tag>()
        for (token in group?.split(',', ';').orEmpty().map { it.trim() }.filter { it.isNotEmpty() }) {
            if (CODE.matches(token)) {
                if (token !in codes) codes += token
            } else {
                val known = KNOWN_TAGS[token.lowercase()]
                // Unknown abbreviations are kept as they are, so a new one shows up as text instead of vanishing.
                val tag = known ?: Tag(token.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { token }, token)
                tags.putIfAbsent(tag.key, tag)
            }
        }

        return Dish(
            name = LocalizedText(name, english ?: name),
            badges = emptyList(),
            prices = prices,
            tags = tags.values.toList(),
            codes = codes,
            co2Grams = null,
            waterLiters = null,
            priceGroupsKnown = prices.size == 3,
        )
    }
}
