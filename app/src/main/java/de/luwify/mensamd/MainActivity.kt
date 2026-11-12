package de.luwify.mensamd

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.luwify.mensamd.card.CardScanState
import de.luwify.mensamd.card.NfcCardScanner
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import de.luwify.mensamd.data.settings.ColorRole
import de.luwify.mensamd.data.settings.CustomBase
import de.luwify.mensamd.ui.theme.CustomTheme
import de.luwify.mensamd.data.settings.ThemeMode
import de.luwify.mensamd.notify.FavoriteNotifier
import de.luwify.mensamd.notify.CardReminderNotifier
import de.luwify.mensamd.ui.CardScreen
import de.luwify.mensamd.ui.AppScreen
import de.luwify.mensamd.ui.MainViewModel
import de.luwify.mensamd.ui.MainScreen
import de.luwify.mensamd.ui.MensaPickerScreen
import de.luwify.mensamd.ui.SettingsNav
import de.luwify.mensamd.ui.SettingsScreen
import de.luwify.mensamd.ui.SettingsTab
import de.luwify.mensamd.stats.StatsRecorder
import de.luwify.mensamd.ui.UpdatePrompts
import de.luwify.mensamd.ui.theme.MensaTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel((application as MensaApp).container) } }
    }

    private val cardScanner by lazy {
        val recorder = (application as MensaApp).container.statsRecorder
        // Runs on the NFC thread, which may block.
        NfcCardScanner(NfcAdapter.getDefaultAdapter(this), wantsCardId = { runBlocking { recorder.wantsCardId() } }).also { scanner ->
            scanner.onRead = { s -> recorder.recordAsync(s.seq, s.balance.value, s.balance.lastTransaction, s.fingerprint, matchNow = true) }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not after a recreation: that intent was handled already.
        if (savedInstanceState == null) handleOpenIntent(intent)
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            // AMOLED is always dark: black bars with light icons whatever the system setting is. A custom theme
            // with a fixed base gets matching bar icons; everything else follows the system (auto).
            val custom = theme?.takeIf { it.mode == ThemeMode.CUSTOM }?.custom
            val systemDark = isSystemInDarkTheme()
            // Fixed when the custom theme doesn't simply follow the system (a set base or a hand-picked background).
            val customDark = custom?.takeIf { it.base != CustomBase.SYSTEM || ColorRole.BACKGROUND in it.overrides }
                ?.let { CustomTheme.isDark(it, systemDark) }
            val blackBars = theme?.mode == ThemeMode.AMOLED ||
                (custom?.base == CustomBase.BLACK && ColorRole.BACKGROUND !in custom.overrides)
            DisposableEffect(theme?.mode, blackBars, customDark) {
                when {
                    blackBars -> enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.BLACK),
                        navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
                    )
                    customDark == true -> enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    )
                    customDark == false -> enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    )
                    else -> enableEdgeToEdge()
                }
                onDispose { }
            }
            // Nothing until the stored theme is known (a few ms), so the wrong theme never flashes.
            theme?.let { MensaTheme(it.mode, it.custom, it.glow) { MensaRoot(viewModel, cardScanner, (application as MensaApp).container.statsRecorder) } }
        }
    }

    // Reader mode while the app is in the foreground: a held card opens the card screen instead of the popup.
    override fun onStart() {
        super.onStart()
        (application as MensaApp).mainUiStarted()
    }

    override fun onResume() {
        super.onResume()
        cardScanner.start(this)
        // Back from the "install unknown apps" setting: the updater goes on if it was granted.
        (application as MensaApp).container.updates.onResume()
        // The Stundenplan MD banner: installed or removed meanwhile.
        (application as MensaApp).container.companion.onResume()
    }

    override fun onPause() {
        cardScanner.stop(this)
        super.onPause()
    }

    /** A tap on the favorites notification (activity already running). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        intent?.getStringExtra(FavoriteNotifier.EXTRA_MENSA_ID)?.let { viewModel.requestOpen(it) }
        if (intent?.getBooleanExtra(CardReminderNotifier.EXTRA_OPEN_CARD, false) == true) viewModel.requestOpenCard()
        if (intent?.getBooleanExtra(EXTRA_OPEN_STATS, false) == true) viewModel.requestOpenCard(stats = true)
        if (intent?.getBooleanExtra(EXTRA_OPEN_CARD_SETTINGS, false) == true) viewModel.requestOpenSettings(SettingsTab.CARD)
    }

    companion object {
        /** From the card popup with a question waiting: the card statistics open (on top of the card screen). */
        const val EXTRA_OPEN_STATS = "de.luwify.mensamd.extra.OPEN_STATS"

        /** From the card popup with no card registered: the settings open on their card tab ("Karte registrieren"). */
        const val EXTRA_OPEN_CARD_SETTINGS = "de.luwify.mensamd.extra.OPEN_CARD_SETTINGS"
    }
}

@Composable
private fun MensaRoot(viewModel: MainViewModel, cardScanner: NfcCardScanner, recorder: StatsRecorder) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    when (val s = screen) {
        // A few ms while DataStore answers: an empty themed background, not a spinner.
        AppScreen.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        AppScreen.Picker -> MensaPickerScreen(onPick = viewModel::pickDefault)
        is AppScreen.Main -> {
            // Overlays, not replacements: the pager keeps its tab while the card screen or the settings are open.
            // The settings lie above the card screen (its gear opens them on the card tab).
            var showCard by rememberSaveable { mutableStateOf(false) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var settingsTab by rememberSaveable { mutableStateOf(SettingsTab.GENERAL) }
            fun openSettings(tab: SettingsTab) {
                settingsTab = tab
                showSettings = true
            }
            // A notification tap goes to the menu, also from the card screen.
            val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
            LaunchedEffect(openRequest) {
                if (openRequest != null) {
                    showCard = false
                    showSettings = false
                }
            }
            val openCard by viewModel.openCard.collectAsStateWithLifecycle()
            LaunchedEffect(openCard) {
                if (openCard) {
                    showSettings = false
                    showCard = true
                    viewModel.consumeOpenCard()
                }
            }
            val openSettingsRequest by viewModel.openSettings.collectAsStateWithLifecycle()
            LaunchedEffect(openSettingsRequest) {
                openSettingsRequest?.let { tab ->
                    openSettings(tab)
                    viewModel.consumeOpenSettings()
                }
            }
            val scan by cardScanner.state.collectAsStateWithLifecycle()
            val registering by recorder.registering.collectAsStateWithLifecycle()
            LaunchedEffect(scan) {
                if (scan == CardScanState.Reading || scan == CardScanState.Unsupported) {
                    val route = SettingsNav.onScan(showCard, showSettings, registering)
                    showCard = route.showCard
                    showSettings = route.showSettings
                }
            }
            Box(Modifier.fillMaxSize()) {
                MainScreen(
                    viewModel, s, showCardIcon = cardScanner.available,
                    onOpenCard = { showCard = true }, onOpenSettings = { openSettings(SettingsTab.GENERAL) },
                )
                if (showCard) CardScreen(cardScanner, viewModel, onOpenSettings = { openSettings(SettingsTab.CARD) }, onBack = {
                    showCard = false
                    cardScanner.reset()
                })
                if (showSettings) {
                    SettingsScreen(
                        viewModel, s.mensas, s.defaultId, s.prefs,
                        initialTab = settingsTab, cardAvailable = cardScanner.available,
                        onBack = { showSettings = false },
                    )
                }
                UpdatePrompts(viewModel.updates)
            }
        }
    }
}
