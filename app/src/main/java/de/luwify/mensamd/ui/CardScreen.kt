package de.luwify.mensamd.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.card.CardBalance
import de.luwify.mensamd.card.CardScanState
import de.luwify.mensamd.card.NfcCardScanner
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.stats.EventKind
import de.luwify.mensamd.stats.RecordResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import de.luwify.mensamd.ui.stats.rememberStatsViewModel
import de.luwify.mensamd.ui.stats.ScanStatsNote
import de.luwify.mensamd.ui.stats.StatsScreen
import de.luwify.mensamd.ui.stats.TopBar
import de.luwify.mensamd.ui.theme.StatsIcon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import de.luwify.mensamd.ui.theme.CardIcon
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import de.luwify.mensamd.stats.LastBalance
import de.luwify.mensamd.stats.LastBalances

/**
 * The "Karte" screen. The scanner runs while the activity is resumed (see `MainActivity`); it opens this screen on a
 * scan. The gear opens the settings on their card tab ([onOpenSettings], an overlay in `MensaRoot` above this screen),
 * the "Statistik" button at the bottom (statistics on, in thumb reach, with the open questions' count) the statistics,
 * an overlay here. Before a scan the last known balance is shown, muted ([LastKnownBalance]).
 */
@Composable
fun CardScreen(scanner: NfcCardScanner, mainVm: MainViewModel, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    val statsVm = rememberStatsViewModel()
    var showStats by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !showStats, onBack = onBack)
    val openStats by mainVm.openStats.collectAsStateWithLifecycle()
    LaunchedEffect(openStats) {
        if (openStats) {
            showStats = true
            mainVm.consumeOpenStats()
        }
    }
    val state by scanner.state.collectAsStateWithLifecycle()
    val settings by statsVm.settings.collectAsStateWithLifecycle()
    val statsOn = settings?.enabled == true
    val snackbar = remember { SnackbarHostState() }
    val record by statsVm.lastRecord.collectAsStateWithLifecycle()
    val stats by statsVm.data.collectAsStateWithLifecycle()
    val lastScan by statsVm.lastCardScan.collectAsStateWithLifecycle()
    val lastBalance = LastBalances.pick(statsOn, stats, lastScan)
    val openCount = if (statsOn) stats?.open?.size ?: 0 else 0
    var announcedSeq by rememberSaveable { mutableLongStateOf(0L) }
    val pfandText = stringResource(R.string.pfand_snackbar)
    val reassignText = stringResource(R.string.pfand_reassign)
    val locale = LocalConfiguration.current.locales[0]
    // An automatic Pfand return: say so once per scan, with "Anders zuordnen" (it may have been a top-up + payment).
    LaunchedEffect(record) {
        val r = record ?: return@LaunchedEffect
        val recorded = r.result as? RecordResult.Recorded ?: return@LaunchedEffect
        if (r.seq == announcedSeq || (state as? CardScanState.Success)?.seq != r.seq) return@LaunchedEffect
        val pfand = recorded.newEvents.firstOrNull { it.kind == EventKind.PFAND } ?: return@LaunchedEffect
        announcedSeq = r.seq
        val result = snackbar.showSnackbar(pfandText.format(formatCardAmount(pfand.amount, locale)), reassignText, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed && stats?.events?.any { it.id == pfand.id } == true) {
            statsVm.reassignCredit(pfand.id)
            showStats = true
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.card_title), onBack) {
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.card_settings_title))
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CardContent(state, lastBalance)
                    if (statsOn) ScanStatsNote(statsVm, state as? CardScanState.Success, onOpenStats = { showStats = true })
                }
            }
            Text(
                stringResource(if (statsOn) R.string.card_privacy_stats else R.string.card_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (statsOn) Modifier else Modifier.navigationBarsPadding())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
            if (statsOn) StatsButton(openCount, onClick = { showStats = true })
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = if (statsOn) 128.dp else 48.dp))
    }
    if (showStats && statsOn) StatsScreen(statsVm, mainVm, onRegister = onOpenSettings, onBack = { showStats = false })
}

/**
 * The way into the statistics, at the bottom in thumb reach: full width, above the navigation bar. Filled (primary) while
 * questions are open, with their count; tonal otherwise.
 */
