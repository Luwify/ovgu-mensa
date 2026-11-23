package de.luwify.mensamd.stats

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import java.time.LocalDate

/**
 * One thing a payment bought, as the question screen lists it: a dish ([name] set) or an extra ([name] null, named by its
 * [key], see [Extras]). [price] is per piece in mills; for a weighed item it is the portion's price and [per100] (if known)
 * gives the weight. [day]: the day this item was bought, when the payment is spread over several days (null: the payment's
 * day). [isNew]: named just now ("neu anlegen"), saved as a hand-named dish together with the meal.
 */
data class MealItem(
    val key: String,
    val name: LocalizedText? = null,
    val mensaId: String? = null,
    val tags: List<Tag> = emptyList(),
    val own: Boolean = false,
    val isNew: Boolean = false,
    val count: Int = 1,
    val price: Int,
    val per100: Int? = null,
    val day: LocalDate? = null,
    /** Paid by weight: never counted together with another portion; [per100] may be unknown. */
    val weighed: Boolean = per100 != null,
    /** A dish named now and paid by weight: saved with this price per 100 g (null: unknown). */
    val newPer100: Int? = null,
) {
    val isExtra: Boolean get() = name == null
    val total: Int get() = price * count

    /** About how many grams [price] bought of a weighed item, else null. */
    val grams: Int? get() = per100?.takeIf { it > 0 }?.let { MealMatcher.estimateGrams(price, it) }
}

/**
 * Something the search offers: a dish of a menu (of the payment's day or another), a dish eaten before, a hand-named one,
 * or an extra. [price] fixed (mills), or null: it takes what is left of the payment ([per100] then estimates the weight).
 * [day]: the menu day it comes from, for a payment whose day isn't known. [tier]: how likely it is (lower first): 0 a dish
 * of that day at exactly what is left, 1 the other dishes of that day, 2 hand-named dishes, 3 extras, 4 everything else
 * (shown only for a search).
 */
data class MealOption(
    val key: String,
    val name: LocalizedText,
    val mensaId: String?,
    val tags: List<Tag>,
    val price: Int?,
    val tier: Int,
    val extra: Boolean = false,
    val own: Boolean = false,
    val per100: Int? = null,
    val day: LocalDate? = null,
    val byWeight: Boolean = per100 != null,
) {
    /** Without a fixed price the user says how much of the payment it was (the rest is offered). */
    val takesRest: Boolean get() = price == null

    private val text = DishSearch.Text(name)

    fun matches(words: List<String>): Boolean = words.all(text::contains)
    fun startsWith(word: String): Boolean = text.startsWith(word)
}

/** A complete answer for the amount: a [candidate] of that [day]'s menu (null when the menu had no day). */
data class Suggestion(val day: LocalDate?, val candidate: MealMatcher.Candidate)

/**
 * The question screen's logic ("Was hast du gekauft?"), pure. A payment is answered with a list of [MealItem]s that adds
 * up to its amount: a suggestion fills it at once, a pick from the search adds one item (a fixed price, capped at what is
 * left; anything without a fixed price takes what is left), and whatever stays open can go to "Sonstiges".
 */
object MealDraft {
    /** Suggestions shown at most. */
    const val MAX_SUGGESTIONS = 3

    fun sum(items: List<MealItem>): Int = items.sumOf { it.total }

    /** What is still open of [amount] (negative: the items cost more). */
    fun rest(amount: Int, items: List<MealItem>): Int = amount - sum(items)

    /** The items add up to [amount] exactly and are each worth something. */
    fun complete(amount: Int, items: List<MealItem>): Boolean =
        items.isNotEmpty() && items.all { it.price > 0 } && rest(amount, items) == 0

