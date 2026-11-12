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
import de.luwify.mensamd.MensaApp
import de.luwify.mensamd.stats.EventKind
import de.luwify.mensamd.stats.RecordResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import de.luwify.mensamd.ui.stats.CardSettingsScreen
import de.luwify.mensamd.ui.stats.ScanStatsNote
import de.luwify.mensamd.ui.stats.StatsScreen
import de.luwify.mensamd.ui.stats.StatsViewModel
import de.luwify.mensamd.ui.stats.TopBar
import de.luwify.mensamd.ui.theme.StatsIcon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.luwify.mensamd.ui.theme.CardIcon
import java.util.Locale

/**
 * The "Karte" screen. The scanner runs while the activity is resumed (see `MainActivity`); it opens this screen on a
 * scan. The gear opens the card settings, the chart button (statistics on) the statistics; both are overlays here.
 */
@Composable
fun CardScreen(scanner: NfcCardScanner, mainVm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val statsVm: StatsViewModel = viewModel(factory = viewModelFactory {
        initializer { StatsViewModel((context.applicationContext as MensaApp).container) }
    })
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !showSettings && !showStats, onBack = onBack)
    val openStats by mainVm.openStats.collectAsStateWithLifecycle()
    LaunchedEffect(openStats) {
        if (openStats) {
            showSettings = false
            showStats = true
            mainVm.consumeOpenStats()
        }
    }
    val state by scanner.state.collectAsStateWithLifecycle()
    val settings by statsVm.settings.collectAsStateWithLifecycle()
    val statsOn = settings?.enabled == true
    val access = rememberNotificationAccess(mainVm)
    val snackbar = remember { SnackbarHostState() }
    val record by statsVm.lastRecord.collectAsStateWithLifecycle()
    val stats by statsVm.data.collectAsStateWithLifecycle()
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
                if (statsOn) {
                    IconButton(onClick = { showStats = true }) { Icon(StatsIcon, stringResource(R.string.stats_title)) }
                }
                IconButton(onClick = { showSettings = true }) {
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
                    CardContent(state)
                    if (statsOn) ScanStatsNote(statsVm, state as? CardScanState.Success, onOpenStats = { showStats = true })
                }
            }
            Text(
                stringResource(if (statsOn) R.string.card_privacy_stats else R.string.card_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = 48.dp))
    }
    if (showSettings) CardSettingsScreen(statsVm, access, onBack = { showSettings = false })
    if (showStats && statsOn) StatsScreen(statsVm, mainVm, onBack = { showStats = false })
}

@Composable
private fun CardContent(state: CardScanState) {
    Column(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            CardScanState.NoNfc -> Message(R.string.card_no_nfc)
            CardScanState.NfcOff -> {
                Message(R.string.card_nfc_off)
                val context = LocalContext.current
                Button(onClick = { openNfcSettings(context) }) { Text(stringResource(R.string.card_nfc_settings)) }
            }
            CardScanState.Waiting -> {
                BigCardIcon()
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
