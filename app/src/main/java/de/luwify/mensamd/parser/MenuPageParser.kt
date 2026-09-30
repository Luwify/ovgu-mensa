package de.luwify.mensamd.parser

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.Tag
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Parses the standard Studentenwerk Magdeburg menu page (everything except PIER 16).
 *
 * Pure JVM, never throws on odd markup: a day whose heading has no date is dropped, a dish without a
 * name is dropped, every other missing part becomes empty/null.
 */
object MenuPageParser {
    private val DATE = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""")
    private val PRICE = Regex("""(\d+),(\d{2})""")
    private val CODE = Regex("""\(([^()]+)\)""")
    private val CO2 = Regex("""(\d+)\s*g\b""")
    private val WATER = Regex("""(\d+)\s*l\b""")
    private val TAG_KEY = Regex("""mensasym_([A-Za-z0-9_]+)\.""")

    /** "Save the meal: ...", "Aktionstheke: ...": a short label (no digits/commas) then a colon and a space. */
    private val PREFIX = Regex("""^([^:,\d]{2,60}):\s+(\S.*)$""")

    fun parse(html: String): MenuPage {
        val doc = Jsoup.parse(html)
        val days = doc.select("div.mensa table").mapNotNull(::parseDay).sortedBy { it.date }
        return MenuPage(days, LegendParser.parse(doc.selectFirst("div.legende")))
    }

    private fun parseDay(table: Element): MenuDay? {
        val heading = table.selectFirst("thead")?.text().orEmpty()
        val date = DATE.find(heading)?.let { m ->
            try {
                LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
            } catch (_: DateTimeException) {
                null
            }
        } ?: return null
        val dishes = table.select("tbody > tr").mapNotNull(::parseDish)
        return MenuDay(date, dishes)
    }

    private fun parseDish(row: Element): Dish? {
        val cell = row.selectFirst("td") ?: return null

        val de = splitBadges(normalizeText(cell.clone().apply { select("span.grau, span.mensapreis").remove() }.text()))
        val enText = cell.selectFirst("span.grau")?.text()?.let(::normalizeText).orEmpty()
        val en = splitBadges(enText)
        if (de.second.isEmpty()) return null
        val badges = (0 until maxOf(de.first.size, en.first.size)).map { i ->
            val d = de.first.getOrNull(i)
            val e = en.first.getOrNull(i)
            LocalizedText(d ?: e!!, e ?: d!!)
        }

        val prices = row.selectFirst("span.mensapreis")?.text().orEmpty().let { text ->
            PRICE.findAll(text).map { it.groupValues[1].toInt() * 100 + it.groupValues[2].toInt() }.toList()
        }

        val tags = LinkedHashMap<String, Tag>() // the page duplicates every icon inside <noscript>
        for (img in row.select("div.mensakennsymb img")) {
            val src = img.attr("data-src").ifEmpty { img.attr("src") }
            val key = TAG_KEY.find(src)?.groupValues?.get(1) ?: continue
            val label = normalizeText(img.attr("alt")).removePrefix("Symbol ").trim().ifEmpty { key }
            tags.putIfAbsent(key, Tag(key, label))
        }

        val codes = row.select("div.mensakennzahl").text().let { text ->
            CODE.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        }

        val eaternity = row.select("div.mensaeaternity").text()
        return Dish(
            name = LocalizedText(de.second, en.second.ifEmpty { de.second }),
            badges = badges,
            prices = prices,
            tags = tags.values.toList(),
            codes = codes,
            co2Grams = CO2.find(eaternity)?.groupValues?.get(1)?.toIntOrNull(),
            waterLiters = WATER.find(eaternity)?.groupValues?.get(1)?.toIntOrNull(),
        )
    }

    /** Peels off leading "label:" prefixes (they can nest) and returns (labels, remaining name). */
    private fun splitBadges(text: String): Pair<List<String>, String> {
        val labels = mutableListOf<String>()
        var rest = text
        while (true) {
            val m = PREFIX.find(rest) ?: break
            labels += m.groupValues[1].trim()
            rest = m.groupValues[2]
        }
        return labels to rest
    }
}

/** Collapses whitespace (incl. nbsp) and the site's " , " list separators. */
internal fun normalizeText(s: String): String =
    s.replace(' ', ' ').replace(Regex("""\s+"""), " ").replace(" ,", ",").trim()
