package de.luwify.mensamd.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.Tag
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(vm: MainViewModel, screen: AppScreen.Menu) {
    val menu by vm.menu.collectAsStateWithLifecycle()
    // Ignore a state that still belongs to the previous Mensa after a switch.
    val ui = menu?.takeIf { it.mensaId == screen.mensa.id }?.ui
    val today = rememberToday()

    Scaffold(
        topBar = {
            TopAppBar(title = { MensaSwitcher(screen, vm::switchTo, vm::makeCurrentTheDefault) })
        },
        // Status bar is handled by the top bar; the bottom inset is applied as list padding so
        // cards scroll under the translucent navigation bar but never rest behind it.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (ui != null) MenuBody(ui, today, screen.mensa.id, vm::refresh)
            if (ui?.isRefreshing == true) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { today = LocalDate.now() } // app left open past midnight
    return today
}

@Composable
private fun MensaSwitcher(screen: AppScreen.Menu, onSwitch: (String) -> Unit, onMakeDefault: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.switch_mensa)) { expanded = true },
        ) {
            Text(screen.mensa.name, maxLines = 1)
            Text(" ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            de.luwify.mensamd.model.MensaCatalog.all.forEach { mensa ->
                DropdownMenuItem(
                    text = { Text(mensa.name) },
                    trailingIcon = if (mensa.id == screen.mensa.id) {
                        { Text("✓") }
                    } else null,
                    onClick = {
                        expanded = false
                        onSwitch(mensa.id)
                    },
                )
            }
            if (!screen.isDefault) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.set_as_default)) },
                    onClick = {
                        expanded = false
                        onMakeDefault()
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuBody(ui: MenuUiState, today: LocalDate, mensaId: String, onRetry: () -> Unit) {
    val days = remember(ui.days, today) { ui.days.upcomingWeekdays(today) }
    when {
        days.isNotEmpty() -> DayPager(ui, days, today, mensaId)
        !ui.hasData && ui.refreshFailed -> Message(stringResource(R.string.error_no_data), onRetry)
        ui.hasData && !ui.isRefreshing -> Message(stringResource(R.string.empty_no_upcoming), null)
        // else: nothing cached yet and the first fetch is running. The progress bar says so; no spinner screen.
    }
}

@Composable
private fun Message(text: String, onRetry: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun DayPager(ui: MenuUiState, days: List<MenuDay>, today: LocalDate, mensaId: String) {
    val locale = LocalConfiguration.current.locales[0]
    val tabFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE d.M.", locale) }
    val scope = rememberCoroutineScope()

    // Remember the day by date, not index, so a refresh that adds or removes days doesn't jump.
    var selected by rememberSaveable(mensaId) { mutableStateOf<String?>(null) }
    val daysNow by rememberUpdatedState(days)
    val pager = rememberPagerState(initialPage = days.indexOfFirst { it.date.toString() == selected }.coerceAtLeast(0)) {
        daysNow.size
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            daysNow.getOrNull(page)?.let { selected = it.date.toString() }
        }
    }
    LaunchedEffect(days) {
        val index = days.indexOfFirst { it.date.toString() == selected }.coerceAtLeast(0)
        if (index != pager.currentPage) pager.scrollToPage(index)
    }

    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage.coerceIn(0, days.lastIndex), edgePadding = 8.dp) {
            days.forEachIndexed { index, day ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = {
                        Text(if (day.date == today) stringResource(R.string.today) else day.date.format(tabFormat), maxLines = 1)
                    },
                )
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { daysNow.getOrNull(it)?.date?.toString() ?: it.toString() },
        ) { page ->
            daysNow.getOrNull(page)?.let { DayList(it, ui) }
        }
    }
}

@Composable
private fun DayList(day: MenuDay, ui: MenuUiState) {
    val locale = LocalConfiguration.current.locales[0]
    val euro = remember(locale) { NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("EUR") } }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(day.dishes) { dish -> DishCard(dish, ui.legend, locale.language, euro) }
        if (ui.lastUpdatedMillis != null) item { UpdatedFooter(ui.lastUpdatedMillis, ui.refreshFailed) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishCard(dish: Dish, legend: Legend, language: String, euro: NumberFormat) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(dish.name.forLanguage(language), style = MaterialTheme.typography.titleMedium)
            if (dish.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dish.tags.forEach { TagLabel(it) }
                }
            }
            if (dish.prices.isNotEmpty()) {
                val fallback = listOf(R.string.price_students, R.string.price_staff, R.string.price_guests)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    dish.prices.forEachIndexed { index, cents ->
                        val label = legend.priceGroups.getOrNull(index)?.forLanguage(language)
                            ?: fallback.getOrNull(index)?.let { stringResource(it) }.orEmpty()
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(euro.format(cents / 100.0), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagLabel(tag: Tag) {
    val label = tagLabelRes(tag.key)?.let { stringResource(it) } ?: tag.label
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun UpdatedFooter(updatedAt: Long, failed: Boolean) {
    val relative = DateUtils.getRelativeTimeSpanString(updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val text = stringResource(R.string.last_updated, relative) + if (failed) " · " + stringResource(R.string.update_failed) else ""
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
