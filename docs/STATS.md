# Card statistics: work checklist

Resume point for the card statistics feature (started 2026-11-05). Tick items as they land; each phase ends with
`./gradlew testDebugUnitTest assembleDebug lint`, a commit and a push. No release.

## Phase 0: what the card exposes
- [x] Code review of the ported reader: Intercard = balance (GET_VALUE, file 1) + last debit (limited credit value
      of file 1's settings); MagnaCarta = balance only. No date, no counter, the UID is never read.
- [x] Probe on the real card (2026-11-05, temporary debug-only code, not committed; kept in the git-ignored
      `private/notes/card-probe/`), two scans:
  - MIFARE DESFire EV1 (hw 1.0, sw 1.4), so no EV2 transaction MAC counter.
  - UID: 7 bytes, NXP (first byte 0x04), NOT random, identical on both scans and identical to the UID in
    GET_VERSION. Stable card identity = the UID; only a salted PBKDF2 hash of it is stored.
  - Intercard app (wire AID 5F 84 15): file 0 = 32-byte standard file, free read (static, same content in another
    app; not needed), file 1 = the balance value file (free GET_VALUE via the EV1 "free get value" flag, limited
    credit value = last debit), files 2 and 3 = cyclic record files of 32-byte records (11 and 6 slots, both full;
    very likely the transaction and top-up logs) that need key 3: NOT readable. Further purse apps (5F8405, 5F8425,
    5F8435, 5F8445: value files with free GET_VALUE, other purposes, not used), 5F8455/65/75 and F3B6E9 locked.
  - Record counts are readable but saturate (cyclic files stay at max-1), so they are no transaction counter.
- [x] Result: the card offers balance + last debit (+ a stable UID) and nothing else usable: no date, no counter,
      no readable log. Recording works from balance differences as planned.

## Phase 1: card settings in the card screen
- [x] Gear in the card screen's top bar -> card settings (Statistik, Detaillierte Statistik, Erinnerung + time,
      Meine Karte register/remove, top-up and Pfand amount lists)
- [x] Statistics button next to the gear (only with Statistik on)
- [x] Meine Karte: only a hash of the card id; other cards show the balance with a hint and are not saved;
      offer registration on the first scan when Statistik is on and no card is registered
- [x] Store (`stats/StatsStore`, versioned JSON in filesDir/stats, migrations in `StatsMigrations`), card identity
      (`CardIdentity`: fingerprint of the UID in memory, salted SHA-256 stored), recorder wired to reader mode and popup
- [x] Registration and first recordings with the real card (2026-11-05, .preview: registration offer, baseline scan,
      unchanged second scan). Not yet seen on a real card: a payment, a top-up, another card (none at hand)

## Phase 2: recording
- [x] Versioned stats store with migrations + tests
- [x] Scan records (time, balance, last transaction)
- [x] Diff logic (one payment, several -> one/split, top-up, Pfand, top-up + payment) + tests (`BalanceDiff`)
- [x] Questions UI for ambiguous diffs (tested on the phone with seeded data)
- [x] Overview: per week/month, per weekday, average per visit, balance over time; link constant for the meal
      statistics website (`StatsLinks.MEAL_STATS_URL`, hidden while empty). Charts: own Canvas code, no library.

## Phase 3: detailed statistics
- [x] Matching (single, same dish twice, two dishes; all Mensen, default Mensa first) + tests (`MealMatcher`); one candidate overall is saved without asking
- [x] Where-did-you-eat question, pick among matches, extras flow, no-match flow (`MealViews`), tested on the phone
- [x] Meals overview (most eaten, by Mensa, vegan/vegetarian share, average per meal, add to favorites) (`MealSummary`)

## Phase 4: reminder
- [x] Rules (weekday, not scanned today, default Mensa has a menu) + tests; WorkManager window; permission
      (`CardReminderRules`, `notify/CardReminder.kt`; fired on the phone at the set time, tap intent opens the card screen)

## Phase 5: backup
- [x] Versioned export/import like Stundenplan MD (`backup/BackupFormat`, `BackupRepository`; settings page: export via
      CreateDocument, import via OpenDocument, confirm dialog, undo snackbar); round-trip tests. The file picker step is
      not tested on the phone (system picker = another app): manual check for the user

## Finish
- [x] Independent review subagent; fixes (2026-11-05): mistyped backup values dropped on import (`KEY_TYPES`), a
      half-failed import rolls back, a single dish match is saved without asking only when both scans were on the same
      day, "Karte registrieren" only while the card settings are open and never re-registers the same card, reminder
      window = up to 3 h after the set time (any time of day), single-price dishes match every price group, card hash =
      PBKDF2 (120k rounds; a 7-byte UID is brute-forceable with a fast hash), process-wide scan numbers, background
      save errors can't crash the app, unreadable `version` = broken file, reminder reschedules when settings can't be read
