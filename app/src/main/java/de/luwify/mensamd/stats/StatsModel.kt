package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalDateSerializer
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/*
 * The card statistics, stored as one versioned JSON document (see StatsStore, StatsMigrations). Pure Kotlin.
 *
 * Amounts are in thousandths of a euro everywhere ("mills"), the unit the card uses (Intercard stores cents x 10);
 * menu prices (cents) are multiplied by 10 when they are compared with card amounts.
 */

/**
 * Everything the statistics keep. [version] is the schema version of this document; any change of the stored shape
 * raises [StatsMigrations.CURRENT] and adds a step there.
 */
@Serializable
data class StatsData(
    val version: Int = StatsMigrations.CURRENT,
    /** "Meine Karte": only scans of this card are recorded. Null = no card registered. */
    val card: RegisteredCard? = null,
    /** Every recorded scan of a registered card, oldest first. */
    val scans: List<Scan> = emptyList(),
    /** What happened between scans (payments, top-ups, Pfand returns), oldest first. */
    val events: List<CardEvent> = emptyList(),
    /** Questions for the user (ambiguous balance changes, meals to pick), oldest first. */
    val open: List<OpenItem> = emptyList(),
    /** The user's own extras ("Salatbar"-like items without a fixed price), by name. */
    val customExtras: List<String> = emptyList(),
    /** Dishes the user saved by hand ("Als neues Gericht speichern"). */
    val customDishes: List<KnownDish> = emptyList(),
    /** Payments (event ids) waiting to be matched to a menu: a scan doesn't load menus, the next visit to the app does. */
    val pendingMatch: List<Long> = emptyList(),
    /** The next free id for scans, events and open items (one sequence for all). */
    val nextId: Long = 1,
)

/**
 * The registered card: [hash] = SHA-256 over [salt] and the card fingerprint (see [CardIdentity]); the card's UID itself
 * is never stored. [since] is epoch millis.
 */
@Serializable
data class RegisteredCard(val salt: String, val hash: String, val since: Long)

/**
 * One scan of the registered card. [at] epoch millis, [date] the local date at that moment (kept, so a later time zone
 * change can't move a scan to another day). [card] is the registered card's hash at the time (a re-registered card
 * starts a new chain: differences are only taken between scans of the same card). [lastDebit] is null for cards
 * without it (MagnaCarta).
 */
@Serializable
data class Scan(
    val id: Long,
    val at: Long,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val card: String,
    val balance: Int,
    val lastDebit: Int? = null,
)

@Serializable
enum class EventKind {
    @SerialName("payment") PAYMENT,
    @SerialName("top_up") TOP_UP,
    @SerialName("pfand") PFAND,
}

/**
 * A payment, top-up or Pfand return found between two scans. [amount] is positive. [date] is the day of the scan that
 * revealed it; [dateKnown] says it really happened that day (no weekday was skipped between the two scans), only then
 * it counts for the weekday chart and can be matched to that day's menu. [scanId] is the revealing scan.
 */
@Serializable
data class CardEvent(
    val id: Long,
    val scanId: Long,
    val kind: EventKind,
    val amount: Int,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val dateKnown: Boolean,
    /** What was eaten (detailed statistics), payments only. */
    val meal: MealRecord? = null,
)

/**
 * What one payment bought. [dishes] with their price; [extras] (keys, see [Extras]) share [extrasAmount], the part of
 * the payment the dishes don't explain. [other] = "Sonstiges": not a meal (or not worth naming).
 */
@Serializable
data class MealRecord(
    val dishes: List<EatenDish> = emptyList(),
    val extras: List<String> = emptyList(),
    val extrasAmount: Int = 0,
    val other: Boolean = false,
)

/**
 * A dish eaten [count] times for [price] each (mills). [key] = `Favorites.normalize(name.de)`. [grams]: a dish paid by
 * weight, about that much; [price] is then what the portion cost, not the menu's price per 100 g. [own]: named by the
 * user, not read from a menu: its [tags] are only what the user said, so none at all means "no information" (see
 * [tagsKnown]); a menu dish without tags is known to carry none.
 */
