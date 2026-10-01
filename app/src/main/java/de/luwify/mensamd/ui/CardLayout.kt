package de.luwify.mensamd.ui

/** Below this screen width (dp) the name needs the full card width. */
const val MIN_WIDTH_FOR_SIDE_TAGS_DP = 360

/** Above this font scale the tag column would squeeze the name too much. */
const val MAX_FONT_SCALE_FOR_SIDE_TAGS = 1.15f

/** Horizontal space the list and card paddings take from the screen width (16 + 16 on each side). */
private const val CARD_CONTENT_INSET_DP = 64

/** Normal card: tags in a right-hand column when there is room, below the name otherwise. */
fun tagsOnRight(screenWidthDp: Int, fontScale: Float): Boolean =
    screenWidthDp >= MIN_WIDTH_FOR_SIDE_TAGS_DP && fontScale <= MAX_FONT_SCALE_FOR_SIDE_TAGS

/** The tag column is capped at about a third of the card content width; the name keeps the rest. */
fun tagColumnMaxWidthDp(screenWidthDp: Int): Float = (screenWidthDp - CARD_CONTENT_INSET_DP) / 3f
