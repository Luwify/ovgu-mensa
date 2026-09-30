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
}
