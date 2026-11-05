package de.luwify.mensamd.data.remote

/**
 * Where the menu pages are fetched from: base URLs in order, each serving the same paths ([de.luwify.mensamd.model.Mensa.path]).
 * The first is the main site; the others are fallbacks, tried when it fails (see [de.luwify.mensamd.data.HostFallback]).
 * To drop a fallback, remove its line.
 */
object MenuHosts {
    val all: List<String> = listOf(
        "https://www.studentenwerk-magdeburg.de/",
        // The Studentenwerk's test server (same WordPress pages); added 2026-10-22 while the main site was down.
        "https://www.studiwerkmagdeburg.de/",
    )
}
