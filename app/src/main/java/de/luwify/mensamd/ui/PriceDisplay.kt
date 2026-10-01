package de.luwify.mensamd.ui

/** Which price column to highlight, and which columns are left for the expandable part. */
data class PriceSplit(val selected: Int, val others: List<Int>)

/**
 * [prices] are in legend order. If the chosen group has no price on this dish (a page with only two
 * prices, or a stored group out of range) the first price is highlighted instead, so a dish
 * never shows no highlighted price. Null when the dish has no prices at all.
 */
fun splitPrices(prices: List<Int>, preferred: Int): PriceSplit? {
    if (prices.isEmpty()) return null
    val selected = if (preferred in prices.indices) preferred else 0
    return PriceSplit(selected, prices.indices.filter { it != selected })
}
