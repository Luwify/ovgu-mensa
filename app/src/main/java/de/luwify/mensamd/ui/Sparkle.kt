package de.luwify.mensamd.ui

import android.content.Context
import android.os.SystemClock
import android.os.Build
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
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
     * The jelly shape of the super aura while scrolling: (top shift, bottom shift, side inset) in px. The edge that
     * trails the scroll lags more than the leading one, so the aura stretches; [wobble] (a bouncy spring) stretches
     * and, swinging through negative, squashes it, with the sides moving in and out to match.
     */
    fun jelly(drag: Float, wobble: Float): FloatArray =
        floatArrayOf(drag * 0.5f - wobble, drag * 1.5f + wobble, wobble * 0.35f)

    /**
     * The [index]-th sparkle of the aura at [timeMs]: (outline position 0..1, distance out in dp, size in dp, opacity).
     * Each one flashes at its own pace and then comes back somewhere else.
     */
    fun sparkleSpot(index: Int, timeMs: Long): FloatArray {
        val period = 1500L + index * 290L
        val shifted = timeMs + index * 733L
        val cycle = shifted / period
        val phase = (shifted % period).toFloat() / period
        fun hash(n: Long): Float {
            var x = n * 0x9E3779B97F4A7C15uL.toLong() + index * 0x632BE59BD9B4E019L
            x = x xor (x ushr 31)
            x *= 0x7FB5D329728EA185L
            x = x xor (x ushr 27)
            return ((x ushr 11) and 0xFFFFF).toFloat() / 0xFFFFF
        }
        val pos = hash(cycle)
        val out = 2f + 8f * hash(cycle + 7919)
        val flash = if (phase < 0.45f) sin(PI.toFloat() * phase / 0.45f) else 0f // on for a moment, then dark
        return floatArrayOf(pos, out, 3.5f + 2.5f * hash(cycle + 104729), flash)
    }

    /**
     * Fast taps on the sparkle switch: the taps within [windowMs] before [now], newest last. Five of them unlock
     * the super sparkle.
     */
    fun recentTaps(taps: List<Long>, now: Long, windowMs: Long = 3000L): List<Long> = taps.filter { now - it in 0..windowMs }

    const val UNLOCK_TAPS = 5

    /**
     * The organic outline of the super aura for a jelly state ([jelly] = top shift, bottom shift, side inset in px)
     * around a card [height] px tall with corner radius [radius]: never a stretched rectangle. The more it stretches,
     * the rounder it gets (bigger corners, blended toward an ellipse); the trailing side (where the aura lags) gets
     * extra rounding and a bulge like a comet tail; [breathe] is the amplitude of the slow harmonics along the
     * outline. At rest it is exactly the card's rounded rectangle (plus the base breathing).
     */
    fun auraShape(jelly: FloatArray, height: Float, radius: Float, reach: Float): AuraShape {
        val shift = (jelly[0] + jelly[1]) / 2f // the whole aura moves by this
        val stretch = jelly[1] - jelly[0] // taller (+) or squashed (-)
        val amount = kotlin.math.abs(stretch) + 0.5f * kotlin.math.abs(shift)
        val round = radius + 0.9f * amount
        val tail = 1.1f * kotlin.math.abs(shift)
        return AuraShape(
            centerShift = shift,
            halfHeightGrow = stretch / 2f,
            inset = jelly[2],
            radiusTop = round + if (shift < 0f) tail else 0f,
            radiusBottom = round + if (shift > 0f) tail else 0f,
            tailTop = if (shift < 0f) 0.6f * -shift else 0f,
            tailBottom = if (shift > 0f) 0.6f * shift else 0f,
            roundness = (amount / (0.25f * height)).coerceIn(0f, 0.6f),
            breathe = 0.5f * reach + 0.12f * amount,
        )
    }

    /** The aura's maximum reach in units of its base reach: 4 x the living factor's maximum (0.55 + 0.75 x 1.1). */
    const val AURA_REACH_FACTOR = 4f * 1.375f

    /**
     * The aura's fall-off at [x] = distance / maximum reach: (1 - x)^4, so it reaches exactly 0 at x = 1 with a
     * zero slope (no visible edge), and is 0 beyond. Mirrors the shader.
     */
    fun auraFalloff(x: Float): Float = if (x >= 1f) 0f else (1f - x.coerceAtLeast(0f)).let { it * it * it * it }

    /**
     * The alpha of each of a stack of nested fills (outermost first) so that, drawn over each other, the total
     * opacity at the i-th fill's edge is [targets] i (which must not decrease inward).
     */
    fun stackedAlphas(targets: List<Float>): List<Float> {
        var covered = 0f
        return targets.map { t ->
            val a = if (covered >= 1f) 0f else ((t - covered) / (1f - covered)).coerceIn(0f, 1f)
            covered = covered + (1f - covered) * a
            a
        }
    }

    /**
     * Where the visible band for the aura starts: the bottom of the pinned day header if one is stuck at the top
     * ([items]: offset and size of each visible item, [isHeader] marks headers), else 0 (the viewport's top).
     */
    fun pinnedHeaderBottom(items: List<Pair<Int, Int>>, isHeader: List<Boolean>): Int =
        items.indices.filter { isHeader[it] && items[it].first <= 0 }.maxOfOrNull { items[it].first + items[it].second }?.coerceAtLeast(0) ?: 0

    /** The aura's trail after a scroll step [dy] (px; the content moved by it): pushed the other way, capped. */
    fun dragAfter(current: Float, dy: Float, max: Float): Float = (current - dy * 0.4f).coerceIn(-max, max)

    /** The jelly's stretch after a scroll step: grows with the speed, either direction, capped. */
    fun wobbleAfter(current: Float, dy: Float, max: Float): Float = (current + kotlin.math.abs(dy) * 0.12f).coerceIn(-max, max)

    /** The color at position [x] (any number, wraps at 1) of a cyclic gradient through [stops]. */
    fun cyclic(stops: List<Color>, x: Float): Color {
        val f = (x % 1f + 1f) % 1f * stops.size
        val i = f.toInt() % stops.size
        return lerp(stops[i], stops[(i + 1) % stops.size], f - f.toInt())
    }
}

