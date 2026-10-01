package de.luwify.mensamd.data.settings

/** The page brightness of the user's own theme. */
enum class CustomBase(val key: String) {
    /** Light or dark following the system setting. */
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),

    /** Dark with a pure black page, like AMOLED. */
    BLACK("black"),
    ;

    companion object {
        fun fromKey(key: String?): CustomBase = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** The colors of the custom theme the user can set one by one in the advanced color editor ("Farbeditor"). */
enum class ColorRole(val key: String) {
    BACKGROUND("bg"),
    CARD("card"),
    TEXT("text"),

    /** The card edge (hairline, or the glow edge when the glow border is on). */
    CARD_BORDER("border"),

    /** The selected price's pill. */
    PRICE("price"),

    /** Tag chips (Fisch, Knoblauch, ...). */
    TAG("tag"),

    /** Prefix badges (Save the meal, Aktionstheke, ...). */
    BADGE("badge"),
    FAVORITE("heart"),
    ;

    companion object {
        fun fromKey(key: String): ColorRole? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The user's own theme (ThemeMode.CUSTOM): everything else is generated from these, with contrast guaranteed
 * (see `CustomTheme`). [accent] is the chosen accent color as 0xRRGGBB (swatch, hue slider, or typed as hex/RGB).
 */
data class CustomThemeSpec(
    val base: CustomBase = CustomBase.SYSTEM,
    val accent: Int = DEFAULT_ACCENT,
    /** The AMOLED-style glowing card border, in the accent color. */
    val glow: Boolean = false,
    /**
     * Colors set one by one in the advanced editor (0xRRGGBB); a missing role is generated. They are wishes: text
     * on them, and colors that must be readable themselves (badges, heart, glow edge), are still nudged to contrast.
     */
    val overrides: Map<ColorRole, Int> = emptyMap(),
) {
    /** Whether the theme is dark, given the system setting. */
    fun isDark(systemDark: Boolean): Boolean = when (base) {
        CustomBase.SYSTEM -> systemDark
        CustomBase.LIGHT -> false
        CustomBase.DARK, CustomBase.BLACK -> true
    }

    companion object {
        /** Violet (the violet swatch: hue 265, HSL saturation 62 %, lightness 52 %): unlike every built-in theme. */
        const val DEFAULT_ACCENT = 0x7839D0

        /** "bg=101010,card=1A1A1A" — unknown roles and broken entries are skipped. */
        fun encodeOverrides(overrides: Map<ColorRole, Int>): String =
            overrides.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.key}=%06X".format(it.value and 0xFFFFFF) }

        fun decodeOverrides(text: String?): Map<ColorRole, Int> = text.orEmpty().split(',').mapNotNull { entry ->
            val role = ColorRole.fromKey(entry.substringBefore('=', "")) ?: return@mapNotNull null
            val rgb = entry.substringAfter('=', "").takeIf { it.length == 6 }?.toIntOrNull(16) ?: return@mapNotNull null
            role to rgb
        }.toMap()
    }
}

/** The theme as the UI needs it: the mode plus the custom spec (used only when the mode is CUSTOM). */
data class ThemeChoice(val mode: ThemeMode, val custom: CustomThemeSpec = CustomThemeSpec())
