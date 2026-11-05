package de.luwify.mensamd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.heightIn
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
import de.luwify.mensamd.AppLanguage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import de.luwify.mensamd.backup.BackupFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.luwify.mensamd.R
import de.luwify.mensamd.update.CompanionPolicy
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
 * The settings page (gear icon in the top bar): preferences that are not filters. Price group, layout, theme,
 * favorites notifications, version (updater) and about; all global. An overlay in `MensaRoot` like the card screen, closed with back.
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
    var showColors by rememberSaveable { mutableStateOf(false) }
    val theme by vm.theme.collectAsStateWithLifecycle()
    val updates = vm.updates
    val update by updates.state.collectAsStateWithLifecycle()
    val download by updates.download.collectAsStateWithLifecycle()
    var showVersions by rememberSaveable { mutableStateOf(false) }
    var openVersion by rememberSaveable { mutableStateOf<Int?>(null) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    val bannerChoice by vm.stundenplanBannerHidden.collectAsStateWithLifecycle()
    val stundenplanInstalled by vm.companion.installed.collectAsStateWithLifecycle()
    val bannerHidden = !CompanionPolicy.wanted(bannerChoice, stundenplanInstalled)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readBackup) }
    val snackbar = remember { SnackbarHostState() }
    val pendingBackup by vm.pendingBackup.collectAsStateWithLifecycle()
    val texts = BackupTexts(
        exported = stringResource(R.string.backup_exported),
        failed = stringResource(R.string.backup_failed),
        notABackup = stringResource(R.string.backup_not_a_backup),
        tooNew = stringResource(R.string.backup_too_new),
        readOnly = stringResource(R.string.backup_stats_read_only),
        restored = stringResource(R.string.backup_restored),
        undone = stringResource(R.string.backup_undone),
        undo = stringResource(R.string.stats_undo),
    )
    LaunchedEffect(vm) {
        vm.backupMessages.collect { m ->
            when (m) {
                is BackupMessage.Restored -> {
                    val r = snackbar.showSnackbar(texts.restored, texts.undo, duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) vm.undoBackup(m.previous)
                }
                BackupMessage.Exported -> snackbar.showSnackbar(texts.exported)
                BackupMessage.Failed -> snackbar.showSnackbar(texts.failed)
                BackupMessage.NotABackup -> snackbar.showSnackbar(texts.notABackup)
                BackupMessage.TooNew -> snackbar.showSnackbar(texts.tooNew)
                BackupMessage.StatsReadOnly -> snackbar.showSnackbar(texts.readOnly)
                BackupMessage.Undone -> snackbar.showSnackbar(texts.undone)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                    // Stundenplan MD first, but only while it isn't installed (unless the user chose otherwise).
                    item(key = "stundenplan-banner") {
                        StundenplanBanner(vm.companion, bannerHidden, onHide = { vm.setStundenplanBannerHidden(true) }, modifier = Modifier.padding(top = 8.dp))
                    }

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

                    item(key = "layout-title") { SheetHeading(stringResource(R.string.layout_title)) }
                    item(key = "layout-picker") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MensaFilterChip(selected = !prefs.compact, onClick = { vm.setCompactMode(false) }, label = { Text(stringResource(R.string.display_normal)) })
                            MensaFilterChip(selected = prefs.compact, onClick = { vm.setCompactMode(true) }, label = { Text(stringResource(R.string.display_compact)) })
                        }
                    }
                    item(key = "legendary") {
                        SwitchRow(stringResource(R.string.legendary_title), stringResource(R.string.legendary_hint), prefs.legendary, vm::setLegendaryFavorites)
                    }

                    item(key = "language-title") { SheetHeading(stringResource(R.string.language_title)) }
                    item(key = "language-picker") {
                        val context = LocalContext.current
                        var chosen by remember { mutableStateOf(AppLanguage.current(context)) }
                        SettingsDropdown(
                            label = stringResource(R.string.language_label),
                            options = listOf(
                                AppLanguage.SYSTEM to stringResource(R.string.language_system),
                                AppLanguage.GERMAN to "Deutsch",
                                AppLanguage.ENGLISH to "English",
                            ),
                            selected = chosen,
                            onSelect = {
                                chosen = it
                                AppLanguage.set(context, it)
                                // From API 33 the system recreates the activity itself.
                                if (android.os.Build.VERSION.SDK_INT < 33) (context as? android.app.Activity)?.recreate()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    item(key = "theme-title") { SheetHeading(stringResource(R.string.theme_title)) }
                    item(key = "theme-picker") {
                        val theme = LocalThemeMode.current
                        Column {
                            SettingsDropdown(
                                label = stringResource(R.string.theme_label),
                                options = listOf(
                                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                                    ThemeMode.SOFT to stringResource(R.string.theme_soft),
                                    ThemeMode.AMOLED to stringResource(R.string.theme_amoled),
                                    ThemeMode.CUSTOM to stringResource(R.string.theme_custom),
                                ),
                                selected = theme,
                                onSelect = vm::setThemeMode,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val choice by vm.theme.collectAsStateWithLifecycle()
                            if (theme == ThemeMode.CUSTOM) {
                                choice?.let { CustomThemeEditor(it.custom, vm::setCustomTheme, onAdvanced = { showColors = true }) }
                            }
                            SwitchRow(stringResource(R.string.custom_glow), stringResource(R.string.custom_glow_hint), choice?.glow == true, vm::setCardGlow)
                        }
                    }

                    item(key = "favorites-title") { SheetHeading(stringResource(R.string.favorites_title)) }
                    item(key = "favorites-manage") { ChevronRow(stringResource(R.string.favorites_manage)) { showFavorites = true } }

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

                    versionSection(
                        update, download,
                        onOverview = { showVersions = true },
                        onOpen = { openVersion = it.versionCode },
                        onCheck = updates::checkNow,
                        onAutoCheck = updates::setAutoCheck,
                    )

                    // The switch for the Stundenplan MD banner at the top (on by default only while it isn't installed).
                    item(key = "stundenplan-banner-setting") {
                        SwitchRow(stringResource(R.string.stundenplan_setting), stringResource(R.string.stundenplan_setting_hint), !bannerHidden) {
                            vm.setStundenplanBannerHidden(!it)
                        }
                    }

                    item(key = "backup-title") { SheetHeading(stringResource(R.string.backup_title)) }
                    item(key = "backup") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { exportLauncher.launch(BackupFormat.fileName(java.time.LocalDate.now())) }) { Text(stringResource(R.string.backup_export)) }
                                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                                    Text(stringResource(R.string.backup_import))
                                }
                            }
                        }
                    }

                    item(key = "about-title") { SheetHeading(stringResource(R.string.about_title)) }
                    item(key = "about-licenses") { ChevronRow(stringResource(R.string.about_licenses)) { showLicenses = true } }
                }
            }
        }
        // The advanced color editor on top; only while the custom theme is active (it edits that theme live).
        if (showColors && theme?.mode == ThemeMode.CUSTOM) {
            theme?.let { ColorEditorScreen(it.custom, onChange = { spec -> vm.setCustomTheme(spec) }, onBack = { showColors = false }) }
        }
        if (showVersions) VersionOverviewScreen(update, onOpen = { openVersion = it.versionCode }, onBack = { showVersions = false })
        update.manifest?.releases?.firstOrNull { it.versionCode == openVersion }?.let { release ->
            VersionDetailScreen(release, updates, onBack = { openVersion = null })
        }
        if (showLicenses) LicensesScreen(update.installed.name, onBack = { showLicenses = false })
        // A tap on a favorite opens the menu: requestOpen closes the settings too (MensaRoot).
        if (showFavorites) FavoritesScreen(vm, mensas, onBack = { showFavorites = false })
        StundenplanDialogs(vm.companion)
        pendingBackup?.let { BackupConfirmDialog(it, onConfirm = vm::confirmBackup, onCancel = vm::cancelBackup) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

private class BackupTexts(
    val exported: String,
    val failed: String,
    val notABackup: String,
    val tooNew: String,
    val readOnly: String,
    val restored: String,
    val undone: String,
    val undo: String,
)

/** Says what a backup import replaces (now -> backup) and asks before doing it. */
@Composable
private fun BackupConfirmDialog(pending: PendingBackup, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val made = pending.incoming.exportedAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
        ?.atZone(java.time.ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    @Composable
    fun line(s: BackupSummary) = listOf(
        pluralStringResource(R.plurals.backup_favorites, s.favorites, s.favorites),
        pluralStringResource(R.plurals.backup_scans, s.scans, s.scans),
        pluralStringResource(R.plurals.backup_entries, s.events, s.events),
        stringResource(if (s.cardRegistered) R.string.backup_card_yes else R.string.backup_card_no),
    ).joinToString(", ")
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = {
            Text(
                stringResource(R.string.backup_confirm_text, line(pending.now), line(pending.incoming)) +
                    (made?.let { "\n\n" + stringResource(R.string.backup_made_at, it) } ?: ""),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_replace)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** A row that opens a page: label and a chevron. */
@Composable
internal fun ChevronRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** A setting with a title, an explanation and a switch; the whole row toggles. */
@Composable
internal fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    SwitchRow(title, hint, checked, enabled = true, onChange = onChange)

/** [SwitchRow] that can be greyed out ([enabled] false: shown, not toggleable). */
@Composable
internal fun SwitchRow(title: String, hint: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(top = 12.dp, bottom = 4.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
