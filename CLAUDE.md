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
  sweep-edge frame with twinkling stars was removed 2026-10-02). Aura: drawn by `SuperAuraOverlay`, ONE Canvas above the LazyColumn for all visible favorite cards (positioned from
  `layoutInfo`), so it lies over neighboring cards and headers. The shader (`AuraShader.kt`, Android 13+) masks the
  card's own area, fades out over 28 dp below the pinned header (`SparkleMath.pinnedHeaderBottom`) and before the
  viewport end, falls off as (1 - d/R)^p (exactly zero; p 4, flattening to 1.6 toward the tail) and is dithered
  (triangular IGN, 1/255). It always hugs the card: only the reach changes per direction (`SparkleMath.trailReach`:
  + up to `TAIL_EXTRA_DP` 70 dp behind, strongest mid-edge, -25 % ahead). Drag (`ScrollDrag`, part of "Favoriten funkeln"; the former hidden Super-Funkeln was merged in and removed 2026-10-08):
  velocity per scroll step (dp/s), low-pass `TRAIL_TAU_MS` 70 ms, target = tanh(|v| / `TRAIL_REFERENCE_DP_S` 1600)
  (500 dp/s 30 %, 1500 dp/s 73 %, fling ~100 %), followed per frame (`TRAIL_FOLLOW_MS` 50 ms; never restart an
  animation per scroll step: it gets cancelled before its first frame); `RELEASE_MS` 60 ms after the last step an
  underdamped spring (0.3 / 140) returns it (overshoot ~1/3 of the stretch). Draw area: base reach on all sides, tail
  only on the trailing side. Iridescent gold, sparkles on the outline. Below API 33: nested filled round rects
  (`stackedAlphas`), anchored to the card. ~30 fps clock only while a favorite card is visible.
  Card glows (AMOLED/custom `cardGlow`) are `EdgeGlow` (`EdgeGlow.kt`): one AGSL gradient per card built in
  `drawWithCache`, dithered; below API 33 14 non-overlapping ring strokes (forced on a phone, AMOLED list: 1.9 %
  janky / p99 18 ms vs 0.7-0.8 % / 16 ms shader). `MEASURE_EDGE_GLOW_FALLBACK` must stay false in commits.
  Measured on a phone (release, System theme, test data, 2026-10-02, with drag): scroll p99 16 ms, janky 0.69 %; idle 30
  frames/s, frame p50 10 ms / GPU 6 ms. Known: a card hidden under the pinned header can still glow below it while
  scrolling (faded in, no line).
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
  card layout (normal/compact chips), design (color scheme drop-down + custom theme editor), favorites notifications,
  version (in-app updater, see below), about (licenses and source code). The filter sheet ("Filter" button) has only the allergen exclusion.
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
- **License GPL-3.0-or-later** (see below).
- Not done / only if asked: a baseline profile (separate `:baselineprofile` module). Swipe smoothness (B3) is closed.

## Known limitations
- Scraping: no API exists; if the Studentenwerk changes its HTML the parser yields empty results (the cache is kept and
  "update failed" is shown). PIER 16 is a hand-edited page and only best effort.
- PIER 16 has only 2 prices per dish that can't be mapped to price groups; no legend or CO2e data there; a dish without
  allergen code is not known to be allergen-free.
