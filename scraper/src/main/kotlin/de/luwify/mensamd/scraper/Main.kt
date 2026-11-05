package de.luwify.mensamd.scraper

import de.luwify.mensamd.data.remote.HttpMenuFetcher
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.exitProcess

private const val USAGE = "usage: scraper --out <data directory>"

fun main(args: Array<String>) {
    val out = args.toList().takeIf { it.size == 2 && it[0] == "--out" }?.get(1)
    if (out == null) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    val version = System.getProperty("mensamd.version") ?: "dev"
    val scraper = Scraper(
        fetcher = HttpMenuFetcher("$version-stats"),
        outDir = File(out),
        today = LocalDate.now(ZoneId.of("Europe/Berlin")),
        log = System.err::println,
    )
    val results = runBlocking { scraper.run() }
    results.forEach { println(it.summary()) }
    // Shown on the run's page by GitHub Actions; a Mensa that fails doesn't fail the run, so this is where it shows.
    if (System.getenv("GITHUB_ACTIONS") == "true") {
        results.filter { !it.fetched }.forEach { println("::warning::${it.mensa.id}: no host answered") }
        results.filter { it.source == DayFile.SOURCE_FALLBACK }.forEach { println("::warning::${it.mensa.id}: fallback host") }
    }
    exitProcess(exitCode(results))
}

private fun MensaResult.summary(): String =
    if (!fetched) "${mensa.id}: FAILED"
    else "${mensa.id}: $source, $days days ($written written, $unchanged unchanged, $kept kept)"
