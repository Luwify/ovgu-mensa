package de.luwify.mensamd.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MensaOrderTest {
    private fun m(id: String) = Mensa(id, LocalizedText(id), null, "https://example.org/$id")
    private val catalog = listOf(m("a"), m("b"), m("c"), m("d"))

    private fun ids(stored: List<String>) = resolveOrder(stored, catalog).map { it.id }

    @Test fun neverReordered_isCatalogOrder() = assertEquals(listOf("a", "b", "c", "d"), ids(emptyList()))

    @Test fun storedOrderIsHonoured() = assertEquals(listOf("c", "a", "d", "b"), ids(listOf("c", "a", "d", "b")))

    @Test fun removedIdsAreDropped() = assertEquals(listOf("c", "a", "b", "d"), ids(listOf("c", "gone", "a", "b", "d")))

    @Test fun newCatalogEntriesAreAppendedInCatalogOrder() =
        assertEquals(listOf("c", "a", "b", "d"), ids(listOf("c", "a")))

    @Test fun duplicatesAreIgnored() = assertEquals(listOf("b", "a", "c", "d"), ids(listOf("b", "a", "b", "a")))

    @Test fun realCatalogIsCompleteAndUnique() {
        val resolved = resolveOrder(MensaCatalog.all.map { it.id }.reversed())
        assertEquals(MensaCatalog.all.map { it.id }.sorted(), resolved.map { it.id }.sorted())
        assertEquals(MensaCatalog.all.size, resolved.map { it.id }.toSet().size)
    }

    @Test fun pier16_isInTheCatalog_asAFixedFormatMensa() {
        val pier = MensaCatalog.byId("pier16")!!
        assertEquals(PageFormat.FIXED, pier.format)
        assertEquals("PIER 16", pier.name.de)
        assertEquals("Gebäude 16, Denhardtstr. 2, 39106 Magdeburg", pier.address)
        assertEquals("https://www.studentenwerk-magdeburg.de/mensen-cafeterien/pier-16-speiseplan/", pier.url)
        assertEquals(listOf("pier16"), MensaCatalog.all.filter { it.format == PageFormat.FIXED }.map { it.id })
    }

    @Test fun orderSavedBeforePier16_getsPierAppendedAtTheEnd() {
        val saved = listOf("herrenkrug", "stendal", "unicampus-oben", "kellercafe", "wernigerode", "unicampus-unten", "halberstadt", "blub-wernigerode")
        val resolved = resolveOrder(saved).map { it.id }
        assertEquals(saved + "pier16", resolved) // exactly the user's order, then the new one
    }
}
