package de.luwify.mensamd.ui.stats

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.stats.CardEvent
import de.luwify.mensamd.stats.EventKind
import de.luwify.mensamd.stats.OpenItem
import de.luwify.mensamd.stats.StatsLinks
import de.luwify.mensamd.stats.StatsSummary
import de.luwify.mensamd.ui.MainViewModel
import de.luwify.mensamd.ui.theme.MensaFilterChip
import de.luwify.mensamd.ui.SheetHeading
import de.luwify.mensamd.ui.forLanguage
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

/**
 * The card statistics (chart button on the card screen): open questions first, then this month's numbers, spending
 * and top-ups per week/month, spending per weekday, the balance over time, the meals (detailed statistics) and the
 * history with delete + undo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: StatsViewModel, mainVm: MainViewModel, onRegister: () -> Unit, onBack: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    val locale: Locale = LocalConfiguration.current.locales[0]
    val language = locale.language
    val context = LocalContext.current
    var unit by rememberSaveable { mutableStateOf(StatsSummary.Unit.WEEK) }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editEventId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var showUnassigned by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val deletedText = stringResource(R.string.stats_deleted)
    val undoText = stringResource(R.string.stats_undo)
    val scope = rememberCoroutineScope()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val today = remember { LocalDate.now() }
    val d = data
    val s = settings
    val mealOverlay = d?.open?.firstOrNull { it.id == openId } as? OpenItem.MealQuestion
    val editEvent = d?.events?.firstOrNull { it.id == editEventId && it.kind == EventKind.PAYMENT }
    BackHandler(enabled = mealOverlay == null && editEvent == null && !showUnassigned, onBack = onBack)
    // "Anders zuordnen" (here or in the card screen's snackbar) opens the new question as soon as it exists.
    val focus by vm.focusQuestion.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.matchPending() }
    // A new question (a scan while this screen is open, or one that follows an answer) goes on top: show it, the list
    // would otherwise keep its place and leave it above the visible part.
    val listState = rememberLazyListState()
    var openCount by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(d?.open?.size) {
        val n = d?.open?.size ?: return@LaunchedEffect
        if (openCount in 0 until n) listState.animateScrollToItem(0)
        openCount = n
    }
    LaunchedEffect(focus, d?.open) {
        val id = focus ?: return@LaunchedEffect
        if (d?.open?.any { it.id == id } == true) {
            openId = id
            vm.consumeFocusQuestion()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopBar(stringResource(R.string.stats_title), onBack)
                if (d == null || s == null) return@Column
                val totals = remember(d, today) { StatsSummary.totals(d, today) }
                LazyColumn(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 72.dp),
                ) {
                    problem?.let { item(key = "problem") { Hint(stringResource(R.string.stats_too_new)) } }
                    if (d.open.isNotEmpty()) {
                        item(key = "open-title") { SheetHeading(pluralStringResource(R.plurals.open_questions_title, d.open.size, d.open.size)) }
                        items(d.open, key = { "open-${it.id}" }) { q -> QuestionRow(q, locale) { openId = q.id } }
                    }
                    if (d.events.isEmpty()) {
                        item(key = "empty") {
                            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    stringResource(if (d.card == null) R.string.stats_empty_no_card else R.string.stats_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                // To the settings' card tab, where "Karte registrieren" is.
                                if (d.card == null) OutlinedButton(onClick = onRegister) { Text(stringResource(R.string.my_card_register)) }
                            }
                        }
                    } else {
                        item(key = "tiles") {
                            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatTile(
                                    stringResource(R.string.stats_spent_in, today.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)),
                                    formatCardAmount(totals.spentThisMonth, locale), hero = true,
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    totals.averagePerVisit?.let { StatTile(stringResource(R.string.stats_average_visit), formatCardAmount(it, locale)) }
                                    StatTile(stringResource(R.string.stats_topped_up_month), formatCardAmount(totals.toppedUpThisMonth, locale))
                                    if (totals.pfandThisMonth > 0) StatTile(stringResource(R.string.stats_pfand_month), formatCardAmount(totals.pfandThisMonth, locale))
                                    totals.currentBalance?.let { StatTile(stringResource(R.string.stats_balance_now), formatCardAmount(it, locale)) }
                                }
                            }
                        }
                        item(key = "periods-title") { SheetHeading(stringResource(R.string.stats_per_period)) }
                        item(key = "periods") {
                            val periods = remember(d.events, unit, today) {
                                StatsSummary.periods(d.events, unit, today, if (unit == StatsSummary.Unit.WEEK) 8 else 6)
                            }
                            val spentName = stringResource(R.string.stats_series_spent)
                            val topUpName = stringResource(R.string.stats_series_top_ups)
                            val spentColor = MaterialTheme.colorScheme.primary
                            val topUpColor = MaterialTheme.colorScheme.tertiary
                            val weekOf = stringResource(R.string.stats_week_of)
                            val groups = periods.map { p ->
                                val label = periodLabel(p.start, unit, locale)
                                val title = if (unit == StatsSummary.Unit.WEEK) "$weekOf $label" else p.start.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + p.start.year
                                BarGroup(label, listOf(p.spent, p.toppedUp), "$title: $spentName ${formatCardAmount(p.spent, locale)} · $topUpName ${formatCardAmount(p.toppedUp, locale)}")
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MensaFilterChip(unit == StatsSummary.Unit.WEEK, { unit = StatsSummary.Unit.WEEK }, label = { Text(stringResource(R.string.stats_unit_week)) })
                                    MensaFilterChip(unit == StatsSummary.Unit.MONTH, { unit = StatsSummary.Unit.MONTH }, label = { Text(stringResource(R.string.stats_unit_month)) })
                                }
                                BarChart(
                                    groups, listOf(spentColor, topUpColor), { euroAxis(it, locale) },
                                    description = groups.joinToString("; ") { it.detail },
                                    legend = listOf(SeriesKey(spentName, spentColor), SeriesKey(topUpName, topUpColor)),
                                )
                            }
                        }
                        item(key = "weekday-title") { SheetHeading(stringResource(R.string.stats_per_weekday)) }
                        item(key = "weekday") {
                            val days = remember(d.events) { StatsSummary.weekdays(d.events) }
                            val groups = days.map { w ->
                                BarGroup(
                                    w.day.getDisplayName(TextStyle.SHORT, locale),
                                    listOf(w.spent),
                                    w.day.getDisplayName(TextStyle.FULL, locale) + ": " +
                                        pluralStringResource(R.plurals.stats_weekday_detail, w.payments, formatCardAmount(w.spent, locale), w.payments),
                                )
                            }
                            Column {
                                BarChart(groups, listOf(MaterialTheme.colorScheme.primary), { euroAxis(it, locale) }, description = groups.joinToString("; ") { it.detail })
                                Hint(stringResource(R.string.stats_weekday_hint), Modifier.padding(top = 8.dp))
                            }
                        }
                        val balance = StatsSummary.balance(d.scans, d.card?.hash)
                        if (balance.size >= 2) {
                            item(key = "balance-title") { SheetHeading(stringResource(R.string.stats_balance_title)) }
                            item(key = "balance") {
                                val points = balance.map { it.at to it.balance }
                                StepLineChart(
                                    points, MaterialTheme.colorScheme.primary,
                                    detail = { i -> formatDateTime(points[i].first, locale) + ": " + formatCardAmount(points[i].second, locale) },
                                    axisLabel = { euroAxis(it, locale) },
                                    description = stringResource(R.string.stats_balance_title) + ": " + formatCardAmount(points.last().second, locale),
                                )
                            }
                        }
                        if (s.detailedActive) mealsSection(d, vm, locale) { showUnassigned = true }
                        item(key = "history-title") { SheetHeading(stringResource(R.string.stats_history)) }
                        val history = d.events.asReversed().let { if (showAll) it else it.take(20) }
                        items(history, key = { "event-${it.id}" }) { e ->
                            EventRow(
                                e, locale, language, canEdit = s.detailedActive && e.kind == EventKind.PAYMENT, onEdit = { editEventId = e.id },
                                onReassign = { vm.reassignCredit(e.id) },
                            ) {
                                vm.deleteEvent(e)
                                scope.launch {
                                    val r = snackbar.showSnackbar(deletedText, undoText, duration = SnackbarDuration.Long)
                                    if (r == SnackbarResult.ActionPerformed) vm.restoreEvent(e)
                                }
                            }
                        }
                        if (!showAll && d.events.size > 20) {
                            item(key = "history-more") {
                                TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.stats_show_all, d.events.size)) }
                            }
                        }
                    }
                    if (StatsLinks.MEAL_STATS_URL.isNotBlank()) {
                        item(key = "link") {
                            de.luwify.mensamd.ui.ChevronRow(stringResource(R.string.stats_meal_website)) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, StatsLinks.MEAL_STATS_URL.toUri()))
                                } catch (e: ActivityNotFoundException) {
                                    // no browser
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    // Answering a question.
    val open = d?.open?.firstOrNull { it.id == openId }
    when (open) {
        is OpenItem.SeveralPayments -> SeveralPaymentsDialog(open, onSave = { parts ->
            openId = null
            vm.resolveSeveral(open.id, parts)
        }, onDismiss = { openId = null })
        is OpenItem.CreditAndPayment -> if (s != null) CreditDialog(
            open, s.known,
            onSave = { credit, kind ->
                openId = null
                vm.resolveCredit(open.id, credit, kind)
            },
            onDismiss = { openId = null },
            onSkip = {
                openId = null
                vm.dismiss(open.id)
            },
        )
        is OpenItem.FirstDebit -> FirstDebitDialog(
            open,
            onSave = { date ->
                openId = null
                vm.resolveFirstDebit(open.id, date)
            },
            onSkip = {
                openId = null
                vm.resolveFirstDebit(open.id, null)
            },
            onDismiss = { openId = null },
        )
        is OpenItem.MealQuestion -> MealQuestionScreen(vm, open, d.customDishes, d.customExtras, d.events, onClose = { openId = null })
        null -> if (openId != null && d != null) openId = null
    }
    if (editEvent != null && d != null) {
        MealQuestionScreen(vm, editEvent.asQuestion(), d.customDishes, d.customExtras, d.events, current = editEvent.meal, onClose = { editEventId = null })
    }
    if (showUnassigned && d != null) UnassignedDishesScreen(vm, d.events, onClose = { showUnassigned = false })
}

/** A payment opened for editing: the meal flow; that day's menu comes from the cached pages (see [MealQuestionScreen]). */
private fun CardEvent.asQuestion() = OpenItem.MealQuestion(-id, scanId, date, dateKnown, id, amount)

