package de.luwify.mensamd.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.luwify.mensamd.R
import de.luwify.mensamd.ui.MainViewModel

/** The card statistics (chart button on the card screen). Filled in phase 2. */
@Composable
fun StatsScreen(vm: StatsViewModel, mainVm: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.stats_title), onBack)
        }
    }
}
