package de.luwify.mensamd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
            MensaTheme { MensaRoot(viewModel) }
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
