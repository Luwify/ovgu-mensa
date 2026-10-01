package de.luwify.mensamd.data.settings

/** Which color theme the app uses. SYSTEM is the Material You (dynamic) look. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    SOFT("soft"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
