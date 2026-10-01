package de.luwify.mensamd.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Long-press-and-drag reordering for a horizontal LazyRow whose item keys are [key]. Used by the Mensa tabs and
 * the filter chips. While dragging only the local [order] changes; [onReorder] gets the final key list once,
 * when the finger lifts (and only if it changed). The row auto-scrolls near its edges.
 *
 * Usage: `LazyRow(state, Modifier.then(reorder.gestures), userScrollEnabled = !reorder.isDragging)`, iterate
 * [order], and give each item `Modifier.reorderItem(this, reorder, key)`.
 */
@Stable
class RowReorder<T> internal constructor(
    private val listState: LazyListState,
    private val key: (T) -> String,
) {
    var order: List<T> by mutableStateOf(emptyList())
        internal set
    var draggedKey: String? by mutableStateOf(null)
        internal set
    val isDragging: Boolean get() = draggedKey != null

    internal var dragStart by mutableFloatStateOf(0f) // dragged item's left edge at drag start, in row coordinates
    internal var dragDelta by mutableFloatStateOf(0f) // finger movement since drag start
    internal var pointerX by mutableFloatStateOf(0f)
    internal lateinit var gesturesModifier: Modifier

    /** Goes on the LazyRow. */
    val gestures: Modifier get() = gesturesModifier

    /** Swap once the dragged item's center passes the center of a neighbor: no flip-flopping. */
    internal fun maybeSwap() {
        val id = draggedKey ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val info = items.firstOrNull { it.key == id } ?: return
        val center = dragStart + dragDelta + info.size / 2f
        val target = items.firstOrNull { it.key != id && center >= it.offset && center <= it.offset + it.size } ?: return
        val from = order.indexOfFirst { key(it) == id }
        val to = order.indexOfFirst { key(it) == target.key }
        if (from < 0 || to < 0) return
        val targetCenter = target.offset + target.size / 2f
        if ((to > from && center > targetCenter) || (to < from && center < targetCenter)) {
            order = order.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    internal fun draggedTranslation(itemKey: String): Float {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
        return if (info != null) dragStart + dragDelta - info.offset else 0f
    }
}

@Composable
fun <T> rememberRowReorder(
    items: List<T>,
    listState: LazyListState,
    key: (T) -> String,
    onReorder: (List<String>) -> Unit,
): RowReorder<T> {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val latestItems by rememberUpdatedState(items)
    val latestOnReorder by rememberUpdatedState(onReorder)
    val state = remember(listState) { RowReorder(listState, key).also { it.order = items } }

    // Follow committed changes (our own commit coming back from DataStore, new items).
    LaunchedEffect(items) { if (state.draggedKey == null) state.order = items }

    fun endDrag(commit: Boolean) {
        val keys = state.order.map(key)
        state.draggedKey = null
        if (commit && keys != latestItems.map(key)) latestOnReorder(keys) else state.order = latestItems
    }

    // Auto-scroll while the finger sits near an edge.
    LaunchedEffect(state.draggedKey) {
        if (state.draggedKey == null) return@LaunchedEffect
        val edge = with(density) { 56.dp.toPx() }
        val maxStep = with(density) { 14.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val width = listState.layoutInfo.viewportSize.width
            val x = state.pointerX
            val step = when {
                x < edge -> -(edge - x).coerceAtMost(edge) / edge * maxStep
                x > width - edge -> (x - (width - edge)).coerceAtMost(edge) / edge * maxStep
                else -> 0f
            }
            if (step != 0f) {
                listState.scrollBy(step)
                state.maybeSwap()
            }
        }
    }

    state.gesturesModifier = Modifier.pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val item = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { offset.x >= it.offset && offset.x <= it.offset + it.size }
                if (item != null) {
                    state.dragStart = item.offset.toFloat()
                    state.dragDelta = 0f
                    state.pointerX = offset.x
                    state.draggedKey = item.key as? String
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDrag = { change, amount ->
                if (state.draggedKey != null) {
                    change.consume()
                    state.dragDelta += amount.x
                    state.pointerX = change.position.x
                    state.maybeSwap()
                }
            },
            onDragEnd = { endDrag(commit = true) },
            onDragCancel = { endDrag(commit = false) },
        )
    }
    return state
}

/**
 * For each item of a [RowReorder] row ([scope] is the item's LazyItemScope): the dragged item is lifted and follows
 * the finger, the others animate into their new places.
 */
fun Modifier.reorderItem(scope: LazyItemScope, reorder: RowReorder<*>, itemKey: String): Modifier =
    if (itemKey == reorder.draggedKey) {
        this.zIndex(1f).graphicsLayer {
            // Follow the finger regardless of where the list has laid the item out.
            translationX = reorder.draggedTranslation(itemKey)
            scaleX = 1.06f
            scaleY = 1.06f
        }
    } else {
        with(scope) { this@reorderItem.animateItem() }
    }
