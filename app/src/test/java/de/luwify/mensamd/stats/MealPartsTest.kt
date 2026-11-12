package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MealPartsTest {
    private val day = LocalDate.of(2026, 11, 12)
    private val vegan = listOf(Tag("vegan", "Vegan"))

    private fun withPayment(amount: Int) = StatsData(
        events = listOf(CardEvent(1, 1, EventKind.PAYMENT, amount, day, true)),
        open = listOf(OpenItem.MealQuestion(2, 1, day, true, 1, amount)),
        nextId = 3,
    )

    @Test fun restAndValidity() {
        assertEquals(1_500, MealParts.rest(4_500, listOf(3_000, null)))
        assertEquals(-500, MealParts.rest(4_500, listOf(3_000, 2_000)))
        assertTrue(MealParts.valid(4_500, listOf(MealPart.Extra("@drink", 1_500), MealPart.NewDish("Wrap", "pier16", emptyList(), 3_000))))
        assertFalse(MealParts.valid(4_500, listOf(MealPart.Extra("@drink", 1_500))))
        assertFalse(MealParts.valid(4_500, emptyList()))
        assertFalse(MealParts.valid(0, listOf(MealPart.Extra("@drink", 0))))
    }

    /** Two items at PIER 16 for 4,50 €: both named now, saved with Mensa, tags and price; the question closes. */
    @Test fun twoNewDishesAreSavedAndMakeTheMeal() {
        val parts = listOf(
            MealPart.NewDish("Bagel", "pier16", vegan, 2_700),
            MealPart.NewDish("Smoothie", "pier16", emptyList(), 1_800),
        )
        val out = MealParts.apply(withPayment(4_500), 1, parts)
        val meal = out.events.single().meal!!
        assertEquals(listOf("bagel" to 2_700, "smoothie" to 1_800), meal.dishes.map { it.key to it.price })
        assertTrue(meal.dishes.all { it.own && it.mensaId == "pier16" })
        assertEquals(vegan, meal.dishes.first().tags)
        assertEquals(listOf("bagel", "smoothie"), out.customDishes.map { it.key })
        assertEquals(listOf(2_700, 1_800), out.customDishes.map { it.price })
        assertTrue(out.open.isEmpty())
    }

    @Test fun menuDishTwiceExtrasTogetherAndAKnownDish() {
        val curry = MealPart.Dish(LocalizedText("Curry"), "unten", vegan, own = false, price = 2_000)
        val wrap = MealPart.Dish(LocalizedText("Wrap"), "pier16", emptyList(), own = true, price = 2_500)
        val parts = listOf(curry, curry, wrap, MealPart.Extra("@drink", 1_200), MealPart.Extra("@salad", 800))
        val meal = MealParts.apply(withPayment(8_500), 1, parts).events.single().meal!!
        assertEquals(listOf(Triple("curry", 2, false), Triple("wrap", 1, true)), meal.dishes.map { Triple(it.key, it.count, it.own) })
        assertEquals(listOf("@drink", "@salad"), meal.extras)
        assertEquals(2_000, meal.extrasAmount)
    }

    @Test fun partsThatDontAddUpChangeNothing() {
        val data = withPayment(4_500)
        assertSame(data, MealParts.apply(data, 1, listOf(MealPart.NewDish("Bagel", "pier16", emptyList(), 2_700))))
        assertNull(MealParts.apply(data, 1, emptyList()).events.single().meal)
    }

    /** Own dishes with a Mensa and a price match like menu dishes, alone and together with a menu dish. */
    @Test fun ownDishesAreCandidates() {
        val own = listOf(
            KnownDish("bagel", LocalizedText("Bagel"), 2_700, vegan, "pier16"),
            KnownDish("smoothie", LocalizedText("Smoothie"), 1_800, mensaId = "pier16"),
            KnownDish("ohne mensa", LocalizedText("Ohne Mensa"), 1_800),
        )
        val snaps = MenuDishes.own(own)
        assertEquals(listOf("Bagel", "Smoothie"), snaps.map { it.name.de })
        assertTrue(snaps.all { it.own && !it.priceGroupsKnown })
        val menu = listOf(SnapDish("pier16", LocalizedText("Currywurst"), listOf(450, 550)))
        val dishes = menu + snaps
        // The price holds for every price group.
        val single = (MealMatcher.plan(2_700, dishes, "unten", 2) as MealMatcher.Plan.Choose).candidates.single()
        assertEquals(listOf("Bagel"), single.picks.map { it.dish.name.de })
        assertTrue(single.toRecord().dishes.single().own)
        val pair = (MealMatcher.plan(4_500, dishes, "unten", 0) as MealMatcher.Plan.Choose).candidates
        assertTrue(pair.any { c -> c.picks.map { it.dish.name.de }.toSet() == setOf("Bagel", "Smoothie") })
        val mixed = (MealMatcher.plan(6_300, dishes, "unten", 0) as MealMatcher.Plan.Choose).candidates
        assertTrue(mixed.any { c -> c.picks.map { it.dish.name.de }.toSet() == setOf("Currywurst", "Smoothie") })
    }
}
