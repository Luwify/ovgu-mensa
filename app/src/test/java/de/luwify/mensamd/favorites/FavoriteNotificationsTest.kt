package de.luwify.mensamd.favorites

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.parser.MenuPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class FavoriteNotificationsTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), berlin)
    private fun dish(de: String) = Dish(LocalizedText(de, "$de (en)"), emptyList(), emptyList(), emptyList(), emptyList(), null, null)
    private fun page(vararg days: Pair<LocalDate, List<String>>) =
        MenuPage(days.map { (date, names) -> MenuDay(date, names.map(::dish)) }, Legend.EMPTY)

    private val thu = LocalDate.of(2026, 10, 1)
    private val fri = LocalDate.of(2026, 10, 2)
    private val sat = LocalDate.of(2026, 10, 3)
    private val mon = LocalDate.of(2026, 10, 5)
    private val fav = setOf("schnitzel", "linsensuppe")

    // ---- when ----

    @Test
    fun nextRun_isTodayAt18_orTomorrowWhenPast() {
        assertEquals(Duration.ofHours(8), FavoriteNotifications.delayUntilNextRun(at(2026, 10, 1, 10)))
        assertEquals(Duration.ofMinutes(1), FavoriteNotifications.delayUntilNextRun(at(2026, 10, 1, 17, 59)))
        assertEquals(Duration.ofHours(24), FavoriteNotifications.delayUntilNextRun(at(2026, 10, 1, 18)))
        assertEquals(Duration.ofHours(23).plusMinutes(30), FavoriteNotifications.delayUntilNextRun(at(2026, 10, 1, 18, 30)))
    }

    @Test
    fun nextRun_staysAt18AcrossTheDstChange() {
        // Sunday 25.10.2026 has 25 hours in Berlin: Saturday 18:00 -> Sunday 18:00 is 25 h
        assertEquals(Duration.ofHours(25), FavoriteNotifications.delayUntilNextRun(at(2026, 10, 24, 18)))
    }

    @Test
    fun targetDate_isTheNextCalendarDay_butNothingForMorningCatchUps() {
        assertEquals(fri, FavoriteNotifications.targetDate(at(2026, 10, 1, 18)))
        assertEquals(fri, FavoriteNotifications.targetDate(at(2026, 10, 1, 23, 59)))
        assertEquals(fri, FavoriteNotifications.targetDate(at(2026, 10, 1, 12)))
        // phone was off at 18:00 and came back after midnight: no "tomorrow" message the morning of
        assertNull(FavoriteNotifications.targetDate(at(2026, 10, 2, 7)))
        assertNull(FavoriteNotifications.targetDate(at(2026, 10, 2, 0, 5)))
    }

    // ---- what ----

    @Test
    fun select_findsFavoritesOnTomorrowsMenuOnly() {
        val menus = listOf("a" to page(thu to listOf("Schnitzel"), fri to listOf("Linsensuppe", "Reis")))
        val hits = FavoriteNotifications.select(menus, fav, fri, emptySet())
        assertEquals(listOf("Linsensuppe"), hits.map { it.dish.name.de })
        assertEquals("2026-10-02|a|linsensuppe", hits.single().notifiedKey)
    }

    @Test
    fun select_weekends_announceNothing_andSundayEveningAnnouncesMonday() {
        val menus = listOf("a" to page(sat to listOf("Schnitzel"), mon to listOf("Schnitzel")))
        // Friday evening -> Saturday: nothing, even if the page lists a Saturday
        assertTrue(FavoriteNotifications.select(menus, fav, FavoriteNotifications.targetDate(at(2026, 10, 2, 18))!!, emptySet()).isEmpty())
        // Saturday evening -> Sunday: nothing
        assertTrue(FavoriteNotifications.select(menus, fav, FavoriteNotifications.targetDate(at(2026, 10, 3, 18))!!, emptySet()).isEmpty())
        // Sunday evening -> Monday
        val monday = FavoriteNotifications.select(menus, fav, FavoriteNotifications.targetDate(at(2026, 10, 4, 18))!!, emptySet())
        assertEquals(listOf(mon), monday.map { it.date })
    }

    @Test
    fun select_missingDayOrNoMenu_isNothing() {
        assertTrue(FavoriteNotifications.select(listOf("a" to page(thu to listOf("Schnitzel"))), fav, fri, emptySet()).isEmpty())
        assertTrue(FavoriteNotifications.select(listOf("a" to null), fav, fri, emptySet()).isEmpty())
        assertTrue(FavoriteNotifications.select(listOf("a" to page(fri to listOf("Schnitzel"))), emptySet(), fri, emptySet()).isEmpty())
    }

    @Test
    fun select_keepsMensaOrder_andEachDishOncePerMensa() {
        val menus = listOf(
            "b" to page(fri to listOf("Schnitzel", "Schnitzel ")), // same dish twice (lunch and dinner)
            "a" to page(fri to listOf("Linsensuppe", "Schnitzel")),
        )
        val hits = FavoriteNotifications.select(menus, fav, fri, emptySet())
        assertEquals(listOf("b" to "schnitzel", "a" to "linsensuppe", "a" to "schnitzel"), hits.map { it.mensaId to it.key })
    }

    @Test
    fun dedup_neverTheSameDishDayAndMensaTwice() {
        val menus = listOf("a" to page(fri to listOf("Schnitzel", "Linsensuppe")), "b" to page(fri to listOf("Schnitzel")))
        val first = FavoriteNotifications.select(menus, fav, fri, emptySet())
        assertEquals(3, first.size)
        val notified = first.map { it.notifiedKey }.toSet()
        assertTrue(FavoriteNotifications.select(menus, fav, fri, notified).isEmpty())
        // a second run the same evening (e.g. retried) with one new favorite only brings the new one
        val more = FavoriteNotifications.select(listOf("a" to page(fri to listOf("Schnitzel", "Linsensuppe", "Pizza"))), fav + "pizza", fri, notified)
        assertEquals(listOf("pizza"), more.map { it.key })
        // the same dish on another day is announced again
        val nextWeek = LocalDate.of(2026, 10, 9)
        assertEquals(1, FavoriteNotifications.select(listOf("a" to page(nextWeek to listOf("Schnitzel"))), fav, nextWeek, notified).size)
    }

    @Test
    fun prune_dropsPastDays_andGarbage() {
        val kept = FavoriteNotifications.prune(
            setOf("2026-09-30|a|x", "2026-10-01|a|y", "2026-10-02|b|z", "garbage", "2026-13-01|a|q"),
            today = thu,
        )
        assertEquals(setOf("2026-10-01|a|y", "2026-10-02|b|z"), kept)
    }

    // ---- how many notifications ----

    @Test
    fun plan_oneNotificationPerRun_singleOrGrouped() {
        assertNull(FavoriteNotifications.plan(emptyList()))
        val one = FavoriteNotifications.select(listOf("a" to page(fri to listOf("Schnitzel"))), fav, fri, emptySet())
        val single = FavoriteNotifications.plan(one)!!
        assertTrue(single.isSingle)
        assertEquals("a", single.openMensaId)
        val many = FavoriteNotifications.select(
            listOf("b" to page(fri to listOf("Linsensuppe")), "a" to page(fri to listOf("Schnitzel"))), fav, fri, emptySet(),
        )
        val grouped = FavoriteNotifications.plan(many)!!
        assertFalse(grouped.isSingle)
        assertEquals(2, grouped.hits.size)
        assertEquals("b", grouped.openMensaId) // the first watched Mensa in tab order that has a hit
    }

    @Test
    fun realPage_favoriteOnTheNextDayIsFound() {
        val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/unicampus-unten.html")).use { it.readBytes().toString(Charsets.UTF_8) }
        val page = MenuPageParser.parse(html)
        val (today, tomorrow) = page.days[0].date to page.days[1].date
        val wanted = page.days[1].dishes.first()
        val hits = FavoriteNotifications.select(listOf("unicampus-unten" to page), setOf(Favorites.key(wanted)), tomorrow, emptySet())
        assertEquals(wanted.name, hits.first().dish.name)
        assertTrue(hits.all { it.date == tomorrow })
        assertTrue(today < tomorrow)
    }

    // ---- user-chosen time ----

    private val seven = java.time.LocalTime.of(7, 0)

    @Test
    fun customTime_nextRunUsesIt_alsoAcrossDst() {
        assertEquals(Duration.ofHours(1), FavoriteNotifications.delayUntilNextRun(at(2026, 10, 1, 6), seven))
        assertEquals(Duration.ofHours(24), FavoriteNotifications.delayUntilNextRun(at(2026, 10, 1, 7), seven))
        // Saturday 07:00 -> Sunday 07:00 spans the clock change (Sunday 03:00 -> 02:00): 25 h
        assertEquals(Duration.ofHours(25), FavoriteNotifications.delayUntilNextRun(at(2026, 10, 24, 7), seven))
        assertEquals(Duration.ofMinutes(30), FavoriteNotifications.delayUntilNextRun(at(2026, 10, 1, 12), java.time.LocalTime.of(12, 30)))
    }

    @Test
    fun morningTime_announcesTheSameDay_untilLunchIsOver() {
        assertTrue(FavoriteNotifications.announcesToday(seven))
        assertTrue(FavoriteNotifications.announcesToday(java.time.LocalTime.of(11, 59)))
        assertFalse(FavoriteNotifications.announcesToday(java.time.LocalTime.NOON))
        assertEquals(thu, FavoriteNotifications.targetDate(at(2026, 10, 1, 7), seven))
        assertEquals(thu, FavoriteNotifications.targetDate(at(2026, 10, 1, 13, 59), seven)) // late, still useful
        assertNull(FavoriteNotifications.targetDate(at(2026, 10, 1, 14), seven)) // lunch is over
        // phone off from before 07:00 until the next morning: that morning's menu
        assertEquals(fri, FavoriteNotifications.targetDate(at(2026, 10, 2, 6, 30), seven))
    }

    @Test
    fun eveningTime_otherThan18_stillAnnouncesTomorrow() {
        val noon = java.time.LocalTime.NOON
        assertEquals(fri, FavoriteNotifications.targetDate(at(2026, 10, 1, 12), noon))
        assertEquals(fri, FavoriteNotifications.targetDate(at(2026, 10, 1, 21), java.time.LocalTime.of(21, 0)))
        assertNull(FavoriteNotifications.targetDate(at(2026, 10, 2, 9), java.time.LocalTime.of(21, 0)))
    }

    @Test
    fun morningTime_weekendsAnnounceNothing_mondayMorningAnnouncesMonday() {
        val menus = listOf("a" to page(sat to listOf("Schnitzel"), mon to listOf("Schnitzel")))
        assertTrue(FavoriteNotifications.select(menus, fav, FavoriteNotifications.targetDate(at(2026, 10, 3, 7), seven)!!, emptySet()).isEmpty())
        assertEquals(listOf(mon), FavoriteNotifications.select(menus, fav, FavoriteNotifications.targetDate(at(2026, 10, 5, 7), seven)!!, emptySet()).map { it.date })
    }

    @Test
    fun plan_carriesTheTodayWording() {
        val hits = FavoriteNotifications.select(listOf("a" to page(thu to listOf("Schnitzel"))), fav, thu, emptySet())
        assertTrue(FavoriteNotifications.plan(hits, today = true)!!.today)
        assertFalse(FavoriteNotifications.plan(hits)!!.today)
    }
}
