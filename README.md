# Mensa MD

Android app that shows only the menus of the Studentenwerk Magdeburg canteens, fast: the cached menu appears
instantly and refreshes in the background. It also reads the balance of your Mensa card over NFC.

- All Mensen and cafeterias as swipeable, reorderable tabs; day pager; filters by tag and by additive/allergen code;
  compact mode; themes System, Soft and AMOLED; German and English.
- Card balance (Intercard cards; MagnaCarta support is ported but untested on a real card): hold the card to the back
  of the phone for a small popup, or use the card icon for the "Karte" screen. Everything stays on the device; nothing
  is logged, stored or sent.
- The menus are scraped from the public pages of the Studentenwerk Magdeburg (there is no API). The app is not
  affiliated with or endorsed by them.

## Build

Requirements: JDK 21 and the Android SDK (compileSdk 37). Put `sdk.dir=/path/to/Sdk` in `local.properties` or set
`ANDROID_HOME`.

```bash
./gradlew testDebugUnitTest assembleDebug lint   # unit tests, debug APK, lint
./gradlew assembleRelease                        # R8-minified APK (signed with the debug key)
./gradlew installRelease                         # install on a connected device
```

Min SDK 26, target SDK 37. Single module `:app`; see `CLAUDE.md` for the architecture and the data-source notes.

## License

Mensa MD is licensed under the **GNU General Public License, version 3 or (at your option) any later version**
(GPL-3.0-or-later); see [LICENSE](LICENSE).

The card reader is a Kotlin translation of parts of
[MensaGuthaben](https://github.com/jakobwenzel/MensaGuthaben) by Jakob Wenzel (GPL-3.0-or-later), which in turn contains
code from FareBot by Eric Butler. That makes the whole app a derivative work, so it is distributed under the same license.
Details and the list of derived files: [NOTICE](NOTICE).
