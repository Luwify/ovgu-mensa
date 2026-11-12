package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag

/**
 * "Mehrere Gerichte": one payment split into any number of parts, each with its own price (mills). A part is a dish (from
 * a menu, or a hand-named one already saved), a new dish (saved as a hand-named dish with its Mensa, tags and price, so it
 * is a candidate next time, see [MenuDishes.own]) or an extra. Pure.
 */
sealed interface MealPart {
    val price: Int

    /** A known dish: from a menu ([own] false) or saved by hand earlier ([own] true). */
    data class Dish(val name: LocalizedText, val mensaId: String?, val tags: List<Tag>, val own: Boolean, override val price: Int) : MealPart

    /** A dish named now: saved with this Mensa, tags and price. */
    data class NewDish(val name: String, val mensaId: String?, val tags: List<Tag>, override val price: Int) : MealPart

    /** An extra (key, see [Extras]); all extras of a meal share their total. */
    data class Extra(val key: String, override val price: Int) : MealPart
}

object MealParts {
    /** What is still to assign of [amount] (negative: the parts cost more). */
    fun rest(amount: Int, prices: List<Int?>): Int = amount - prices.sumOf { it ?: 0 }

    /** The parts make a meal: at least one, every price positive, and together exactly [amount]. */
    fun valid(amount: Int, parts: List<MealPart>): Boolean =
        parts.isNotEmpty() && parts.all { it.price > 0 } && parts.sumOf { it.price } == amount

    /**
     * Sets [parts] as payment [eventId]'s meal: new dishes are saved first (see [StatsOps.addCustomDish]); the same dish at
     * the same price twice becomes one dish eaten twice; the extras go together with their total. Nothing changes when the
     * parts don't add up to the payment.
     */
    fun apply(data: StatsData, eventId: Long, parts: List<MealPart>): StatsData {
        val event = data.events.firstOrNull { it.id == eventId && it.kind == EventKind.PAYMENT } ?: return data
        if (!valid(event.amount, parts)) return data
        var next = data
        val eaten = parts.mapNotNull { part ->
            when (part) {
                is MealPart.Dish -> EatenDish(Favorites.normalize(part.name.de), part.name, part.mensaId, part.price, 1, part.tags, own = part.own)
                is MealPart.NewDish -> StatsOps.addCustomDish(next, part.name, part.price, part.mensaId, part.tags).let { (d, dish) ->
                    next = d
                    dish
                }
                is MealPart.Extra -> null
            }
        }
        val dishes = eaten.groupBy { Triple(it.key, it.mensaId, it.price) }.values.map { same -> same.first().copy(count = same.sumOf { it.count }) }
        val extras = parts.filterIsInstance<MealPart.Extra>()
        val meal = MealRecord(dishes = dishes, extras = extras.map { it.key }.distinct(), extrasAmount = extras.sumOf { it.price })
        return StatsOps.setMeal(next, eventId, meal)
    }
}
