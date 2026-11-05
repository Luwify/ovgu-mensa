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
    GET_VERSION. Stable card identity = the UID; only a salted SHA-256 of it is stored.
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
- [ ] Registration and first recordings with the real card (needs the user)

## Phase 2: recording
- [x] Versioned stats store with migrations + tests
- [x] Scan records (time, balance, last transaction)
- [x] Diff logic (one payment, several -> one/split, top-up, Pfand, top-up + payment) + tests (`BalanceDiff`)
- [x] Questions UI for ambiguous diffs (tested on the phone with seeded data)
- [x] Overview: per week/month, per weekday, average per visit, balance over time; link constant for the meal
      statistics website (`StatsLinks.MEAL_STATS_URL`, hidden while empty). Charts: own Canvas code, no library.

## Phase 3: detailed statistics
- [x] Matching (single, same dish twice, two dishes; default Mensa first) + tests (`MealMatcher`); one candidate is saved without asking
- [x] Where-did-you-eat question, pick among matches, extras flow, no-match flow (`MealViews`), tested on the phone
- [x] Meals overview (most eaten, by Mensa, vegan/vegetarian share, average per meal, add to favorites) (`MealSummary`)

## Phase 4: reminder
- [ ] Rules (weekday, not scanned today, default Mensa has a menu) + tests; WorkManager window; permission

## Phase 5: backup
- [ ] Versioned export/import like Stundenplan MD; round-trip test

## Finish
- [ ] Independent review subagent; fixes
- [ ] Changelog unreleased.*.md; installRelease over the real app (update only); commit, push
