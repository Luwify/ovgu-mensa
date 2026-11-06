New:
- Card statistics (turn on in the card view via the gear): every scan of your own, registered card stores the balance and the last debit, only on the device. Payments, top-ups and Pfand returns are worked out from that; for unclear changes the app asks (split several payments, top-up plus payment).
- "My card": only a hash of the card ID is stored. Other cards still show their balance but are not stored.
- Statistics overview: spending and top-ups per week or month, spending by weekday, average per visit, balance over time and a history of all entries with delete and undo.
- Detailed statistics: payments are matched to that day's menu (default Mensa first, your price group), including two dishes or the same dish twice. Plus extras like the salad bar, dessert by weight or drinks, and your own extras and dishes. The overview shows the dishes eaten most, the Mensen, the vegan and vegetarian share and the average per meal; dishes can be added to the favorites from there.
- Reminder on weekdays at the chosen time when the card wasn't scanned that day (not when the default Mensa is closed).
- Top-up amounts (10, 20, 30, 50 €) and Pfand amounts (0.08, 0.15, 0.25 €) can be set in the card settings. A balance increase that is a sum of Pfand amounts up to 5 €, with no payment since the last scan, counts as a Pfand return that "Assign differently" can turn into a top-up plus payment; an equal top-up amount wins.
- Backup in the settings: export settings, favorites and card statistics as one file and import them again, with a confirmation and "Undo".
- The end of the statistics overview links to the new website with statistics on all menus: how often each dish appears, price history, vegan and vegetarian share per Mensa.
- The pasta buffet and the soup at UniCampus lower are paid by weight: the menu now shows "/100 g" next to the price.

Fixed:
- Detailed statistics: matching a payment to the menu now looks at all Mensen. A dish is only saved without asking when exactly one fits overall that day; otherwise the app asks and shows the Mensa next to each dish, the default Mensa first.
- Correcting a payment now lists that day's dishes from all Mensen, those with a matching price first, then all others.
- "Choose an existing dish" now searches the menus of all Mensen and days plus eaten and custom dishes, finds parts of words and ignores case and umlauts. That day's dishes come first.
- Detailed statistics: dishes paid by weight no longer count as a fixed price and are never saved automatically. They are offered with the estimated weight ("approx. 470 g"), alone or in the "Dish + extras" step together with fixed dishes and extras. The average per meal uses the amount actually paid.
- A dish is only saved automatically when the menus of all Mensen are known. The app loads missing ones on a scan; if that fails, it asks and names those Mensen.
- After a scan the balance and the recognized payment appear right away; the question about the dish may follow a moment later.
- Menus of past days stay stored for 14 days, so corrections still find those days' dishes.
- A working fallback server is remembered across app restarts for the rest of the hour, which saves the wait for the unreachable main site.
