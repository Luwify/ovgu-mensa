package de.luwify.mensamd.data.settings

/** Everything the user can set that changes what the menu pages show; global for all Mensen. */
data class ViewPrefs(
    /** Active tag filters (see `DishFilters`). */
    val tagFilters: Set<String> = emptySet(),
    /** Codes whose dishes are hidden. */
    val excludedCodes: Set<String> = emptySet(),
    /** Index of the highlighted price column; 0 = students. */
    val priceGroup: Int = 0,
    /** Compact dish cards (tap to expand) instead of the normal layout. */
    val compact: Boolean = false,
)
