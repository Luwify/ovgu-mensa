# Mensa MD

Android app showing only the menus of the Studentenwerk Magdeburg canteens.
Goal: noticeably faster and cleaner than the Uni Now app (no copied assets/branding).
GPL-3.0-or-later, published on GitHub (`Luwify/ovgu-mensa`); Stundenplan MD is the author's other app (same build setup,
same updater and release pipeline).

This file is public (GitHub). Personal details (devices, paths, release infrastructure, test notes) live in the
git-ignored `CLAUDE.local.md` at the repo root, which Claude Code loads after this file.

## Features
- **All 9 Mensen/Cafeterias** (UniCampus unten/oben, Kellercafé, Herrenkrug, PIER 16, Stendal, Halberstadt, Wernigerode,
  BLUB), each a tab in a swipeable pager; tabs can be reordered (long-press + drag) and one is the default (star).
- **Day pager** per Mensa, lunch/dinner split, price pill (price group chosen in the filter sheet), CO2e/water colors.
- **Filters:** tag chips (vegan, vegetarisch, …, built from the discovered tags) and a sheet to hide dishes by
  additive/allergen code; selection is global and persisted in DataStore. Two virtual (non-tag) chips follow
  Vegan/Vegetarisch: **Kuchen** (`:kuchen`, only the cake of the day, offered only when the page has one; detection
  only in `Dish.isCakeOfTheDay`, German "Lieblingskuchen" or English "favo(u)rite cake") and **Favoriten**
  (`:favoriten`, always offered; empty state before the first favorite). Virtual keys contain a colon, tag keys never do.
- **Favorites:** heart at the top right of every card (normal + compact; 24dp icon, 48dp touch area overhanging into
  the card padding, compact cards min 48dp tall). A favorite = `Favorites.normalize(dish.name.de)` (lowercase,
  collapsed whitespace, badges removed, punctuation trimmed at the ends), exact match only, global, string set in DataStore.
  Icons: Material Symbols "favorite" outlined/filled (Apache 2.0, NOTICE).
  Sparkles (`ui/Sparkle.kt`, math in `SparkleMath`, unit-tested): tapping an empty heart plays a one-shot burst (heart
  pop + gold/red stars and a ring, 650 ms; only the tapped heart, via `FavoriteBurst.pending`; `FavoriteEffects` draws
  only this burst). "Favoriten funkeln" (`legendary_favorites`, default on) = the aura on every favorite (the old gold
  sweep-edge frame with twinkling stars was removed 2026-10-02). Aura: `SuperAuraOverlay`, ONE Canvas above the
  LazyColumn for all visible favorite cards (so it lies over neighbors and headers); shader in `AuraShader.kt`
  (Android 13+), math and constants in `SparkleMath`, scroll drag in `ScrollDrag` (the former hidden Super-Funkeln was
  merged in 2026-10-08). Gotcha: the drag target is followed per frame; never restart an animation per scroll step (it
  gets cancelled before its first frame). Below API 33: nested filled round rects. ~30 fps clock only while a
  favorite card is visible.
  Card glows (AMOLED/custom `cardGlow`) are `EdgeGlow` (`EdgeGlow.kt`, AGSL; below API 33 ring strokes: forced on a
  phone, 1.9 % janky / p99 18 ms vs 0.7-0.8 % / 16 ms shader). `MEASURE_EDGE_GLOW_FALLBACK` must stay false in commits.
  Measured on a phone (release, System theme, test data, 2026-10-02, with drag): scroll p99 16 ms, janky 0.69 %; idle 30
  frames/s, frame p50 10 ms / GPU 6 ms. Known: a card hidden under the pinned header can still glow below it while
  scrolling (faded in, no line).
