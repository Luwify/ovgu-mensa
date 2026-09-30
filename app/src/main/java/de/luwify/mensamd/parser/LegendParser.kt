package de.luwify.mensamd.parser

import de.luwify.mensamd.model.CodeCategory
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.LegendCode
import de.luwify.mensamd.model.LegendNote
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.codeCategory
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Parses `div.legende`. German text sits directly in the div, the English text in `span.grau`.
 * Sections are told apart by content, not by their (hardcoded) headings: a run with `|` is the price
 * scale, a run with `(code)` items is a code list, anything else is a free-text note.
 */
internal object LegendParser {
    private val CODE = Regex("""\(([0-9]{1,2}|[a-n][1-9]?|[A-Z])\)""")

    private class Section(val title: String, val body: String)

    fun parse(root: Element?): Legend {
        if (root == null) return Legend.EMPTY

        val deRoot = root.clone().apply { select("span.grau").remove() }
        val deSections = sections(deRoot)
        val enSpans = root.select("span.grau")
        val enSections = enSpans.flatMap { sections(it) }

        val dePrice = deSections.firstOrNull { '|' in it.body }
        val enPrice = enSpans.firstOrNull { it.selectFirst("b") == null && '|' in it.text() }
            ?.text()?.substringAfter(':')?.trim()
        val priceGroups = dePrice?.body?.let { de ->
            val deNames = de.split('|').map { it.trim() }
            val enNames = enPrice?.split('|')?.map { it.trim() }.orEmpty()
            deNames.mapIndexed { i, name -> LocalizedText(name, enNames.getOrElse(i) { name }) }
        }.orEmpty()

        val deRest = deSections.filter { it !== dePrice }
        val enRest = enSections.filter { '|' !in it.body }

        val de = deRest.filter { CODE.containsMatchIn(it.body) }.flatMap { codeEntries(it.body) }
        val en = enRest.filter { CODE.containsMatchIn(it.body) }.flatMap { codeEntries(it.body) }.toMap()
        val deMap = de.toMap()
        // German list misses some English-only codes (h7-h9), so take the union.
        val codes = (deMap.keys + en.keys).map { code ->
            val text = LocalizedText(deMap[code] ?: en.getValue(code), en[code] ?: deMap.getValue(code))
            LegendCode(code, codeCategory(code), text)
        }.sortedWith(
            compareBy<LegendCode>({ it.category }, { it.code.filter(Char::isLetter) }, { it.code.filter(Char::isDigit).toIntOrNull() ?: 0 }),
        )

        val deNotes = deRest.filterNot { CODE.containsMatchIn(it.body) }
        val enNotes = enRest.filterNot { CODE.containsMatchIn(it.body) }
        val notes = deNotes.mapIndexed { i, s ->
            val e = enNotes.getOrNull(i)
            LegendNote(
                title = LocalizedText(s.title, e?.title ?: s.title),
                text = LocalizedText(s.body, e?.body ?: s.body),
            )
        }

        return Legend(priceGroups, codes, notes)
    }

    /** `<b>Title:</b> body up to the next <br>` for every bold heading ending in a colon. */
    private fun sections(root: Element): List<Section> = root.select("b").mapNotNull { b ->
        val title = b.text().trim()
        if (!title.endsWith(":")) return@mapNotNull null
        val body = StringBuilder()
        var n = b.nextSibling()
        while (n != null && !(n is Element && (n.tagName() == "br" || n.tagName() == "b"))) {
            body.append(if (n is TextNode) n.text() else (n as? Element)?.text().orEmpty()).append(' ')
            n = n.nextSibling()
        }
        Section(title.removeSuffix(":").trim(), normalizeText(body.toString()))
    }

    /** "mit Farbstoff (1), mit Konservierungsstoff (2)" -> [("1","mit Farbstoff"), ("2", ...)] */
    private fun codeEntries(body: String): List<Pair<String, String>> {
        var start = 0
        return CODE.findAll(body).map { m ->
            val text = body.substring(start, m.range.first).trimStart(',', ' ').trim()
            start = m.range.last + 1
            m.groupValues[1] to text
        }.toList()
    }
}
