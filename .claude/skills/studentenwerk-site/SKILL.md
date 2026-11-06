---
name: studentenwerk-site
description: Studentenwerk Magdeburg website findings (no API, page URLs, menu HTML markup, tags/legend, PIER 16 format, fallback host, test fixtures). Load before touching parser/, data/remote, MenuHosts, HostFallback or the test fixtures.
---

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
- **Prices per 100 g:** the pages never say a price is per 100 g (searched all pages and fixtures, 2026-11-06). Known cases:
  badge "Pastabuffet" (0,50 | 0,80 | 1,10) and the `suppe` icon in UniCampus unten (0,60 | 0,80 | 1,00). Rules: `WeightPricing`.
- The `/en/` variant of the pages adds nothing (same German alt texts), so don't fetch it.
- **Fallback host** `www.studiwerkmagdeburg.de` (the Studentenwerk's test server; checked 2026-10-22 while the main site
  was down): the same paths and the same `#content .entry-content` markup on all 9 pages, current days, PIER 16's list
  identical; the parsers read all of them (fixtures `2026-10-22-fallback/`, `FallbackHostFixturesTest`). Its `/en/`
  pages redirect (301) to the German ones. It is a test server: its data can differ, hence the footer note.
- Test fixtures: every page from 2026-09-30, a PIER 16 copy from 2026-10-01 and every page of the fallback host from
  2026-10-22 (`2026-10-22-fallback/`) in `app/src/test/resources/fixtures/`,
  trimmed to the `div.entry-content` block (the raw markup of that block is unchanged; the site's header, footer and
  scripts are left out). A new fixture is trimmed the same way: only the block the parsers read.
