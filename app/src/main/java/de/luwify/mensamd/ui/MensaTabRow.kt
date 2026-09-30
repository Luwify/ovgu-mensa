package de.luwify.mensamd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.luwify.mensamd.R
import de.luwify.mensamd.model.Mensa

/**
 * One scrollable tab per Mensa.
 *
 * Long-press a tab and drag to reorder; the row auto-scrolls near its edges. While dragging, only
 * a local working order changes; [onReorder] gets the final id list once, when the finger lifts.
 * The star marks the default Mensa; on the active tab it is a button to make it the default.
 */
@Composable
fun MensaTabRow(
    mensas: List<Mensa>,
    selectedId: String?,
    defaultId: String,
    onSelect: (String) -> Unit,
    onMakeDefault: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalConfiguration.current.locales[0].language
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var order by remember { mutableStateOf(mensas) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableFloatStateOf(0f) } // dragged tab's left edge at drag start, in row coordinates
    var dragDelta by remember { mutableFloatStateOf(0f) } // finger movement since drag start
    var pointerX by remember { mutableFloatStateOf(0f) }
    val latestMensas by rememberUpdatedState(mensas)
    val latestOnReorder by rememberUpdatedState(onReorder)

    // Follow committed changes (our own commit coming back from DataStore, catalog changes).
    LaunchedEffect(mensas) { if (draggedId == null) order = mensas }

    /** Swap once the dragged tab's center passes the center of a neighbor: no flip-flopping. */
    fun maybeSwap() {
        val id = draggedId ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val info = items.firstOrNull { it.key == id } ?: return
        val center = dragStart + dragDelta + info.size / 2f
        val target = items.firstOrNull { it.key != id && center >= it.offset && center <= it.offset + it.size } ?: return
        val from = order.indexOfFirst { it.id == id }
        val to = order.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        val targetCenter = target.offset + target.size / 2f
        if ((to > from && center > targetCenter) || (to < from && center < targetCenter)) {
            order = order.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun endDrag(commit: Boolean) {
        val ids = order.map { it.id }
        draggedId = null
        if (commit && ids != latestMensas.map { it.id }) latestOnReorder(ids) else order = latestMensas
    }

    // Auto-scroll while the finger sits near an edge.
    LaunchedEffect(draggedId) {
        if (draggedId == null) return@LaunchedEffect
        val edge = with(density) { 56.dp.toPx() }
        val maxStep = with(density) { 14.dp.toPx() }
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val width = listState.layoutInfo.viewportSize.width
            val step = when {
                pointerX < edge -> -(edge - pointerX).coerceAtMost(edge) / edge * maxStep
                pointerX > width - edge -> (pointerX - (width - edge)).coerceAtMost(edge) / edge * maxStep
                else -> 0f
            }
            if (step != 0f) {
                listState.scrollBy(step)
                maybeSwap()
            }
        }
    }

    // Keep the active tab in view when the pager moves (incl. on first show: default tab).
    LaunchedEffect(selectedId) {
        if (draggedId != null) return@LaunchedEffect
        val index = order.indexOfFirst { it.id == selectedId }
        if (index < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = visible != null && visible.offset >= 0 && visible.offset + visible.size <= info.viewportSize.width
        if (!fullyVisible) listState.animateScrollToItem(index, scrollOffset = -info.viewportSize.width / 4)
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val item = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { offset.x >= it.offset && offset.x <= it.offset + it.size }
                        if (item != null) {
                            dragStart = item.offset.toFloat()
                            dragDelta = 0f
                            pointerX = offset.x
                            draggedId = item.key as? String
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, amount ->
                        if (draggedId != null) {
                            change.consume()
                            dragDelta += amount.x
                            pointerX = change.position.x
                            maybeSwap()
                        }
                    },
                    onDragEnd = { endDrag(commit = true) },
                    onDragCancel = { endDrag(commit = false) },
                )
            },
        userScrollEnabled = draggedId == null,
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(order, key = { it.id }) { mensa ->
            val selected = mensa.id == selectedId
            val dragging = mensa.id == draggedId
            MensaTab(
                name = mensa.name.forLanguage(language),
                selected = selected,
                isDefault = mensa.id == defaultId,
                dragging = dragging,
                onClick = { onSelect(mensa.id) },
                onStar = { onMakeDefault(mensa.id) },
                modifier = if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer {
                        // Follow the finger regardless of where the list has laid the item out.
                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == mensa.id }
                        translationX = if (info != null) dragStart + dragDelta - info.offset else 0f
                        scaleX = 1.06f
                        scaleY = 1.06f
                    }
                } else {
                    Modifier.animateItem()
                },
            )
        }
    }
}

@Composable
private fun MensaTab(
    name: String,
    selected: Boolean,
    isDefault: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    onStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.primary else colors.onSurfaceVariant
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (dragging) colors.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent),
    ) {
        Row(
            Modifier.fillMaxHeight().clickable(role = Role.Tab, onClick = onClick).padding(start = 12.dp, end = if (selected || isDefault) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                maxLines = 1,
                color = content,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (selected || isDefault) {
                val description = stringResource(if (isDefault) R.string.default_mensa else R.string.make_default_mensa)
                val canSet = selected && !isDefault
                Box(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(min = 36.dp)
                        .then(if (canSet) Modifier.clickable(role = Role.Button, onClick = onStar) else Modifier)
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (isDefault) "★" else "☆", color = content, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(colors.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
    }
}
