package de.luwify.mensamd.ui

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The numbers behind the favorite sparkles, free of drawing so they are unit-tested. */
object SparkleMath {
    /** One particle of the burst at a moment: direction, distance from the heart (dp), size (dp), opacity. */
    data class Particle(val angleDeg: Float, val distanceDp: Float, val sizeDp: Float, val alpha: Float, val star: Boolean)

    const val BURST_MS = 650
    const val PARTICLES = 12

    /** The burst at [progress] 0..1: stars and dots fly out fast, slow down and fade. Nothing is left at 1. */
    fun burst(progress: Float): List<Particle> {
        val p = progress.coerceIn(0f, 1f)
        val out = 1f - (1f - p) * (1f - p) // ease out
        val alpha = if (p < 0.35f) 1f else 1f - (p - 0.35f) / 0.65f
        return (0 until PARTICLES).map { i ->
            val star = i % 2 == 0
            Particle(
                angleDeg = i * (360f / PARTICLES) + if (star) 0f else 9f,
                distanceDp = (9f + 25f * out) * if (star) 1f else 0.72f,
                sizeDp = if (star) 5f * (1f - 0.45f * p) else 1.8f,
                alpha = alpha.coerceIn(0f, 1f),
                star = star,
            )
        }
    }

    /** The heart's pop: up to 1.35x halfway through the first half of the burst, back to 1 by its middle. */
    fun heartScale(progress: Float): Float {
        val p = (progress / 0.5f).coerceIn(0f, 1f)
        return 1f + 0.35f * sin(PI.toFloat() * p)
    }

    /** One twinkling star of the legendary frame: opacity 0..1 over time, each star ([index]) at its own beat. */
    fun twinkle(timeMs: Long, index: Int): Float {
        val period = 1700L + index * 430L
        val t = ((timeMs + index * 610L) % period).toFloat() / period
        val wave = sin(2f * PI.toFloat() * t)
        return (wave * wave * wave).coerceAtLeast(0f) // mostly off, a short bright twinkle
    }

    /** Fraction of a turn of the legendary frame's gradient at [timeMs] (one turn in [periodMs]). */
    fun turn(timeMs: Long, periodMs: Long = 3600L): Float = (timeMs % periodMs).toFloat() / periodMs

    /**
     * A point on the outline of a rounded rectangle ([w] x [h], corner radius [r]) at [s] (0..1, clockwise from the
     * top-left corner's end) and the outward normal there: (x, y, nx, ny). Used to wave the super aura around the
     * card while keeping its round corners.
     */
    fun roundedRectPoint(s: Float, w: Float, h: Float, r: Float): FloatArray {
        val rr = r.coerceIn(0f, minOf(w, h) / 2f)
        val top = w - 2 * rr
        val side = h - 2 * rr
        val arc = (PI / 2).toFloat() * rr
        val total = 2 * top + 2 * side + 4 * arc
        var d = ((s % 1f + 1f) % 1f) * total
        fun corner(cx: Float, cy: Float, startDeg: Float, along: Float): FloatArray {
            val a = Math.toRadians((startDeg + 90f * along / arc.coerceAtLeast(0.0001f)).toDouble())
            val nx = cos(a).toFloat()
            val ny = sin(a).toFloat()
            return floatArrayOf(cx + nx * rr, cy + ny * rr, nx, ny)
        }
        if (d < top) return floatArrayOf(rr + d, 0f, 0f, -1f)
        d -= top
        if (d < arc) return corner(w - rr, rr, -90f, d)
        d -= arc
        if (d < side) return floatArrayOf(w, rr + d, 1f, 0f)
        d -= side
        if (d < arc) return corner(w - rr, h - rr, 0f, d)
        d -= arc
        if (d < top) return floatArrayOf(w - rr - d, h, 0f, 1f)
        d -= top
        if (d < arc) return corner(rr, h - rr, 90f, d)
        d -= arc
        if (d < side) return floatArrayOf(0f, h - rr - d, -1f, 0f)
        d -= side
        return corner(rr, rr, 180f, d.coerceAtMost(arc))
    }

    /**
     * How far (in units of the aura's amplitude, about -1..1) the super aura bulges out at outline position [s] at
     * [timeMs]: two waves running in opposite directions, so it flickers like a flame instead of rotating.
     */
    fun auraWave(s: Float, timeMs: Long): Float {
        val t = timeMs / 1000f
        val tau = 2f * PI.toFloat()
        return 0.6f * sin(tau * (3f * s) + t * 2.1f) + 0.4f * sin(tau * (5f * s) - t * 3.3f)
    }

