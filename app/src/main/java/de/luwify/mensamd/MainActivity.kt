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
import de.luwify.mensamd.ui.CardScreen
import de.luwify.mensamd.ui.AppScreen
import de.luwify.mensamd.ui.MainViewModel
import de.luwify.mensamd.ui.MainScreen
import de.luwify.mensamd.ui.MensaPickerScreen
import de.luwify.mensamd.ui.SettingsScreen
import de.luwify.mensamd.ui.theme.MensaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel((application as MensaApp).container) } }
    }

    private val cardScanner by lazy { NfcCardScanner(NfcAdapter.getDefaultAdapter(this)) }

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
            theme?.let { MensaTheme(it.mode, it.custom, it.glow) { MensaRoot(viewModel, cardScanner) } }
        }
    }

    // Reader mode while the app is in the foreground: a held card opens the card screen instead of the popup.
    override fun onResume() {
        super.onResume()
        cardScanner.start(this)
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
        intent?.getStringExtra(FavoriteNotifier.EXTRA_MENSA_ID)?.let(viewModel::requestOpen)
    }
}

@Composable
private fun MensaRoot(viewModel: MainViewModel, cardScanner: NfcCardScanner) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    when (val s = screen) {
        // A few ms while DataStore answers: an empty themed background, not a spinner.
        AppScreen.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        AppScreen.Picker -> MensaPickerScreen(onPick = viewModel::pickDefault)
        is AppScreen.Main -> {
            // Overlay, not a replacement: the pager keeps its tab while the card screen is open.
            var showCard by rememberSaveable { mutableStateOf(false) }
            // A notification tap goes to the menu, also from the card screen.
            val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
            var showSettings by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(openRequest) {
                if (openRequest != null) {
                    showCard = false
                    showSettings = false
                }
            }
            val scan by cardScanner.state.collectAsStateWithLifecycle()
            LaunchedEffect(scan) {
                if (scan == CardScanState.Reading || scan == CardScanState.Unsupported) showCard = true
            }
            Box(Modifier.fillMaxSize()) {
                MainScreen(
                    viewModel, s, showCardIcon = cardScanner.available,
                    onOpenCard = { showCard = true }, onOpenSettings = { showSettings = true },
                )
                if (showSettings) SettingsScreen(viewModel, s.mensas, s.defaultId, s.prefs, onBack = { showSettings = false })
                if (showCard) CardScreen(cardScanner, onBack = {
                    showCard = false
                    cardScanner.reset()
                })
            }
        }
    }
}
