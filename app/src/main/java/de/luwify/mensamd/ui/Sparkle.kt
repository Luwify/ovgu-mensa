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
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
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
                    clock = SystemClock.uptimeMillis() // read only while drawing: redraws, never recomposes
                    delay(33)
                }
            }
        }
    }
    Box(
        modifier.drawWithContent {
            if (legendary) drawLegendaryGlow(shape, gold)
            drawContent()
            if (legendary) drawLegendaryEdge(shape, gold, if (animate) clock else 0L, animate)
            val p = burst.progress.value
            if (p < 1f) drawBurst(heartCenter(size), p, gold[1], favoriteColor)
        },
    ) { content() }
}

private fun DrawScope.drawLegendaryGlow(shape: Shape, gold: List<Color>) {
    fun ring(width: Float, alpha: Float) {
        val outline = shape.createOutline(Size(size.width + width, size.height + width), layoutDirection, this)
        translate(-width / 2, -width / 2) { drawOutline(outline, gold[1].copy(alpha = alpha), style = Stroke(width)) }
    }
    ring(7.dp.toPx(), 0.16f)
    ring(3.dp.toPx(), 0.38f)
}

private fun DrawScope.drawLegendaryEdge(shape: Shape, gold: List<Color>, timeMs: Long, animate: Boolean) {
    val width = 2.dp.toPx()
    val turn = SparkleMath.turn(timeMs)
    // 13 stops of the cyclic gold, shifted by the turn: the shine runs around the card.
    val colors = (0..12).map { SparkleMath.cyclic(gold, it / 12f - turn) }
    val outline = shape.createOutline(Size(size.width - width, size.height - width), layoutDirection, this)
    translate(width / 2, width / 2) {
        drawOutline(outline, Brush.sweepGradient(colors, center), style = Stroke(width))
    }
    // Three stars on the edge: top left, right side, bottom.
    val spots = listOf(Offset(0.10f, 0f) to 7f, Offset(1f, 0.70f) to 5.5f, Offset(0.36f, 1f) to 6f)
    spots.forEachIndexed { i, (at, r) ->
        val alpha = if (animate) SparkleMath.twinkle(timeMs, i) else 0.85f
        if (alpha > 0.02f) drawStar(Offset(at.x * size.width, at.y * size.height), r.dp.toPx(), gold[0].copy(alpha = alpha))
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