@Composable
private fun QuestionRow(item: OpenItem, locale: Locale, onClick: () -> Unit) {
    val fmt = { v: Int -> formatCardAmount(v, locale) }
    val text = when (item) {
        is OpenItem.SeveralPayments -> stringResource(R.string.q_row_several, fmt(item.amount))
        is OpenItem.CreditAndPayment -> when {
            item.delta > 0 -> stringResource(R.string.q_row_credit_up, fmt(item.delta))
            item.delta < 0 -> stringResource(R.string.q_row_credit_down, fmt(-item.delta))
            else -> stringResource(R.string.q_row_credit_zero)
        }
        is OpenItem.MealQuestion -> stringResource(R.string.q_row_meal, fmt(item.amount))
        is OpenItem.FirstDebit -> stringResource(R.string.q_first_title, fmt(item.amount))
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(shortDate(item.date, locale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(stringResource(R.string.q_answer), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun EventRow(
    e: CardEvent,
    locale: Locale,
    language: String,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onReassign: () -> Unit,
    onDelete: () -> Unit,
) {
    val amount = formatCardAmount(e.amount, locale)
    val title = when (e.kind) {
        EventKind.PAYMENT -> stringResource(R.string.event_payment)
        EventKind.TOP_UP -> stringResource(R.string.event_top_up)
        EventKind.PFAND -> stringResource(R.string.event_pfand)
    }
    val meal = e.meal?.let { mealSummary(it, language) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (canEdit) Modifier.clickable(role = Role.Button, onClick = onEdit) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title + " · " + (if (e.dateKnown) shortDate(e.date, locale) else stringResource(R.string.event_until, shortDate(e.date, locale))), style = MaterialTheme.typography.bodyLarge)
            meal?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (e.kind == EventKind.PFAND) {
                TextButton(onClick = onReassign, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.pfand_reassign)) }
            }
        }
        Text(
            (if (e.kind == EventKind.PAYMENT) "−" else "+") + amount,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 72.dp).padding(start = 8.dp),
        )
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.stats_delete_event), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, hero: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (hero) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** "Seelachs, 2× Burger, Farfalle (ca. 470 g), Salatbar" (dishes, then extras), or "Sonstiges". */
@Composable
internal fun mealSummary(meal: de.luwify.mensamd.stats.MealRecord, language: String): String {
    if (meal.other) return stringResource(R.string.meal_other)
    val dishes = meal.dishes.map { d ->
        (if (d.count > 1) "${d.count}× " else "") + d.name.forLanguage(language) +
            (d.grams?.let { " (" + stringResource(R.string.meal_weight_estimate, it) + ")" } ?: "")
    }
    val extras = meal.extras.map { extraName(it) }
    return (dishes + extras).joinToString(", ")
}

@Composable
internal fun extraName(key: String): String = when (key) {
    de.luwify.mensamd.stats.Extras.SALAD -> stringResource(R.string.extra_salad)
    de.luwify.mensamd.stats.Extras.DESSERT -> stringResource(R.string.extra_dessert)
    de.luwify.mensamd.stats.Extras.PUDDING -> stringResource(R.string.extra_pudding)
    de.luwify.mensamd.stats.Extras.ROTE_GRUETZE -> stringResource(R.string.extra_rote_gruetze)
    de.luwify.mensamd.stats.Extras.DRINK -> stringResource(R.string.extra_drink)
    de.luwify.mensamd.stats.Extras.COFFEE -> stringResource(R.string.extra_coffee)
    else -> key
}

internal fun shortDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern(if (locale.language == "de") "EE d.M." else "EEE d MMM", locale))

private fun periodLabel(start: LocalDate, unit: StatsSummary.Unit, locale: Locale): String = when (unit) {
    StatsSummary.Unit.WEEK -> start.format(DateTimeFormatter.ofPattern(if (locale.language == "de") "d.M." else "d/M", locale))
    StatsSummary.Unit.MONTH -> start.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
}

private fun formatDateTime(epochMillis: Long, locale: Locale): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))

/** Axis maximum in whole euros ("20 €"). */
private fun euroAxis(mills: Int, locale: Locale): String {
    val f = NumberFormat.getCurrencyInstance(locale)
    f.currency = Currency.getInstance("EUR")
    f.maximumFractionDigits = 0
    f.minimumFractionDigits = 0
    return f.format(mills / 1000)
}
