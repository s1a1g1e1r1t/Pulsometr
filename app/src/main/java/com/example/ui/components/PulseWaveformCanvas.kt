package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.PulseMeasurement

@Composable
fun PulseWaveformCanvas(
    samples: List<Float>,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp),
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // Subtle medical monitor grid
        val gridStepX = w / 8f
        val gridStepY = h / 4f
        for (i in 1..7) {
            drawLine(
                color = gridColor.copy(alpha = 0.45f),
                start = Offset(i * gridStepX, 0f),
                end = Offset(i * gridStepX, h),
                strokeWidth = 1f,
            )
        }
        for (j in 1..3) {
            drawLine(
                color = gridColor.copy(alpha = 0.45f),
                start = Offset(0f, j * gridStepY),
                end = Offset(w, j * gridStepY),
                strokeWidth = 1f,
            )
        }

        if (samples.size < 2) {
            // Flat baseline when idle
            drawLine(
                color = activeColor.copy(alpha = 0.35f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val path = Path()
        val fillPath = Path()
        val maxAmp = h * 0.40f
        val stepX = w / (samples.size - 1).coerceAtLeast(1)

        samples.forEachIndexed { index, raw ->
            val x = index * stepX
            val y = (midY - raw.coerceIn(-1f, 1f) * maxAmp).coerceIn(4f, h - 4f)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (index - 1) * stepX
                val prevY = (midY - samples[index - 1].coerceIn(-1f, 1f) * maxAmp).coerceIn(4f, h - 4f)
                val ctrlX = (prevX + x) / 2f
                path.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
                fillPath.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
            }
        }
        val lastX = (samples.size - 1) * stepX
        val lastY = (midY - samples.last().coerceIn(-1f, 1f) * maxAmp).coerceIn(4f, h - 4f)
        fillPath.lineTo(lastX, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(activeColor.copy(alpha = 0.28f), Color.Transparent),
                startY = 0f,
                endY = h,
            ),
        )
        drawPath(
            path = path,
            color = activeColor,
            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Glowing pulse tip
        drawCircle(
            color = activeColor.copy(alpha = 0.3f),
            radius = 12f,
            center = Offset(lastX, lastY),
        )
        drawCircle(
            color = activeColor,
            radius = 5.5f,
            center = Offset(lastX, lastY),
        )
    }
}

@Composable
fun TrendLineChartCanvas(
    measurements: List<PulseMeasurement>,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    modifier: Modifier = Modifier,
) {
    val chronological = measurements.take(20).reversed()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp),
    ) {
        val w = size.width
        val h = size.height
        val padTop = 16f
        val padBottom = 16f
        val plotH = (h - padTop - padBottom).coerceAtLeast(1f)

        val minBpm = ((chronological.minOfOrNull { it.bpm } ?: 50) - 10).coerceAtLeast(35).toFloat()
        val maxBpm = ((chronological.maxOfOrNull { it.bpm } ?: 120) + 10).coerceAtMost(220).toFloat()
        val range = (maxBpm - minBpm).coerceAtLeast(25f)

        // Horizontal reference lines
        for (i in 0..3) {
            val y = padTop + (plotH * i / 3f)
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }

        if (chronological.isEmpty()) return@Canvas

        if (chronological.size == 1) {
            val y = padTop + plotH * (1f - ((chronological[0].bpm - minBpm) / range))
            drawCircle(color = lineColor, radius = 8f, center = Offset(w / 2f, y))
            return@Canvas
        }

        val stepX = (w - 24f) / (chronological.size - 1)
        val path = Path()
        val areaPath = Path()
        val points = chronological.mapIndexed { idx, item ->
            val x = 12f + idx * stepX
            val y = padTop + plotH * (1f - ((item.bpm.toFloat() - minBpm) / range))
            Offset(x, y.coerceIn(padTop, h - padBottom))
        }

        points.forEachIndexed { idx, pt ->
            if (idx == 0) {
                path.moveTo(pt.x, pt.y)
                areaPath.moveTo(pt.x, h - padBottom)
                areaPath.lineTo(pt.x, pt.y)
            } else {
                val prev = points[idx - 1]
                val cx = (prev.x + pt.x) / 2f
                path.cubicTo(cx, prev.y, cx, pt.y, pt.x, pt.y)
                areaPath.cubicTo(cx, prev.y, cx, pt.y, pt.x, pt.y)
            }
        }
        areaPath.lineTo(points.last().x, h - padBottom)
        areaPath.close()

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.30f), Color.Transparent),
                startY = padTop,
                endY = h - padBottom,
            ),
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        points.forEachIndexed { idx, pt ->
            val zoneColor = chronological[idx].zone().color
            drawCircle(color = zoneColor, radius = 7f, center = pt)
            drawCircle(color = Color.White, radius = 2.8f, center = pt)
        }
    }
}
