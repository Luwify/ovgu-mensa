package de.luwify.mensamd.data.settings

/** Which color theme the app uses. SYSTEM is the subtle Material You (dynamic) look. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    SOFT("soft"),

    /** Always dark: pure black background, very dark gray cards, blue accent. */
    AMOLED("amoled"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
