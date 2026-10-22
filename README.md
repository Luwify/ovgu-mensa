# Mensa MD

> [!WARNING]
> **This project is completely vibecoded.** The code was written by an AI (Claude Code) from prompts, not by hand.
> Expect bugs, use it at your own risk, and don't treat the code as a reference for how things should be done.

Android app that shows the menus of the Studentenwerk Magdeburg canteens and cafeterias, fast: the cached menu
appears instantly and refreshes in the background. It can also read the balance of your Mensa card over NFC.

- All canteens and cafeterias (UniCampus, Kellercafé, Herrenkrug, PIER 16, Stendal, Halberstadt, Wernigerode) as
  swipeable, reorderable tabs; a day pager per canteen.
- Filters: vegan, vegetarian, cake of the day, favorites, and hiding dishes by additive or allergen code.
- Prices for your price group, CO₂e and water per portion when the Studentenwerk publishes them.
- Favorite dishes (a heart on each card) and an optional daily notification when a favorite is on the menu.
- Card balance (Intercard cards; MagnaCarta support is ported but untested on a real card): hold the card to the
  phone. Everything stays on the device; nothing is logged, stored or sent.
- Themes System, Soft, AMOLED and a custom theme; German and English.

**Not affiliated with or endorsed by the Otto-von-Guericke-Universität Magdeburg or the Studentenwerk Magdeburg.**
The menus are read from the Studentenwerk's public web pages (there is no API); if the site changes or is down, the
app shows the last menu it has.

## Download

Get the APK from the [GitHub releases](https://github.com/Luwify/ovgu-mensa/releases/latest) (Android 8.0 or newer)
and install it ("install unknown apps" has to be allowed for your browser or file manager). The app then finds and
installs updates itself (Settings → Version); every update is checked against its SHA-256 and the signing key before
Android asks you to confirm it. The only network requests are the menu pages of the Studentenwerk and the version
list of this repository's releases, without any identifiers.

Signing certificate SHA-256 (CN=Luwify):
`D7:17:D7:0F:F7:FC:19:6D:0E:5E:FD:BE:D3:39:A4:BF:E0:A0:1C:BF:BB:DD:66:DE:75:11:15:CD:94:15:49:AF`

## Build

Requirements: JDK 21 and the Android SDK (compileSdk 37). Put `sdk.dir=/path/to/Sdk` in `local.properties` or set
`ANDROID_HOME`.

```bash
./gradlew testDebugUnitTest assembleDebug lint   # unit tests, debug APK, lint
./gradlew installDebug                           # debug build "Mensa Preview" (debug key, own package)
./gradlew assembleRelease                        # R8-minified APK, needs the release signing properties
```

Release builds are signed with the maintainer's key; they need the Gradle properties `LUWIFY_STORE_FILE`,
`LUWIFY_STORE_PASSWORD`, `LUWIFY_KEY_ALIAS` and `LUWIFY_KEY_PASSWORD` and fail without them. To build your own
release, point them at your own keystore. The version lives in `version.properties`.

Min SDK 26, target SDK 37. Single module `:app`; see `CLAUDE.md` for the architecture and the data-source notes.

## License

Mensa MD is licensed under the **GNU General Public License, version 3 or (at your option) any later version**
(GPL-3.0-or-later); see [LICENSE](LICENSE).

The card reader is a Kotlin translation of parts of
[MensaGuthaben](https://github.com/jakobwenzel/MensaGuthaben) by Jakob Wenzel (GPL-3.0-or-later), which in turn
contains code from [FareBot](https://github.com/codebutler/farebot) by Eric Butler (GPL-3.0-or-later). That makes the
whole app a derivative work, so it is distributed under the same license. Details, the list of derived files and the
other third-party material (icons, libraries): [NOTICE](NOTICE). The app shows the same under Settings → Lizenzen und
Quellcode.
