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
    /** Favorite dishes as normalized names (see `Favorites`). */
    val favorites: Set<String> = emptySet(),
    /** The user's filter chip order (see `DishFilters.mergeOrder`); empty = default order. */
    val filterOrder: List<String> = emptyList(),
    /** "Favoriten funkeln": favorite dishes get the shimmering gold aura with sparkles. */
    val legendary: Boolean = true,
)
