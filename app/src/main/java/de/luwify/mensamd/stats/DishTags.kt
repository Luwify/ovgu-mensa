package de.luwify.mensamd.stats

import de.luwify.mensamd.model.Tag

/**
 * The tags a user can give a dish they named by hand: the ones the menus use (same keys as the filter chips and
 * `ui/tagLabelRes`), in the chips' order. The label is the German one, like the site's; the screens show their own
 * translation. Pure.
 */
object DishTags {
    val selectable: List<Tag> = listOf(
        Tag("vegan", "Vegan"),
        Tag("vegetarisch", "Vegetarisch"),
        Tag("schwein", "Schwein"),
        Tag("rind", "Rind"),
        Tag("gefluegel", "Geflügel"),
        Tag("fisch", "Fisch"),
        Tag("knoblauch", "Knoblauch"),
        Tag("alkohol", "Alkohol"),
        Tag("suppe", "Suppe"),
    )

    /**
     * What to offer for a dish that already has [current] tags: the [selectable] ones, then any other tag the dish
     * carries (a key the site added), so editing never drops a tag unseen.
     */
    fun offered(current: List<Tag>): List<Tag> =
        selectable + current.filter { c -> selectable.none { it.key == c.key } }.distinctBy { it.key }

    /** The tags for the selected [keys], in the order of [offered]; keys nobody offers are ignored. */
    fun pick(keys: Set<String>, current: List<Tag>): List<Tag> = offered(current).filter { it.key in keys }
}
