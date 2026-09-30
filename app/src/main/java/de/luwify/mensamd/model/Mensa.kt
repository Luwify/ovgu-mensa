package de.luwify.mensamd.model

/**
 * One canteen page. [id] is our own stable slug and what DataStore and the cache file names use;
 * [name] is short because it is the tab label.
 */
data class Mensa(val id: String, val name: LocalizedText, val address: String?, val url: String)

/**
 * Bundled, OvGU-relevant first. PIER 16 is left out until PierParser exists, because its page
 * is not in the standard format.
 */
object MensaCatalog {
    private const val BASE = "https://www.studentenwerk-magdeburg.de/mensen-cafeterien/"

    val all: List<Mensa> = listOf(
        Mensa("unicampus-unten", LocalizedText("UniCampus unten", "UniCampus lower"), "Pfälzer Straße", BASE + "mensa-unicampus-speiseplan-unten/"),
        Mensa("unicampus-oben", LocalizedText("UniCampus oben", "UniCampus upper"), "Pfälzer Straße", BASE + "mensa-unicampus-speiseplan-oben/"),
        Mensa("kellercafe", LocalizedText("Kellercafé"), "Zschokkestraße", BASE + "mensa-kellercafe-speiseplan/"),
        Mensa("herrenkrug", LocalizedText("Herrenkrug"), null, BASE + "mensa-herrenkrug-speiseplan/"),
        Mensa("stendal", LocalizedText("Stendal"), null, BASE + "mensa-stendal-speiseplan/"),
        Mensa("halberstadt", LocalizedText("DomCafete Halberstadt"), null, BASE + "mensa-halberstadt-speiseplan/"),
        Mensa("wernigerode", LocalizedText("Wernigerode"), null, BASE + "mensa-wernigerode-speiseplan/"),
        Mensa("blub-wernigerode", LocalizedText("blub Wernigerode"), null, BASE + "blub-wernigerode-speiseplan/"),
    )

    fun byId(id: String): Mensa? = all.firstOrNull { it.id == id }
}

/**
 * Applies a stored custom order to the current catalog. Unknown (removed) ids in [storedIds] are
 * dropped, duplicates ignored, and catalog entries the stored list doesn't know yet (newly added
 * Mensen) are appended in catalog order. Always returns every catalog entry exactly once.
 */
fun resolveOrder(storedIds: List<String>, catalog: List<Mensa> = MensaCatalog.all): List<Mensa> {
    val byId = catalog.associateBy { it.id }
    val seen = HashSet<String>()
    val known = storedIds.mapNotNull { id -> byId[id]?.takeIf { seen.add(id) } }
    return known + catalog.filter { it.id !in seen }
}