- **Favorites overview** (settings, section "Favoriten" above the notifications: "Favoriten verwalten"; overlay
  `FavoritesScreen` in `SettingsScreen`; until 1.1.0 it was a heart in the top bar):
  every favorite with its next serving, from the cached menus of all Mensen only (`MenuRepository.observeCached`, never
  fetches; a Mensa never opened has no cache, so it can't contribute). Pure logic in `favorites/FavoriteOverview`:
  from today on, no weekends (like the pager), earliest date, same date -> tab order; a fixed menu (PIER 16) counts only
  without a dated serving ("Festes Angebot · PIER 16"). Order: dated soonest first, then fixed, then "Derzeit nicht im
  Plan", ties and the last groups alphabetical (collator, shown language). Name and tags shown come from the serving,
  else from `FavoriteInfo` (settings key `favorite_info`, JSON map key -> name de/en + tags; saved on the heart tap,
  refreshed whenever the overview finds the dish, dropped on removal; favorites from before 1.1.0 have none until
  then and show the capitalized key). Tap -> `OpenRequest(mensaId, date)`: the pager switches, then `dayRequest`
  scrolls that Mensa's list to the day header. Removal: heart or swipe, undo in a Long snackbar.
- **Favorites notifications** (`notify/`, pure logic in `favorites/FavoriteNotifications`): unique one-time WorkManager
  job for the next run time (user-chosen on the settings page, default 18:00, `notify_minute_of_day`; network required) that re-enqueues itself with REPLACE as its last action (no drift like
  periodic work); WorkManager is initialized on demand (`MensaApp` is the `Configuration.Provider`, startup initializer
  removed). Scheduled only while enabled AND favorites exist (`MainViewModel.init`). The job refreshes the watched
  Mensen via `MenuRepository.refreshIfStale` (failure: uses the cache). Run time from 12:00: announces the NEXT
  calendar day ("Morgen …"; a late catch-up before noon announces nothing). Run time before 12:00: announces the SAME
  day ("Heute …", only until 14:00). Weekends: nothing. A changed time replaces the pending job (REPLACE), app start keeps it (KEEP); one notification per run, dedup set
  `date|mensaId|favorite` pruned of past days. Tap opens that Mensa (`EXTRA_MENSA_ID` -> `MainViewModel.openRequest`).
  Watched Mensen default to the default Mensa; PIER 16 (no dates) is not offered. POST_NOTIFICATIONS is explained
  once on the first favorite; "Einschalten" uses the system dialog while it can still appear, else the app's notification
  settings. No exact alarms: Doze/App standby can delay the run (maintenance windows). Favorites are never logged.
- **Settings page** (gear icon in the top bar, overlay `SettingsScreen` in `MensaRoot` like the card screen): price group,
  card layout (normal/compact chips), design (color scheme drop-down + custom theme editor), favorites ("Favoriten
  verwalten" -> overview), favorites notifications, version (in-app updater, see below) + the switch for the Stundenplan MD
  banner (the card itself is the first item, see its section), backup (export/import, see card statistics), about
  (licenses and source code). The filter sheet ("Filter" button) has only the allergen exclusion.
- **Chip order:** long-press + drag reorders the filter chips like the Mensa tabs (shared `RowReorder` helper for both);
  global, stored as `filter_order`. A drag on one page is merged by `DishFilters.mergeOrder`: chips not on that page keep
  their place; keys the stored order doesn't know follow in the default order (vegan, vegetarisch, kuchen, favoriten, ...).
- **Compact mode:** short dish cards that expand to the normal layout (setting on the settings page, persisted).
- **Themes** (setting on the settings page, persisted): *System* (subtle Material You, dynamic color, neutral surfaces),
  *Soft* (own warm cream/sage palette), *AMOLED* (always dark, pure black). WCAG AA text pairs are unit-tested.
  Card contrast (chosen 2026-10-01 from on-device screenshots): System = page one step deeper (`withCardContrast`:
  light surfaceContainer, dark surfaceContainerLowest; dark cards surfaceContainer, below the tag chips' tone), cards
  stand out by fill, hairline edge is decorative. AMOLED = blue 1dp outline + `cardGlow` halo (two plain strokes, no
  blur/layer; scroll benchmark on release unchanged). Soft unchanged. Rejected: stronger outline (too heavy in dark,
  8:1), shadow (invisible in dark).
  *Eigenes/Custom* (`ThemeMode.CUSTOM`, `CustomThemeSpec`: base system/light/dark/black, accent color 0xRRGGBB,
  glow on/off; keys `custom_theme_base/accent/glow`, the old hue-only `custom_theme_hue` is migrated via `hueToAccent`):
  `ui/theme/CustomTheme` uses the exact accent as primary where readable, otherwise nudges it (`withContrast`), and
  builds the other tones from its hue, scaled by its saturation (gray accent = neutral theme); text AA, borders 3:1,
  page/card apart; `ThemeContrastTest` checks every 5° hue plus extreme typed colors x base x glow. Editor on the
  settings page: background as a grouped radio list (`SettingsRadioGroup`), hue slider (keeps saturation/lightness;
  live draft in `MainViewModel.customDraft`, saved on release), hex and R/G/B fields (`ColorInput`, applied when
  valid); no preset swatches. "Erweiterte Farben" opens `ColorEditorScreen` (overlay in `SettingsScreen`): per-role
  overrides (`ColorRole`: background, card, text, card border, price, tag, badge, heart; stored as
  `custom_theme_overrides` "bg=RRGGBB,..."), each picked in `ColorPickerDialog` (hue ring + saturation/value square,
  10 shades, hex; Automatisch/Abbrechen/OK). Overrides are wishes: a hand-picked background decides light/dark
  (`CustomTheme.isDark`), hand-picked page/card are moved just enough from the mode's text, everything readable on
  them is still nudged; rows say "für Lesbarkeit angepasst" when that happened. A seeded random test (400 combos)
  checks every pair. The color scheme picker is `SettingsDropdown` (rounded tonal field + rounded menu with a
  check on the current value; own Check/Chevron vectors in `CardIcons.kt`). Glow = the AMOLED `cardGlow` in the accent. Fixed bases set matching system bar icons (`MainActivity`).
- **Card balance** (NFC): popup when a card is held to the phone (`CardPopupActivity`) and the "Karte" screen from the
  top bar icon; see the architecture section. **Intercard verified on a real card, MagnaCarta not verified.**
- **Card statistics** (opt-in; gear on the card screen -> `ui/stats/CardSettingsScreen`; checklist and phase-0 card
  findings in `docs/STATS.md`): the card exposes only balance + last debit (DESFire EV1, logs need a key, no counter),
  so everything comes from differences between scans. Pure logic in `stats/`: `StatsStore` (one JSON file
  `filesDir/stats/stats.json`, versioned via `StatsMigrations` (raise CURRENT + add a step + test for any shape change;
  older file kept as `.v<n>`, newer never written, broken moved aside), atomic writes under one lock), `StatsOps`,
  `BalanceDiff` (rules in its KDoc; unclear changes become `OpenItem`s answered in `StatsScreen`), `MealMatcher`
  (detailed statistics: one dish / same dish twice / two dishes of one Mensa at the user's price group, all Mensen, default
  Mensa first, >5 candidates -> "where did you eat?", one candidate overall saved directly only when both scans were on the same day
  and every Mensa's menu of that day is known: `MenuRepository.loadDay` loads the unknown ones first, those that still fail
  are named in the question (`missingMensen`); the day's dishes are kept with an open question; past days stay in the menu
  cache for 14 days), `StatsSummary`/`MealSummary`, `CardReminderRules`. `StatsRecorder` (app scope) records scans from reader mode
  and the popup; the result is out as soon as the scan is stored. A card tap touches no network (update check included): payments wait in `StatsData.pendingMatch` until `matchPending` (main UI start, statistics screen). "Meine Karte": only scans of the registered card are stored; the UID becomes a fingerprint in memory
  (`CardIdentity`), only a salted PBKDF2 hash of it is stored. Amounts are mills (card unit); menu prices are cents x 10.
  Charts are own Canvas code (`ui/stats/Charts.kt`), no library. Reminder: `notify/CardReminder.kt` (one-time
  WorkManager job re-enqueued like the favorites job; tap -> `EXTRA_OPEN_CARD`). Backup: `backup/BackupFormat`
  (`mensamd-backup` v1: every settings key except device state, language, statistics) + `BackupRepository`, settings page.
  Meal statistics website link: `StatsLinks.MEAL_STATS_URL` (hidden while empty). Pfand: a credit up to 5 € that is a
  sum of the per-bottle amounts (default 0.08/0.15/0.25 €) is a Pfand return when the last payment did not change
  (changed: top-up + payment, asked), with "Anders zuordnen"; a top-up amount wins. A real top-up kept the last payment
  (docs/STATS.md).
- **Hand-named dishes** ("Als neues Gericht speichern", `ui/stats/DishDialogs`): name + Mensa (catalog or unknown) + the menu's tag
  chips (`DishTags`), saved as `KnownDish` and eaten as `EatenDish(own = true)`. For an `own` dish no tags = no information
  (`EatenDish.tagsKnown`): `MealSummary` computes the vegan/vegetarian shares over dishes with known tags only (`unknownTags` is shown
  as a hint); a menu dish without tags is known to carry none. A tap on a Verlauf entry lists its dishes to edit (`StatsOps.editDish`:
  Mensa + tags; the name only for `own`, and an `own` change reaches every payment with that dish and the saved dish); "Unbekannte Mensa"
  in "Nach Mensa" lists dishes without a Mensa (`MealSummary.withoutMensa`). Schema 4 marks old dishes without Mensa and tags as `own`.
- **Dishes priced per 100 g:** the pages don't say so, so `model/WeightPricing` decides (the only place with these rules:
  badge "Pastabuffet", soup icon in UniCampus unten). Menu cards show "/100 g" at the price. Such dishes never match as a
  fixed price and are never saved automatically: the meal question lists them with the weight the amount buys
  (`MealMatcher.weightDishes`, "ca. 470 g"), the extras step takes them next to fixed dishes and extras (all that is left, or
  an entered weight). `EatenDish.price` is what was paid, `grams` the weight; `SnapDish.byWeight` marks them.
- **License GPL-3.0-or-later** (see below).
- Not done / only if asked: a baseline profile (separate `:baselineprofile` module). Swipe smoothness (B3) is closed.

## Known limitations
- Scraping: no API exists; if the Studentenwerk changes its HTML the parser yields empty results (the cache is kept and
  "update failed" is shown). The fallback host is a test server: it may disappear or serve different data. PIER 16 is a hand-edited page and only best effort.
- PIER 16 has only 2 prices per dish that can't be mapped to price groups; no legend or CO2e data there; a dish without
  allergen code is not known to be allergen-free.
- Card statistics are derived from balance differences only: several payments between two scans can't be told apart
  without asking, and a payment's day is only known when no weekday lies between two scans. MagnaCarta (no last debit)
  counts every decrease as one payment.
- Card: only DESFire/ISO-DEP cards (Intercard, MagnaCarta); no MIFARE Classic, no card history. MagnaCarta is untested
  on a real card. Reading needs a card whose balance file is readable without a key.
- The popup activity matches every ISO-DEP tag: bank cards start it (it finishes silently), and other apps with an IsoDep
  `TECH_DISCOVERED` filter (e.g. an eID app) make Android show a chooser. Reader mode (Karte screen) wins over the popup.
- Release builds need the release key (see Releases); there is no CI and no baseline profile.
- UI languages: de and en only (`localeFilters`).

## License
GPL-3.0-or-later (`LICENSE`, `README.md`, `NOTICE`). **Why:** the card reader (`card/`) is translated to Kotlin from
MensaGuthaben (Jakob Wenzel, GPL-3.0-or-later), which bundles FareBot code (Eric Butler), so the app is a derivative
work and must be GPL when distributed. Rules: every derived file keeps the original copyright lines and carries a GPL
header saying it was translated to Kotlin and modified (with the date); `NOTICE` lists the derived files. New code
taken from those projects needs the same treatment.

## Stack
Versions in `gradle/libs.versions.toml`. Deliberately minimal: manual DI (`AppContainer`); no Hilt/Koin, OkHttp/Retrofit,
Room or Navigation library (screens are overlays in `MensaRoot`); WorkManager only for the favorites job and the card reminder,
lifecycle-process only for the updater's foreground check.

## Architecture (`:app`, `:core`, `:scraper`)
`:core` is plain Kotlin/JVM and holds what doesn't need Android: `model`, `parser`, `data/HostFallback`,
`data/MenuRepository`, `data/cache/MenuCache`, `data/remote`, `favorites/Favorites` (same packages as in the app; their
tests stay in `app/src/test`). `:app` is everything else. `:scraper` is a CLI on top of `:core` for the statistics site
(`Luwify/ovgu-mensa-stats`): `scraper --out <dir>` writes `YYYY/MM/DD/<mensa>.json` (format: KDoc of `DayFile`), one
`MenuRepository` per Mensa (every page asks the main site first), rules in `Scraper`: no time in the files, so a rerun
changes nothing; the fallback host never replaces main-site data and adds no past days; a Mensa that fails doesn't stop
the others, exit 1 only when no Mensa could be fetched (pages without dishes are a success). The site's workflow builds it from the latest release tag.
- `data/remote`: `HttpURLConnection` GET (gzip, custom User-Agent, connect 6 s / read 8 s) on `Dispatchers.IO`.
  `MenuHosts.all` = the base URLs in order (main site, then fallbacks; one line each, remove a line to drop a host);
  `Mensa.path` is relative to them.
- **Host fallback** (`data/HostFallback`, pure; the loop in `MenuRepository.fetchFromHosts`): a host is skipped when the
  fetch fails (timeout, error status) or its page has no dishes and either lacks `div.entry-content`
  (`PageCheck.hasContentBlock`: maintenance/PHP error pages, often 200) or would replace cached dishes. A page with the
  block but no dishes over an empty cache = closed Mensa, accepted from the main site without asking a fallback. A
  fallback that worked is tried first for 1 h (shared by all Mensen, not extended while used, kept in
  `cache/menus/fallback-host.txt` across cold starts: `HostMemory`), then the main site again; the main site working forgets it. Nothing usable anywhere: as before (cache kept, "update failed").
  Data from a fallback is marked (`CachedMenu.fromFallback` -> `MenuState` -> footer "Daten vom Ersatzserver").
  Tests: `HostFallbackTest`, `MenuRepositoryFallbackTest`.
- `parser`: **pure Kotlin, no Android imports**, JVM unit-testable
  - `MenuPageParser`: day tables, dishes, legend
  - `PierParser`: PIER 16's hand-edited fixed list. **Done**, best effort (see the PIER 16 notes below).
- `data/cache`: one JSON file per Mensa (parsed model + `fetchedAt`).
  Room was rejected: the data is one small document read as a whole, with no queries,
  and Room would add KSP and build time.
- `data/MenuRepository.observeMenu(id): Flow`: emits the cache immediately, then refreshes
  in the background (stale-while-revalidate). The page has no ETag/Last-Modified
  (`max-age=600` only), so freshness uses our own timer: refresh when older than ~30 min,
  on app foreground, or on pull-to-refresh. On failure it keeps the cache and shows "last updated".
  Days the site dropped stay up to `PAST_DAYS_KEPT` (14) days in `CachedMenu.past` for the statistics only
  (`MenuState.pageWithPast`); `page` stays what the site gave, so the menu screens and the fallback rules don't see them.
- `data/settings`: one DataStore (`SettingsRepository`, built only by `AppContainer`): default Mensa id, tab order, tag filters,
  excluded codes, price group, compact mode, theme mode, favorites (+ their `favorite_info`), card statistics settings, notification settings/dedup set (string sets where
  values can contain commas). The first-run picker is shown only when the default is unset. The updater has its own
  DataStore `updates` (`data/update/UpdateSettings`); `SingleDataStoreTest` allows exactly these two names, each declared once.
- Mensa list is bundled and data-driven (id, name, address, URL, page format `DATED`/`FIXED`), OvGU-relevant ones first.
  New entries (like PIER 16) are appended to every saved tab order by `resolveOrder`.
- UI: day pager over the dates present in the data (weekends and missing days never show),
  dish cards, filter chips built from the discovered tags.
- Card balance reader (`card/`, NFC; icon in the top bar opens the "Karte" overlay in `MensaRoot`, hidden without NFC):
  - Pure Kotlin (JVM-testable): `DesfireProtocol` over a `DesfireSession` interface (APDU wrap, 91 AF reassembly),
    `DesfireFileSettings`, `IntercardReader` (AID 0x5F8415, file 1, value file; GET_VALUE = balance, limited credit
    value of the file settings = last debit), `MagnaCartaReader` (AID 0xF080F3, file 2, bytes 6..7 big endian ×10, no last
    transaction), `CardReaders.read` (MagnaCarta first, then Intercard; Broadcom workaround = dummy `selectApp(0)`
    that may answer too short, then reconnect). Amounts are thousandths of a euro (original's scale, /1000).
  - Android side: `NfcCardScanner` (reader mode `FLAG_READER_NFC_A | SKIP_NDEF_CHECK`, started/stopped by `MainActivity` onResume/onPause, so a card held
    while the app is open opens the Karte screen (`MensaRoot`, on Reading/Unsupported) instead of the popup; `reset()` on back; state in a `StateFlow<CardScanState>`, cleared on stop). Only DESFire/ISO-DEP; no MIFARE Classic.
  - **Intercard reader: verified on a real card** (2026-10-01: balance and last withdrawal correct).
    **MagnaCartaReader: ported from the original, NOT verified on a real card** (no card available), unit tests on a fake
    session only.
  - `CardPopupActivity`: the only NFC intent filter (`TECH_DISCOVERED`, IsoDep, `res/xml/nfc_tech_filter.xml`); the main
    activity has none. Transparent window, draws only after the read (unsupported card = finish silently), shows
    balance + last withdrawal + "App öffnen". Own task affinity, excludeFromRecents, noHistory, finishes on stop,
    consumes the tag once. Theme and settings come from the same `AppContainer` (one DataStore per process: never create a
    second DataStore for `settings`; `SingleDataStoreTest` guards this). While the app is in the foreground, reader mode takes
    precedence over the popup. Any other app with an IsoDep `TECH_DISCOVERED` filter makes Android show a chooser.
  - Privacy rules: no network, never log card data (not even in debug), never store or log the UID (looked at only after a successful read with the statistics on, and it only becomes a
    fingerprint in memory, `CardIdentity`). Nothing is stored unless the user turns on the card statistics and registers
    the card; then only scans of that card (balance, last debit, time) and a salted hash. Test bytes are synthetic.

## Build / test commands
Needs JDK 21 (`JAVA_HOME`) and the Android SDK (`sdk.dir` in the git-ignored `local.properties`, or `ANDROID_HOME`).
```bash
./gradlew testDebugUnitTest assembleDebug lint   # run after EVERY change, fix failures first (lint must report no issues)
./gradlew clean testDebugUnitTest assembleDebug lint   # the same from scratch
./gradlew :scraper:installDist                   # the statistics CLI: scraper/build/install/scraper/bin/scraper --out <dir>
./gradlew installDebug                           # debug copy "Mensa Preview" (de.luwify.mensamd.preview, debug key)
./gradlew installRelease                         # install the release build (needs the signing properties, see Releases)
./gradlew assembleRelease                        # R8 minified, release-signed
```
Swipe smoothness (B3) is closed: judge smoothness on `installRelease` only (debug is ~12x jankier); no further investigation planned.
JDK 21 is required (Android Studio's JBR); a newer system JDK doesn't work with this Gradle/AGP.
Lint's "a newer version of X is available" notices (NewerVersionAvailable, GradleDependency, AndroidGradlePluginVersion)
depend on what is published that day; versions are kept in step with Stundenplan MD, so they are bumped in both apps
together, not on their own.

## Releases, updater, Stundenplan MD banner
Details in the skill `release-updater` (`.claude/skills/release-updater/SKILL.md`): version and changelog rules,
signing, updates.json, `tools/release.sh`, updater and banner internals. `tools/`, the updater (`update/`, `data/update/`,
`ui/UpdateScreens.kt`) and the banner are copies of Stundenplan MD: **keep both copies in sync**. The banner only opens the other app or its release page in the browser: **never install another app in-app** (Play Protect blocked every version for that, 2026-11). **Only the maintainer
runs the real release** (`tools/release.sh <ver> --dry-run` is allowed). New third-party code, libraries or icons go into
NOTICE and `LicensesScreen`.

## Data source
No API: the menus are scraped from the Studentenwerk HTML (Jsoup). Markup, URLs, PIER 16, the fallback host and the
fixture rules are in the skill `studentenwerk-site` (`.claude/skills/studentenwerk-site/SKILL.md`).

## Rules
- Check the real environment before assuming anything; be direct and technical.
- Never run `sudo` or install system/SDK packages; give the user the exact command instead.
  Stay inside the project directory.
- Small steps; after each working step, run the build/test/lint command above, then commit
  with a clear message.
- Tell the user exactly what to check manually on the device after an install.
- Device tests only on the debug build (`de.luwify.mensamd.preview`, launcher label "Mensa Preview"), never on the
  real app `de.luwify.mensamd` (it holds the user's favorites and settings): no `pm clear`, no uninstall, no `run-as` on it.
- **Secrets:** never read, print, cat, grep or copy `~/.gradle/gradle.properties`, the keystore or any token; never
  put secrets into the repo, logs or commit messages. Builds that use the signing config and
  `tools/release.sh --dry-run` are allowed; the real release only the user runs.
- **Card values:** docs, tests, fixtures, changelogs and commit messages never contain real card values (balances, amounts
  of real payments or top-ups, UIDs, card bytes). Test values are made up; docs describe what happened, without the amount.
- Speed is the main feature: cached menu instantly, no blocking splash, parse off the main thread,
  minimal dependencies, R8 for release.
- Don't hardcode tag/allergen legends. Unknown tags must render, never crash or be dropped.
- Dish names follow the system locale (de/en); UI strings in de + en.
- Ask the user only when something is truly blocking.