    /**
     * Adds [option] to [items]: at [price] when the user gave one (an option without a fixed price asks), else its fixed
     * price, at most what is left, or what is left. Nothing changes when nothing is left (and no price was given). The
     * same dish at the same price and day is counted ("2× Chili").
     */
    fun pick(amount: Int, items: List<MealItem>, option: MealOption, price: Int? = null): List<MealItem> {
        val rest = rest(amount, items)
        if (price == null && rest <= 0) return items
        val paid = price ?: option.price?.coerceAtMost(rest) ?: rest
        if (paid <= 0) return items
        val item = MealItem(
            key = option.key,
            name = option.name.takeUnless { option.extra },
            mensaId = option.mensaId,
            tags = option.tags,
            own = option.own,
            price = paid,
            per100 = option.per100,
            day = option.day,
            weighed = option.byWeight,
        )
        return add(items, item)
    }

    /** A dish named now ("„…“ neu anlegen"), at [price] (mills); [byWeight] with its price per 100 g, if known. */
    fun newDish(name: String, mensaId: String?, tags: List<Tag>, price: Int, day: LocalDate? = null, byWeight: Boolean = false, per100: Int? = null): MealItem {
        val clean = name.trim()
        val p100 = per100?.takeIf { byWeight && it > 0 }
        return MealItem(
            Favorites.normalize(clean), LocalizedText(clean), mensaId, tags, own = true, isNew = true, price = price, day = day,
            per100 = p100, weighed = byWeight, newPer100 = p100,
        )
    }

    /** Adds [item], counting it to an equal one (same dish, Mensa, price and day; never weighed items or extras). */
    fun add(items: List<MealItem>, item: MealItem): List<MealItem> {
        val i = items.indexOfFirst {
            !it.isExtra && !item.isExtra && !it.weighed && !item.weighed && it.key == item.key &&
                it.mensaId == item.mensaId && it.price == item.price && it.day == item.day && it.isNew == item.isNew
        }
        return if (i < 0) items + item else items.mapIndexed { j, it -> if (j == i) it.copy(count = it.count + item.count) else it }
    }

    /** Removes one piece of item [index] (the whole item when it was one). */
    fun removeOne(items: List<MealItem>, index: Int): List<MealItem> {
        val item = items.getOrNull(index) ?: return items
        return if (item.count > 1) items.mapIndexed { j, it -> if (j == index) it.copy(count = it.count - 1) else it } else items.filterIndexed { j, _ -> j != index }
    }

    /** "Rest als Sonstiges": what is left goes to "Sonstiges". */
    fun restAsOther(amount: Int, items: List<MealItem>): List<MealItem> {
        val rest = rest(amount, items)
        return if (rest <= 0) items else items + MealItem(Extras.OTHER, price = rest)
    }

    /**
     * A single pick that answers the whole payment is saved at once: [before] was empty and [after] is one complete item.
     * Several items wait for "Speichern" (a weighed one may still be adjusted).
     */
    fun savesAtOnce(amount: Int, before: List<MealItem>, after: List<MealItem>): Boolean =
        before.isEmpty() && after.size == 1 && complete(amount, after)

    /** The items of a saved meal, for a correction. */
    fun fromRecord(record: MealRecord?, amount: Int): List<MealItem> {
        if (record == null) return emptyList()
        if (record.other) return listOf(MealItem(Extras.OTHER, price = amount))
        fun per100(price: Int, grams: Int?) = grams?.takeIf { it > 0 }?.let { ((price.toLong() * 100 + it / 2) / it).toInt() }
        return record.dishes.map {
            MealItem(it.key, it.name, it.mensaId, it.tags, it.own, count = it.count, price = it.price, per100 = per100(it.price, it.grams), weighed = it.grams != null)
        } +
            record.extras.map { MealItem(it.key, price = it.amount, per100 = per100(it.amount, it.grams)) }
    }

