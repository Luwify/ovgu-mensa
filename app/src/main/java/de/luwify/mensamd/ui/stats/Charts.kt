package de.luwify.mensamd.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

/*
 * Small Canvas charts (no chart library): thin columns (<= 24 dp, 4 dp rounded top, square at the baseline, 2 dp gap
 * between neighbors), a 2 dp step line with a ringed end dot and a 10 % wash, hairline baseline and one top gridline.
 * Tapping selects a column / point; its values show in the readout line above the chart (the chart's "tooltip").
 * Text uses text colors, never the series color.
 */

/** One column group: [label] under it, one value per series. */
data class BarGroup(val label: String, val values: List<Int>, val detail: String)

/** A series key for the legend (two or more series). */
data class SeriesKey(val name: String, val color: Color)

@Composable
fun BarChart(
    groups: List<BarGroup>,
    colors: List<Color>,
    axisLabel: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
    legend: List<SeriesKey> = emptyList(),
) {
    if (groups.isEmpty()) return
    var selected by remember(groups.size) { mutableIntStateOf(groups.lastIndex) }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelSmall.copy(color = textColor)
    val max = niceMax(groups.maxOf { it.values.maxOrNull() ?: 0 })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (legend.size >= 2) Legend(legend)
        Text(groups.getOrNull(selected)?.detail ?: "", style = MaterialTheme.typography.bodyMedium)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = description }
                .pointerInput(groups) {
                    detectTapGestures { pos ->
                        val slot = size.width / groups.size
                        selected = (pos.x / slot).toInt().coerceIn(0, groups.lastIndex)
                    }
                },
        ) {
            val labelHeight = 18.dp.toPx()
            val top = 20.dp.toPx()
            val base = size.height - labelHeight
            val plot = base - top
            val slot = size.width / groups.size
            // The selected group: a quiet band behind it (its values are in the readout above).
            drawRoundRect(
                highlight, topLeft = Offset(selected * slot + 2.dp.toPx(), top - 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(slot - 4.dp.toPx(), base - top + 4.dp.toPx() + labelHeight),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            // Recessive grid: the baseline and one line at the axis maximum, labeled.
            drawLine(grid, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(0f, top), Offset(size.width, top), strokeWidth = 1.dp.toPx())
            drawText(measurer, axisLabel(max), Offset(0f, 0f), style = small)
            val series = groups.first().values.size
            val gap = 2.dp.toPx()
            val barWidth = minOf(24.dp.toPx(), (slot * 0.7f - gap * (series - 1)) / series)
            val radius = 4.dp.toPx()
            groups.forEachIndexed { i, g ->
                val groupWidth = barWidth * series + gap * (series - 1)
                var x = i * slot + (slot - groupWidth) / 2
                g.values.forEachIndexed { s, v ->
                    if (v > 0 && max > 0) {
                        val h = maxOf(2.dp.toPx(), plot * v / max)
                        val color = colors[s % colors.size]
                        // Rounded at the data end only: a round rect whose bottom corners are square.
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = x, top = base - h, right = x + barWidth, bottom = base,
                                    topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                                    bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                                ),
                            )
                        }
                        drawPath(path, color)
                    }
                    x += barWidth + gap
                }
                // Labels: all when they fit, otherwise every other one (always the selected one).
                val every = if (slot < 28.dp.toPx()) 2 else 1
                if (i % every == (groups.lastIndex % every) || i == selected) {
                    val layout = measurer.measure(g.label, if (i == selected) small.copy(fontWeight = FontWeight.Bold) else small)
                    drawText(layout, topLeft = Offset(i * slot + (slot - layout.size.width) / 2, base + 3.dp.toPx()))
                }
            }
        }
    }
}

/** A step line of [points] (x = time, y = value) with a ringed dot at the selected point. */
@Composable
fun StepLineChart(
    points: List<Pair<Long, Int>>,
    color: Color,
    detail: (Int) -> String,
    axisLabel: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return
    var selected by remember(points.size) { mutableIntStateOf(points.lastIndex) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val max = niceMax(points.maxOf { it.second })
    val t0 = points.first().first
    val span = (points.last().first - t0).coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(detail(selected), style = MaterialTheme.typography.bodyMedium)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { pos ->
                        val t = t0 + (pos.x / size.width * span).toLong()
                        selected = points.indices.minBy { kotlin.math.abs(points[it].first - t) }
                    }
                },
        ) {
            val top = 20.dp.toPx()
            val base = size.height - 4.dp.toPx()
            val inset = 6.dp.toPx()
            fun x(t: Long) = inset + (size.width - 2 * inset) * (t - t0) / span
            fun y(v: Int) = base - (base - top) * v.coerceAtLeast(0) / max.coerceAtLeast(1)
            drawLine(grid, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(0f, top), Offset(size.width, top), strokeWidth = 1.dp.toPx())
            drawText(measurer, axisLabel(max), Offset(0f, 0f), style = small)
            val line = Path()
            points.forEachIndexed { i, (t, v) ->
                if (i == 0) line.moveTo(x(t), y(v)) else {
                    line.lineTo(x(t), y(points[i - 1].second))
                    line.lineTo(x(t), y(v))
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(points.last().first), base)
                lineTo(x(t0), base)
                close()
            }
            drawPath(area, color.copy(alpha = 0.10f))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val (st, sv) = points[selected]
            drawCircle(surface, radius = 6.dp.toPx(), center = Offset(x(st), y(sv)))
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(x(st), y(sv)))
        }
    }
}

@Composable
fun Legend(keys: List<SeriesKey>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        keys.forEach { key ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(key.color))
                Text(key.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The axis maximum: [value] rounded up to a clean whole-euro step (1, 2, 3, 4, 5, 6, 8 x 10^n). Mills in, mills out. */
fun niceMax(value: Int): Int {
    if (value <= 0) return 1_000
    val euros = (value + 999) / 1_000
    var step = 1
    while (true) {
        for (m in intArrayOf(1, 2, 3, 4, 5, 6, 8)) if (m * step >= euros) return m * step * 1_000
        step *= 10
    }
}
