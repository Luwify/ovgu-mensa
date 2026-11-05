package de.luwify.mensamd.ui.stats

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.stats.Amounts
import de.luwify.mensamd.ui.NotificationAccess
import de.luwify.mensamd.ui.SheetHeading
import de.luwify.mensamd.ui.SwitchRow
import de.luwify.mensamd.ui.theme.BackIcon
import de.luwify.mensamd.ui.theme.PlusIcon
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Card settings (gear on the card screen): statistics on/off, detailed statistics, the reminder, "Meine Karte" and the
 * known top-up and Pfand amounts. An overlay over the card screen, closed with back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardSettingsScreen(vm: StatsViewModel, access: NotificationAccess, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val registering by vm.registering.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    val locale: Locale = LocalConfiguration.current.locales[0]
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var addingTo by rememberSaveable { mutableStateOf<String?>(null) } // "topup" / "pfand"
    val s = settings ?: return

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.card_settings_title), onBack)
            LazyColumn(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 24.dp),
            ) {
                problem?.let { p -> item(key = "problem") { ProblemNote(p) } }
                item(key = "stats-title") { SheetHeading(stringResource(R.string.stats_section)) }
                item(key = "stats") {
                    SwitchRow(stringResource(R.string.stats_toggle), stringResource(R.string.stats_toggle_hint), s.enabled, onChange = vm::setEnabled)
                }
                item(key = "detailed") {
                    SwitchRow(
                        stringResource(R.string.stats_detailed), stringResource(R.string.stats_detailed_hint),
                        s.detailed, enabled = s.enabled, onChange = vm::setDetailed,
                    )
                }

                item(key = "card-title") { SheetHeading(stringResource(R.string.my_card_title)) }
                item(key = "card") {
                    val card = data?.card
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            when {
                                registering -> stringResource(R.string.my_card_hold)
                                card == null -> stringResource(R.string.my_card_none)
                                else -> stringResource(R.string.my_card_since, formatDate(card.since, locale))
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                registering -> OutlinedButton(onClick = { vm.setRegistering(false) }) { Text(stringResource(R.string.cancel_action)) }
                                card == null -> Button(onClick = { vm.setRegistering(true) }, enabled = s.enabled) {
                                    Text(stringResource(R.string.my_card_register))
                                }
                                else -> {
                                    OutlinedButton(onClick = { vm.setRegistering(true) }, enabled = s.enabled) { Text(stringResource(R.string.my_card_replace)) }
                                    OutlinedButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.my_card_remove)) }
                                }
                            }
                        }
                        Hint(stringResource(if (s.enabled) R.string.my_card_hint else R.string.my_card_needs_stats))
                    }
                }

                item(key = "reminder-title") { SheetHeading(stringResource(R.string.card_reminder_title)) }
                item(key = "reminder") {
                    SwitchRow(
                        stringResource(R.string.card_reminder_toggle), stringResource(R.string.card_reminder_hint),
                        s.reminder, enabled = s.enabled,
                    ) { on ->
                        vm.setReminder(on)
                        if (on && !access.allowed) access.request()
                    }
                }
                if (s.reminder && s.enabled) {
                    item(key = "reminder-time") { TimeRow(stringResource(R.string.card_reminder_time), s.reminderTime, vm::setReminderTime) }
                    if (!access.allowed) {
                        item(key = "reminder-blocked") {
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Hint(stringResource(R.string.notify_blocked), Modifier.weight(1f))
                                TextButton(onClick = access.request) { Text(stringResource(R.string.notify_open_settings)) }
                            }
                        }
                    }
                }

                item(key = "topups-title") { SheetHeading(stringResource(R.string.top_up_amounts)) }
                item(key = "topups") {
                    AmountChips(s.topUps, locale, onRemove = { vm.setTopUps(s.topUps - it) }, onAdd = { addingTo = "topup" })
                }
                item(key = "pfand-title") { SheetHeading(stringResource(R.string.pfand_amounts)) }
                item(key = "pfand") {
                    Column {
                        AmountChips(s.pfand, locale, onRemove = { vm.setPfand(s.pfand - it) }, onAdd = { addingTo = "pfand" })
                        Hint(stringResource(R.string.pfand_amounts_hint))
                    }
                }

                item(key = "data-title") { SheetHeading(stringResource(R.string.stats_data_title)) }
                item(key = "clear") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { confirmClear = true }, enabled = data?.scans?.isNotEmpty() == true || data?.events?.isNotEmpty() == true) {
                            Text(stringResource(R.string.stats_clear))
                        }
                        Hint(stringResource(R.string.stats_backup_hint))
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.my_card_remove_title),
            text = stringResource(R.string.my_card_remove_text),
            confirm = stringResource(R.string.my_card_remove),
            onConfirm = {
                confirmRemove = false
                vm.unregister()
            },
            onDismiss = { confirmRemove = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.stats_clear_title),
            text = stringResource(R.string.stats_clear_text),
            confirm = stringResource(R.string.stats_clear_confirm),
            onConfirm = {
                confirmClear = false
                vm.clearHistory()
            },
            onDismiss = { confirmClear = false },
        )
    }
    addingTo?.let { target ->
        AmountDialog(
            title = stringResource(if (target == "topup") R.string.top_up_add else R.string.pfand_add),
            initial = null,
            onConfirm = { amount ->
                addingTo = null
                if (target == "topup") vm.setTopUps(s.topUps + amount) else vm.setPfand(s.pfand + amount)
            },
            onDismiss = { addingTo = null },
        )
    }
}

@Composable
internal fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(56.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(BackIcon, contentDescription = stringResource(R.string.card_back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun ProblemNote(problem: de.luwify.mensamd.stats.StatsProblem) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            when (problem) {
                is de.luwify.mensamd.stats.StatsProblem.TooNew -> stringResource(R.string.stats_too_new)
                is de.luwify.mensamd.stats.StatsProblem.Broken -> stringResource(R.string.stats_broken, problem.keptAs)
            },
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountChips(amounts: List<Int>, locale: Locale, onRemove: (Int) -> Unit, onAdd: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        amounts.forEach { amount ->
            val label = formatCardAmount(amount, locale)
            InputChip(
                selected = false,
                onClick = { onRemove(amount) },
                label = { Text(label) },
                trailingIcon = {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.amount_remove, label), Modifier.size(18.dp))
                },
            )
        }
        AssistChip(
            onClick = onAdd,
            label = { Text(stringResource(R.string.amount_add)) },
            leadingIcon = { Icon(PlusIcon, null, Modifier.size(18.dp)) },
        )
    }
}

/** Asks for a euro amount; OK only when it parses (see [Amounts.parseEuro]). */
@Composable
internal fun AmountDialog(title: String, initial: Int?, onConfirm: (Int) -> Unit, onDismiss: () -> Unit, text: String? = null) {
    val locale = LocalConfiguration.current.locales[0]
    val comma = remember(locale) { java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',' }
    var input by rememberSaveable { mutableStateOf(initial?.let { Amounts.toInput(it, comma) } ?: "") }
    val parsed = Amounts.parseEuro(input)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                text?.let { Text(it) }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(10) },
                    singleLine = true,
                    suffix = { Text("€") },
                    isError = input.isNotBlank() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.amount_label)) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) } },
    )
}

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(label: String, time: LocalTime, onTime: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val formatted = remember(time, locale) { time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { picking = true }).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Text(formatted, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    if (picking) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    onTime(LocalTime.of(state.hour, state.minute))
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel_action)) } },
            title = { Text(label) },
            text = { TimePicker(state) },
        )
    }
}

internal fun formatDate(epochMillis: Long, locale: Locale): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
