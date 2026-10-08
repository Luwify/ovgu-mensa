package de.luwify.mensamd.ui

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.luwify.mensamd.ui.theme.LocalMensaColors
import kotlinx.coroutines.delay

/**
 * The super aura of every visible favorite card, drawn in one layer ABOVE the list (so it lies over the cards and
 * headers next to it instead of under them), positioned from [listState]'s layout info. Cards are [favoriteKeys]
 * (item keys) laid out with [cardInset] on both sides; [headerKeys] are the sticky day headers: the glow fades out
 * below a pinned one and before the end of the viewport instead of being cut. Draw phase only; its ~30 fps clock
 * runs only while a favorite card is visible and the app is resumed.
 */
@Composable
fun SuperAuraOverlay(
    listState: LazyListState,
    favoriteKeys: Set<String>,
    headerKeys: (Any) -> Boolean,
    drag: ScrollDrag?,
    modifier: Modifier = Modifier,
) {
    val background = LocalMensaColors.current.cardContainer ?: MaterialTheme.colorScheme.surface
    val colors = remember(background) { auraFor(background) }
    val shape = CardDefaults.shape
    // One shader per visible card (each has its own size and shape uniforms); dropped when the card leaves.
    val shaders = remember(colors) { HashMap<Any, AuraShader>() }
    val anyVisible by remember(favoriteKeys) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key in favoriteKeys } }
    }
    var clock by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    if (anyVisible) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    clock = SystemClock.uptimeMillis() // read only while drawing: redraws, never recomposes
                    delay(33)
                }
            }
        }
    }
    if (!anyVisible) return
    Canvas(modifier) {
        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        val top = SparkleMath.pinnedHeaderBottom(items.map { it.offset to it.size }, items.map { headerKeys(it.key) }).toFloat()
        val bottom = info.viewportSize.height.toFloat()
        val inset = 16.dp.toPx()
        val trail = drag?.trail() ?: 0f
        val visible = HashSet<Any>()
        for (item in items) {
            if (item.key !in favoriteKeys) continue
            visible += item.key
            val card = Size(size.width - 2 * inset, item.size.toFloat())
            val radius = (shape.createOutline(card, layoutDirection, this) as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
            val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) shaders.getOrPut(item.key) { AuraShader(colors) } else null
            translate(inset, item.offset.toFloat()) {
                // The visible band in the card's own coordinates.
                drawSuperAura(card, radius, colors, shader, clock, trail, top - item.offset, bottom - item.offset)
            }
        }
        shaders.keys.retainAll(visible)
    }
}