/** See [SparkleMath.auraShape]; all lengths in px. */
data class AuraShape(
    val centerShift: Float,
    val halfHeightGrow: Float,
    val inset: Float,
    val radiusTop: Float,
    val radiusBottom: Float,
    val tailTop: Float,
    val tailBottom: Float,
    val roundness: Float,
    val breathe: Float,
)

/** Gold for the legendary frame: bright tones on dark pages, deeper ones that still show on light pages. */
private val GOLD_ON_DARK = listOf(Color(0xFFFFF3B0), Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFF8F00), Color(0xFFFFD54F))
private val GOLD_ON_LIGHT = listOf(Color(0xFFE0A800), Color(0xFFB07800), Color(0xFF8A5A00), Color(0xFFC68A00), Color(0xFFFFC233))

fun goldFor(background: Color): List<Color> = if (background.luminance() < 0.4f) GOLD_ON_DARK else GOLD_ON_LIGHT

/**
 * The super aura's iridescent gold, four tones the glow drifts through: warm gold, apricot, rose gold, champagne.
 * Bright on dark cards, deeper on light ones.
 */
private val AURA_ON_DARK = listOf(Color(0xFFFFCF4A), Color(0xFFFFA968), Color(0xFFFF9EC0), Color(0xFFFFF0B3))
private val AURA_ON_LIGHT = listOf(Color(0xFFE0A000), Color(0xFFE07A30), Color(0xFFD0608F), Color(0xFFC9A227))

fun auraFor(background: Color): List<Color> = if (background.luminance() < 0.4f) AURA_ON_DARK else AURA_ON_LIGHT

/**
 * How far the super aura trails the scrolling list: pushed against the scroll direction by each scroll step, then
 * springing back with a little overshoot. One per list; cards read [offset] only while drawing.
 */
@Stable
class ScrollDrag(private val scope: CoroutineScope, private val maxPx: Float) {
    val offset = Animatable(0f)

    /** The jelly's stretch (positive) and squash (negative); a bouncier spring than [offset]. */
    val wobble = Animatable(0f)
    private var job: Job? = null
    private var wobbleJob: Job? = null

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
            offset.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 70f))
        }
        wobbleJob?.cancel()
        wobbleJob = scope.launch {
            wobble.snapTo(SparkleMath.wobbleAfter(wobble.value, dy, maxPx * 0.6f))
            // Little damping: after the scroll stops it swings through squash and back a few times.
            wobble.animateTo(0f, spring(dampingRatio = 0.18f, stiffness = 110f))
        }
    }
}

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
 * resumed and the card is composed), with no blur and no offscreen layer, so scrolling stays smooth. The super aura
 * is not drawn here but by [SuperAuraOverlay] above the list (it must lie over the neighboring cards).
 */
