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
 * Long-press a tab and drag to reorder (see [rememberRowReorder]); [onReorder] gets the final id list
 * once, when the finger lifts.
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
    val listState = rememberLazyListState()
    val reorder = rememberRowReorder(mensas, listState, key = { it.id }, onReorder = onReorder)
    val order = reorder.order

    // Keep the active tab in view when the pager moves (incl. on first show: default tab).
    LaunchedEffect(selectedId) {
        if (reorder.isDragging) return@LaunchedEffect
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
            .then(reorder.gestures),
        userScrollEnabled = !reorder.isDragging,
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(order, key = { it.id }) { mensa ->
            val selected = mensa.id == selectedId
            val dragging = mensa.id == reorder.draggedKey
            MensaTab(
                name = mensa.name.forLanguage(language),
                selected = selected,
                isDefault = mensa.id == defaultId,
                dragging = dragging,
                onClick = { onSelect(mensa.id) },
                onStar = { onMakeDefault(mensa.id) },
                modifier = Modifier.reorderItem(this, reorder, mensa.id),
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
