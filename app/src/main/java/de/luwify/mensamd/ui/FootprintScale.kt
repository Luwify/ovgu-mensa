package de.luwify.mensamd.ui

import de.luwify.mensamd.model.MenuDay
import java.time.LocalDate

/** Value range of one metric over one day of one Mensa. [level] is 0 (lowest) to 1 (highest). */
data class MetricScale(val min: Int, val max: Int) {
    fun level(value: Int): Float = ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)
}

/** A null scale means "show neutral": too few data points, or no meaningful spread. */
data class DayFootprint(val co2: MetricScale?, val water: MetricScale?)

/** Fewer data points than this say nothing about "relative to the others". */
const val MIN_FOOTPRINT_POINTS = 3

/** Values closer than this (spread as a share of the maximum) count as "all the same". */
const val CLOSE_RATIO = 0.2f

fun metricScale(values: List<Int>): MetricScale? {
    if (values.size < MIN_FOOTPRINT_POINTS) return null
    val min = values.min()
    val max = values.max()
    if (max <= 0 || (max - min).toFloat() < CLOSE_RATIO * max) return null
    return MetricScale(min, max)
}

/**
 * One scale per day and metric, from every dish of that day that has data. Pass the days *before*
 * tag/code filtering so the colors don't shift when the user filters.
 */
fun dayFootprints(days: List<MenuDay>): Map<LocalDate, DayFootprint> = days.associate { day ->
    day.date to DayFootprint(
        co2 = metricScale(day.dishes.mapNotNull { it.co2Grams }),
        water = metricScale(day.dishes.mapNotNull { it.waterLiters }),
    )
}
