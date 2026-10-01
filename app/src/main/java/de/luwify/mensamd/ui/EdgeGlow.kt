package de.luwify.mensamd.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

/**
 * A static glow around a card (the gold "Favoriten funkeln" glow, the AMOLED and custom-theme glows), drawn as one
 * smooth gradient: on Android 13+ an AGSL shader (rounded-rect distance, fall-off `strength * (1 - d/reach)^power`
 * reaching exactly zero, triangular dither of 1/255, so 8-bit color shows no steps); below that thin rings side by
 * side, each at the curve's alpha. It replaces stacked
 * strokes, which showed as separate bands with a hard outer edge. Drawn behind the card; inside the card it is at
 * full strength (hidden by the opaque card, so no dark seam can appear at the card's anti-aliased edge).
 */
private const val EDGE_GLOW_AGSL = """
uniform float2 size;
uniform float radius;
uniform float reach;
uniform float strength;
uniform float power;
layout(color) uniform half4 color;

float roundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float ign(float2 xy) {
    return fract(52.9829189 * fract(dot(xy, float2(0.06711056, 0.00583715))));
}

half4 main(float2 xy) {
    float d = max(roundBox(xy - size * 0.5, size * 0.5, radius), 0.0);
    float a = pow(max(1.0 - d / reach, 0.0), power) * strength * float(color.a);
    if (a > 0.0) {
        a = clamp(a + (ign(xy) + ign(xy + float2(17.0, 59.0)) - 1.0) / 255.0, 0.0, 1.0);
    }
    return half4(color.rgb * half(a), half(a));
}
"""

/** Fall-off of the edge glow at [x] = distance / reach (pure, mirrors the shader). */
fun edgeGlowFalloff(x: Float, power: Float): Float = if (x >= 1f) 0f else Math.pow((1f - x.coerceAtLeast(0f)).toDouble(), power.toDouble()).toFloat()

/** Only for measuring the fallback's cost on an Android 13+ test build; always false in commits. */
internal const val MEASURE_EDGE_GLOW_FALLBACK = false

/** Number of rings of the below-Android-13 fallback: more = smaller alpha steps, but one more stroke each. */
const val EDGE_GLOW_FALLBACK_FILLS = 14

/**
 * Draws the edge glow behind the content, for a card with [shape]: [color] (its alpha included) times [strength]
 * at the card's edge, falling to zero at [reach]. Built once per size (drawWithCache), so a static glow costs one
 * draw per frame. [forceFallback] is for measuring the fallback's cost on a newer phone.
 */
fun Modifier.edgeGlow(
    color: Color,
    shape: Shape,
    reach: Dp,
    strength: Float,
    power: Float,
    forceFallback: Boolean = MEASURE_EDGE_GLOW_FALLBACK,
): Modifier =
    drawWithCache {
        val r = (shape.createOutline(size, layoutDirection, this) as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        val reachPx = reach.toPx()
        val margin = reachPx + 2f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !forceFallback) {
            val shader = RuntimeShader(EDGE_GLOW_AGSL).apply {
                setFloatUniform("size", size.width, size.height)
                setFloatUniform("radius", r)
                setFloatUniform("reach", reachPx)
                setFloatUniform("strength", strength)
                setFloatUniform("power", power)
                setColorUniform("color", color.toArgb())
            }
            val brush = ShaderBrush(shader)
            onDrawBehind { drawRect(brush, topLeft = Offset(-margin, -margin), size = Size(size.width + 2 * margin, size.height + 2 * margin)) }
        } else {
            // Below Android 13: rings side by side, each exactly as wide as the gap to the next (no overlap: the old
            // strokes overlapped by 0.6 px and drew a line at every seam), each at the curve's alpha for its middle.
            // Measured alternatives (AMOLED list, forced on a newer phone): nested full fills 3 % janky frames
            // (they paint the card's area 14 times), even-odd ring paths 4-5 % (path rendering); these strokes are
            // the cheapest.
            val n = EDGE_GLOW_FALLBACK_FILLS
            val step = reachPx / n
            val solid = color.copy(alpha = 1f)
            val rings = (0 until n).map { i ->
                val mid = (i + 0.5f) * step
                mid to (strength * color.alpha * edgeGlowFalloff(mid / reachPx, power)).coerceIn(0f, 1f)
            }
            val stroke = Stroke(step)
            onDrawBehind {
                for ((mid, alpha) in rings) {
                    drawRoundRect(
                        solid,
                        topLeft = Offset(-mid, -mid),
                        size = Size(size.width + 2 * mid, size.height + 2 * mid),
                        cornerRadius = CornerRadius(r + mid),
                        alpha = alpha,
                        style = stroke,
                    )
                }
            }
        }
    }