    /** The meal the [items] make (each extra key once, with its amount); only "Sonstiges" = the whole payment is other. */
    fun toRecord(items: List<MealItem>): MealRecord {
        val dishes = items.filterNot { it.isExtra }.map {
            EatenDish(it.key, it.name!!, it.mensaId, it.price, it.count, it.tags, it.grams, it.own || it.isNew)
        }
        val extras = items.filter { it.isExtra }.groupBy { it.key }.map { (key, list) ->
            val grams = list.map { it.grams }
            ExtraItem(key, list.sumOf { it.total }, if (grams.all { it != null }) grams.sumOf { it!! } else null)
        }
        return if (dishes.isEmpty() && extras.isNotEmpty() && extras.all { it.key == Extras.OTHER }) MealRecord(other = true)
        else MealRecord(dishes, extras)
    }

    /**
     * Saves [items] as payment [eventId]'s meal (they must add up to it). Dishes named now are saved as hand-named dishes
     * first; a changed Mensa or tags of a hand-named one reach its other meals. [date]: the day the user chose for the payment (null: unchanged). Items with their own [MealItem.day] on
     * different days split the payment into one payment per day, each with its part of the items.
     */
    fun save(data: StatsData, eventId: Long, items: List<MealItem>, date: LocalDate?): StatsData {
        val event = data.events.firstOrNull { it.id == eventId && it.kind == EventKind.PAYMENT } ?: return data
        if (!complete(event.amount, items)) return data
        var next = data
        items.filter { it.isNew }.distinctBy { it.key }.forEach {
            next = StatsOps.addCustomDish(next, it.name!!.de, it.price, it.mensaId, it.tags, it.weighed, it.newPer100).first
        }
        // A hand-named dish is one dish for the app: a Mensa or tags changed here reach its other meals too.
        items.filter { it.own && !it.isNew }.forEach { next = StatsOps.followOwnDish(next, it.key, it.mensaId, it.tags) }
        val byDay = items.groupBy { it.day ?: date }
        if (byDay.size <= 1) return StatsOps.answerMeal(next, eventId, toRecord(items), byDay.keys.singleOrNull())
        val parts = byDay.map { (day, list) -> Triple(day ?: event.date, sum(list), toRecord(list.map { it.copy(day = null) })) }
        return StatsOps.splitPayment(next, eventId, parts)
    }

    /**
     * The 1-3 most likely complete answers for [amount] from the menus of the payment's [days] (day to its dishes, the
     * hand-named dishes with a Mensa included): dishes eaten before first, then favorites, one dish before the same dish
     * twice before two dishes, the default Mensa first, the later day first. The same dishes on several days count once.
     */
    fun suggestions(
        amount: Int,
        days: List<Pair<LocalDate, List<SnapDish>>>,
        defaultId: String?,
        group: Int,
        eaten: Set<String>,
        favorites: Set<String>,
        max: Int = MAX_SUGGESTIONS,
    ): List<Suggestion> {
        if (amount <= 0) return emptyList()
        fun keys(c: MealMatcher.Candidate) = c.picks.map { Favorites.normalize(it.dish.name.de) }
        return days.sortedByDescending { it.first }
            .flatMap { (day, dishes) -> MealMatcher.candidates(amount, dishes, group).map { Suggestion(day, it) } }
            .sortedWith(
                compareByDescending<Suggestion> { s -> keys(s.candidate).any { it in eaten } }
                    .thenByDescending { s -> keys(s.candidate).any { it in favorites } }
                    .thenBy { s -> s.candidate.picks.size + s.candidate.picks.sumOf { it.count - 1 } }
                    .thenBy { s -> s.candidate.mensaId != defaultId },
            )
            .distinctBy { s -> s.candidate.mensaId to s.candidate.picks.map { Favorites.normalize(it.dish.name.de) to it.count } }
            .take(max)
    }

    /** The items a [suggestion] stands for; its day only when the payment's day wasn't known ([range]). */
    fun itemsOf(suggestion: Suggestion, range: Boolean): List<MealItem> = suggestion.candidate.picks.map { p ->
        MealItem(
            Favorites.normalize(p.dish.name.de), p.dish.name, p.dish.mensaId, p.dish.tags, p.dish.own,
            count = p.count, price = p.price, day = suggestion.day.takeIf { range },
        )
    }