- [x] Changelog unreleased.*.md; installRelease over the real app (update only, 2026-11-05 18:02); commit, push

## Follow-ups
- [x] Pfand (2026-11-05, the user's rule): defaults 0.08 / 0.15 / 0.25 € per bottle (editable); the machine credits the
      sum of one return, so a credit counts as Pfand when it is a sum of these amounts and at most 5 €
      (`KnownAmounts.isPfandSum`); a top-up amount of the same size wins. Note: with 8/15/25 cents every whole-cent
      amount from 0.68 € to 5 € is such a sum (only 34 smaller amounts are not), so nearly every credit up to 5 € is
      read as Pfand. "Top-up + payment" offers top-up amounts only.
- [x] Increase rules (2026-11-05, the user's rule): an increase that is a top-up amount is a top-up. Otherwise: the
      card's last payment changed since the previous scan -> a payment happened -> "top-up + payment?" is asked (the
      top-up d + L is preselected when it is a top-up amount); unchanged and a Pfand sum (<= 5 €) -> Pfand, with
      "Anders zuordnen" in the card screen's snackbar and in the history (back to the question; the question also
      offers "Nur eine Pfandrückgabe"); anything else -> asked.
- [x] **The "last payment" signal on a top-up, checked on a real card (2026-11-06).** The user scanned right before and
      right after a top-up (no payment between) and the app recorded a plain top-up without a question. In
      `BalanceDiff.derive` a positive d with a changed last payment above 0 is always asked, and the top-up amount rule
      comes after that check, so the card's last payment did not change to another amount: a top-up keeps it, as the
      rules assume. Not told apart by this: a last payment reset to 0 (the rule ignores 0), which would also read as a
      plain top-up. Seen once, on one card.
- [x] Meal matching across Mensen (2026-11-06, first real use: Pommes at the default Mensa was saved for a Chili of
      the same price elsewhere): all Mensen count, only one candidate overall is saved, more are asked. Corrections
      read the cached pages (`MenuDishes`), since the menu is kept only with an open question.
- [x] Past days (2026-11-06): the menu cache keeps days the site dropped for 14 days (`CachedMenu.past`, statistics only), so
      corrections of recent payments still find that day's dishes; older ones only have the search.
- [x] Dishes priced per 100 g (2026-11-06): no hint on the pages (all 8 dated pages live and every fixture searched for
      "100 g", "pro 100", "Gewicht" and the like), so rules in `WeightPricing`: badge "Pastabuffet" (0,50 | 0,80 | 1,10, in
      several Mensen), and the soup icon in UniCampus unten (0,60 | 0,80 | 1,00; soups and stews at 2,00 elsewhere have no
      icon, or are a portion). Never a fixed match, never saved automatically; a single fixed match still saves even on a
      day with such dishes, whose weight can fit any amount.
- [x] No automatic saving on incomplete data (2026-11-06): unknown menus of that day are loaded first; Mensen that still
      fail are named in the question. Statistics schema 2 (`SnapDish.byWeight`, `EatenDish.grams`, `missingMensen`).
- [x] Scan speed (2026-11-06): the result is published before the matching; the popup draws the balance before the note.
      Measured with fake scans on the phone: about 0.55 s until stored in a warm process (the PBKDF2 card check), the meal step 0.1 s
      later with all menus cached, about 1.5 s more with 7 Mensen to load.
- [x] No network from a card tap (2026-11-09): a scan only marks its payments (`pendingMatch`, schema 3); matching and any menu loading run when the main UI or the statistics screen opens.
- [x] First scan (2026-11-12): a registered card's first scan offers its last debit as a payment (`OpenItem.FirstDebit`, schema 5):
      the day is picked (default the scan's day), then the usual meal question; "Nicht erfassen" drops it.
- [x] Several payments (2026-11-12): a decrease of more than the last debit L asks "one payment or split" (split prefilled with
      the rest and L, each part matched on its own). A top-up plus payments: after picking the top-up, payments above L become the
      same "several payments?" question (before: always one payment).
- [x] Instant question (2026-11-12): matching decides with the cached menus only (`MenuRepository.knowsDay`) and asks at once; the
      missing Mensen load in the background (`StatsRecorder.loading`, "lädt weitere Mensen …"), then the open questions of that day get
      the dishes (a single candidate is then saved, unless that question is on screen). A scan in the open app matches right away; the
      popup still loads nothing and its button opens the statistics. Questions left with missing menus are retried at the next visit (7 days).
- [x] Several dishes (2026-11-12): "Mehrere Gerichte" splits a payment into parts (menu dish, known dish, new dish with price, extra;
      `MealParts`), which must add up. Hand-named dishes saved with a Mensa and a price are candidates of that Mensa on every day
      (`MenuDishes.own`), alone or in pairs, like PIER 16's fixed list. No schema change.
