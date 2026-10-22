package de.luwify.mensamd.parser

import org.jsoup.Jsoup

object PageCheck {
    /**
     * The page has the WordPress content block both parsers read (`div.entry-content`). A real menu page always has
     * it, even with no dishes (a closed Mensa); maintenance and PHP error pages, often sent with status 200, don't.
     */
    fun hasContentBlock(html: String): Boolean = Jsoup.parse(html).selectFirst("div.entry-content") != null
}