    /**
     * Everything the search can add, most likely first (see [MealOption.tier]): the dishes of the payment's [days] (of
     * every Mensa, the default first; with [range] each carries its day), the hand-named dishes ([custom]), the [extras]
     * (key and shown name, in that order), then every other dish the app knows: all cached [menus] and the dishes of
     * earlier meals ([events]). Each dish once per Mensa (and day).
     */
    fun options(
        rest: Int,
        days: List<Pair<LocalDate, List<SnapDish>>>,
        range: Boolean,
        menus: List<SnapDish>,
        events: List<CardEvent>,
        custom: List<KnownDish>,
        extras: List<Pair<String, String>>,
        defaultId: String?,
        group: Int,
    ): List<MealOption> {
        val onDay = days.sortedByDescending { it.first }.flatMap { (day, dishes) ->
            dishes.filterNot { it.own }.sortedBy { it.mensaId != defaultId }.mapNotNull { d ->
                val price = MealMatcher.prices(d, group).firstOrNull()
                val tier = if (!d.byWeight && price == rest) 0 else 1
                MealOption(
                    Favorites.normalize(d.name.de), d.name, d.mensaId, d.tags,
                    price = price.takeUnless { d.byWeight }, tier = tier,
                    per100 = price.takeIf { d.byWeight && (it ?: 0) > 0 }, day = day.takeIf { range }, byWeight = d.byWeight,
                )
            }
        }.distinctBy { Triple(it.key, it.mensaId, it.day) }.sortedBy { it.tier }
        val dayKeys = onDay.map { it.key to it.mensaId }.toSet()
        val own = custom.map {
            if (it.byWeight) MealOption(it.key, it.name, it.mensaId, it.tags, null, tier = 2, own = true, per100 = it.per100, byWeight = true)
            else MealOption(it.key, it.name, it.mensaId, it.tags, it.price.takeIf { p -> p > 0 }, tier = 2, own = true)
        }
            .sortedBy { it.name.de.lowercase() }
        val extraOptions = extras.map { (key, label) -> MealOption(key, LocalizedText(label), null, emptyList(), null, tier = 3, extra = true) }
        val shown = dayKeys + own.map { it.key to it.mensaId }
        val menuOthers = menus.filterNot { it.own }.map { d ->
            val price = MealMatcher.prices(d, group).firstOrNull()
            MealOption(Favorites.normalize(d.name.de), d.name, d.mensaId, d.tags, price.takeUnless { d.byWeight }, tier = 4, per100 = price.takeIf { d.byWeight }, byWeight = d.byWeight)
        }
        // The latest price of a dish eaten before; a weighed portion's price says nothing about the next one.
        val eaten = events.mapNotNull { it.meal }.flatMap { it.dishes }.asReversed().map { d ->
            MealOption(d.key, d.name, d.mensaId, d.tags, d.price.takeIf { d.grams == null }, tier = 4, own = d.own, byWeight = d.grams != null)
        }
        val others = (menuOthers + eaten).filterNot { (it.key to it.mensaId) in shown }.distinctBy { it.key to it.mensaId }
            .sortedBy { it.name.de.lowercase() }
        return onDay + own + extraOptions + others
    }

    /**
     * [options] for [query]: without one the likely ones (tiers 0-3); else every match: that day's dishes first, then
     * names starting with the first word, then by tier.
     */
    fun search(options: List<MealOption>, query: String): List<MealOption> {
        val words = DishSearch.words(query)
        if (words.isEmpty()) return options.filter { it.tier <= 3 }
        return options.filter { it.matches(words) }
            .sortedWith(compareBy<MealOption> { it.tier > 1 }.thenBy { !it.startsWith(words.first()) }.thenBy { it.tier })
    }

    /** The weekdays from [from] to [to] (both included), the days a payment between two scans can have been made. */
    fun weekdays(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter { it.dayOfWeek.value <= 5 }.toList()
}
