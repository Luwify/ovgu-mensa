package de.luwify.mensamd

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import de.luwify.mensamd.card.CardBalance
import de.luwify.mensamd.card.CardReadResult
import de.luwify.mensamd.card.cardFingerprint
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.card.readCardFromTag
import de.luwify.mensamd.data.settings.ThemeChoice
import de.luwify.mensamd.data.settings.ThemeMode
import de.luwify.mensamd.ui.theme.MensaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.luwify.mensamd.stats.EventKind
import de.luwify.mensamd.stats.RecordResult

/**
 * Small popup shown when a card is held to the phone while the app isn't in front.
 *
 * The window is transparent and draws nothing until the card was read, so an unsupported card (a
 * bank card, say) finishes without any visible trace. Nothing is kept: the tag is consumed from
 * the intent once, no state is saved, the activity finishes as soon as it stops, and it is not in
 * recents (see the manifest). The theme comes from the app's single settings DataStore.
 */
class CardPopupActivity : ComponentActivity() {
    private sealed interface Popup {
        /** [note]: what the card statistics did with this scan (string resource), if anything worth saying. */
        data class Success(val balance: CardBalance, val note: Int? = null) : Popup
        data object Error : Popup
    }

    private var popup by mutableStateOf<Popup?>(null)
    private var theme by mutableStateOf(ThemeChoice(ThemeMode.SYSTEM))
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Re-created or reopened from history: the tag is long gone, there is nothing to show.
        if (savedInstanceState != null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            finish()
            return
        }
        setContent {
            val current = popup
            if (current != null) {
                MensaTheme(theme.mode, theme.custom, theme.glow) {
                    PopupContent(current, onOpenApp = ::openApp, onDismiss = ::finish)
                }
            }
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A new tap while the popup is open: update it, never stack a second one.
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    /** Takes the tag out of [intent] (once), reads it off the main thread and updates the popup. */
    private fun handle(intent: Intent) {
        val tag = if (intent.action == NfcAdapter.ACTION_TECH_DISCOVERED) intent.tag() else null
        intent.removeExtra(NfcAdapter.EXTRA_TAG)
        intent.removeExtra(NfcAdapter.EXTRA_ID)
        setIntent(Intent())
        if (tag == null) {
            if (popup == null) finish()
            return
        }
        job?.cancel()
        val settings = (application as MensaApp).container.settings
        job = lifecycleScope.launch {
            val mode = async { run {
                    val m = settings.themeMode.first()
                    ThemeChoice(m, settings.customTheme.first(), settings.cardGlow.first() ?: (m == ThemeMode.AMOLED))
                } }
            val result = withContext(Dispatchers.IO) { readCardFromTag(tag) }
            when (result) {
                is CardReadResult.Success -> {
                    // The statistics (if on) store the scan in the app scope; the balance doesn't wait for them, the note follows.
                    val recorder = (application as MensaApp).container.statsRecorder
                    val fingerprint = if (recorder.wantsCardId()) cardFingerprint(tag.id) else null
                    val recording = recorder
                        .recordAsync(-1, result.balance.value, result.balance.lastTransaction, fingerprint)
                    show(Popup.Success(result.balance, null), mode.await())
                    val note = when (val r = recording.await()) {
                        is RecordResult.Recorded -> when {
                            r.newQuestions.isNotEmpty() -> R.string.popup_stats_question
                            // A dated payment waits for its dish: matched (or asked) as soon as the app opens.
                            r.newEvents.any { it.kind == EventKind.PAYMENT && it.dateKnown } && recorder.detailedActive() -> R.string.popup_stats_meal
                            else -> R.string.popup_stats_saved
                        }
                        RecordResult.Unchanged -> R.string.popup_stats_saved
                        RecordResult.OtherCard -> R.string.scan_other_card
                        RecordResult.NoCard -> R.string.popup_stats_no_card
                        else -> null
                    }
                    if (note != null) popup = Popup.Success(result.balance, note)
                }
                CardReadResult.Failed -> show(Popup.Error, mode.await())
                // Not our card: leave silently. An already visible popup stays as it is.
                CardReadResult.Unsupported -> if (popup == null) finish()
            }
        }
    }

    private fun show(content: Popup, mode: ThemeChoice) {
        theme = mode // set together with the content, so the first drawn frame already has the right theme
        popup = content
    }

    /** Opens the app; with a question waiting (or a dish to choose) straight in the card statistics. */
    private fun openApp() {
        val answer = (popup as? Popup.Success)?.note.let { it == R.string.popup_stats_question || it == R.string.popup_stats_meal }
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_OPEN_STATS, answer),
        )
        finish()
    }

    @Suppress("DEPRECATION")
    private fun Intent.tag(): Tag? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        else getParcelableExtra(NfcAdapter.EXTRA_TAG)

    @Composable
    private fun PopupContent(content: Popup, onOpenApp: () -> Unit, onDismiss: () -> Unit) {
        // Tap outside the card dismisses; no ripple, it's a scrim.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 360.dp)
                    .pointerInput(Unit) { detectTapGestures { } }, // taps on the card are not "outside"
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (content) {
                        is Popup.Success -> {
                            val locale = LocalConfiguration.current.locales[0]
                            Text(stringResource(R.string.card_balance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatCardAmount(content.balance.value, locale),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            content.balance.lastTransaction?.let {
                                Text(stringResource(R.string.card_last_transaction, formatCardAmount(it, locale)), style = MaterialTheme.typography.titleMedium)
                            }
                            content.note?.let {
                                Text(
                                    stringResource(it),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        Popup.Error -> Text(stringResource(R.string.card_popup_error), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                    val answer = (content as? Popup.Success)?.note.let { it == R.string.popup_stats_question || it == R.string.popup_stats_meal }
                    Button(onClick = onOpenApp, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                        Text(stringResource(if (answer) R.string.card_popup_answer else R.string.card_popup_open))
                    }
                }
            }
        }
    }
}
