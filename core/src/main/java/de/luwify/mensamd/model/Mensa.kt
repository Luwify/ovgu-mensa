package de.luwify.mensamd.model

/**
 * One canteen page. [id] is our own stable slug and what DataStore and the cache file names use;
 * [name] is short because it is the tab label. [path] is relative to a host of
 * [de.luwify.mensamd.data.remote.MenuHosts] (all hosts serve the same paths).
 */
data class Mensa(
    val id: String,
    val name: LocalizedText,
    val address: String?,
    val path: String,
    val format: PageFormat = PageFormat.DATED,
)

/** How a Mensa's page is laid out, which decides the parser and how the menu is shown. */
enum class PageFormat {
    /** One table per day with dates, legend on the page (all Mensen except PIER 16). */
    DATED,

    /** A hand-edited fixed list without dates or legend (PIER 16). */
    FIXED,
}

/**
 * Bundled, OvGU-relevant first. A Mensa added here later shows up at the end of every saved tab
 * order (see [resolveOrder]), so nobody's order breaks.
 */
object MensaCatalog {
    private const val BASE = "mensen-cafeterien/"

    val all: List<Mensa> = listOf(
        Mensa("unicampus-unten", LocalizedText("UniCampus unten", "UniCampus lower"), "Pfälzer Straße", BASE + "mensa-unicampus-speiseplan-unten/"),
        Mensa("unicampus-oben", LocalizedText("UniCampus oben", "UniCampus upper"), "Pfälzer Straße", BASE + "mensa-unicampus-speiseplan-oben/"),
        Mensa("kellercafe", LocalizedText("Kellercafé"), "Zschokkestraße", BASE + "mensa-kellercafe-speiseplan/"),
        Mensa("herrenkrug", LocalizedText("Herrenkrug"), null, BASE + "mensa-herrenkrug-speiseplan/"),
        Mensa(
            "pier16", LocalizedText("PIER 16"), "Gebäude 16, Denhardtstr. 2, 39106 Magdeburg",
            BASE + "pier-16-speiseplan/", PageFormat.FIXED,
        ),
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
