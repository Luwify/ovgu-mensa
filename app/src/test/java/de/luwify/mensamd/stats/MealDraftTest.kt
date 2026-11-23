package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.parser.MenuPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MealDraftTest {
    private val unten = "unicampus-unten"
    private val oben = "unicampus-oben"
    private val monday = LocalDate.of(2026, 11, 16)
    private val wednesday = LocalDate.of(2026, 11, 18)
    private val vegan = listOf(Tag("vegan", "Vegan"))

    private fun dish(mensa: String, name: String, vararg cents: Int) = SnapDish(mensa, LocalizedText(name), cents.toList(), true, vegan)
    private val chili = dish(unten, "Chili", 250, 450, 550)
    private val pommes = dish(unten, "Pommes", 120, 200, 250)
    private val salat = dish(oben, "Salat", 250, 400, 500)
    private val pasta = dish(oben, "Pasta", 280, 490, 590)
    private val farfalle = SnapDish(oben, LocalizedText("Farfalle"), listOf(50, 80, 110), byWeight = true)
    private val day = listOf(chili, pommes, salat, pasta, farfalle)
    private val extras = listOf(Extras.SALAD to "Salatbar", Extras.OTHER to "Sonstiges")

    private fun options(rest: Int, days: List<Pair<LocalDate, List<SnapDish>>> = listOf(wednesday to day), events: List<CardEvent> = emptyList(), custom: List<KnownDish> = emptyList(), menus: List<SnapDish> = emptyList()) =
        MealDraft.options(rest, days, days.size > 1, menus, events, custom, extras, unten, 0)

    private fun option(rest: Int, name: String) = options(rest).first { it.name.de == name }

    // Picking

    @Test fun aFixedPriceDishAddsItsPriceAndTheSameDishCountsUp() {
        var items = MealDraft.pick(5_000, emptyList(), option(5_000, "Chili"))
        assertEquals(listOf(2_500), items.map { it.price })
        items = MealDraft.pick(5_000, items, option(2_500, "Chili"))
        assertEquals(1, items.size)
        assertEquals(2, items.single().count)
        assertTrue(MealDraft.complete(5_000, items))
        assertEquals(2, MealDraft.toRecord(items).dishes.single().count)
    }

    @Test fun aPriceAboveWhatIsLeftIsCappedAndNothingIsAddedWhenNothingIsLeft() {
        val items = MealDraft.pick(2_000, emptyList(), option(2_000, "Pasta")) // 2,80 € on the menu, 2,00 € paid
        assertEquals(2_000, items.single().price)
        assertSame(items, MealDraft.pick(2_000, items, option(0, "Chili")))
    }

    @Test fun aWeighedDishOrAnExtraTakesWhatIsLeft() {
        val first = MealDraft.pick(4_850, emptyList(), option(4_850, "Chili"))
        val withPasta = MealDraft.pick(4_850, first, option(2_350, "Farfalle"))
        assertEquals(2_350, withPasta[1].price)
        assertEquals(470, withPasta[1].grams)
        val withSalad = MealDraft.pick(3_400, MealDraft.pick(3_400, emptyList(), option(3_400, "Chili")), options(900).first { it.key == Extras.SALAD })
        assertEquals(900, withSalad[1].price)
        assertTrue(withSalad[1].isExtra)
    }

    @Test fun anAmountGivenForAnExtraLeavesRoomForAnother() {
        val chili = MealDraft.pick(3_400, emptyList(), option(3_400, "Chili"))
        val salad = MealDraft.pick(3_400, chili, options(900).first { it.key == Extras.SALAD }, price = 500)
        assertEquals(400, MealDraft.rest(3_400, salad))
        val pudding = MealDraft.pick(3_400, salad, options(400).first { it.key == MealDraft.DESSERT.key })
        assertTrue(MealDraft.complete(3_400, pudding))
        assertEquals(listOf(ExtraItem(Extras.SALAD, 500)), MealDraft.toRecord(pudding).extras)
        assertEquals(listOf(400 to true), MealDraft.toRecord(pudding).dishes.filter { it.key == "dessert" }.map { it.price to it.dessert })
        assertTrue(options(900).first { it.key == Extras.SALAD }.takesRest)
        assertFalse(option(900, "Chili").takesRest)
    }

    @Test fun aNewDishByWeightIsSavedWithItsPricePer100gAndOfferedByWeightNextTime() {
        val item = MealDraft.newDish("Rote Grütze", unten, vegan, 900, byWeight = true, per100 = 150)
        assertEquals(600, item.grams)
        val out = MealDraft.save(asked.copy(events = listOf(event.copy(amount = 900))), 1, listOf(item), null)
        val known = out.customDishes.single()
        assertEquals(true to 150, known.byWeight to known.per100)
        assertEquals(600, out.events.single().meal!!.dishes.single().grams)
        val offered = options(500, custom = out.customDishes).first { it.key == known.key }
        assertTrue(offered.takesRest && offered.byWeight)
        assertEquals(150, offered.per100)
        // Never a fixed-price candidate.
        assertTrue(MenuDishes.own(out.customDishes).isEmpty())
        // Two portions are two items, never "2×".
        val two = MealDraft.pick(1_000, MealDraft.pick(1_000, emptyList(), offered, 500), offered, 500)
        assertEquals(listOf(1, 1), two.map { it.count })
    }

    @Test fun onlyASinglePickForTheWholeAmountSavesAtOnce() {
        val one = MealDraft.pick(2_500, emptyList(), option(2_500, "Chili"))
        assertTrue(MealDraft.savesAtOnce(2_500, emptyList(), one))
        val first = MealDraft.pick(3_400, emptyList(), option(3_400, "Chili"))
        assertFalse(MealDraft.savesAtOnce(3_400, emptyList(), first)) // 0,90 € still open
        val second = MealDraft.pick(3_400, first, options(900).first { it.key == MealDraft.DESSERT.key })
        assertFalse(MealDraft.savesAtOnce(3_400, first, second)) // complete, but waits for "Speichern"
        assertTrue(MealDraft.complete(3_400, second))
    }

    @Test fun restAsOtherAndRemoving() {
        val first = MealDraft.pick(3_400, emptyList(), option(3_400, "Chili"))
        val all = MealDraft.restAsOther(3_400, first)
        assertEquals(listOf(Extras.OTHER), all.filter { it.isExtra }.map { it.key })
        assertEquals(MealRecord(first.map { EatenDish("chili", it.name!!, unten, 2_500, 1, vegan) }, listOf(ExtraItem(Extras.OTHER, 900))), MealDraft.toRecord(all))
        val twice = MealDraft.pick(5_000, MealDraft.pick(5_000, emptyList(), option(5_000, "Chili")), option(2_500, "Chili"))
        assertEquals(1, MealDraft.removeOne(twice, 0).single().count)
        assertEquals(emptyList<MealItem>(), MealDraft.removeOne(MealDraft.removeOne(twice, 0), 0))
    }

    @Test fun onlyOtherIsTheWholePaymentAsOther() {
        assertEquals(MealRecord(other = true), MealDraft.toRecord(MealDraft.restAsOther(1_200, emptyList())))
        assertEquals(listOf(MealItem(Extras.OTHER, price = 1_200)), MealDraft.fromRecord(MealRecord(other = true), 1_200))
    }

    @Test fun aSavedMealComesBackAsTheSameItems() {
        val record = MealRecord(
            dishes = listOf(
                EatenDish("chili", LocalizedText("Chili"), unten, 2_500, 2, vegan),
                EatenDish("farfalle", LocalizedText("Farfalle"), oben, 2_350, 1, emptyList(), grams = 470),
            ),
            extras = listOf(ExtraItem(Extras.SALAD, 900)),
        )
        val items = MealDraft.fromRecord(record, 8_250)
        assertEquals(listOf(5_000, 2_350, 900), items.map { it.total })
        assertEquals(470, items[1].grams)
        assertEquals(record, MealDraft.toRecord(items))
    }

    // Saving

    private val event = CardEvent(1, 1, EventKind.PAYMENT, 3_400, wednesday, false)
    private val asked = StatsData(
        events = listOf(event),
        open = listOf(OpenItem.MealQuestion(2, 1, wednesday, false, 1, 3_400)),
        nextId = 3,
    )

    @Test fun savingSetsTheMealClosesTheQuestionAndDatesThePaymentWhenADayWasChosen() {
        val items = MealDraft.restAsOther(3_400, MealDraft.pick(3_400, emptyList(), option(3_400, "Chili")))
        val kept = MealDraft.save(asked, 1, items, null)
        assertEquals(MealDraft.toRecord(items), kept.events.single().meal)
        assertTrue(kept.open.isEmpty())
        assertFalse(kept.events.single().dateKnown)
        val dated = MealDraft.save(asked, 1, items, monday).events.single()
        assertEquals(monday to true, dated.date to dated.dateKnown)
    }

    @Test fun incompleteItemsAreNotSaved() {
        assertSame(asked, MealDraft.save(asked, 1, MealDraft.pick(3_400, emptyList(), option(3_400, "Chili")), null))
        assertSame(asked, MealDraft.save(asked, 1, emptyList(), null))
    }

    @Test fun aNewDishIsSavedAsAHandNamedDish() {
        val items = listOf(MealDraft.newDish("Bagel", "pier16", vegan, 3_400))
        val out = MealDraft.save(asked, 1, items, null)
        val known = out.customDishes.single()
        assertEquals("bagel" to 3_400, known.key to known.price)
        assertEquals("pier16", known.mensaId)
        assertTrue(out.events.single().meal!!.dishes.single().own)
    }

    @Test fun itemsOnSeveralDaysSplitThePayment() {
        val chiliMonday = MealDraft.pick(3_400, emptyList(), option(3_400, "Chili")).map { it.copy(day = monday) }
        val items = chiliMonday + MealItem(Extras.SALAD, price = 900, day = wednesday)
        val out = MealDraft.save(asked, 1, items, null)
        assertEquals(listOf(1L, 3L), out.events.map { it.id })
        assertEquals(listOf(monday to 2_500, wednesday to 900), out.events.map { it.date to it.amount })
        assertTrue(out.events.all { it.dateKnown && it.scanId == 1L })
        assertEquals(listOf(ExtraItem(Extras.SALAD, 900)), out.events[1].meal!!.extras)
        assertTrue(out.open.isEmpty())
        assertEquals(4, out.nextId)
    }

    @Test fun aHandNamedDishChangedHereChangesEverywhere() {
        val bagel = EatenDish("bagel", LocalizedText("Bagel"), null, 3_400, own = true)
        val earlier = CardEvent(5, 1, EventKind.PAYMENT, 3_400, monday, true, MealRecord(listOf(bagel)))
        val data = asked.copy(events = listOf(earlier) + asked.events, customDishes = listOf(KnownDish("bagel", LocalizedText("Bagel"), 3_400)))
        val item = MealDraft.fromRecord(MealRecord(listOf(bagel)), 3_400).single().copy(mensaId = "pier16", tags = vegan)
        val out = MealDraft.save(data, 1, listOf(item), null)
        assertTrue(out.events.all { it.meal!!.dishes.single().mensaId == "pier16" })
        assertEquals("pier16", out.customDishes.single().mensaId)
        assertEquals(3_400, out.customDishes.single().price)
    }

    // Suggestions

    @Test fun suggestionsAreAtMostThreeEatenFirstThenSingleDishesAtTheDefaultMensa() {
        val many = day + dish(oben, "Bowl", 250) + dish(unten, "Wrap", 125)
        val s = MealDraft.suggestions(2_500, listOf(wednesday to many), unten, 0, eaten = emptySet(), favorites = emptySet())
        assertEquals(3, s.size)
        assertEquals("Chili", s[0].candidate.picks.single().dish.name.de) // single dish at the default Mensa
        assertTrue(s.drop(1).all { it.candidate.mensaId == oben }) // the other singles before "2× Wrap"
        val eatenFirst = MealDraft.suggestions(2_500, listOf(wednesday to many), unten, 0, eaten = setOf("wrap"), favorites = emptySet())
        assertEquals(listOf("2xWrap"), eatenFirst[0].candidate.picks.map { "${it.count}x${it.dish.name.de}" })
    }

    @Test fun combinationsAreSuggested() {
        val s = MealDraft.suggestions(3_700, listOf(wednesday to day), unten, 0, emptySet(), emptySet())
        assertEquals(listOf("Chili", "Pommes"), s.single().candidate.picks.map { it.dish.name.de })
    }

    @Test fun aPaymentBetweenScansGetsSuggestionsOfEveryDayLatestFirst() {
        val s = MealDraft.suggestions(2_500, listOf(monday to listOf(chili), wednesday to listOf(salat)), unten, 0, emptySet(), emptySet())
        assertEquals(listOf(unten to monday, oben to wednesday), s.map { it.candidate.mensaId to it.day })
        val items = MealDraft.itemsOf(s[0], range = true)
        assertEquals(monday, items.single().day)
        assertNull(MealDraft.itemsOf(s[0], range = false).single().day)
        // The same dish on two days is one suggestion.
        assertEquals(1, MealDraft.suggestions(2_500, listOf(monday to listOf(chili), wednesday to listOf(chili)), unten, 0, emptySet(), emptySet()).size)
    }

    // Search

    @Test fun withoutAQueryTheDaysDishesComeFirstThoseAtWhatIsLeftFirstThenOwnDishesThenExtras() {
        val custom = listOf(KnownDish("bagel", LocalizedText("Bagel"), 3_400, mensaId = "pier16"))
        val list = MealDraft.search(options(2_800, custom = custom, menus = listOf(dish("herrenkrug", "Seelachs", 310))), "")
        assertEquals(listOf("Pasta", "Chili", "Pommes", "Salat", "Farfalle", "Bagel", "Dessert", "Salatbar", "Sonstiges"), list.map { it.name.de })
        assertNull(list.first { it.name.de == "Farfalle" }.price)
        assertEquals(500, list.first { it.name.de == "Farfalle" }.per100)
    }

    @Test fun aQueryFindsEveryKnownDishTheDaysFirst() {
        val eaten = CardEvent(9, 1, EventKind.PAYMENT, 3_100, monday, true, MealRecord(listOf(EatenDish("seelachs", LocalizedText("Seelachs"), "herrenkrug", 3_100))))
        val all = options(2_500, events = listOf(eaten), menus = listOf(dish(oben, "Salatteller", 300), salat))
        assertEquals(listOf("Salat", "Salatbar", "Salatteller"), MealDraft.search(all, "sala").map { it.name.de })
        assertEquals("herrenkrug", MealDraft.search(all, "seelachs").single().mensaId)
        assertEquals(emptyList<MealOption>(), MealDraft.search(all, "gibtsnicht"))
    }

    @Test fun anyCaseAnyPartAndUmlautsDontMatter() {
        val list = options(1_000, listOf(wednesday to listOf(dish(unten, "Gemüsebrühe", 100), dish(unten, "Soße", 100))))
        for (query in listOf("gemüse", "gemuse", "gemuese", "GEMÜSE", "bruehe", "  brühe ")) assertEquals(query, listOf("Gemüsebrühe"), MealDraft.search(list, query).map { it.name.de })
        assertEquals(listOf("Soße"), MealDraft.search(list, "sosse").map { it.name.de })
    }

    /** The real dish of 2026-11-06, found at the Mensa it was eaten, with the student price. */
    @Test fun chiliSinFindsTheRealDish() {
        val friday = LocalDate.of(2026, 11, 6)
        fun page(mensa: String) = mensa to MenuPageParser.parse(
            checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-11-06/$mensa.html")).use { it.readBytes().toString(Charsets.UTF_8) },
        )
        val pages = listOf(page(unten), page(oben))
        val list = MealDraft.options(2_000, listOf(friday to MenuDishes.onDay(pages, friday)), false, MenuDishes.all(pages), emptyList(), emptyList(), extras, unten, 0)
        val hit = MealDraft.search(list, "Chili sin").first()
        assertTrue(hit.name.de.startsWith("Chili sin carne aus BIO-Sonnenblumenhack"))
        assertEquals(oben to 2_000, hit.mensaId to hit.price)
        assertEquals(0, hit.tier) // exactly the payment
    }

    @Test fun weekdaysBetweenScans() {
        assertEquals(listOf(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 16)), MealDraft.weekdays(LocalDate.of(2026, 11, 13), monday))
    }

    @Test fun theGenericDessertIsOfferedAndANewDessertIsSavedAsOne() {
        val generic = options(900).single { it.key == MealDraft.DESSERT.key }
        assertTrue(generic.dessert && generic.takesRest)
        val picked = MealDraft.pick(900, emptyList(), generic, 900)
        assertTrue(MealDraft.toRecord(picked).dishes.single().dessert)
        val item = MealDraft.newDish("Pudding", null, emptyList(), 900, byWeight = true, per100 = 250, dessert = true)
        val out = MealDraft.save(asked.copy(events = listOf(event.copy(amount = 900))), 1, listOf(item), null)
        assertTrue(out.customDishes.single().dessert)
        assertTrue(out.events.single().meal!!.dishes.single().dessert)
    }

    /** Desserts by weight cost 0,60 € per 100 g unless said otherwise; the price per 100 g can be changed per pick. */
    @Test fun dessertsByWeightStartAt60CentsPer100g() {
        val generic = options(900).single { it.key == MealDraft.DESSERT.key }
        assertEquals(600, generic.per100)
        assertEquals(150, MealDraft.pick(900, emptyList(), generic, 900).single().grams)
        // A special one (or a new price): 0,75 € per 100 g.
        val special = MealDraft.pick(900, emptyList(), generic, 900, per100 = 750).single()
        assertEquals(750 to 120, special.per100 to special.grams)
        // An own dessert by weight without a price per 100 g (e.g. migrated "Pudding") is offered at the default too.
        val pudding = KnownDish("pudding", LocalizedText("Pudding"), 0, byWeight = true, dessert = true)
        assertEquals(600, options(900, custom = listOf(pudding)).single { it.key == "pudding" }.per100)
        // A changed price per 100 g reaches the saved dessert.
        val data = asked.copy(events = listOf(event.copy(amount = 900)), customDishes = listOf(pudding))
        val picked = MealDraft.pick(900, emptyList(), options(900, custom = listOf(pudding)).single { it.key == "pudding" }, 900, per100 = 700)
        assertEquals(700, MealDraft.save(data, 1, picked, null).customDishes.single().per100)
        // A dish by weight that is no dessert has no default.
        val salad = KnownDish("nudelbar", LocalizedText("Nudelbar"), 0, byWeight = true)
        assertEquals(null, options(900, custom = listOf(salad)).single { it.key == "nudelbar" }.per100)
    }

    /** A dessert with a fixed price (e.g. a cake) is a dessert too, offered at its price, never by weight. */
    @Test fun aDessertWithAFixedPrice() {
        val cake = MealDraft.newDish("Kuchen", null, emptyList(), 1_200, dessert = true)
        assertTrue(cake.dessert && !cake.weighed)
        val out = MealDraft.save(asked.copy(events = listOf(event.copy(amount = 1_200))), 1, listOf(cake), null)
        val known = out.customDishes.single()
        assertTrue(known.dessert && !known.byWeight)
        assertTrue(out.events.single().meal!!.dishes.single().dessert)
        val offered = options(1_200, custom = out.customDishes).single { it.key == known.key }
        assertEquals(1_200 to true, offered.price to offered.dessert)
        assertFalse(offered.byWeight)
        // A correction keeps it at its fixed price.
        assertFalse(MealDraft.fromRecord(out.events.single().meal, 1_200, out.customDishes).single().weighed)
        assertEquals(1, MealSummary.desserts(out.events).count)
    }

    @Test fun markingAHandNamedDishAsDessertReachesItsOtherMeals() {
        val pud = EatenDish("pudding", LocalizedText("Pudding"), null, 500, own = true)
        val earlier = CardEvent(5, 1, EventKind.PAYMENT, 500, monday, true, MealRecord(listOf(pud)))
        val data = asked.copy(
            events = listOf(earlier, event.copy(amount = 500)),
            customDishes = listOf(KnownDish("pudding", LocalizedText("Pudding"), 500, byWeight = true)),
        )
        val item = MealDraft.fromRecord(MealRecord(listOf(pud)), 500).single().copy(weighed = true, dessert = true, per100 = 250)
        val out = MealDraft.save(data, 1, listOf(item), null)
        assertTrue(out.events.all { it.meal!!.dishes.single().dessert })
        assertEquals(true to 250, out.customDishes.single().let { it.dessert to it.per100 })
    }

    /** An own extra from before 1.3.0 ("Obstsalat") edited into a dessert dish: saved, and the search offers only the dish. */
    @Test fun anOwnExtraBecomesADessertDish() {
        val older = CardEvent(7, 1, EventKind.PAYMENT, 3_300, monday, true, MealRecord(
            listOf(EatenDish("chili", LocalizedText("Chili"), unten, 2_500)), listOf(ExtraItem("Obstsalat", 800), ExtraItem(Extras.SALAD, 0)),
        ))
        val withExtra = asked.copy(events = listOf(older, event.copy(amount = 1_000)), customExtras = listOf("Obstsalat"))
        val dish = MealDraft.newDish("Obstsalat", null, emptyList(), 1_000, byWeight = true, per100 = 200, dessert = true).copy(fromExtra = "Obstsalat")
        val out = MealDraft.save(withExtra, 1, listOf(dish), null)
        assertTrue(out.events[1].meal!!.dishes.single().dessert)
        // The older payment's extra became the same dish, with its own amount and weight.
        val converted = out.events[0].meal!!
        assertEquals(listOf(ExtraItem(Extras.SALAD, 0)), converted.extras)
        assertEquals(listOf(800 to 400), converted.dishes.filter { it.dessert }.map { it.price to it.grams })
        assertTrue(out.customExtras.isEmpty())
        assertEquals(2, MealSummary.desserts(out.events).count)
        val search = MealDraft.options(500, emptyList(), false, emptyList(), out.events, out.customDishes, listOf("Obstsalat" to "Obstsalat", Extras.SALAD to "Salatbar"), null, 0)
        assertEquals(listOf(false), search.filter { it.name.de == "Obstsalat" }.map { it.extra })
        assertTrue(search.any { it.key == Extras.SALAD })
    }

    /** Reopened, a dessert at 0,60 € per 100 g shows 0,60 €, not 0,59 € or 0,61 € from the rounded weight. */
    @Test fun aReopenedPricePer100gIsTheOneEntered() {
        fun reopened(price: Int, per100: Int, custom: List<KnownDish> = emptyList(), key: String = "obstsalat"): Int? {
            val dish = EatenDish(key, LocalizedText("Obstsalat"), null, price, grams = MealMatcher.estimateGrams(price, per100), own = true, dessert = true)
            return MealDraft.fromRecord(MealRecord(listOf(dish)), price, custom).single().per100
        }
        assertEquals(600, reopened(1_000, 600)) // 170 g; 1,00 € / 170 g = 0,588 €
        assertEquals(600, reopened(970, 600)) // 160 g; 0,606 €
        // Another price saved for the dessert, or none known: the whole-cent price that gives the weight.
        val special = listOf(KnownDish("obstsalat", LocalizedText("Obstsalat"), 0, byWeight = true, per100 = 750, dessert = true))
        assertEquals(750, reopened(1_000, 750, special))
        assertEquals(800, MealDraft.per100(2_350, MealMatcher.estimateGrams(2_350, 800)))
        assertEquals(null, MealDraft.per100(1_000, null))
    }
}
