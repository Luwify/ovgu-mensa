package de.luwify.mensamd.ui

import androidx.compose.foundation.layout.Box
import de.luwify.mensamd.data.settings.ViewPrefs
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Tab row of Mensen on top, one horizontally swipeable page per Mensa below. */
@Composable
fun MainScreen(vm: MainViewModel, screen: AppScreen.Main) {
    val mensas = screen.mensas
    val latestMensas by rememberUpdatedState(mensas)
    val today = rememberToday()
    val scope = rememberCoroutineScope()

    val pager = rememberPagerState(initialPage = mensas.indexOfFirst { it.id == screen.defaultId }.coerceAtLeast(0)) {
        latestMensas.size
    }

    // Track the active Mensa by id so a reorder (which changes indices) keeps you on the same one.
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> latestMensas.getOrNull(page)?.let { activeId = it.id } }
    }
    LaunchedEffect(mensas.map { it.id }) {
        val index = mensas.indexOfFirst { it.id == activeId }
        if (index >= 0 && index != pager.currentPage) pager.scrollToPage(index)
    }

    Scaffold(
        topBar = {
            Surface {
                MensaTabRow(
                    mensas = mensas,
                    selectedId = mensas.getOrNull(pager.currentPage)?.id,
                    defaultId = screen.defaultId,
                    onSelect = { id ->
                        val index = mensas.indexOfFirst { it.id == id }
                        if (index >= 0) scope.launch { pager.animateScrollToPage(index) }
                    },
                    onMakeDefault = vm::pickDefault,
                    onReorder = vm::setOrder,
                    // Status bar + display cutout sides; the bottom inset is handled by the lists.
                    modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                // Keep the neighbors composed: their pages are what "observe visible tab and neighbors" means.
                beyondViewportPageCount = 1,
                key = { latestMensas.getOrNull(it)?.id ?: it.toString() },
            ) { page ->
                latestMensas.getOrNull(page)?.let { mensa -> MensaPage(vm, mensa.id, today, screen.prefs) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MensaPage(vm: MainViewModel, mensaId: String, today: LocalDate, prefs: ViewPrefs) {
    val flow = remember(mensaId) { vm.menuFor(mensaId) }
    val ui by flow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Only the pull gesture's own spinner; background refreshes get the thin progress bar instead.
    var pulling by remember { mutableStateOf(false) }

    val current = ui
    val days = remember(current?.days, today) { current?.days?.upcomingWeekdays(today).orEmpty() }
    val options = remember(days, prefs.tagFilters) { DishFilters.options(days, prefs.tagFilters) }
    val shown = remember(days, prefs.tagFilters, prefs.excludedCodes) {
        DishFilters.apply(days, prefs.tagFilters, prefs.excludedCodes)
    }
    // Scales come from the unfiltered days, so colors don't shift when the user filters.
    val footprints = remember(days) { dayFootprints(days) }
    var showSheet by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = pulling,
        onRefresh = {
            scope.launch {
                pulling = true
                try {
                    vm.refresh(mensaId).join()
                } finally {
                    pulling = false
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (current?.hasData == true) {
                FilterRow(options, prefs.tagFilters, prefs.excludedCodes.size, vm::toggleFilter) { showSheet = true }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (current != null) MenuBody(current, shown, today, prefs, footprints) { vm.refresh(mensaId) }
            }
        }
        if (showSheet && current != null) {
            FilterSheet(
                legend = current.legend,
                prefs = prefs,
                onPriceGroup = vm::setPriceGroup,
                onCompact = vm::setCompactMode,
                onSetCodes = vm::setCodesExcluded,
                onClearCodes = vm::clearExcludedCodes,
                onDismiss = { showSheet = false },
            )
        }
        if (current?.isRefreshing == true && !pulling) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

/**
 * The sheet button stays pinned at the start; the tag chips (built from the tags found on this
 * page plus active filters; the selection is global) scroll next to it.
 */
@Composable
private fun FilterRow(
    options: List<DishFilters.Option>,
    active: Set<String>,
    excludedCodeCount: Int,
    onToggle: (String) -> Unit,
    onOpenSheet: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = onOpenSheet,
            label = {
                Text(
                    if (excludedCodeCount > 0) stringResource(R.string.filter_button_count, excludedCodeCount)
                    else stringResource(R.string.filter_button),
                )
            },
            modifier = Modifier.padding(start = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options, key = { it.key }) { option ->
                val label = filterLabelRes(option.key)?.let { stringResource(it) } ?: stringResource(R.string.filter_no_generic, option.label)
                FilterChip(selected = option.key in active, onClick = { onToggle(option.key) }, label = { Text(label) })
            }
        }
    }
}
