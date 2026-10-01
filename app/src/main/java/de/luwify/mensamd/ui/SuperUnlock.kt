package de.luwify.mensamd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luwify.mensamd.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** The timeline of the reveal, in seconds (pure, unit-tested). */
object UnlockTimeline {
    const val STAR_END = 0.75f // the shooting star reaches the center
    const val FLASH_END = 1.35f
    const val STARS_START = 1.25f // the five stars pop in one after another
    const val STAR_STEP = 0.17f
    const val TITLE_AT = 2.15f
    const val TOTAL = 3.2f

    /** Pop of an element that starts at [start]: 0, overshoot to 1.3, settle at 1. */
    fun pop(t: Float, start: Float, duration: Float = 0.28f): Float {
        val u = (t - start) / duration
        return when {
            u <= 0f -> 0f
            u < 0.6f -> u / 0.6f * 1.3f
            u < 1f -> 1.3f - 0.3f * (u - 0.6f) / 0.4f
            else -> 1f
        }
    }

    fun fadeIn(t: Float, start: Float, duration: Float = 0.35f): Float = ((t - start) / duration).coerceIn(0f, 1f)
}

/**
 * The reveal when super sparkle is unlocked, in the spirit of a 5-star pull: the screen dims, a gold shooting star
 * falls to the center, a flash, slowly turning rays and a sparkle burst, then five stars pop in one by one and the
 * title. Tap (or back) closes it. Everything is drawn from one timeline; with animations off it shows the end.
 */
@Composable
fun SuperSparkleUnlock(onDone: () -> Unit) {
    val context = LocalContext.current
    val animate = remember(context) { animationsEnabled(context) }
    val time = remember { Animatable(if (animate) 0f else UnlockTimeline.TOTAL) }
    val spin = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    BackHandler(onBack = onDone)
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        time.animateTo(UnlockTimeline.STAR_END, tween((UnlockTimeline.STAR_END * 1000).toInt(), easing = LinearEasing))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        time.animateTo(UnlockTimeline.TOTAL, tween(((UnlockTimeline.TOTAL - UnlockTimeline.STAR_END) * 1000).toInt(), easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        if (animate) spin.animateTo(360f * 40, tween(40 * 9000, easing = LinearEasing)) // the rays keep turning
    }
    val gold = Color(0xFFFFD54F)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { if (time.value > UnlockTimeline.STARS_START) onDone() } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val c = Offset(size.width / 2f, size.height * 0.42f)
            val big = max(size.width, size.height)
            drawRect(Color.Black.copy(alpha = 0.88f * (t / 0.4f).coerceIn(0f, 1f)))

            // Shooting star from the top left to the center, accelerating, with a fading gold tail.
            if (t in 0.08f..UnlockTimeline.STAR_END) {
                val q = ((t - 0.08f) / (UnlockTimeline.STAR_END - 0.08f)).let { it * it }
                val from = Offset(-0.15f * size.width, 0.02f * size.height)
                val head = from + (c - from) * q
                val dir = (c - from) / (c - from).getDistance()
                val tail = head - dir * (0.45f * size.width * (0.4f + 0.6f * q))
                drawLine(Brush.linearGradient(listOf(Color.Transparent, gold.copy(alpha = 0.9f)), tail, head), tail, head, strokeWidth = 5.dp.toPx())
                drawCircle(gold.copy(alpha = 0.35f), 16.dp.toPx(), head)
                drawCircle(Color.White, 5.dp.toPx(), head)
            }

            if (t >= UnlockTimeline.STAR_END) {
                val after = t - UnlockTimeline.STAR_END
                // Glow and turning rays behind the stars.
                val grow = (after / 0.5f).coerceIn(0f, 1f)
                drawCircle(
                    Brush.radialGradient(listOf(gold.copy(alpha = 0.45f), Color(0x00FF9800)), c, 0.55f * size.width * grow + 1f),
                    0.55f * size.width * grow + 1f, c,
                )
                rotate(spin.value % 360f, c) {
                    val rays = 14
                    val len = 0.75f * size.width * grow
                    for (i in 0 until rays) {
                        val a = Math.toRadians(i * 360.0 / rays)
                        val b = Math.toRadians(i * 360.0 / rays + 5.0)
                        val path = Path().apply {
                            moveTo(c.x, c.y)
                            lineTo(c.x + (cos(a) * len).toFloat(), c.y + (sin(a) * len).toFloat())
                            lineTo(c.x + (cos(b) * len).toFloat(), c.y + (sin(b) * len).toFloat())
                            close()
                        }
                        drawPath(path, gold.copy(alpha = 0.13f))
                    }
                }
                // The flash.
                if (t < UnlockTimeline.FLASH_END) {
                    val f = (after / (UnlockTimeline.FLASH_END - UnlockTimeline.STAR_END)).coerceIn(0f, 1f)
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 1f - f), gold.copy(alpha = 0.6f * (1f - f)), Color.Transparent), c, big * f + 1f),
                        big * f + 1f, c,
                    )
                    drawCircle(gold.copy(alpha = 0.8f * (1f - f)), (40f + 260f * f).dp.toPx(), c, style = Stroke((6f * (1f - f)).dp.toPx()))
                }
                // A big sparkle burst from the center.
                val bp = (after / 0.9f).coerceIn(0f, 1f)
                if (bp < 1f) {
                    SparkleMath.burst(bp).forEach { p ->
                        val a = Math.toRadians(p.angleDeg.toDouble() + 7.0)
                        val at = c + Offset((cos(a) * p.distanceDp * 5f).toFloat().dp.toPx(), (sin(a) * p.distanceDp * 5f).toFloat().dp.toPx())
                        drawCircle(gold.copy(alpha = p.alpha), (if (p.star) 4f else 2.5f).dp.toPx(), at)
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 32.dp).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 5) {
                    Text(
                        "★",
                        color = gold,
                        fontSize = 40.sp,
                        modifier = Modifier.graphicsLayer {
                            val s = UnlockTimeline.pop(time.value, UnlockTimeline.STARS_START + i * UnlockTimeline.STAR_STEP)
                            scaleX = s
                            scaleY = s
                            alpha = s.coerceAtMost(1f)
                        },
                    )
                }
            }
            Text(
                stringResource(R.string.super_unlocked_title),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .graphicsLayer { alpha = UnlockTimeline.fadeIn(time.value, UnlockTimeline.TITLE_AT) },
            )
            Text(
                stringResource(R.string.super_unlocked_text),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = UnlockTimeline.fadeIn(time.value, UnlockTimeline.TITLE_AT + 0.2f) },
            )
            Text(
                stringResource(R.string.super_unlocked_hint),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 24.dp).graphicsLayer { alpha = UnlockTimeline.fadeIn(time.value, UnlockTimeline.TITLE_AT + 0.5f) },
            )
        }
    }
}
