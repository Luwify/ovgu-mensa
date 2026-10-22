package de.luwify.mensamd.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.data.update.Updates
import kotlinx.coroutines.flow.filterNotNull

/**
 * The updater's prompts above every screen (menu, settings, card screen): the one-time snackbar for a new version
 * ("Anzeigen" opens [UpdateDialog]), and the dialogs of a running update ([UpdateDialogs]). In `MensaRoot`; the
 * Stundenplan MD counterpart sits in its MainScreen.
 */
@Composable
fun BoxScope.UpdatePrompts(updates: Updates) {
    val update by updates.state.collectAsStateWithLifecycle()
    val download by updates.download.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val res = LocalResources.current
    var updateDialog by rememberSaveable { mutableStateOf<Int?>(null) }

    // A check found a newer version: one snackbar per version, "Anzeigen" opens the update dialog.
    // (Collected, not keyed on the value: clearing it must not cancel the snackbar.)
    LaunchedEffect(updates) {
        updates.announcement.filterNotNull().collect { release ->
            updates.announcement.value = null
            val r = snackbar.showSnackbar(
                res.getString(R.string.update_snackbar, release.versionName),
                actionLabel = res.getString(R.string.update_show),
                duration = SnackbarDuration.Long,
            )
            if (r == SnackbarResult.ActionPerformed) updateDialog = release.versionCode
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())

    update.manifest?.releases?.firstOrNull { it.versionCode == updateDialog }?.let { release ->
        UpdateDialog(
            update.installed.name, release,
            onInstall = { updateDialog = null; updates.install(release) },
            onSkip = { dontShow -> updateDialog = null; if (dontShow) updates.skip(release) },
            onDismiss = { updateDialog = null },
        )
    }
    UpdateDialogs(updates, download)
}
