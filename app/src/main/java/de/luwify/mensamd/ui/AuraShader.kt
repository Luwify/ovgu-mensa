package de.luwify.mensamd.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

/**
 * The super aura as one GPU shader (Android 13+): a smooth glow around the card's rounded rectangle (signed
 * distance, exponential fall-off, so there are no lines), whose reach swells and shrinks around the card like a
 * living mass, tinted with a slowly drifting iridescent gold. The jelly deformation (top/bottom shift, side inset)
 * comes in as uniforms. Coordinates are the card's own (0,0 = its top left).
 */
private const val AURA_AGSL = """
uniform float2 size;
uniform float radius;
uniform float time;
uniform float top;
uniform float bottom;
uniform float inset;
uniform float reach;
uniform float strength;
layout(color) uniform half4 c0;
layout(color) uniform half4 c1;
layout(color) uniform half4 c2;
layout(color) uniform half4 c3;

float roundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half3 palette(float t) {
    float x = fract(t) * 4.0;
    float f = smoothstep(0.0, 1.0, fract(x));
    half3 a = x < 1.0 ? c0.rgb : (x < 2.0 ? c1.rgb : (x < 3.0 ? c2.rgb : c3.rgb));
    half3 b = x < 1.0 ? c1.rgb : (x < 2.0 ? c2.rgb : (x < 3.0 ? c3.rgb : c0.rgb));
    return mix(a, b, half(f));
}

half4 main(float2 xy) {
    // Jelly: rows near the top move by `top`, near the bottom by `bottom` (the trailing edge lags more).
    float rel = clamp(xy.y / size.y, -0.5, 1.5);
    float2 p = float2(xy.x, xy.y - mix(top, bottom, rel));
    float2 c = size * 0.5;
    float d = max(roundBox(p - c, float2(c.x - inset, c.y), radius), 0.0);
    // A living mass: the reach swells and shrinks around the card, the bulges flowing in both directions.
    float a = atan(p.y - c.y, p.x - c.x);
    float live = 0.62
        + 0.22 * sin(a * 3.0 + time * 1.1)
        + 0.16 * sin(a * 5.0 - time * 1.7 + 1.3)
        + 0.10 * sin(a * 2.0 + time * 0.6 + 4.0);
    float k = exp(-d / (reach * (0.55 + 0.75 * live)));
    // Iridescent gold: the hue drifts around the card and shifts a little with distance.
    half3 col = palette(a / 6.2831853 + time * 0.045 + d / (reach * 7.0));
    float alpha = clamp(k * strength, 0.0, 1.0);
    return half4(col * half(alpha), half(alpha));
}
"""

/** One card's aura shader and the brush that draws it (Android 13+ only). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AuraShader(colors: List<Color>) {
    private val shader = RuntimeShader(AURA_AGSL).apply {
        setColorUniform("c0", colors[0].toArgb())
        setColorUniform("c1", colors[1].toArgb())
        setColorUniform("c2", colors[2].toArgb())
        setColorUniform("c3", colors[3].toArgb())
    }
    val brush = ShaderBrush(shader)

    @Suppress("LongParameterList")
    fun update(w: Float, h: Float, radius: Float, timeS: Float, jelly: FloatArray, reach: Float, strength: Float) {
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("time", timeS)
        shader.setFloatUniform("top", jelly[0])
        shader.setFloatUniform("bottom", jelly[1])
        shader.setFloatUniform("inset", jelly[2])
        shader.setFloatUniform("reach", reach)
        shader.setFloatUniform("strength", strength)
    }
}
