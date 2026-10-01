package de.luwify.mensamd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.luwify.mensamd.R
import de.luwify.mensamd.favorites.FavoriteNotifications
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import de.luwify.mensamd.data.settings.ThemeMode
import de.luwify.mensamd.data.settings.ViewPrefs
import de.luwify.mensamd.model.Mensa
import de.luwify.mensamd.model.PageFormat
import de.luwify.mensamd.ui.theme.BackIcon
import de.luwify.mensamd.ui.theme.LocalThemeMode
import de.luwify.mensamd.ui.theme.MensaFilterChip

/** The "Benachrichtigungen" section of [SettingsScreen]: favorites notifications, which Mensen they check, a test button. */
class NotifySheet(
    val settings: NotifySettings,
    /** Mensen that can be watched (dated menus only), in tab order. */
    val mensas: List<Mensa>,
    /** False when the system permission is missing or the user blocked the app's notifications. */
    val allowed: Boolean,
    val onEnabled: (Boolean) -> Unit,
    val onTime: (LocalTime) -> Unit,
    val onWatch: (String, Boolean) -> Unit,
    val onAllow: () -> Unit,
    val onTest: () -> Unit,
)

/**
 * The settings page (gear icon in the top bar): preferences that are not filters. Price group, layout, theme and
 * favorites notifications; all global. An overlay in `MensaRoot` like the card screen, closed with back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, mensas: List<Mensa>, defaultId: String, prefs: ViewPrefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val language = LocalConfiguration.current.locales[0].language
    val access = rememberNotificationAccess(vm)
    val notifySettings by vm.notifySettings.collectAsStateWithLifecycle()
    val notifyMensas = remember(mensas) { mensas.filter { it.format == PageFormat.DATED } }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            }
            LazyColumn(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 24.dp),
            ) {
                item(key = "price-title") { SheetHeading(stringResource(R.string.price_group_title)) }
                item(key = "price-picker") {
                    // The legend's group names differ per page only in wording; our own labels keep this page page-independent.
                    val groups = listOf(R.string.price_students, R.string.price_staff, R.string.price_guests)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEachIndexed { index, label ->
                            MensaFilterChip(selected = prefs.priceGroup == index, onClick = { vm.setPriceGroup(index) }, label = { Text(stringResource(label)) })
                        }
                    }
                }

                item(key = "display-title") { SheetHeading(stringResource(R.string.display_title)) }
                item(key = "display-picker") {
                    val theme = LocalThemeMode.current
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MensaFilterChip(selected = !prefs.compact, onClick = { vm.setCompactMode(false) }, label = { Text(stringResource(R.string.display_normal)) })
                            MensaFilterChip(selected = prefs.compact, onClick = { vm.setCompactMode(true) }, label = { Text(stringResource(R.string.display_compact)) })
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MensaFilterChip(selected = theme == ThemeMode.SYSTEM, onClick = { vm.setThemeMode(ThemeMode.SYSTEM) }, label = { Text(stringResource(R.string.theme_system)) })
                            MensaFilterChip(selected = theme == ThemeMode.SOFT, onClick = { vm.setThemeMode(ThemeMode.SOFT) }, label = { Text(stringResource(R.string.theme_soft)) })
                            MensaFilterChip(selected = theme == ThemeMode.AMOLED, onClick = { vm.setThemeMode(ThemeMode.AMOLED) }, label = { Text(stringResource(R.string.theme_amoled)) })
                            MensaFilterChip(selected = theme == ThemeMode.CUSTOM, onClick = { vm.setThemeMode(ThemeMode.CUSTOM) }, label = { Text(stringResource(R.string.theme_custom)) })
                        }
                        if (theme == ThemeMode.CUSTOM) {
                            val choice by vm.theme.collectAsStateWithLifecycle()
                            choice?.let { CustomThemeEditor(it.custom, vm::setCustomTheme) }
                        }
                    }
                }

                notifySettings?.let { settings ->
                    notifySection(
                        NotifySheet(
                            settings = settings,
                            mensas = notifyMensas,
                            allowed = access.allowed,
                            onEnabled = vm::setNotificationsEnabled,
                            onTime = vm::setNotifyTime,
                            onWatch = vm::setNotifyMensa,
                            onAllow = access.request,
                            onTest = { if (access.allowed) vm.postTestNotification(defaultId) else access.request() },
                        ),
                        language,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.notifySection(notify: NotifySheet, language: String) {
    val enabled = notify.settings.enabled
    item(key = "notify-title") { SheetHeading(stringResource(R.string.notify_section_title)) }
    item(key = "notify-toggle") {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = notify.onEnabled)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.notify_toggle), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.notify_toggle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
    if (!enabled) return
    item(key = "notify-time") { NotifyTimeRow(notify.settings.time, notify.onTime) }
    if (!notify.allowed) {
        item(key = "notify-blocked") {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.notify_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                    TextButton(onClick = notify.onAllow) {
                        Text(stringResource(R.string.notify_open_settings), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
    }
    item(key = "notify-mensen-title") {
        Text(
            stringResource(R.string.notify_mensen_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        )
    }
    items(notify.mensas, key = { "notify-${it.id}" }) { mensa ->
        val watched = mensa.id in notify.settings.watched
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = watched, role = Role.Checkbox, onValueChange = { notify.onWatch(mensa.id, it) })
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = watched, onCheckedChange = null)
            Text(mensa.name.forLanguage(language), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
        }
    }
    item(key = "notify-test") {
        OutlinedButton(onClick = notify.onTest, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.notify_test))
        }
    }
}

/**
 * "Uhrzeit 18:00" with what that time announces; tap opens the time picker. Before noon the notification is about
 * the same day's menu, from noon on about the next day's (see `FavoriteNotifications.targetDate`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotifyTimeRow(time: LocalTime, onTime: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val formatted = remember(time, locale) { time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = { picking = true })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.notify_time), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    if (FavoriteNotifications.announcesToday(time)) R.string.notify_time_hint_today else R.string.notify_time_hint_tomorrow,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(formatted, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    if (picking) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    onTime(LocalTime.of(state.hour, state.minute))
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(android.R.string.cancel)) } },
            title = { Text(stringResource(R.string.notify_time)) },
            text = { TimePicker(state) },
        )
    }
}
