package de.luwify.mensamd.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.stats.Amounts
import de.luwify.mensamd.stats.Extras
import de.luwify.mensamd.stats.MealDraft
import de.luwify.mensamd.stats.MealItem
import de.luwify.mensamd.stats.MealOption
import de.luwify.mensamd.stats.MenuDishes
import de.luwify.mensamd.stats.OpenItem
import de.luwify.mensamd.stats.StatsData
import de.luwify.mensamd.stats.StatsOps
import de.luwify.mensamd.stats.Suggestion
import de.luwify.mensamd.ui.SheetHeading
import de.luwify.mensamd.ui.forLanguage
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * "Was hast du gekauft?" for one payment, also for correcting a saved one ([current] = its meal, [question] then has a
 * negative id). One screen: the amount and a tappable date line; 1-3 suggestions that save with one tap; one search over
 * that day's dishes of every Mensa, the hand-named dishes, extras and every other known dish, with "neu anlegen"; what is
 * picked is listed with "Noch x € offen" until it adds up. "Später" keeps the question, "Nicht erfassen" keeps only the
 * amount. Full-screen overlay; back closes it (nothing is saved).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealQuestionScreen(
    vm: StatsViewModel,
    question: OpenItem.MealQuestion,
    data: StatsData,
    current: de.luwify.mensamd.stats.MealRecord? = null,
    onClose: () -> Unit,
) {
    val editing = question.id < 0
    val context by vm.mealContext.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val language = locale.language
    val group = context?.priceGroup ?: 0
    val defaultId = context?.defaultMensaId
    val event = data.events.firstOrNull { it.id == question.eventId }
    val amount = event?.amount ?: question.amount
    val initial = remember(question.eventId) { MealDraft.fromRecord(current, amount) }
    val picked = remember(question.eventId) { mutableStateListOf<MealItem>().apply { addAll(initial) } }
    var query by rememberSaveable { mutableStateOf("") }
    // The day the user chose with the date line (epoch day), null: unchanged.
    var chosenDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var spread by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var naming by remember { mutableStateOf<String?>(null) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    BackHandler(onBack = onClose)

    val pages by produceState<List<Pair<String, MenuPage>>?>(null, question.missingMensen) { value = vm.cachedPages() }
    val loadingDays by vm.loadingDays.collectAsStateWithLifecycle()
    if (!editing) {
        DisposableEffect(question.id) {
            vm.setViewing(question.id)
            onDispose { vm.setViewing(null) }
        }
    }
    val scanDate = event?.date ?: question.date
    val previous = remember(data.scans, question.scanId) { StatsOps.previousScanDate(data, question.scanId) }
    val chosen = chosenDay?.let(LocalDate::ofEpochDay)
    // The days the payment can have been made: the chosen one, its known day, or the weekdays since the previous scan.
    val days = remember(chosen, event, previous) {
        when {
            chosen != null -> listOf(chosen)
            event?.dateKnown != false || previous == null -> listOf(scanDate)
            else -> MealDraft.weekdays(previous, scanDate).ifEmpty { listOf(scanDate) }
        }
    }
    val range = days.size > 1
    // "Auf mehrere Tage verteilen": only when the scan came more than a day after the previous one.
    val spreadDays = remember(previous, scanDate) {
        if (previous != null && scanDate.isAfter(previous.plusDays(1))) MealDraft.weekdays(previous, scanDate).ifEmpty { null } else null
    }
    val dayMenus = remember(days, pages, question.dishes, data.customDishes) {
        days.map { d ->
            val kept = if (d == question.date) question.dishes else emptyList()
            d to MenuDishes.merge(kept, MenuDishes.onDay(pages.orEmpty(), d)) + MenuDishes.own(data.customDishes)
        }
    }
    val missing = remember(question, pages) {
        question.missingMensen.filter { id -> pages.orEmpty().none { (mensa, page) -> mensa == id && MenuDishes.onDay(id, page, question.date).isNotEmpty() } }
    }
    val rest = MealDraft.rest(amount, picked)
    val eatenKeys = remember(data.events) { data.events.mapNotNull { it.meal }.flatMap { it.dishes }.map { it.key }.toSet() }
    val suggestions = remember(rest, dayMenus, defaultId, group, eatenKeys, favorites) {
        MealDraft.suggestions(rest, dayMenus, defaultId, group, eatenKeys, favorites)
    }
    val extraNames = (Extras.builtIn + data.customExtras + Extras.OTHER).map { it to extraName(it) }
    val options = remember(rest, dayMenus, range, pages, data.events, data.customDishes, extraNames, defaultId, group) {
        MealDraft.options(rest, dayMenus, range, MenuDishes.all(pages.orEmpty()), data.events, data.customDishes, extraNames, defaultId, group)
    }
    // Without a search, a dish that is already a suggestion on its own isn't listed again.
    val results = remember(options, query, suggestions) {
        val shown = if (query.isBlank()) suggestions.mapNotNull { s -> s.candidate.picks.singleOrNull()?.takeIf { it.count == 1 }?.let { MealDraft.itemsOf(s, range).single() } }
            .map { Triple(it.key, it.mensaId, it.day) }.toSet() else emptySet()
        MealDraft.search(options, query).filterNot { Triple(it.key, it.mensaId, it.day) in shown }
    }
    val complete = MealDraft.complete(amount, picked)
    val changed = picked.toList() != initial || chosen != null
    val unknownMensa = stringResource(R.string.meal_unknown_mensa)
    // The list keeps its place by key: picked items appear above the search field, so show them.
    val listState = rememberLazyListState()
    val pickedCount = picked.sumOf { it.count }
    LaunchedEffect(pickedCount, rest) { if (pickedCount > 0) listState.animateScrollToItem(0) }

    fun save(list: List<MealItem>) {
        vm.saveItems(question.eventId, list, chosen)
        onClose()
    }
    // A pick: saved at once when it alone answers the whole payment (not while correcting or spreading over days).
    fun applyPick(after: List<MealItem>, oneTap: Boolean) {
        query = ""
        if (oneTap && !editing && !spread && MealDraft.complete(amount, after)) return save(after)
        picked.clear()
        picked.addAll(after)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(stringResource(R.string.meal_title, formatCardAmount(amount, locale)), onBack = onClose)
            DateLine(days, scanDate, previous, range, chosen != null, locale) { pickingDate = true }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                if (question.date in loadingDays) item(key = "loading") {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Hint(stringResource(R.string.meal_loading_mensen))
                    }
                } else if (missing.isNotEmpty()) item(key = "missing") {
                    Hint(stringResource(R.string.meal_missing_mensen, missing.joinToString(", ") { mensaName(it, language, unknownMensa) }), Modifier.padding(vertical = 8.dp))
                }
                if (picked.isNotEmpty()) {
                    pickedRows(picked.toList(), spread, spreadDays ?: emptyList(), chosen ?: scanDate, language, locale, unknownMensa,
                        onEdit = { editIndex = it },
                        onRemove = { i -> MealDraft.removeOne(picked.toList(), i).let { picked.clear(); picked.addAll(it) } },
                        onDay = { i, d -> picked[i] = picked[i].copy(day = d) },
                    )
                    item(key = "rest") {
                        RestBar(rest, complete, editing && !changed, locale,
                            onOther = { save(MealDraft.restAsOther(amount, picked.toList())) },
                            onSave = { save(picked.toList()) },
                        )
                    }
                }
                if (spreadDays != null) item(key = "spread") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Switch) { spread = !spread },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.meal_spread), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = spread, onCheckedChange = null)
                    }
                }
                if (rest > 0) {
                    if (query.isBlank() && suggestions.isNotEmpty()) {
                        item(key = "sugg-title") { SheetHeading(stringResource(R.string.meal_suggestions)) }
                        items(suggestions.size, key = { "sugg-$it" }) { i ->
                            SuggestionCard(suggestions[i], range, language, locale, unknownMensa) {
                                var after = picked.toList()
                                MealDraft.itemsOf(suggestions[i], range).forEach { after = MealDraft.add(after, it) }
                                applyPick(after, oneTap = true)
                            }
                        }
                    } else if (query.isBlank() && picked.isEmpty() && pages != null && dayMenus.all { it.second.isEmpty() }) {
                        item(key = "no-menu") { Hint(stringResource(R.string.meal_no_menu), Modifier.padding(vertical = 8.dp)) }
                    }
                    item(key = "search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(60) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.meal_search)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    if (query.isNotBlank()) item(key = "create") {
                        OptionRow(stringResource(R.string.meal_create, query.trim()), null, primary = true) { naming = query.trim() }
                    }
                    if (query.isNotBlank() && results.isEmpty()) item(key = "none") { Hint(stringResource(R.string.meal_search_none), Modifier.padding(vertical = 8.dp)) }
                    items(results, key = { "opt-${it.key}|${it.mensaId}|${it.day}|${it.extra}" }) { o ->
                        OptionRow(o.name.forLanguage(language), optionDetail(o, rest, range, language, locale, unknownMensa)) {
                            applyPick(MealDraft.pick(amount, picked.toList(), o), oneTap = picked.isEmpty())
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (editing) R.string.cancel_action else R.string.meal_later))
                }
                OutlinedButton(
                    onClick = {
                        if (editing) vm.setMeal(question.eventId, null) else vm.dismiss(question.id)
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.meal_dont_record)) }
            }
        }
    }

    naming?.let { name ->
        DishDetailsDialog(
            title = stringResource(R.string.meal_new_title),
            name = name,
            nameEditable = true,
            mensaId = defaultId,
            tags = emptyList(),
            note = null,
            price = rest.coerceAtLeast(0),
            onConfirm = { n, mensaId, tags, price ->
                naming = null
                val item = MealDraft.newDish(n, mensaId, tags, price ?: rest, days.singleOrNull()?.takeIf { range })
                applyPick(MealDraft.add(picked.toList(), item), oneTap = picked.isEmpty())
            },
            onDismiss = { naming = null },
        )
    }
    editIndex?.let { i -> picked.getOrNull(i) }?.let { item ->
        ItemDialog(item, language, onConfirm = { changedItem ->
            picked[editIndex!!] = changedItem
            editIndex = null
        }, onDismiss = { editIndex = null })
    }
    if (pickingDate) {
        val latest = scanDate
        val earliest = previous ?: latest.minusYears(1)
        val start = chosen ?: days.last()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            yearRange = earliest.year..latest.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !d.isAfter(latest) && !d.isBefore(earliest)
                }

                override fun isSelectableYear(year: Int): Boolean = year in earliest.year..latest.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { chosenDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickingDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel_action)) } },
        ) { DatePicker(state) }
    }
}

