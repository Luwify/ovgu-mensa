package de.luwify.mensamd.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R

/** Whether favorites notifications can be shown, and how to ask for that. */
class NotificationAccess(
    val allowed: Boolean,
    /** Android 13+ before the first answer: the runtime permission was never granted. */
    val needsRuntimePermission: Boolean,
    /** The system dialog while it can still appear, otherwise the app's notification settings. */
    val request: () -> Unit,
)

@Composable
fun rememberNotificationAccess(vm: MainViewModel): NotificationAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val requested by vm.permissionRequested.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(vm.canPostNotifications()) }
    // The user may change it in the system settings while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = vm.canPostNotifications() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = vm.canPostNotifications() }
    val runtimeMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    return NotificationAccess(allowed, runtimeMissing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeMissing &&
            (requested == false || activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true)
        ) {
            vm.markPermissionRequested()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Denied for good, or blocked in the system settings: only the settings can change that.
            openNotificationSettings(context)
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** Shown once, when the first favorite is set: why the app wants to notify, before the system asks. */
@Composable
fun NotificationPermissionDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.notify_permission_title)) },
        text = { Text(stringResource(R.string.notify_permission_text)) },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.notify_permission_allow)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.notify_permission_later)) } },
    )
}
