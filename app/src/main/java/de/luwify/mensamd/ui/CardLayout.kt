package de.luwify.mensamd.ui

/** Below this window width (dp) the tags always go on their own row below the price. */
const val MIN_WIDTH_FOR_TAGS_BESIDE_PRICE_DP = 360

/** Above this font scale the tags always go on their own row below the price. */
const val MAX_FONT_SCALE_FOR_TAGS_BESIDE_PRICE = 1.15f

/**
 * Normal card: may the tags sit right-aligned on the price row? Only on normal-sized windows and
 * fonts. (Also needs the pill to be collapsed and the tags to fit, decided where they are measured.)
 */
fun tagsBesidePrice(windowWidthDp: Int, fontScale: Float): Boolean =
    windowWidthDp >= MIN_WIDTH_FOR_TAGS_BESIDE_PRICE_DP && fontScale <= MAX_FONT_SCALE_FOR_TAGS_BESIDE_PRICE

/** The tags stay on the price row only if pill, gap and the tags in one line fit; the pill is never squeezed. */
fun fitsBesidePrice(priceWidth: Int, gap: Int, tagsWidth: Int, maxWidth: Int): Boolean =
    priceWidth + gap + tagsWidth <= maxWidth