    /** A floating mote of the super aura: where along the card (0..1 from the left) and how far up its loop is. */
    fun mote(timeMs: Long, index: Int): Triple<Float, Float, Float> {
        val period = 2600L + index * 370L
        val t = ((timeMs + index * 977L) % period).toFloat() / period // 0..1 rise
        val x = ((index * 0.173f + 0.08f) % 1f) + 0.03f * sin(2f * PI.toFloat() * (t * 2f + index))
        val alpha = sin(PI.toFloat() * t) // fades in and out
        return Triple(x.coerceIn(0f, 1f), t, alpha)
    }

    /**
     * Fast taps on the sparkle switch: the taps within [windowMs] before [now], newest last. Five of them unlock
     * the super sparkle.
     */
    fun recentTaps(taps: List<Long>, now: Long, windowMs: Long = 3000L): List<Long> = taps.filter { now - it in 0..windowMs }

    const val UNLOCK_TAPS = 5

    /** The aura's trail after a scroll step [dy] (px; the content moved by it): pushed the other way, capped. */
    fun dragAfter(current: Float, dy: Float, max: Float): Float = (current - dy * 0.4f).coerceIn(-max, max)

    /** The color at position [x] (any number, wraps at 1) of a cyclic gradient through [stops]. */
    fun cyclic(stops: List<Color>, x: Float): Color {
        val f = (x % 1f + 1f) % 1f * stops.size
        val i = f.toInt() % stops.size
        return lerp(stops[i], stops[(i + 1) % stops.size], f - f.toInt())
    }
}

/** Gold for the legendary frame: bright tones on dark pages, deeper ones that still show on light pages. */
private val GOLD_ON_DARK = listOf(Color(0xFFFFF3B0), Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFF8F00), Color(0xFFFFD54F))
private val GOLD_ON_LIGHT = listOf(Color(0xFFE0A800), Color(0xFFB07800), Color(0xFF8A5A00), Color(0xFFC68A00), Color(0xFFFFC233))

fun goldFor(background: Color): List<Color> = if (background.luminance() < 0.4f) GOLD_ON_DARK else GOLD_ON_LIGHT

/** The super aura: gold into orange, like a 5-star glow. Brighter on dark cards, deeper on light ones. */
private val AURA_ON_DARK = listOf(Color(0xFFFFF3B0), Color(0xFFFFC233), Color(0xFFFF9800), Color(0xFFFF6D00), Color(0xFFFFB300))
private val AURA_ON_LIGHT = listOf(Color(0xFFFFB300), Color(0xFFE65100), Color(0xFFFF8F00), Color(0xFFC77700), Color(0xFFFFA000))

fun auraFor(background: Color): List<Color> = if (background.luminance() < 0.4f) AURA_ON_DARK else AURA_ON_LIGHT

/**
 * How far the super aura trails the scrolling list: pushed against the scroll direction by each scroll step, then
 * springing back with a little overshoot. One per list; cards read [offset] only while drawing.
 */
@Stable
class ScrollDrag(private val scope: CoroutineScope, private val maxPx: Float) {
    val offset = Animatable(0f)
    private var job: Job? = null

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y != 0f) push(consumed.y)
            return Offset.Zero
        }
    }

    private fun push(dy: Float) {
        job?.cancel()
        job = scope.launch {
            offset.snapTo(SparkleMath.dragAfter(offset.value, dy, maxPx))
            offset.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 90f))
        }
    }
}

/** The list's [ScrollDrag], when super sparkle is on; null otherwise (no aura drag). */
val LocalScrollDrag = staticCompositionLocalOf<ScrollDrag?> { null }

@Composable
fun rememberScrollDrag(): ScrollDrag {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(scope, density) { ScrollDrag(scope, with(density) { 22.dp.toPx() }) }
}

/** False when the user turned animations off in Android's settings: then no burst and a still frame. */
fun animationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/** The burst of one card: started from the heart, drawn by [FavoriteEffects] (so the card's clip can't cut it). */
@Stable
class FavoriteBurst {
    val progress = Animatable(1f)

    /** Set when the user taps an empty heart; only that heart bursts, not other copies of the same dish. */
    var pending = false

    suspend fun play() {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SparkleMath.BURST_MS, easing = LinearOutSlowInEasing))
    }
}

