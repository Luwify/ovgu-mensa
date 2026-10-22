package de.luwify.mensamd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.favorites.FavoriteEntry
import de.luwify.mensamd.favorites.NextServing
import de.luwify.mensamd.model.Mensa
import de.luwify.mensamd.ui.theme.BackIcon
import de.luwify.mensamd.ui.theme.LocalMensaColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Favoriten" (heart in the top bar): every favorite with where and when it is next on the menu, from the menus the
 * app already has (never fetches). Soonest first, then the rest alphabetically. A tap opens that Mensa and day; the
 * heart or a swipe removes a favorite, with undo in a snackbar. An overlay in `MensaRoot`, closed with back.
 */
@Composable
fun FavoritesScreen(vm: MainViewModel, mensas: List<Mensa>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val locale = LocalConfiguration.current.locales[0]
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { today = LocalDate.now() }
    val ids = remember(mensas) { mensas.map { it.id } }
    val flow = remember(ids, today, locale.language) { vm.favoriteOverview(ids, today, locale.language) }
    val entries by flow.collectAsStateWithLifecycle(initialValue = null)
    val names = remember(mensas, locale.language) { mensas.associate { it.id to it.name.forLanguage(locale.language) } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val res = LocalResources.current

    val remove: (FavoriteEntry) -> Unit = { entry ->
        vm.removeFavorite(entry)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                res.getString(R.string.favorites_removed, entry.name.forLanguage(locale.language)),
                actionLabel = res.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.restoreFavorite(entry)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(BackIcon, contentDescription = stringResource(R.string.back)) }
                    Text(stringResource(R.string.favorites_title), style = MaterialTheme.typography.titleLarge)
                }
                val list = entries
                when {
                    list == null -> Unit // a few ms while the caches are read
                    list.isEmpty() -> Text(
                        stringResource(R.string.favorites_overview_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(list, key = { it.key }) { entry ->
                            FavoriteRow(
                                entry, locale, today, names,
                                onOpen = { next ->
                                    onBack()
                                    vm.requestOpen(next.mensaId, next.date)
                                },
                                onRemove = { remove(entry) },
                            )
                        }
                        item(key = "note", contentType = "note") {
                            Text(
                                stringResource(R.string.favorites_overview_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

@Composable
private fun FavoriteRow(
    entry: FavoriteEntry,
    locale: Locale,
    today: LocalDate,
    mensaNames: Map<String, String>,
    onOpen: (NextServing) -> Unit,
    onRemove: () -> Unit,
) {
    val dismiss = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) {
            onRemove()
            dismiss.snapTo(SwipeToDismissBoxValue.Settled) // the row leaves with the data; an undo brings it back settled
        }
    }
    SwipeToDismissBox(dismiss, backgroundContent = {}) {
        val theme = LocalMensaColors.current
        val colors = theme.cardContainer?.let { CardDefaults.cardColors(containerColor = it) } ?: CardDefaults.cardColors()
        val border = theme.cardBorder?.let { BorderStroke(theme.cardBorderWidth, it) }
        val next = entry.next
        val content: @Composable () -> Unit = {
            Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.name.forLanguage(locale.language), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (next == null) stringResource(R.string.favorites_not_on_menu) else whenAndWhere(next, today, locale, mensaNames),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (next != null) FontWeight.SemiBold else null,
                        color = if (next != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            entry.tags.forEach { TagLabel(it) }
                        }
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        painterResource(R.drawable.ic_favorite_filled),
                        contentDescription = stringResource(R.string.favorite_remove),
                        tint = theme.favorite,
                    )
                }
            }
        }
        if (next != null) Card(onClick = { onOpen(next) }, colors = colors, border = border) { content() }
        else Card(colors = colors, border = border) { content() }
    }
}

/** "Heute · UniCampus unten", "Do 23.10. · Herrenkrug", "Festes Angebot · PIER 16". */
@Composable
private fun whenAndWhere(next: NextServing, today: LocalDate, locale: Locale, mensaNames: Map<String, String>): String {
    val day = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val date = remember(locale) { DateTimeFormatter.ofPattern("d.M.", locale) }
    val whenText = when (next.date) {
        null -> stringResource(R.string.fixed_offer)
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> "${next.date.format(day).removeSuffix(".")} ${next.date.format(date)}"
    }
    return "$whenText · ${mensaNames[next.mensaId] ?: next.mensaId}"
}
