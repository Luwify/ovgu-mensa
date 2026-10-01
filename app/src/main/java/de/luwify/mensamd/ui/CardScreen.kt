package de.luwify.mensamd.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.card.CardBalance
import de.luwify.mensamd.card.CardScanState
import de.luwify.mensamd.card.NfcCardScanner
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.ui.theme.BackIcon
import de.luwify.mensamd.ui.theme.CardIcon
import java.util.Locale

/** The "Karte" screen: scans only while it is open and resumed. */
@Composable
fun CardScreen(scanner: NfcCardScanner, onBack: () -> Unit) {
    val activity = LocalActivity.current
    LifecycleResumeEffect(scanner, activity) {
        activity?.let(scanner::start)
        onPauseOrDispose { activity?.let(scanner::stop) }
    }
    BackHandler(onBack = onBack)
    val state by scanner.state.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(BackIcon, contentDescription = stringResource(R.string.card_back))
                }
                Text(stringResource(R.string.card_title), style = MaterialTheme.typography.titleLarge)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CardContent(state)
            }
            Text(
                stringResource(R.string.card_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
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
