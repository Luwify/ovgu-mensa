package de.luwify.mensamd

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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.luwify.mensamd.data.settings.ThemeMode
import de.luwify.mensamd.ui.AppScreen
import de.luwify.mensamd.ui.MainViewModel
import de.luwify.mensamd.ui.MainScreen
import de.luwify.mensamd.ui.MensaPickerScreen
import de.luwify.mensamd.ui.theme.MensaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel((application as MensaApp).container) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val mode by viewModel.themeMode.collectAsStateWithLifecycle()
            // AMOLED is always dark: black bars with light icons whatever the system setting is;
            // the other themes follow the system (auto) again.
            DisposableEffect(mode) {
                if (mode == ThemeMode.AMOLED) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.BLACK),
                        navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
                    )
                } else {
                    enableEdgeToEdge()
                }
                onDispose { }
            }
            // Nothing until the stored theme is known (a few ms), so the wrong theme never flashes.
            mode?.let { MensaTheme(it) { MensaRoot(viewModel) } }
        }
    }
}

@Composable
private fun MensaRoot(viewModel: MainViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    when (val s = screen) {
        // A few ms while DataStore answers: an empty themed background, not a spinner.
        AppScreen.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        AppScreen.Picker -> MensaPickerScreen(onPick = viewModel::pickDefault)
        is AppScreen.Main -> MainScreen(viewModel, s)
    }
}
