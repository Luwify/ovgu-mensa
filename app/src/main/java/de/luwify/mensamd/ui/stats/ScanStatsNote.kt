package de.luwify.mensamd.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.card.CardScanState
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.stats.EventKind
import de.luwify.mensamd.stats.RecordResult
import de.luwify.mensamd.ui.forLanguage

/**
 * Under the balance on the card screen (statistics on): what this scan did. Offers to register the card when none is
 * registered, says when another card was scanned, lists what was found and points to open questions.
 */
@Composable
fun ScanStatsNote(vm: StatsViewModel, scan: CardScanState.Success?, onOpenStats: () -> Unit) {
    val record by vm.lastRecord.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0]
    var declined by rememberSaveable { mutableStateOf(false) }
    val result = record?.takeIf { scan != null && it.seq == scan.seq }?.result
    val open = data?.open.orEmpty()

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (result) {
            RecordResult.NoCard -> if (!declined && scan?.fingerprint != null) {
                NoteCard {
                    Text(stringResource(R.string.register_offer_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.register_offer_text), style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { declined = true }) { Text(stringResource(R.string.register_offer_later)) }
                        Button(onClick = {
                            vm.registerScanned(scan.fingerprint, scan.balance.value, scan.balance.lastTransaction, scan.seq)
                        }) { Text(stringResource(R.string.register_offer_yes)) }
                    }
                }
            }
            RecordResult.OtherCard -> Note(stringResource(R.string.scan_other_card))
            RecordResult.Unchanged -> Note(stringResource(R.string.scan_unchanged))
            is RecordResult.Recorded -> {
                if (result.baseline) {
                    Note(stringResource(R.string.scan_baseline))
                } else {
                    val events = data?.events.orEmpty().filter { it.scanId == result.scanId }
                    if (events.isEmpty() && result.newQuestions.isEmpty()) Note(stringResource(R.string.scan_saved))
                    events.forEach { e ->
                        val amount = formatCardAmount(e.amount, language)
                        val meal = e.meal?.dishes?.joinToString(", ") { it.name.forLanguage(language.language) }?.takeIf { it.isNotEmpty() }
                        Note(
                            when (e.kind) {
                                EventKind.PAYMENT -> if (meal != null) stringResource(R.string.scan_payment_meal, amount, meal) else stringResource(R.string.scan_payment, amount)
                                EventKind.TOP_UP -> stringResource(R.string.scan_top_up, amount)
                                EventKind.PFAND -> stringResource(R.string.scan_pfand, amount)
                            },
                        )
                    }
                }
            }
            RecordResult.Off, null -> Unit
        }
        if (open.isNotEmpty()) {
            Button(onClick = onOpenStats) { Text(pluralStringResource(R.plurals.open_questions_button, open.size, open.size)) }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
internal fun NoteCard(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