/**
 * Wraps a dish card: draws the burst over it (centered on [heartCenter], which knows where the card puts its
 * heart) and, when [legendary], the legendary frame: a gold glow, a gold gradient edge that slowly turns, and three
 * twinkling stars. Everything is drawn in the draw phase from one clock (about 30 fps, only while the app is
 * resumed and the card is composed), with no blur and no offscreen layer, so scrolling stays smooth.
 */
@Composable
fun FavoriteEffects(
    legendary: Boolean,
    superAura: Boolean,
    burst: FavoriteBurst,
    shape: Shape,
    favoriteColor: Color,
    background: Color,
    heartCenter: Density.(Size) -> Offset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val animate = remember(context) { animationsEnabled(context) }
    val gold = remember(background) { goldFor(background) }
    val aura = remember(background) { auraFor(background) }
    val drag = LocalScrollDrag.current
    var clock by remember { mutableLongStateOf(0L) }
    if (legendary && animate) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    // Read only while drawing: redraws, never recomposes. ~30 fps is plenty for the slow shine and
                    // aura (measured: every frame doubled the idle cost); while scrolling the list redraws anyway.
                    clock = SystemClock.uptimeMillis()
                    delay(33)
                }
            }
        }
    }
    Box(
        modifier.drawWithContent {
            if (legendary && superAura) drawSuperAura(shape, aura, clock, if (animate) drag?.offset?.value ?: 0f else 0f, animate)
            if (legendary) drawLegendaryGlow(shape, gold)
            drawContent()
            if (legendary) drawLegendaryEdge(shape, gold, if (animate) clock else 0L, animate)
            val p = burst.progress.value
            if (p < 1f) drawBurst(heartCenter(size), p, gold[1], favoriteColor)
        },
    ) { content() }
}

/** The card's corner radius in px (from its shape at this size). */
internal fun DrawScope.cornerRadiusOf(shape: Shape): Float =
    (shape.createOutline(size, layoutDirection, this) as? androidx.compose.ui.graphics.Outline.Rounded)
        ?.roundRect?.topLeftCornerRadius?.x ?: 0f

/**
 * A stroke parallel to the card's outline, [distance] px outside it (negative: inside), with the corner radius that
 * distance needs (the card's radius plus the distance), so every ring shares the card's corner center.
 */
internal fun DrawScope.drawParallelRing(radius: Float, distance: Float, width: Float, brush: Brush, alpha: Float = 1f) {
    val r = (radius + distance).coerceAtLeast(0f)
    drawRoundRect(
        brush,
        alpha = alpha,
        topLeft = Offset(-distance, -distance),
        size = Size(size.width + 2 * distance, size.height + 2 * distance),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        style = Stroke(width),
    )
}

private fun DrawScope.drawLegendaryGlow(shape: Shape, gold: List<Color>) {
    val r = cornerRadiusOf(shape)
    // Two soft rings centered 3.5 and 1.5 dp outside the edge.
    drawParallelRing(r, 3.5.dp.toPx(), 7.dp.toPx(), androidx.compose.ui.graphics.SolidColor(gold[1].copy(alpha = 0.16f)))
    drawParallelRing(r, 1.5.dp.toPx(), 3.dp.toPx(), androidx.compose.ui.graphics.SolidColor(gold[1].copy(alpha = 0.38f)))
}

private fun DrawScope.drawLegendaryEdge(shape: Shape, gold: List<Color>, timeMs: Long, animate: Boolean) {
    val width = 2.dp.toPx()
    val turn = SparkleMath.turn(timeMs)
    // 13 stops of the cyclic gold, shifted by the turn: the shine runs around the card.
    val colors = (0..12).map { SparkleMath.cyclic(gold, it / 12f - turn) }
    // Centered half a stroke inside the edge, with the radius for that.
    drawParallelRing(cornerRadiusOf(shape), -width / 2, width, Brush.sweepGradient(colors, center))
    // Three stars on the edge: top left, right side, bottom.
    val spots = listOf(Offset(0.10f, 0f) to 7f, Offset(1f, 0.70f) to 5.5f, Offset(0.36f, 1f) to 6f)
    spots.forEachIndexed { i, (at, r) ->
        val alpha = if (animate) SparkleMath.twinkle(timeMs, i) else 0.85f
        if (alpha > 0.02f) drawStar(Offset(at.x * size.width, at.y * size.height), r.dp.toPx(), gold[0].copy(alpha = alpha))
    }
}