@Serializable
data class EatenDish(
    val key: String,
    val name: LocalizedText,
    val mensaId: String? = null,
    val price: Int,
    val count: Int = 1,
    val tags: List<Tag> = emptyList(),
    val grams: Int? = null,
    val own: Boolean = false,
) {
    /** Whether [tags] say anything about the dish (the vegan/vegetarian shares only count these). */
    val tagsKnown: Boolean get() = !own || tags.isNotEmpty()
}

/**
 * A dish the user named by hand, with the price it was saved with (mills), its [mensaId] (null = unknown) and its
 * [tags] (none = no information). [own] is true for every stored one; the list of dishes known from earlier meals
 * ([MealSummary.knownDishes]) also carries menu dishes, with false.
 */
@Serializable
data class KnownDish(
    val key: String,
    val name: LocalizedText,
    val price: Int,
    val tags: List<Tag> = emptyList(),
    val mensaId: String? = null,
    val own: Boolean = true,
)

/** A dish of that day's menu, kept with an open meal question until it is answered (menus are not kept otherwise). */
@Serializable
data class SnapDish(
    val mensaId: String,
    val name: LocalizedText,
    /** Cents in legend order, as on the menu. */
    val prices: List<Int>,
    val priceGroupsKnown: Boolean = true,
    val tags: List<Tag> = emptyList(),
    /** Paid by weight ([de.luwify.mensamd.model.WeightPricing]): [prices] are per 100 g, never the price of a portion. */
    val byWeight: Boolean = false,
    /** A hand-named dish picked from the known list (never stored with a question): see [EatenDish.own]. */
    val own: Boolean = false,
)

/** A day's menu as far as the app could load it: [dishes] of every Mensa, and the ids of the [missing] ones. */
data class DayMenu(val dishes: List<SnapDish>, val missing: List<String> = emptyList())

/** Something only the user can decide. Every item belongs to the scan [scanId] that raised it. */
@Serializable
sealed interface OpenItem {
    val id: Long
    val scanId: Long
    val date: LocalDate
    val dateKnown: Boolean

    /**
     * The balance went down by more than the last debit: several payments. Saved as one, or split into parts that add
     * up to [amount]. [lastDebit] (the last of them) prefills the split.
     */
    @Serializable
    @SerialName("several")
    data class SeveralPayments(
        override val id: Long,
        override val scanId: Long,
        @Serializable(with = LocalDateSerializer::class) override val date: LocalDate,
        override val dateKnown: Boolean,
        val amount: Int,
        val lastDebit: Int? = null,
    ) : OpenItem

    /**
     * The balance changed by [delta] (signed), which no single known amount explains: a top-up (or Pfand) plus a
     * payment? [suggested] is the credit the last debit points to, if any.
     */
    @Serializable
    @SerialName("credit")
    data class CreditAndPayment(
        override val id: Long,
        override val scanId: Long,
        @Serializable(with = LocalDateSerializer::class) override val date: LocalDate,
        override val dateKnown: Boolean,
        val delta: Int,
        val lastDebit: Int? = null,
        val suggested: Int? = null,
    ) : OpenItem

    /**
     * Which dish(es) did payment [eventId] buy? [dishes] is that day's menu of every Mensa with one; [missingMensen] are
     * the Mensen whose menu of that day could not be loaded, so [dishes] may be incomplete.
     */
    @Serializable
    @SerialName("meal")
    data class MealQuestion(
        override val id: Long,
        override val scanId: Long,
        @Serializable(with = LocalDateSerializer::class) override val date: LocalDate,
        override val dateKnown: Boolean,
        val eventId: Long,
        val amount: Int,
        val dishes: List<SnapDish> = emptyList(),
        val missingMensen: List<String> = emptyList(),
    ) : OpenItem
}

/** Built-in extras (keys start with '@', localized in the UI); anything else in a meal's extras is a custom name. */
object Extras {
    const val SALAD = "@salad"
    const val DESSERT = "@dessert"
    const val PUDDING = "@pudding"
    const val ROTE_GRUETZE = "@rote_gruetze"
    const val DRINK = "@drink"
    const val COFFEE = "@coffee"

    val builtIn: List<String> = listOf(SALAD, DESSERT, PUDDING, ROTE_GRUETZE, DRINK, COFFEE)

    fun isBuiltIn(key: String) = key.startsWith("@")
}