@Composable
private fun StatsButton(openCount: Int, onClick: () -> Unit) {
    val modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
        .navigationBarsPadding()
        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        .heightIn(min = 56.dp)
    val content: @Composable RowScope.() -> Unit = {
        Icon(StatsIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            if (openCount > 0) pluralStringResource(R.plurals.stats_button_questions, openCount, openCount)
            else stringResource(R.string.stats_title),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (openCount > 0) Button(onClick = onClick, modifier = modifier, content = content)
    else FilledTonalButton(onClick = onClick, modifier = modifier, content = content)
}

/** The last known balance before a scan: muted (not primary, not bold), with which card and when, so it never reads as live. */
@Composable
private fun LastKnownBalance(last: LastBalance) {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val amount = formatCardAmount(last.balance, locale)
    val whenText = lastScanTime(last.at, locale, android.text.format.DateFormat.is24HourFormat(context))
    val line = stringResource(R.string.last_balance_when, whenText)
    val a11y = stringResource(R.string.last_balance_a11y, amount, whenText)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = a11y },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(CardIcon, contentDescription = null, modifier = Modifier.size(48.dp), tint = muted)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (last.mine) R.string.last_balance_mine else R.string.last_balance_any),
            style = MaterialTheme.typography.titleMedium,
            color = muted,
        )
        Text(amount, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Normal, color = muted)
        Text(line, style = MaterialTheme.typography.bodyLarge, color = muted)
    }
}

/** "heute 9:19", "gestern 18:02", "Mo. 23.11." (another year: "Mo. 23.11.2025"); English alike. */
@Composable
private fun lastScanTime(at: Long, locale: Locale, is24: Boolean): String {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val time = Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern(if (is24) "H:mm" else "h:mm a", locale))
    val de = locale.language == "de"
    return when (LastBalances.day(at, now, zone)) {
        LastBalances.Day.TODAY -> stringResource(R.string.last_balance_today, time)
        LastBalances.Day.YESTERDAY -> stringResource(R.string.last_balance_yesterday, time)
        LastBalances.Day.EARLIER -> {
            val date = LastBalances.date(at, zone)
            val sameYear = date.year == LastBalances.date(now, zone).year
            val pattern = when {
                de && sameYear -> "EE d.M."
                de -> "EE d.M.yyyy"
                sameYear -> "EEE d MMM"
                else -> "EEE d MMM yyyy"
            }
            date.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
    }
}

@Composable
private fun CardContent(state: CardScanState, last: LastBalance?) {
    Column(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            CardScanState.NoNfc -> Message(R.string.card_no_nfc)
            CardScanState.NfcOff -> {
                last?.let { LastKnownBalance(it) }
                Message(R.string.card_nfc_off)
                val context = LocalContext.current
                Button(onClick = { openNfcSettings(context) }) { Text(stringResource(R.string.card_nfc_settings)) }
            }
            CardScanState.Waiting -> {
                if (last != null) LastKnownBalance(last) else BigCardIcon()
                Message(R.string.card_hold)
            }
            CardScanState.Reading -> {
                CircularProgressIndicator()
                Message(R.string.card_reading)
            }
            is CardScanState.Success -> Balance(state.balance)
            CardScanState.Unsupported -> {
                BigCardIcon()
                Message(R.string.card_unsupported)
                Hint()
            }
            CardScanState.Error -> {
                BigCardIcon()
                Message(R.string.card_error)
                Hint()
            }
        }
    }
}

@Composable
private fun Balance(balance: CardBalance) {
    val locale: Locale = LocalConfiguration.current.locales[0]
    Text(stringResource(R.string.card_balance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        formatCardAmount(balance.value, locale),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    balance.lastTransaction?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.card_last_transaction, formatCardAmount(it, locale)),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Spacer(Modifier.height(8.dp))
    Hint()
}

@Composable
private fun BigCardIcon() {
    Icon(CardIcon, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Message(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
}

@Composable
private fun Hint() {
    Text(
        stringResource(R.string.card_rescan),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private fun openNfcSettings(context: android.content.Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            // no settings app to open
        }
    }
}
