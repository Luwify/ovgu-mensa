package de.luwify.mensamd.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.R
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.stats.Amounts
import de.luwify.mensamd.stats.BalanceDiff
import de.luwify.mensamd.stats.OpenItem
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * The balance went down by more than the last debit: "Als eine Ausgabe speichern" or "Aufteilen" (parts that add up
 * exactly; the split starts with the rest and the last debit).
 */
@Composable
fun SeveralPaymentsDialog(item: OpenItem.SeveralPayments, onSave: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val comma = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',' }
    var splitting by rememberSaveable { mutableStateOf(false) }
    val parts = remember {
        mutableStateListOf<String>().apply {
            val last = item.lastDebit?.takeIf { it in 1 until item.amount }
            if (last != null) {
                add(Amounts.toInput(item.amount - last, comma))
                add(Amounts.toInput(last, comma))
            } else {
                add("")
                add("")
            }
        }
    }
    val parsed = parts.map { Amounts.parseEuro(it) }
    val valid = parsed.all { it != null } && BalanceDiff.validSplit(item.amount, parsed.filterNotNull())
    val rest = item.amount - parsed.sumOf { it ?: 0 }
    val fmt = { v: Int -> formatCardAmount(v, locale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.q_several_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (item.lastDebit != null) stringResource(R.string.q_several_text, fmt(item.amount), fmt(item.lastDebit))
                    else stringResource(R.string.q_several_text_no_debit, fmt(item.amount)),
                )
                if (splitting) {
                    parts.forEachIndexed { i, text ->
                        OutlinedTextField(
                            value = text,
                            onValueChange = { parts[i] = it.take(10) },
                            singleLine = true,
                            suffix = { Text("€") },
                            isError = text.isNotBlank() && parsed[i] == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = { Text(stringResource(R.string.q_part, i + 1)) },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (rest == 0) stringResource(R.string.q_split_ok) else stringResource(R.string.q_split_rest, fmt(abs(rest)).let { if (rest < 0) "−$it" else it }),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rest == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { parts.add(if (rest > 0) Amounts.toInput(rest, comma) else "") }, enabled = parts.size < 8) {
                            Text(stringResource(R.string.q_add_part))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (splitting) {
                TextButton(onClick = { onSave(parsed.filterNotNull()) }, enabled = valid) { Text(stringResource(R.string.q_save_split)) }
            } else {
                TextButton(onClick = { onSave(listOf(item.amount)) }) { Text(stringResource(R.string.q_save_one)) }
            }
        },
        dismissButton = {
            if (splitting) TextButton(onClick = { splitting = false }) { Text(stringResource(R.string.q_back)) }
            else TextButton(onClick = { splitting = true }) { Text(stringResource(R.string.q_split)) }
        },
    )
}

/**
 * The first scan of a card shows a last debit from before: "Letzte Abbuchung x € erfassen?" The card keeps no date, so
 * the day is asked (default: the scan's day, any day up to it); "Erfassen" records the payment (then the meal question
 * follows), "Nicht erfassen" drops the question. Closing the dialog keeps the question for later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstDebitDialog(item: OpenItem.FirstDebit, onSave: (LocalDate) -> Unit, onSkip: () -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var epochDay by rememberSaveable { mutableLongStateOf(item.date.toEpochDay()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(epochDay)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.q_first_title, formatCardAmount(item.amount, locale))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.q_first_text))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.q_first_date, shortDate(date, locale)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picking = true }) { Text(stringResource(R.string.q_first_change_date)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(date) }) { Text(stringResource(R.string.q_first_record)) } },
        dismissButton = { TextButton(onClick = onSkip) { Text(stringResource(R.string.q_first_skip)) } },
    )
    if (picking) {
        // The picker works in UTC midnight millis; days after the scan can't be picked.
        val latest = item.date
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            yearRange = (latest.year - 1)..latest.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(latest)

                override fun isSelectableYear(year: Int): Boolean = year <= latest.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { epochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    picking = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel_action)) } },
        ) { DatePicker(state) }
    }
}
