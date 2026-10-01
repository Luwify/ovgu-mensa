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

/**
 * The user's own theme (ThemeMode.CUSTOM): everything else is generated from these, with contrast guaranteed
 * (see `CustomTheme`). [accent] is the chosen accent color as 0xRRGGBB (swatch, hue slider, or typed as hex/RGB).
 */
data class CustomThemeSpec(
    val base: CustomBase = CustomBase.SYSTEM,
    val accent: Int = DEFAULT_ACCENT,
    /** The AMOLED-style glowing card border, in the accent color. */
    val glow: Boolean = false,
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
    }
}

/** The theme as the UI needs it: the mode plus the custom spec (used only when the mode is CUSTOM). */
data class ThemeChoice(val mode: ThemeMode, val custom: CustomThemeSpec = CustomThemeSpec())