/** "Di 18.11." or "Zwischen Mo 17.11. und Mi 19.11.", tappable: picks the payment's day. */
@Composable
private fun DateLine(days: List<LocalDate>, scanDate: LocalDate, previous: LocalDate?, range: Boolean, chosen: Boolean, locale: Locale, onClick: () -> Unit) {
    val text = if (range) stringResource(R.string.meal_date_range, shortDate(previous ?: days.first(), locale), shortDate(scanDate, locale))
    else shortDate(days.single(), locale)
    Row(
        Modifier.padding(start = 4.dp, bottom = 4.dp).clickable(role = Role.Button, onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            " · " + stringResource(if (range && !chosen) R.string.meal_date_pick else R.string.meal_date_change),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The picked items: name, Mensa (and weight) below, the price; tap edits, × removes one. With [spread] each has a day. */
@Suppress("FunctionName")
private fun androidx.compose.foundation.lazy.LazyListScope.pickedRows(
    list: List<MealItem>,
    spread: Boolean,
    spreadDays: List<LocalDate>,
    defaultDay: LocalDate,
    language: String,
    locale: Locale,
    unknownMensa: String,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDay: (Int, LocalDate) -> Unit,
) {
    items(list.size, key = { "item-$it" }) { i ->
        val item = list[i]
        val name = (if (item.count > 1) "${item.count}× " else "") + (item.name?.forLanguage(language) ?: extraName(item.key))
        val detail = listOfNotNull(
            if (item.isExtra) null else mensaName(item.mensaId, language, unknownMensa),
            item.grams?.let { stringResource(R.string.meal_weight_estimate, it) },
            if (item.isNew) stringResource(R.string.meal_item_new) else null,
        ).joinToString(" · ")
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onEdit(i) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (spread && spreadDays.isNotEmpty()) DayChoice(item.day ?: defaultDay, spreadDays, locale) { onDay(i, it) }
            }
            Text(formatCardAmount(item.total, locale), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            IconButton(onClick = { onRemove(i) }) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.meal_item_remove), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DayChoice(day: LocalDate, days: List<LocalDate>, locale: Locale, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(shortDate(day, locale) + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            days.forEach { d ->
                DropdownMenuItem(text = { Text(shortDate(d, locale)) }, onClick = {
                    open = false
                    onPick(d)
                })
            }
        }
    }
}

/** "Noch 0,90 € offen · Rest als Sonstiges", or "Passt · Speichern" (nothing to save while correcting unchanged). */
@Composable
private fun RestBar(rest: Int, complete: Boolean, unchanged: Boolean, locale: Locale, onOther: () -> Unit, onSave: () -> Unit) {
    Surface(
        color = if (rest < 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    rest > 0 -> stringResource(R.string.meal_rest_open, formatCardAmount(rest, locale))
                    rest < 0 -> stringResource(R.string.meal_rest_too_much, formatCardAmount(-rest, locale))
                    else -> stringResource(R.string.meal_rest_ok)
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (rest < 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            when {
                rest > 0 -> TextButton(onClick = onOther) { Text(stringResource(R.string.meal_rest_other)) }
                complete && !unchanged -> Button(onClick = onSave) { Text(stringResource(R.string.q_save)) }
            }
        }
    }
}

/** A complete answer: the Mensa (and day) above, each dish with its price; one tap saves it. */
@Composable
private fun SuggestionCard(s: Suggestion, range: Boolean, language: String, locale: Locale, unknownMensa: String, onPick: () -> Unit) {
    Surface(
        onClick = onPick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                listOfNotNull(mensaName(s.candidate.mensaId, language, unknownMensa), s.day?.takeIf { range }?.let { shortDate(it, locale) }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            s.candidate.picks.forEach { p ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        (if (p.count > 1) "${p.count}× " else "") + p.dish.name.forLanguage(language),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(formatCardAmount(p.price * p.count, locale), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** "UniCampus unten · 2,50 €", "… · 0,80 € /100 g · ca. 470 g", "nimmt den Rest" for extras. */
@Composable
private fun optionDetail(o: MealOption, rest: Int, range: Boolean, language: String, locale: Locale, unknownMensa: String): String = listOfNotNull(
    if (o.extra) null else mensaName(o.mensaId, language, unknownMensa),
    o.day?.takeIf { range }?.let { shortDate(it, locale) },
    when {
        o.per100 != null -> formatCardAmount(o.per100, locale) + " " + stringResource(R.string.price_per_100g) + " · " +
            stringResource(R.string.meal_weight_estimate, de.luwify.mensamd.stats.MealMatcher.estimateGrams(rest, o.per100))
        o.price != null -> formatCardAmount(o.price, locale)
        else -> stringResource(R.string.meal_takes_rest)
    },
).joinToString(" · ")

@Composable
private fun OptionRow(label: String, detail: String?, primary: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (!detail.isNullOrEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One picked item: its price (a weighed one shows the weight it buys). A dish also its Mensa and tags (and the name of a
 * dish named just now), the same fields as "neu anlegen".
 */
@Composable
private fun ItemDialog(item: MealItem, language: String, onConfirm: (MealItem) -> Unit, onDismiss: () -> Unit) {
    if (!item.isExtra) {
        DishDetailsDialog(
            title = stringResource(R.string.dish_edit_title),
            name = item.name!!.forLanguage(language),
            nameEditable = item.isNew,
            mensaId = item.mensaId,
            tags = item.tags,
            note = if (item.own && !item.isNew) stringResource(R.string.dish_own_note) else null,
            price = item.price,
            onConfirm = { name, mensaId, tags, price ->
                val renamed = if (item.isNew && name != item.name.de) MealDraft.newDish(name, mensaId, tags, item.price) else item
                onConfirm(renamed.copy(mensaId = mensaId, tags = tags, price = price ?: item.price, count = item.count, day = item.day))
            },
            onDismiss = onDismiss,
        )
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val comma = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',' }
    var text by rememberSaveable { mutableStateOf(Amounts.toInput(item.price, comma)) }
    val price = Amounts.parseEuro(text)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(extraName(item.key)) },
        text = { PriceField(text, price, item.per100) { text = it } },
        confirmButton = { TextButton(onClick = { onConfirm(item.copy(price = price!!)) }, enabled = price != null) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) } },
    )
}

/** A price in euros; with [per100] the weight it buys below. */
@Composable
internal fun PriceField(text: String, price: Int?, per100: Int?, onText: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { onText(it.take(10)) },
            singleLine = true,
            suffix = { Text("€") },
            isError = price == null,
            label = { Text(stringResource(R.string.meal_price)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (per100 != null && price != null) Hint(stringResource(R.string.meal_weight_estimate, de.luwify.mensamd.stats.MealMatcher.estimateGrams(price, per100)))
    }
}
