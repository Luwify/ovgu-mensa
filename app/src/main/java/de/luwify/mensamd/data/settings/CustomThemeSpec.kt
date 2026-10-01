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
 * (see `CustomTheme`). [hue] is the accent hue in degrees, 0..359.
 */
data class CustomThemeSpec(
    val base: CustomBase = CustomBase.SYSTEM,
    val hue: Int = DEFAULT_HUE,
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
        const val DEFAULT_HUE = 265 // violet: unlike every built-in theme
    }
}

/** The theme as the UI needs it: the mode plus the custom spec (used only when the mode is CUSTOM). */
data class ThemeChoice(val mode: ThemeMode, val custom: CustomThemeSpec = CustomThemeSpec())
