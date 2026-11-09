package de.luwify.mensamd.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.stats.CardEvent
import de.luwify.mensamd.stats.DishSearch
import de.luwify.mensamd.stats.EatenDish
import de.luwify.mensamd.stats.Extras
import de.luwify.mensamd.stats.KnownDish
import de.luwify.mensamd.stats.MealMatcher
import de.luwify.mensamd.stats.MealRecord
import de.luwify.mensamd.stats.MealSummary
import de.luwify.mensamd.stats.MenuDishes
import de.luwify.mensamd.stats.OpenItem
import de.luwify.mensamd.stats.SnapDish
import de.luwify.mensamd.stats.StatsData
import de.luwify.mensamd.ui.SheetHeading
import de.luwify.mensamd.ui.forLanguage
import de.luwify.mensamd.ui.tagLabelRes
import de.luwify.mensamd.ui.theme.PlusIcon
import java.text.NumberFormat
import java.util.Locale

private enum class MealStep { MAIN, WHERE, EXTRAS, SEARCH }

internal fun mensaName(id: String?, language: String, unknown: String): String =
    id?.let { MensaCatalog.byId(it)?.name?.forLanguage(language) ?: it } ?: unknown

/**
 * Which dish(es) did a payment buy? Candidates from that day's menu of every Mensa (default Mensa first; too many:
 * "where did you eat?" first), or dishes + extras, an existing dish, a new dish, "Sonstiges", or any other dish of that
 * day. The menu is the one kept with the question plus the cached pages; a question with a negative id is a payment
 * opened for editing, which only has the cached pages. Full-screen overlay; back goes one step back, then closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealQuestionScreen(
    vm: StatsViewModel,
    question: OpenItem.MealQuestion,
    customDishes: List<KnownDish>,
    customExtras: List<String>,
    events: List<CardEvent>,
    /** What an edited payment has saved now (null for a question): its dishes can be changed in place. */
    current: MealRecord? = null,
    onClose: () -> Unit,
) {
    val editing = question.id < 0
    val context by vm.mealContext.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val language = locale.language
    val group = context?.priceGroup ?: 0
    var step by rememberSaveable { mutableStateOf(MealStep.MAIN) }
    var where by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var naming by rememberSaveable { mutableStateOf(false) }
    // Index of the saved dish being edited.
    var editingDish by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler { if (step != MealStep.MAIN) step = MealStep.MAIN else onClose() }
    // Null until the cached pages are read (one file per Mensa at most, usually already in memory).
    val pages by produceState<List<Pair<String, MenuPage>>?>(null) { value = vm.cachedPages() }
    val dishes = remember(question, pages) { MenuDishes.merge(question.dishes, MenuDishes.onDay(pages.orEmpty(), question.date)) }
    val plan = remember(question, dishes, context, where) {
        MealMatcher.plan(question.amount, dishes, context?.defaultMensaId, group, where?.toSet())
    }
    val weights = remember(dishes, context, where) { MealMatcher.weightDishes(dishes, context?.defaultMensaId, group, where?.toSet()) }
    val others = remember(plan, dishes, context, where) {
        MealMatcher.others((plan as? MealMatcher.Plan.Choose)?.candidates.orEmpty(), dishes, context?.defaultMensaId, where?.toSet())
    }
    // Named until the page turns up (the user opened that Mensa meanwhile).
    val missing = remember(question, pages) {
        question.missingMensen.filter { id -> pages.orEmpty().none { (mensa, page) -> mensa == id && MenuDishes.onDay(id, page, question.date).isNotEmpty() } }
    }
    val mensaIds = remember(dishes, context) { dishes.map { it.mensaId }.distinct().sortedBy { it != context?.defaultMensaId } }
    val amount = formatCardAmount(question.amount, locale)
    val unknownMensa = stringResource(R.string.meal_unknown_mensa)
    fun answer(record: MealRecord?) {
        vm.setMeal(question.eventId, record)
        onClose()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.meal_title, amount), onBack = { if (step != MealStep.MAIN) step = MealStep.MAIN else onClose() })
            Text(
                shortDate(question.date, locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            val askWhere = step == MealStep.WHERE || (step == MealStep.MAIN && plan is MealMatcher.Plan.AskWhere && where == null)
            when {
                askWhere -> WhereStep(
                    mensaIds, (plan as? MealMatcher.Plan.AskWhere)?.preselected ?: where?.toSet() ?: emptySet(), language, unknownMensa,
                ) { chosen ->
                    where = chosen.toList()
                    step = MealStep.MAIN
                }
                step == MealStep.EXTRAS -> ExtrasStep(
                    vm, question, dishes, events, customDishes, customExtras, group, language, locale,
                    focus = where?.toSet() ?: setOfNotNull(context?.defaultMensaId),
                ) { answer(it) }
                step == MealStep.SEARCH -> SearchStep(pages.orEmpty(), events, customDishes, dishes, group, language, locale, unknownMensa) { dish ->
                    answer(MealRecord(dishes = listOf(EatenDish(dish.key, dish.name, dish.mensaId, question.amount, 1, dish.tags, dish.grams(question.amount), dish.own))))
                }
                // Only with the cached pages read: an item added later above the others would stay scrolled out of view.
                pages != null -> LazyColumn(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
                ) {
                    if (editing && current != null && current.dishes.isNotEmpty()) {
                        item(key = "current-title") { SheetHeading(stringResource(R.string.meal_current)) }
                        items(current.dishes.size, key = { "current-$it" }) { i ->
                            CurrentDishRow(current.dishes[i], language, unknownMensa) { editingDish = i }
                        }
                    }
                    if (missing.isNotEmpty()) item(key = "missing") {
                        val names = missing.map { mensaName(it, language, unknownMensa) }.joinToString(", ")
                        NoteCard { Text(stringResource(R.string.meal_missing_mensen, names), style = MaterialTheme.typography.bodyMedium) }
                    }
                    when (plan) {
                        is MealMatcher.Plan.Choose -> {
                            item(key = "cands-title") { SheetHeading(stringResource(R.string.meal_candidates)) }
                            items(plan.candidates.size, key = { "cand-$it" }) { i ->
                                CandidateCard(plan.candidates[i], language, locale, unknownMensa) { answer(plan.candidates[i].toRecord()) }
                            }
                        }
                        MealMatcher.Plan.NoMatch -> item(key = "none") {
                            Text(
                                if (dishes.isEmpty()) stringResource(R.string.meal_no_menu) else stringResource(R.string.meal_no_match, amount),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        is MealMatcher.Plan.AskWhere -> Unit
                    }
                    if (weights.isNotEmpty()) {
                        item(key = "weight-title") { SheetHeading(stringResource(R.string.meal_by_weight)) }
                        items(weights.size, key = { "weight-$it" }) { i ->
                            WeightCard(weights[i], question.amount, language, locale, unknownMensa) { answer(weights[i].toRecord(question.amount)) }
                        }
                    }
                    item(key = "options-title") { SheetHeading(stringResource(R.string.meal_other_options)) }
                    if (mensaIds.size > 1) item(key = "where") { OptionRow(stringResource(R.string.meal_where_option)) { step = MealStep.WHERE } }
                    item(key = "extras") { OptionRow(stringResource(R.string.meal_with_extras)) { step = MealStep.EXTRAS } }
                    item(key = "search") { OptionRow(stringResource(R.string.meal_existing)) { step = MealStep.SEARCH } }
                    item(key = "new") { OptionRow(stringResource(R.string.meal_new)) { naming = true } }
                    item(key = "other") { OptionRow(stringResource(R.string.meal_save_other)) { answer(MealRecord(other = true)) } }
                    if (!editing && question.dateKnown) {
                        item(key = "not-today") {
                            OptionRow(stringResource(R.string.meal_not_that_day)) {
                                vm.markDateUnknown(question.eventId)
                                onClose()
                            }
                        }
                    }
                    if (editing) {
                        item(key = "clear") { OptionRow(stringResource(R.string.meal_clear)) { answer(null) } }
                    } else {
                        item(key = "skip") {
                            OptionRow(stringResource(R.string.meal_skip)) {
                                vm.dismiss(question.id)
                                onClose()
                            }
                        }
                    }
                    if (others.isNotEmpty()) {
                        item(key = "others-title") { SheetHeading(stringResource(R.string.meal_other_dishes)) }
                        items(others.size, key = { "other-$it" }) { i ->
                            OtherDishRow(others[i], group, language, locale, unknownMensa) { answer(MealMatcher.asPayment(others[i], question.amount)) }
                        }
                    }
                }
            }
        }
    }
    if (naming) {
        // The Mensa the user named in "where did you eat?" (the default one if it is among them), else the default.
        val preselected = where?.let { w -> w.firstOrNull { it == context?.defaultMensaId } ?: w.firstOrNull() } ?: context?.defaultMensaId
        DishDetailsDialog(
            title = stringResource(R.string.meal_new_title),
            name = "",
            nameEditable = true,
            mensaId = preselected,
            tags = emptyList(),
            note = null,
            onConfirm = { name, mensaId, tags ->
                naming = false
                vm.saveNewDish(question.eventId, name, question.amount, mensaId, tags)
                onClose()
            },
            onDismiss = { naming = false },
        )
    }
    val editIndex = editingDish
    val editDish = editIndex?.let { current?.dishes?.getOrNull(it) }
    if (editIndex != null && editDish != null) {
        EditDishDialog(editDish, language, onConfirm = { name, mensaId, tags ->
            editingDish = null
            vm.editDish(question.eventId, editIndex, name, mensaId, tags)
            onClose()
        }, onDismiss = { editingDish = null })
    }
}

/** "Gericht bearbeiten" for one dish: a hand-named dish can be renamed and its change reaches every payment with it. */
@Composable
internal fun EditDishDialog(
    dish: EatenDish,
    language: String,
    onConfirm: (name: String, mensaId: String?, tags: List<Tag>) -> Unit,
    onDismiss: () -> Unit,
) {
    DishDetailsDialog(
        title = stringResource(R.string.dish_edit_title),
        name = dish.name.forLanguage(language),
        nameEditable = dish.own,
        mensaId = dish.mensaId,
        tags = dish.tags,
        note = if (dish.own) stringResource(R.string.dish_own_note) else null,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** A dish a payment has saved now: name, then Mensa and tags below; tapping it edits them. */
@Composable
private fun CurrentDishRow(dish: EatenDish, language: String, unknownMensa: String, onClick: () -> Unit) {
    val tags = dish.tags.map { t -> tagLabelRes(t.key)?.let { stringResource(it) } ?: t.label }.joinToString(", ")
    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp)) {
        Text((if (dish.count > 1) "${dish.count}× " else "") + dish.name.forLanguage(language), style = MaterialTheme.typography.bodyLarge)
        Text(
            listOf(mensaName(dish.mensaId, language, unknownMensa), tags.ifEmpty { stringResource(R.string.dish_no_tags) }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CandidateCard(c: MealMatcher.Candidate, language: String, locale: Locale, unknownMensa: String, onPick: () -> Unit) {
    Surface(
        onClick = onPick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mensaName(c.mensaId, language, unknownMensa), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            c.picks.forEach { p ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        (if (p.count > 1) "${p.count}× " else "") + p.dish.name.forLanguage(language),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(formatCardAmount(p.price, locale), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** A dish paid by weight as the whole payment: "Farfalle … · ca. 470 g", the Mensa and its price per 100 g above. */
@Composable
private fun WeightCard(w: MealMatcher.WeightDish, amount: Int, language: String, locale: Locale, unknownMensa: String, onPick: () -> Unit) {
    Surface(
        onClick = onPick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                mensaName(w.dish.mensaId, language, unknownMensa) + " · " + perHundred(w.per100, locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Top) {
                Text(w.dish.name.forLanguage(language), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.meal_weight_estimate, w.grams(amount)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** "0,50 € /100 g". */
@Composable
private fun perHundred(price: Int, locale: Locale) = formatCardAmount(price, locale) + " " + stringResource(R.string.price_per_100g)

/** A dish of that day that doesn't fit the payment's amount: the Mensa and its menu price below the name. */
@Composable
private fun OtherDishRow(d: SnapDish, group: Int, language: String, locale: Locale, unknownMensa: String, onPick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onPick).padding(vertical = 6.dp)) {
        Text(d.name.forLanguage(language), style = MaterialTheme.typography.bodyLarge)
        Text(
            (listOf(mensaName(d.mensaId, language, unknownMensa)) + MealMatcher.prices(d, group).map { formatCardAmount(it, locale) }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OptionRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Wo hast du gegessen?": one or more Mensen, then the matching runs again on those only. */
@Composable
private fun WhereStep(mensaIds: List<String>, preselected: Set<String>, language: String, unknownMensa: String, onDone: (Set<String>) -> Unit) {
    val chosen = remember { mutableStateMapOf<String, Boolean>().apply { preselected.forEach { put(it, true) } } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding()) {
        SheetHeading(stringResource(R.string.meal_where_title))
        Hint(stringResource(R.string.meal_where_hint))
        LazyColumn(Modifier.weight(1f)) {
            items(mensaIds, key = { it }) { id ->
                val on = chosen[id] == true
                Row(
                    Modifier.fillMaxWidth().toggleable(value = on, role = Role.Checkbox, onValueChange = { chosen[id] = it }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Text(mensaName(id, language, unknownMensa), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        val selection = chosen.filterValues { it }.keys
        Button(onClick = { onDone(selection) }, enabled = selection.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(stringResource(R.string.meal_where_next))
        }
    }
}

/**
 * Real dishes with their price, dishes paid by weight, then any number of extras; the rest of the payment goes to the
 * extras as a total. A dish paid by weight takes all that is left unless a weight is entered. The dishes come from that
 * day's menu, or (no menu kept) from the known dishes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtrasStep(
    vm: StatsViewModel,
    question: OpenItem.MealQuestion,
    dishes: List<SnapDish>,
    events: List<CardEvent>,
    customDishes: List<KnownDish>,
    customExtras: List<String>,
    group: Int,
    language: String,
    locale: Locale,
    focus: Set<String>,
    onSave: (MealRecord) -> Unit,
) {
    // Only the Mensen in [focus] (where the user said they ate, else the default) until "all Mensen" is chosen.
    var allMensen by rememberSaveable { mutableStateOf(dishes.none { it.mensaId in focus }) }
    // Every pickable dish once, as a SnapDish with one price.
    val pickable: List<Pair<SnapDish, Int>> = remember(question, dishes, events, customDishes, group, allMensen) {
        if (dishes.isNotEmpty()) {
            dishes.filter { !it.byWeight && (allMensen || it.mensaId in focus) }
                .flatMap { d -> MealMatcher.prices(d, group).map { d to it } }
                .distinctBy { (d, p) -> Triple(d.mensaId, Favorites.normalize(d.name.de), p) }
                .filter { it.second <= question.amount }
        } else {
            MealSummary.knownDishes(events, customDishes)
                .filter { it.price <= question.amount }
                .map { SnapDish(it.mensaId.orEmpty(), it.name, emptyList(), tags = it.tags, own = it.own) to it.price }
        }
    }
    // Counts by dish identity, so they survive switching the Mensa filter.
    val counts = remember { mutableStateMapOf<Triple<String, String, Int>, Int>() }
    fun keyOf(d: SnapDish, p: Int) = Triple(d.mensaId, Favorites.normalize(d.name.de), p)
    val extras = remember { mutableStateMapOf<String, Boolean>() }
    var adding by rememberSaveable { mutableStateOf(false) }
    val picks = pickable.mapNotNull { (d, p) -> counts[keyOf(d, p)]?.takeIf { it > 0 }?.let { MealMatcher.Pick(d, it, p) } }
    val chosenExtras = (Extras.builtIn + customExtras).filter { extras[it] == true }
    val weights = remember(dishes, group, allMensen) { MealMatcher.weightDishes(dishes.filter { allMensen || it.mensaId in focus }, null, group) }
    // Ticked dishes paid by weight (by WeightDish.id) with the text of their weight field.
    val weightTexts = remember { mutableStateMapOf<String, String>() }
    val weightPicks = weights.mapNotNull { w ->
        weightTexts[w.id]?.let { text -> MealMatcher.WeightPick(w, text.toIntOrNull()?.takeIf { it > 0 }) }
    }
    val record = MealMatcher.withExtras(question.amount, picks, chosenExtras, weightPicks)?.let { r ->
        // Dishes from the known list have no Mensa.
        r.copy(dishes = r.dishes.map { if (it.mensaId.isNullOrEmpty()) it.copy(mensaId = null) else it })
    }
    val dishSum = picks.sumOf { it.count * it.price } + weightPicks.sumOf { p -> p.grams?.let(p.weight::cost) ?: 0 }
    val rest = question.amount - dishSum
    val openWeights = weightPicks.count { it.grams == null }
    val unknownMensa = stringResource(R.string.meal_unknown_mensa)

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item(key = "extras-title") { SheetHeading(stringResource(R.string.meal_pick_extras)) }
            item(key = "extras") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Extras.builtIn + customExtras).forEach { key ->
                        FilterChip(selected = extras[key] == true, onClick = { extras[key] = extras[key] != true }, label = { Text(extraName(key)) })
                    }
                    FilterChip(
                        selected = false, onClick = { adding = true },
                        label = { Text(stringResource(R.string.meal_extra_add)) },
                        leadingIcon = { Icon(PlusIcon, null, Modifier.size(18.dp)) },
                    )
                }
            }
            item(key = "dishes-title") { SheetHeading(stringResource(R.string.meal_pick_dishes)) }
            if (dishes.any { it.mensaId !in focus } && dishes.any { it.mensaId in focus }) {
                item(key = "all-mensen") {
                    FilterChip(selected = allMensen, onClick = { allMensen = !allMensen }, label = { Text(stringResource(R.string.meal_all_mensen)) })
                }
            }
            if (pickable.isEmpty()) item(key = "no-dishes") { Hint(stringResource(R.string.meal_pick_none)) }
            items(pickable.size, key = { "pick-$it" }) { i ->
                val (d, p) = pickable[i]
                val n = counts[keyOf(d, p)] ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name.forLanguage(language), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            (if (d.mensaId.isNotEmpty()) mensaName(d.mensaId, language, unknownMensa) + " · " else "") + formatCardAmount(p, locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { counts[keyOf(d, p)] = (n - 1).coerceAtLeast(0) }, enabled = n > 0) { Text("−") }
                    Text("$n", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { counts[keyOf(d, p)] = n + 1 }) { Text("+") }
                }
            }
            if (weights.isNotEmpty()) {
                item(key = "weight-title") { SheetHeading(stringResource(R.string.meal_by_weight)) }
                items(weights.size, key = { "weight-${weights[it].id}" }) { i ->
                    val w = weights[i]
                    val text = weightTexts[w.id]
                    WeightRow(
                        w, rest, text, language, locale, unknownMensa,
                        onToggle = { on -> if (on) weightTexts[w.id] = "" else weightTexts.remove(w.id) },
                        onText = { weightTexts[w.id] = it },
                    )
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    rest < 0 -> stringResource(R.string.meal_dishes_too_much, formatCardAmount(dishSum, locale))
                    openWeights > 1 -> stringResource(R.string.meal_weight_one_open)
                    openWeights == 1 && rest == 0 -> stringResource(R.string.meal_weight_nothing_left)
                    openWeights == 1 -> stringResource(R.string.meal_split_summary, formatCardAmount(question.amount, locale), formatCardAmount(0, locale))
                    rest > 0 && chosenExtras.isEmpty() -> stringResource(R.string.meal_rest_needs_extra, formatCardAmount(rest, locale))
                    else -> stringResource(R.string.meal_split_summary, formatCardAmount(dishSum, locale), formatCardAmount(rest.coerceAtLeast(0), locale))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (record == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Button(onClick = { record?.let(onSave) }, enabled = record != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.q_save)) }
        }
    }
    if (adding) {
        NameDialog(
            title = stringResource(R.string.meal_extra_add_title),
            label = stringResource(R.string.meal_extra_add_label),
            onConfirm = { name ->
                adding = false
                vm.addCustomExtra(name)
                extras[name.trim()] = true
            },
            onDismiss = { adding = false },
        )
    }
}

/**
 * A dish paid by weight in a mixed payment: ticked, it takes the [rest] of the payment, or as much as the weight entered
 * below says ([text] null = not ticked).
 */
@Composable
private fun WeightRow(
    w: MealMatcher.WeightDish,
    rest: Int,
    text: String?,
    language: String,
    locale: Locale,
    unknownMensa: String,
    onToggle: (Boolean) -> Unit,
    onText: (String) -> Unit,
) {
    val entered = text?.toIntOrNull()?.takeIf { it > 0 }
    val details = listOfNotNull(
        mensaName(w.dish.mensaId, language, unknownMensa),
        perHundred(w.per100, locale),
        when {
            entered != null -> formatCardAmount(w.cost(entered), locale)
            rest > 0 -> stringResource(R.string.meal_weight_estimate, w.grams(rest))
            else -> null
        },
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = text != null, role = Role.Checkbox, onValueChange = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(w.dish.name.forLanguage(language), style = MaterialTheme.typography.bodyMedium)
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(checked = text != null, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
        }
        if (text != null) {
            OutlinedTextField(
                value = text,
                onValueChange = { typed -> onText(typed.filter(Char::isDigit).take(5)) },
                singleLine = true,
                suffix = { Text("g") },
                label = { Text(stringResource(R.string.meal_weight_field)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "Vorhandenes Gericht wählen": search over every cached menu, every dish eaten or saved; that day's dishes first. */
@Composable
private fun SearchStep(
    pages: List<Pair<String, MenuPage>>,
    events: List<CardEvent>,
    customDishes: List<KnownDish>,
    day: List<SnapDish>,
    group: Int,
    language: String,
    locale: Locale,
    unknownMensa: String,
    onPick: (DishSearch.Hit) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(pages, events, customDishes, day, group) { DishSearch.hits(day, MenuDishes.all(pages), events, customDishes, group) }
    val results = remember(hits, query) { DishSearch.search(hits, query) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(60) },
            singleLine = true,
            label = { Text(stringResource(R.string.meal_search)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (results.isEmpty()) Hint(stringResource(R.string.meal_search_none), Modifier.padding(top = 12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(results, key = { "${it.key}|${it.mensaId}" }) { d ->
                Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { onPick(d) }.padding(vertical = 6.dp)) {
                    Text(d.name.forLanguage(language), style = MaterialTheme.typography.bodyLarge)
                    val details = listOfNotNull(
                        d.mensaId?.let { mensaName(it, language, unknownMensa) },
                        d.price?.let { if (d.byWeight) perHundred(it, locale) else formatCardAmount(it, locale) },
                    )
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NameDialog(title: String, label: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it.take(80) }, singleLine = true, label = { Text(label) })
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) } },
    )
}

/** The meals part of the statistics overview (detailed statistics). */
internal fun LazyListScope.mealsSection(data: StatsData, vm: StatsViewModel, locale: Locale, onUnassigned: () -> Unit) {
    val summary = MealSummary.summarize(data.events)
    val language = locale.language
    item(key = "meals-title") { SheetHeading(stringResource(R.string.meals_title)) }
    if (summary.servings == 0) {
        item(key = "meals-empty") { Hint(stringResource(R.string.meals_empty)) }
        return
    }
    item(key = "meals-tiles") {
        val percent = NumberFormat.getPercentInstance(locale)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            summary.averagePerMeal?.let { Tile(stringResource(R.string.meals_average), formatCardAmount(it, locale)) }
            summary.veganShare?.let { Tile(stringResource(R.string.meals_vegan), percent.format(it)) }
            summary.vegetarianShare?.let { Tile(stringResource(R.string.meals_vegetarian), percent.format(it)) }
        }
    }
    if (summary.unknownTags > 0) item(key = "meals-unknown-tags") {
        Hint(pluralStringResource(R.plurals.meals_unknown_tags, summary.unknownTags, summary.unknownTags), Modifier.padding(top = 8.dp))
    }
    item(key = "meals-top-title") {
        Text(stringResource(R.string.meals_top), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    }
    items(summary.top, key = { "top-${it.key}" }) { d ->
        val favorites by vm.favorites.collectAsStateWithLifecycle()
        val favorite = d.key in favorites
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${d.count}×", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp))
            Text(d.name.forLanguage(language), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.toggleFavorite(EatenDish(d.key, d.name, null, d.lastPrice, 1, d.tags)) }) {
                Icon(
                    painterResource(if (favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                    stringResource(if (favorite) R.string.meals_unfavorite else R.string.meals_favorite),
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item(key = "meals-mensa-title") {
        Text(stringResource(R.string.meals_by_mensa), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    }
    item(key = "meals-mensa") {
        val unknown = stringResource(R.string.meal_unknown_mensa)
        val max = summary.byMensa.maxOf { it.second }.coerceAtLeast(1)
        val barColor = MaterialTheme.colorScheme.primary
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            summary.byMensa.forEach { (id, n) ->
                Column {
                    // "Unbekannte Mensa" opens the list of those dishes, to assign a Mensa.
                    Row(
                        Modifier.fillMaxWidth().then(if (id == null) Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onUnassigned) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(mensaName(id, language, unknown), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("$n×", style = MaterialTheme.typography.bodyMedium)
                        if (id == null) Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // A thin horizontal bar: 8 dp, rounded ends, length = share of the most-visited Mensa.
                    Box(Modifier.fillMaxWidth(n.toFloat() / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * "Unbekannte Mensa": every dish of the meals without a Mensa, newest first, to assign one (and tags, and for a
 * hand-named dish a new name) with the same dialog as in the history. Full-screen overlay; the list shrinks as dishes
 * get a Mensa.
 */
@Composable
internal fun UnassignedDishesScreen(vm: StatsViewModel, events: List<CardEvent>, onClose: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val language = locale.language
    val rows = remember(events) { MealSummary.withoutMensa(events) }
    var editing by rememberSaveable { mutableStateOf<Pair<Long, Int>?>(null) }
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.meal_unknown_mensa), onBack = onClose)
            Hint(stringResource(R.string.unassigned_hint), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
            if (rows.isEmpty()) Text(stringResource(R.string.unassigned_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            LazyColumn(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            ) {
                items(rows, key = { "${it.eventId}-${it.index}" }) { r ->
                    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { editing = r.eventId to r.index }.padding(vertical = 6.dp)) {
                        Text((if (r.dish.count > 1) "${r.dish.count}× " else "") + r.dish.name.forLanguage(language), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            shortDate(r.date, locale) + " · " + formatCardAmount(r.dish.price * r.dish.count, locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    val target = editing?.let { (eventId, index) -> rows.firstOrNull { it.eventId == eventId && it.index == index } }
    if (target != null) {
        EditDishDialog(target.dish, language, onConfirm = { name, mensaId, tags ->
            editing = null
            vm.editDish(target.eventId, target.index, name, mensaId, tags)
        }, onDismiss = { editing = null })
    }
}
