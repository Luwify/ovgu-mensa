---
name: release-updater
description: Mensa MD release scheme (version.properties, changelogs, signing, updates.json, tools/release.sh) plus the in-app updater and Stundenplan MD banner (both ported from Stundenplan MD, kept in sync). Load before preparing a release, writing a changelog, or touching update/, data/update/, UpdateScreens, UpdatePrompts or the banner.
---

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
- **tools/release.sh <ver> [--dry-run]** (gh CLI): preflight checks, release build, APK/cert/manifest verification,
  source tarball and new updates.json (read the script for the exact steps); a dry run stops before tagging (output in
  `build/release/<ver>/`). On failure it prints what was done and how to clean up (deletes nothing). **Only the
  maintainer runs the real release.** `--backfill <tag>` rebuilds an existing tag (not needed so far).
- `tools/withdraw.sh <ver>` sets withdrawn=true in updates.json of the latest release (with confirmation).

## In-app updater
Ported from Stundenplan MD; **keep both copies in sync** (fixes in one belong in the other). Mapping: Stundenplan's
`:core` `update/` = `de.luwify.mensamd.update` here (pure Kotlin, no Android imports, tests in app/src/test), `data/update/`
and `ui/UpdateScreens.kt` = the same files here. Left out here: "App teilen" (QR + APK share, needs ZXing) and the updater
settings in the backup (Mensa MD's `backup/BackupFormat` covers the settings DataStore, not `updates`; Stundenplan MD's backup
carries the automatic check and the skipped version). Mensa-only glue: `ui/UpdatePrompts.kt` (snackbar, update dialog and the running
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

## Stundenplan MD banner (settings, top)
Ported from Stundenplan MD's Mensa MD banner; **keep both copies in sync** (same mapping as the updater). Pure:
`update/Companion.kt` = Stundenplan's `:core` `update/Companion.kt` with `CompanionApp.STUNDENPLAN` (package
`de.luwify.stundenplanmd`, manifest of `Luwify/ovgu-stundenplan`, release page, pinned certificate =
`release/signing-cert.sha256`, the same key), `CompanionPolicy` (`banner` state, `release`, `problems`: signer must be
exactly the pin, not this app's own, so the .preview build installs it the same way); tests `update/CompanionTest`.
App: `data/update/Companion.kt` (`CompanionInstaller`: installed check via `<queries>`, re-checked in
`MainActivity.onResume`; download with the updater's `UpdateHttp`; `ApkInstaller.install(packageName, companion = true)`
so `InstallResultReceiver` routes the result; "unknown apps" via the shared `UnknownSourcesDialog`; `Failure.Unavailable`
when the manifest offers nothing for this device), `ui/StundenplanBanner` (+ `StundenplanDialogs`). Icon
`ic_stundenplan.xml` = Stundenplan's adaptive launcher icon in one vector. Hide flag: settings key
`stundenplan_banner_hidden` (X on the card, switch "Hinweis auf Stundenplan MD anzeigen" after the "Version" section;
setter takes null = back to "never chosen"); unset: `CompanionPolicy.wanted` shows the card (first item of the
settings) only while Stundenplan MD isn't installed. Same as Stundenplan MD's banner; a backup keeps the choice in both apps
(here as an ordinary settings key). Mensa-only differences: card
colors from `LocalMensaColors`, `toUri()` for the release page (lint `UseKtx`), and `CompanionTest` also checks that the banner stays during an install.
