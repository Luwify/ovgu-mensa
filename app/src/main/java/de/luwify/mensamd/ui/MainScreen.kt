package de.luwify.mensamd.ui

import androidx.compose.foundation.layout.Box
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
                latestMensas.getOrNull(page)?.let { mensa -> MensaPage(vm, mensa.id, today) }
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
private fun MensaPage(vm: MainViewModel, mensaId: String, today: LocalDate) {
    val flow = remember(mensaId) { vm.menuFor(mensaId) }
    val ui by flow.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        ui?.let { MenuBody(it, today) { vm.refresh(mensaId) } }
        if (ui?.isRefreshing == true) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}