@Composable
fun FavoriteEffects(
    legendary: Boolean,
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
        modifier
            // The gold glow: one smooth gradient (EdgeGlow), about as strong and wide as the old two rings.
            .then(if (legendary) Modifier.edgeGlow(gold[1], shape, reach = 9.dp, strength = 0.5f, power = 2f) else Modifier)
            .drawWithContent {
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
 * The super aura behind the card: one soft, living glow (the [AuraShader] on Android 13+; below that, fine fading
 * rings as a plain gradient), deformed like jelly by [jelly], plus sparkles that flash up inside it now and then.
 */
internal fun DrawScope.drawSuperAura(
    cardSize: Size,
    r: Float,
    colors: List<Color>,
    shader: AuraShader?,
    timeMs: Long,
    jelly: FloatArray,
    animate: Boolean,
    fadeTop: Float,
    fadeBottom: Float,
) {
    val w = cardSize.width
    val h = cardSize.height
    val center = Offset(w / 2f, h / 2f)
    val fade = 28.dp.toPx()
    val reach = 9.dp.toPx()
    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // The draw area must hold the whole glow: its maximum reach (4 x reach x the living factor's maximum 1.375)
        // plus the jelly's shifts, and a little more.
        val shape = SparkleMath.auraShape(jelly, h, r, reach)
        // Room for the maximum reach, the jelly's move and stretch, the tail and the breathing.
        val margin = SparkleMath.AURA_REACH_FACTOR * reach + kotlin.math.abs(shape.centerShift) + kotlin.math.abs(shape.halfHeightGrow) +
            shape.tailTop + shape.tailBottom + shape.breathe + 4.dp.toPx()
        shader.update(w, h, r, timeMs / 1000f, shape, reach, if (animate) 0.85f else 0.7f, fadeTop, fadeBottom, fade)
        drawRect(shader.brush, topLeft = Offset(-margin, -margin), size = Size(w + 2 * margin, h + 2 * margin))
    } else {
        drawFallbackAura(w, h, r, colors, jelly, reach, fadeTop, fadeBottom, fade)
    }
    if (!animate) return
    // Sparkles inside the glow: each flashes for a moment somewhere along the card, then elsewhere.
    for (i in 0 until 6) {
        val (pos, out, sizeDp, alpha) = SparkleMath.sparkleSpot(i, timeMs).let { listOf(it[0], it[1], it[2], it[3]) }
        if (alpha < 0.03f) continue
        val p = SparkleMath.roundedRectPoint(pos, w, h, r)
        if (p[1] < fadeTop + fade || p[1] > fadeBottom - fade) continue // not in the faded edges either
        val shift = jelly[0] + (jelly[1] - jelly[0]) * (p[1] / h)
        val at = Offset(p[0] + p[2] * out.dp.toPx() - p[2] * jelly[2], p[1] + p[3] * out.dp.toPx() + shift)
        drawStar(at, sizeDp.dp.toPx(), colors[3].copy(alpha = alpha))
    }
}

/**
 * The aura below Android 13 (no runtime shaders): nested filled rounded rectangles from the outside in, whose
 * alphas ([SparkleMath.stackedAlphas]) stack up to exactly the shader's fall-off, so there are no seams (strokes
 * side by side would overlap and draw lines). The card is clipped out; a vertical gradient fades it out toward the
 * pinned header and the viewport's end. Plain gold, no iridescence; the jelly shifts and inset still apply.
 */
private fun DrawScope.drawFallbackAura(
    w: Float, h: Float, r: Float, colors: List<Color>, jelly: FloatArray, reach: Float,
    fadeTop: Float, fadeBottom: Float, fade: Float,
) {
    val steps = 18
    val maxReach = 4f * reach
    val shape = SparkleMath.auraShape(jelly, h, r, reach)
    val distances = (0 until steps).map { maxReach * (steps - it) / steps } // outside in
    val alphas = SparkleMath.stackedAlphas(distances.map { 0.85f * SparkleMath.auraFalloff(it / maxReach) })
    val color = colors[0]
    val fadeSpan = (fadeBottom - fadeTop).coerceAtLeast(1f)
    val brush = Brush.verticalGradient(
        0f to color.copy(alpha = 0f),
        (fade / fadeSpan).coerceIn(0f, 0.5f) to color,
        (1f - fade / fadeSpan).coerceIn(0.5f, 1f) to color,
        1f to color.copy(alpha = 0f),
        startY = fadeTop, endY = fadeBottom,
    )
    val card = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, androidx.compose.ui.geometry.CornerRadius(r))) }
    clipPath(card, androidx.compose.ui.graphics.ClipOp.Difference) {
        distances.forEachIndexed { i, d ->
            // Moved and stretched as a whole, with the organic shape's (larger) corner radius: never a stretched box.
            val round = maxOf(shape.radiusTop, shape.radiusBottom) + d
            drawRoundRect(
                brush,
                topLeft = Offset(-d + shape.inset, -d + shape.centerShift - shape.halfHeightGrow),
                size = Size(w + 2 * d - 2 * shape.inset, h + 2 * d + 2 * shape.halfHeightGrow),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(round),
                alpha = alphas[i],
            )
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
