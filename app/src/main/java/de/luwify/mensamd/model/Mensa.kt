package de.luwify.mensamd.model

/** One canteen page. [id] is our own stable slug and what DataStore and the cache file names use. */
data class Mensa(val id: String, val name: String, val address: String?, val url: String)

/**
 * Bundled, OvGU-relevant first. PIER 16 is left out until PierParser exists, because its page
 * is not in the standard format.
 */
object MensaCatalog {
    private const val BASE = "https://www.studentenwerk-magdeburg.de/mensen-cafeterien/"

    val all: List<Mensa> = listOf(
        Mensa("unicampus-unten", "UniCampus, unterer Saal", "Pfälzer Straße", BASE + "mensa-unicampus-speiseplan-unten/"),
        Mensa("unicampus-oben", "UniCampus, oberer Saal", "Pfälzer Straße", BASE + "mensa-unicampus-speiseplan-oben/"),
        Mensa("kellercafe", "Kellercafé", "Zschokkestraße", BASE + "mensa-kellercafe-speiseplan/"),
        Mensa("herrenkrug", "Herrenkrug", null, BASE + "mensa-herrenkrug-speiseplan/"),
        Mensa("stendal", "Stendal", null, BASE + "mensa-stendal-speiseplan/"),
        Mensa("halberstadt", "DomCafete Halberstadt", null, BASE + "mensa-halberstadt-speiseplan/"),
        Mensa("wernigerode", "Wernigerode", null, BASE + "mensa-wernigerode-speiseplan/"),
        Mensa("blub-wernigerode", "blub Wernigerode", null, BASE + "blub-wernigerode-speiseplan/"),
    )

    fun byId(id: String): Mensa? = all.firstOrNull { it.id == id }
}