- Card: only DESFire/ISO-DEP cards (Intercard, MagnaCarta); no MIFARE Classic, no history, no UID. MagnaCarta is untested
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
- Kotlin 2.4 (AGP 9 built-in Kotlin), AGP 9.4, Gradle 9.8 (wrapper), JDK 21 (Android Studio JBR)
- Jetpack Compose + Material 3 (BOM 2026.09), dynamic color, edge-to-edge, dark mode
- compileSdk 37, targetSdk 37, minSdk 26
- WorkManager 2.12 (only for the daily favorites job)
- Coroutines/Flow, ViewModel, DataStore Preferences (no Navigation library: the card screen is an overlay in `MensaRoot`)
- Jsoup (HTML parsing), kotlinx.serialization (JSON cache)
- Manual DI (`AppContainer`), no Hilt/Koin, no OkHttp/Retrofit
- Package / applicationId: `de.luwify.mensamd`, app name "Mensa MD"; debug builds `de.luwify.mensamd.preview` ("Mensa Preview")
- lifecycle-process (only for the updater's foreground check)
- Versions live in `gradle/libs.versions.toml`

## Architecture (single module `:app`)
- `data/remote`: `HttpURLConnection` GET (gzip, custom User-Agent) on `Dispatchers.IO`
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
- `data/settings`: one DataStore (`SettingsRepository`, built only by `AppContainer`): default Mensa id, tab order, tag filters,
  excluded codes, price group, compact mode, theme mode, favorites, notification settings/dedup set (string sets where
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
  - Privacy rules: no network, never log card data (not even in debug), never read/store the UID, no history, nothing on
    disk. Test bytes are synthetic.

## Build / test commands
Needs JDK 21 (`JAVA_HOME`) and the Android SDK (`sdk.dir` in the git-ignored `local.properties`, or `ANDROID_HOME`).
```bash
./gradlew testDebugUnitTest assembleDebug lint   # run after EVERY change, fix failures first (lint must report no issues)
./gradlew clean testDebugUnitTest assembleDebug lint   # the same from scratch
./gradlew installDebug                           # debug copy "Mensa Preview" (de.luwify.mensamd.preview, debug key)
./gradlew installRelease                         # install the release build (needs the signing properties, see Releases)
./gradlew assembleRelease                        # R8 minified, release-signed
```
Swipe smoothness (B3) is closed: judge smoothness on `installRelease` only (debug is ~12x jankier); no further investigation planned.
JDK 21 is required (Android Studio's JBR); a newer system JDK doesn't work with this Gradle/AGP.
Lint's "a newer version of X is available" notices (NewerVersionAvailable, GradleDependency, AndroidGradlePluginVersion)
depend on what is published that day; versions are kept in step with Stundenplan MD, so they are bumped in both apps
together, not on their own.

## Releases
Same scheme and tools as Stundenplan MD (`tools/` is a copy: keep the two in sync; this app's settings are only in
`release/app.conf` and `release/signing-cert.sha256`).
- **Version:** `version.properties` (`version=major.minor.patch`) is the only source; `app/build.gradle.kts` derives
  versionName and versionCode = major*10000 + minor*100 + patch (minor, patch < 100). Raise it in a commit before
  releasing (with `changelog/<ver>.de.md` and `.en.md`, short, for users); the script doesn't bump. 1.0.0 is the first
  public release.
  Changelog style (`unreleased.*.md` and release files): impersonal and user-facing, grouped under short headings when
  there are several kinds of change; describe what the app does now ("Der Kuchen-Filter zeigt jetzt …"), no first
  person, "du" only where needed; no internal details (class names, timings, test numbers).
- **Signing:** release build type uses `signingConfigs.release` from the Gradle properties `LUWIFY_STORE_FILE`,
  `LUWIFY_STORE_PASSWORD`, `LUWIFY_KEY_ALIAS`, `LUWIFY_KEY_PASSWORD` (in the maintainer's `~/.gradle/gradle.properties`;
  the same key as Stundenplan MD). Missing -> `checkReleaseSigning` fails the release build (never debug-key fallback);
  debug builds don't need them. Pinned certificate SHA-256 in `release/signing-cert.sha256` (CN=Luwify, `d717d70f…941549af`);
  the script refuses a real release without a valid pin.
- **GitHub releases** (repo `Luwify/ovgu-mensa`, branch `main`): every release has the APK `mensa-md-<ver>.apk`, the GPL
  source archive and `updates.json` with the full history. The app reads
  `https://github.com/Luwify/ovgu-mensa/releases/latest/download/updates.json`.
  `updates.json`: `update/UpdateManifest` (`schema` 1, `app`, `releases` newest first: versionName, versionCode, date,
  minSdk, changelog de/en, apkUrl `.../releases/download/v<ver>/mensa-md-<ver>.apk`, sha256, size, withdrawn),
  `Manifests.parse` (TooNew/Broken, validation, unknown keys ignored), `updateFor(installed, sdk)`. Written only via
  `./gradlew :app:manifest` (`ManifestTool.kt` in app/src/test: add/withdraw/check, run on the unit-test classpath), so
  the writer is the app's parser.
- **tools/release.sh <ver> [--dry-run]** (gh CLI): preflight (gh login, remote = the GitHub repo, repo public, branch
  main, clean tree, version.properties == ver, changelogs, tag neither local nor on the remote, no such release, no newer
  release, tests + lint), assembleRelease with `-PupdateManifestUrl` set explicitly, apksigner cert == pin, aapt2
  package/versionCode/versionName/not debuggable/minSdk, manifest URL in the APK, sha256 + size, `git archive` source
  tarball (`.gitattributes` export-ignore: CLAUDE.md, notes/, prompt.md, tools/, release/), new updates.json from the
  newest release's one + diff; dry run stops there (output in `build/release/<ver>/`; it also runs before the GitHub
  remote exists, with a warning). Real run: tag + push, draft release with notes and all three files, check the asset
  sizes, publish (`--latest`), then an anonymous outsider check (https only, at most 3 redirects, final host = the ones
  the app allows). On failure it prints what was done and how to clean up (deletes nothing). **Only the maintainer runs
  the real release.** `--backfill <tag>` rebuilds an existing tag (not needed so far).
- `tools/withdraw.sh <ver>` sets withdrawn=true in updates.json of the latest release (with confirmation).

## In-app updater
Ported from Stundenplan MD; **keep both copies in sync** (fixes in one belong in the other). Mapping: Stundenplan's
`:core` `update/` = `de.luwify.mensamd.update` here (pure Kotlin, no Android imports, tests in app/src/test), `data/update/`
and `ui/UpdateScreens.kt` = the same files here. Left out here: "App teilen" (QR + APK share, needs ZXing) and the backup
integration (Mensa MD has no backup). Mensa-only glue: `ui/UpdatePrompts.kt` (snackbar, update dialog and the running
update's dialogs in `MensaRoot`, so they show above the settings and card overlays), the gear badge in `MainScreen`, the
"Version" section and the version pages in `SettingsScreen`.
- `update/UpdatePolicy.kt`: `UpdateSource` (manifest URL, APK prefix `.../releases/download/`, redirect rules, `override`
  for debug; `forManifestUrl` accepts only `https://github.com/<owner>/<repo>/releases/latest/download/<file>`),
  `UpdatePolicy` (auto check at most every 6 h since the last successful one, overview hides withdrawn except the
  installed one, `canInstall` = newer + not withdrawn + minSdk, `offer` minus the skipped version, snackbar once per
  version), `ApkCheck` (size, sha256, package, versionCode > installed and == manifest, signer set == installed). Manifest
  validation allows plain http only for 127.0.0.1/localhost (the release source still requires https).
- **Manifest URL per build:** Gradle property `updateManifestUrl` (default the GitHub URL above) ->
  `BuildConfig.UPDATE_MANIFEST_URL` -> `data/update/ReleaseSource.kt`; `ReleaseSourceTest` checks that it parses.
- **Redirects:** GitHub answers `latest/download/<f>` with 302 -> `github.com/<repo>/releases/download/<tag>/<f>` -> 302
  -> `release-assets.githubusercontent.com/...?<signed query>`. `UpdateHttp` follows redirects by hand, each hop through
  `UpdateSource.redirect` (https only, no user info, default port, no fragment; below the repo's `releases/download/` on
  github.com, or any path on `release-assets.githubusercontent.com`); at most 3. Anything else fails safe.
- `data/update/`: `Updates` (process-wide in `AppContainer`: state, check whenever the app comes to the foreground
  (`ProcessLifecycleOwner` ON_START registered in `MensaApp`, after the next frame; the card popup counts as foreground
  too, the 6 h throttle applies), download to `cacheDir/updates` with progress/cancel, verify, PackageInstaller session,
  "install unknown apps" explanation once then `ACTION_MANAGE_UNKNOWN_APP_SOURCES`, continues in `MainActivity.onResume`;
  APKs deleted after the session write and once per process start), `UpdateHttp` (HttpURLConnection, checked redirects,
  User-Agent `MensaMD/<version>`, no identifiers), `ApkInspector`/`ApkInstaller`/`InstallResultReceiver`, `UpdateSettings`
  (DataStore `updates`, `schema_version` 1: auto_check, last_check, manifest (last good one, shown offline),
  announced_code, skipped_code, unknown_sources_explained).
- UI `UpdateScreens.kt`: settings section "Version" (App-Version, Versionsübersicht, "Update auf x", Nach Updates suchen
  + status, Automatisch suchen), overview (radio = installed), detail (changelog de/en, Installieren only if installable),
  `UpdateDialog` (snackbar "Anzeigen"; "nicht erneut anzeigen" = skip), download dialog (Abbrechen/Minimieren; while
  installing "Schließen", because Android may never report back, e.g. Play Protect), failure dialog.
- **Debug-only override** (src/debug; src/release has none): `files/debug_update_url` in the .preview app, e.g.
  `http://127.0.0.1:8765/updates.json` served on the dev machine with `adb reverse tcp:8765 tcp:8765`; cleartext only to
  127.0.0.1/localhost (debug network_security_config); no redirects. Test APKs: debug builds with a raised
  version.properties (never commit that), manifest via `:app:manifest add` with app id `de.luwify.mensamd.preview`.
- Licenses: settings section "Über die App" -> `LicensesScreen` (GPL notice, source link `SOURCE_URL`, not-affiliated
  note, MensaGuthaben/FareBot credits, libraries, jsoup MIT text from `res/raw/license_jsoup.txt`). New third-party code,
  libraries or icons go into NOTICE and this screen.

## Data-source findings (investigated 2026-09-30 with curl)
- **No API/feed.** The site is WordPress; `/wp-json/` has no menu routes. The OpenMensa entries
  (ids 1, 157, 2, 3, 4, 5) are dead (no days, old address). → Scrape HTML with Jsoup.
- **One GET per Mensa returns all published days** (usually ~6 working days, sometimes with gaps,
  e.g. 05.10 then 12.10–14.10). Date query params are ignored. Take the day from the heading
  text, never from its position. Response is ~25 KB gzipped.
- robots.txt only disallows `/wp-admin/`. Be polite: cache, no polling, UA `MensaMD/<version> (student project)`.
- Canonical URLs (`https://www.studentenwerk-magdeburg.de/mensen-cafeterien/…`):

  | page_id | slug | notes |
  |---|---|---|
  | 1684 | `mensa-unicampus-speiseplan-unten/` | UniCampus lower hall |
  | 2200 | `mensa-unicampus-speiseplan-oben/` | UniCampus upper hall |
  | 4851 | `mensa-kellercafe-speiseplan/` | Kellercafé (Zschokkestraße / Geb. 40) |
  | 1679 | `mensa-herrenkrug-speiseplan/` | Herrenkrug |
  | 16740 | `pier-16-speiseplan/` | **different format** (`PageFormat.FIXED`), implemented, see below |
  | 1676 | `mensa-stendal-speiseplan/` | |
  | 1672 | `mensa-halberstadt-speiseplan/` | DomCafete |
  | 656 | `mensa-wernigerode-speiseplan/` | |
  | – | `blub-wernigerode-speiseplan/` | often 1 dish per day |

- Markup (the `#content .entry-content` block):
  - `div.mensa > table` per day; the date is in `thead` ("Donnerstag / Thursday, 01.10.2026")
  - `tbody tr` per dish:
    - `td[80%]`: the German name is the first text (possibly wrapped in `span.gruen`),
      the English name is `span.grau`, prices are `span.mensapreis` "(3,40 | 5,10 | 6,50)"
    - `div.mensakennsymb img`: tag icons (`class=lazyload`, `data-src`; a duplicate sits in `<noscript>`)
    - `div.mensakennzahl`: codes "(1) (9) (a1) (g)"
    - `div.mensaeaternity` ×2: "1119g CO₂e / Portion", "16l / Portion", or "Keine Daten / No data" → hide
  - `span.gruen` = vegan or vegetarian highlight (redundant with the icons)
  - Name prefixes "Save the meal:", "Grab the prep:", "Aktionstheke:" / "Promotional counter:"
    → strip and show as a badge. "BIO-" appears inside names.
- Tags: there is **no text legend for tag icons** (`#legendeeaternity` explains only CO2e/water).
  - Key = icon filename (`mensasym_<key>.png`); label = alt text minus "Symbol ".
  - The site emits broken alt text (`"Symbol enth lt Alkohol"`), so never key on alt.
  - Known keys (2026-09): vegan, vegetarisch, knoblauch, gefluegel, schwein, fisch, rind, alkohol, suppe.
  - Unknown keys must render with the alt label.
- `div.legende` (parse, cache, don't hardcode) has German text plus English in `span.grau`:
  - price groups (Studenten | Bedienstete | Gäste)
  - additives 1–10, allergens a1–n, other codes A/P/Q/R/S/T
  - "Save the meal" / "Grab the prep"
  - The German allergen list is missing h7–h9, which only the English list has → merge both.
- PIER 16 (Gebäude 16, Denhardtstr. 2, 39106 Magdeburg): **implemented**, but the parser is **best effort** because
  the page is hand-edited and can change shape without notice. A hand-edited fixed list, no dates, mostly German only.
  - Markup (checked live 2026-10-01, identical to 2026-09-30): one `table`, one `tr` per dish,
    `<strong>Name</strong> (3, 9,a1,i,j, k, rin)<br /> English text<br /> 4,20 l 5,90 l 7,30`.
    The English line is optional; the price separator is a literal letter `l`; no headings, sections, dates or legend today.
  - Codes and tags share the parentheses: tokens like `3`, `a1`, `g` are codes (same legend as the other Mensen);
    `kno rin sch get veg vgt` are tags, mapped to knoblauch/rind/schwein/gefluegel/vegan/vegetarisch.
    Unknown tokens are kept as tags (shown as text), never dropped.
  - Prices: 3 = students | staff | guests (same as the other Mensen); **only 2 cannot be matched to groups**, so those dishes
    have `priceGroupsKnown = false` and show plain amounts without group names or a highlighted pill.
  - Headings (`h2-h4`) or a bold-only row start a new `FixedSection`; none exist on the page today.
  - No legend on the page: `MenuRepository` borrows the legend of the first dated Mensa that has one cached
    (without any cached, codes show raw). No CO2e/water data. Codes are typed by hand, so a dish **without** an allergen
    code is not known to be free of it (the filter sheet says so).
  - Model: `MenuPage.fixed` (undated `FixedSection`s) next to `days`; the UI shows one "Festes Angebot" label instead of day headers.
  - It never throws: a changed, empty or garbled page parses to an empty result; over a cached menu that counts as a failed refresh.
- The `/en/` variant of the pages adds nothing (same German alt texts), so don't fetch it.
- Test fixtures: every page from 2026-09-30 and a PIER 16 copy from 2026-10-01 in `app/src/test/resources/fixtures/`,
  trimmed to the `div.entry-content` block (the raw markup of that block is unchanged; the site's header, footer and
  scripts are left out). A new fixture is trimmed the same way: only the block the parsers read.

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
- Speed is the main feature: cached menu instantly, no blocking splash, parse off the main thread,
  minimal dependencies, R8 for release.
- Don't hardcode tag/allergen legends. Unknown tags must render, never crash or be dropped.
- Dish names follow the system locale (de/en); UI strings in de + en.
- Ask the user only when something is truly blocking.