/**
 * The super aura behind the card, built only from shapes the GPU draws cheaply (no free-form paths, which would be
 * re-tessellated every frame): three rounded-rect rings parallel to the card that breathe in and out of phase,
 * ten soft glow blobs (radial gradients) wandering around the outline like flames, flickering in size
 * ([SparkleMath.auraWave]), and motes rising along both sides. Shifted by [dragY]: it trails the scrolling.
 */
private fun DrawScope.drawSuperAura(shape: Shape, colors: List<Color>, timeMs: Long, dragY: Float, animate: Boolean) {
    val r = cornerRadiusOf(shape)
    val w = size.width
    val h = size.height
    val t = if (animate) timeMs / 1000f else 0f
    val turn = if (animate) SparkleMath.turn(timeMs, 5200L) else 0f
    val brush = Brush.sweepGradient((0..12).map { SparkleMath.cyclic(colors, it / 12f + turn) }, center)
    translate(0f, dragY) {
        // width, alpha, distance out, breathing amplitude, speed (all dp / per second)
        val rings = listOf(
            floatArrayOf(14f, 0.08f, 9f, 2.5f, 1.3f),
            floatArrayOf(8f, 0.13f, 6f, 1.6f, 1.9f),
            floatArrayOf(3f, 0.28f, 3f, 0.8f, 2.6f),
        )
        rings.forEachIndexed { i, (width, alpha, out, amp, speed) ->
            val d = (out + amp * sin(t * speed + i * 1.7f)).dp.toPx()
            drawParallelRing(r, d, width.dp.toPx(), brush, alpha)
        }
        val tau = 2f * PI.toFloat()
        for (i in 0 until 10) {
            val direction = if (i % 2 == 0) 1f else -1f
            val pos = ((i / 10f + direction * t * (0.035f + i * 0.004f)) % 1f + 1f) % 1f
            val p = SparkleMath.roundedRectPoint(pos, w, h, r)
            val radius = (11f + 5f * SparkleMath.auraWave(pos, timeMs + i * 211L)).dp.toPx()
            val at = Offset(p[0] + p[2] * 5.dp.toPx(), p[1] + p[3] * 5.dp.toPx())
            val color = colors[i % colors.size]
            val flicker = 0.75f + 0.25f * sin(t * 5.3f + i * tau / 10f)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.32f * flicker), color.copy(alpha = 0f)), at, radius), radius, at)
        }
        if (animate) {
            for (i in 0 until 8) {
                val (xFrac, rise, a) = SparkleMath.mote(timeMs, i)
                val left = i % 2 == 0
                val x = if (left) -(4f + 10f * xFrac).dp.toPx() else w + (4f + 10f * xFrac).dp.toPx()
                val y = h - rise * (h + 24.dp.toPx())
                drawCircle(colors[0].copy(alpha = 0.75f * a), (1.4f + 1.2f * xFrac).dp.toPx(), Offset(x, y))
            }
        }
    }
}

private fun DrawScope.drawBurst(center: Offset, progress: Float, gold: Color, heart: Color) {
    // An expanding, fading ring first, then the particles.
    val ringP = (progress / 0.6f).coerceIn(0f, 1f)
    if (ringP < 1f) {
        drawCircle(gold.copy(alpha = 0.55f * (1f - ringP)), radius = (8f + 22f * ringP).dp.toPx(), center = center, style = Stroke((2.5f * (1f - ringP)).dp.toPx()))
    }
    SparkleMath.burst(progress).forEachIndexed { i, p ->
        if (p.alpha <= 0f) return@forEachIndexed
        val a = Math.toRadians(p.angleDeg.toDouble())
        val at = center + Offset((cos(a) * p.distanceDp).toFloat().dp.toPx(), (sin(a) * p.distanceDp).toFloat().dp.toPx())
        val color = (if (i % 4 == 0) heart else gold).copy(alpha = p.alpha)
        if (p.star) drawStar(at, p.sizeDp.dp.toPx(), color) else drawCircle(color, p.sizeDp.dp.toPx(), at)
    }
}

/** A four-pointed sparkle with a white core. */
private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val w = radius * 0.26f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + w, center.y - w)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + w, center.y + w)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - w, center.y + w)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - w, center.y - w)
        close()
    }
    drawPath(path, color)
    drawCircle(Color.White.copy(alpha = color.alpha * 0.9f), radius * 0.16f, center)
}
