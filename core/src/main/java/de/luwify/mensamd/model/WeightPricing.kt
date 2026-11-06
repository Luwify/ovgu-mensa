package de.luwify.mensamd.model

/**
 * Dishes paid by weight: the price on the menu is per 100 g. The pages never say so (no "100 g", label or note anywhere;
 * all pages checked 2026-11-06), so these rules decide. This is the only place that knows: add a rule to [RULES].
 */
object WeightPricing {
    private const val UNICAMPUS_UNTEN = "unicampus-unten"

    /** The soup icon (`mensasym_suppe`). Fixed-price soups and stews elsewhere have no icon or cost a portion. */
    private const val SOUP_TAG = "suppe"

    private val RULES: List<(mensaId: String, dish: Dish) -> Boolean> = listOf(
        // Badge "Pastabuffet", English "pasta buffet"; a badge may have only one language, so either side is enough.
        { _, dish -> dish.badges.any { it.de.contains("pastabuffet", ignoreCase = true) || it.en.contains("pasta buffet", ignoreCase = true) } },
        // The soup at UniCampus unten is ladled and weighed.
        { mensaId, dish -> mensaId == UNICAMPUS_UNTEN && dish.tags.any { it.key == SOUP_TAG } },
    )

    fun isPer100g(mensaId: String, dish: Dish): Boolean = RULES.any { it(mensaId, dish) }
}
