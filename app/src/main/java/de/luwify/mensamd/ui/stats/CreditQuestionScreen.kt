package de.luwify.mensamd.ui.stats

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.R
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.stats.CreditDraft
import de.luwify.mensamd.stats.EventKind
import de.luwify.mensamd.stats.KnownAmounts
import de.luwify.mensamd.stats.Movement
import de.luwify.mensamd.stats.MovementSuggestion
import de.luwify.mensamd.stats.OpenItem
import de.luwify.mensamd.stats.StatsData
import de.luwify.mensamd.stats.StatsOps
import de.luwify.mensamd.ui.SheetHeading
import de.luwify.mensamd.ui.theme.MensaFilterChip
import java.time.LocalDate
import java.util.Locale

/**
 * "Was ist passiert?" for a change no single known amount explains ([OpenItem.CreditAndPayment]), in the style of the meal
 * question screen: the change at the top, 1-3 suggestions (from the card's last debit, the top-up amounts and Pfand sums),
 * then the movements (Aufladung, Zahlung, Pfand zurück), each with its amount and day, and "Noch x € offen" until they add up.
 * A suggestion saves at once when every movement can only be on the scan's day; otherwise it fills the list, so the days
 * can be set first. "Später" keeps the question, "Nicht erfassen" drops it. Back closes (nothing is saved).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreditQuestionScreen(
    item: OpenItem.CreditAndPayment,
    data: StatsData,
    known: KnownAmounts,
    onSave: (List<Movement>) -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    BackHandler(onBack = onClose)
    val previous = remember(data.scans, item.scanId) { StatsOps.previousScanDate(data, item.scanId) }
    val days = remember(previous, item.date) { CreditDraft.days(previous, item.date) }
    val single = days.size == 1
    val suggestions = remember(item, known, data.scans) {
        CreditDraft.suggestions(item.delta, item.lastDebit, CreditDraft.debitChanged(data, item.scanId), known, item.date)
    }
    val movements = remember(item.id) { mutableStateListOf<Movement>() }
    // The amount dialog: a new movement of this kind, or the movement at this index.
    var adding by remember { mutableStateOf<EventKind?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val rest = CreditDraft.rest(item.delta, movements)
    val complete = CreditDraft.complete(item.delta, movements)

    fun take(s: MovementSuggestion) {
        val list = s.movements.map { it.copy(dayChosen = single) }
        if (single) return onSave(list)
        movements.clear()
        movements.addAll(list)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.credit_title), onBack = onClose)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                item(key = "head") {
                    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.credit_change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(signed(item.delta, locale), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                        Hint(
                            if (previous != null && previous != item.date) stringResource(R.string.credit_between, shortDate(previous, locale), shortDate(item.date, locale))
                            else shortDate(item.date, locale),
                        )
                        item.lastDebit?.takeIf { it > 0 }?.let { Hint(stringResource(R.string.credit_last_debit, formatCardAmount(it, locale))) }
                    }
                }
                if (movements.isNotEmpty()) {
                    items(movements.size, key = { "move-$it" }) { i ->
                        MovementRow(movements[i], days, locale, onEdit = { editing = i }, onRemove = { movements.removeAt(i) }) { day ->
                            movements[i] = movements[i].copy(day = day, dayChosen = true)
                        }
                    }
                    item(key = "rest") {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (rest != 0) stringResource(R.string.meal_rest_open, signed(rest, locale)) else stringResource(R.string.meal_rest_ok),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                if (complete) Button(onClick = { onSave(movements.toList()) }) { Text(stringResource(R.string.q_save)) }
                            }
                        }
                    }
                } else if (suggestions.isNotEmpty()) {
                    item(key = "sugg-title") { SheetHeading(stringResource(R.string.meal_suggestions)) }
                    items(suggestions.size, key = { "sugg-$it" }) { i ->
                        SuggestionCard(suggestions[i], locale) { take(suggestions[i]) }
                    }
                }
                item(key = "add") {
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.credit_add), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (kind in listOf(EventKind.TOP_UP, EventKind.PAYMENT, EventKind.PFAND)) {
                                OutlinedButton(onClick = { adding = kind }) { Text("+ " + kindName(kind)) }
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.meal_later)) }
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.meal_dont_record)) }
            }
        }
    }

    adding?.let { kind ->
        AmountDialog(
            title = kindName(kind),
            initial = CreditDraft.prefill(kind, rest),
            text = if (kind == EventKind.PFAND) stringResource(R.string.credit_pfand_hint) else null,
            onConfirm = {
                adding = null
                movements.add(Movement(kind, it, item.date, dayChosen = single))
            },
            onDismiss = { adding = null },
        )
    }
    editing?.let { i -> movements.getOrNull(i)?.let { i to it } }?.let { (i, m) ->
        AmountDialog(
            title = kindName(m.kind),
            initial = m.amount,
            onConfirm = {
                editing = null
                movements[i] = m.copy(amount = it)
            },
            onDismiss = { editing = null },
        )
    }
}

/** One movement: its kind and day (a choice when the scan gap allows several days), the signed amount; tap edits, × removes. */
@Composable
private fun MovementRow(m: Movement, days: List<LocalDate>, locale: Locale, onEdit: () -> Unit, onRemove: () -> Unit, onDay: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onEdit), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(kindName(m.kind), style = MaterialTheme.typography.bodyLarge)
            if (days.size > 1) DayPick(m.day, days, locale, onDay)
            else Text(shortDate(m.day, locale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(signed(m.signed, locale), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.meal_item_remove), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The day of a movement: up to three days as chips side by side, more in a menu. */
@Composable
private fun DayPick(day: LocalDate, days: List<LocalDate>, locale: Locale, onPick: (LocalDate) -> Unit) {
    if (days.size <= 3) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { d -> MensaFilterChip(d == day, { onPick(d) }, label = { Text(shortDate(d, locale)) }) }
        }
        return
    }
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

/** A complete answer: one line per movement with its signed amount; "Mit der letzten Abbuchung" above when it uses it. */
@Composable
private fun SuggestionCard(s: MovementSuggestion, locale: Locale, onPick: () -> Unit) {
    Surface(
        onClick = onPick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (s.usesLastDebit) {
                Text(stringResource(R.string.credit_uses_last_debit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.movements.forEach { m ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(kindName(m.kind), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(signed(m.signed, locale), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun kindName(kind: EventKind): String = stringResource(
    when (kind) {
        EventKind.TOP_UP -> R.string.event_top_up
        EventKind.PAYMENT -> R.string.event_payment
        EventKind.PFAND -> R.string.credit_pfand_back
    },
)

/** "+19,10 €", "−1,05 €" (a real minus sign), "0,00 €". */
private fun signed(mills: Int, locale: Locale): String = when {
    mills > 0 -> "+" + formatCardAmount(mills, locale)
    mills < 0 -> "−" + formatCardAmount(-mills, locale)
    else -> formatCardAmount(0, locale)
}
