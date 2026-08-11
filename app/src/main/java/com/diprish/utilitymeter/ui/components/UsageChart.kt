package com.diprish.utilitymeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diprish.utilitymeter.ui.formatNumber
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/** One bar in the usage chart: consumption over a period with a short label. */
data class UsageBar(
    val label: String,
    val value: Double,
)

/**
 * Dependency-free bar chart of usage per period. Draws a labelled y-axis with
 * gridlines, gradient rounded bars with their value on top, an average
 * reference line, and the period labels underneath.
 */
@Composable
fun UsageChart(
    bars: List<UsageBar>,
    unit: String,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (bars.isEmpty()) {
        Text(
            text = "Add at least two readings to see usage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 24.dp),
        )
        return
    }

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = labelColor.copy(alpha = 0.18f)
    val avgColor = labelColor.copy(alpha = 0.75f)
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 11.sp, color = labelColor)
    val valueStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = labelColor)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(top = 8.dp),
    ) {
        drawUsage(bars, barColor, gridColor, avgColor, textMeasurer, axisStyle, valueStyle)
    }
}

private fun DrawScope.drawUsage(
    bars: List<UsageBar>,
    barColor: Color,
    gridColor: Color,
    avgColor: Color,
    textMeasurer: TextMeasurer,
    axisStyle: TextStyle,
    valueStyle: TextStyle,
) {
    val rawMax = bars.maxOf { it.value }.coerceAtLeast(0.0001)
    val niceMax = niceCeil(rawMax)
    val gridCount = 4

    // Pre-measure y-axis labels (top -> bottom) to reserve left space.
    val yLabelLayouts = (0..gridCount).map { i ->
        val v = niceMax * (gridCount - i) / gridCount
        textMeasurer.measure(formatNumber(v), axisStyle)
    }
    val leftPad = yLabelLayouts.maxOf { it.size.width }.toFloat() + 12f
    val topPad = 24f
    val bottomPad = 42f

    val chartTop = topPad
    val chartBottom = size.height - bottomPad
    val chartHeight = chartBottom - chartTop
    val chartLeft = leftPad
    val chartRight = size.width
    val chartWidth = chartRight - chartLeft

    // Gridlines + y-axis labels.
    for (i in 0..gridCount) {
        val y = chartTop + chartHeight * i / gridCount
        drawLine(gridColor, Offset(chartLeft, y), Offset(chartRight, y), strokeWidth = 1.5f)
        val layout = yLabelLayouts[i]
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(chartLeft - layout.size.width - 6f, y - layout.size.height / 2f),
        )
    }

    // Bars.
    val count = bars.size
    val slot = chartWidth / count
    val barWidth = min(slot * 0.5f, 64f)
    bars.forEachIndexed { index, bar ->
        val fraction = (bar.value / niceMax).toFloat().coerceIn(0f, 1f)
        val barHeight = chartHeight * fraction
        val left = chartLeft + slot * index + (slot - barWidth) / 2f
        val top = chartBottom - barHeight

        val brush = Brush.verticalGradient(
            colors = listOf(barColor, barColor.copy(alpha = 0.55f)),
            startY = top,
            endY = chartBottom,
        )
        drawRoundedTopBar(left, top, barWidth, barHeight, brush, cornerRadius = 10f)

        val valueLayout = textMeasurer.measure(formatNumber(bar.value), valueStyle)
        drawText(
            textLayoutResult = valueLayout,
            topLeft = Offset(
                x = chartLeft + slot * index + (slot - valueLayout.size.width) / 2f,
                y = top - valueLayout.size.height - 4f,
            ),
        )

        val labelLayout = textMeasurer.measure(bar.label, axisStyle)
        drawText(
            textLayoutResult = labelLayout,
            topLeft = Offset(
                x = chartLeft + slot * index + (slot - labelLayout.size.width) / 2f,
                y = chartBottom + 10f,
            ),
        )
    }

    // Average reference line (only meaningful with more than one bar).
    if (bars.size > 1) {
        val avg = bars.sumOf { it.value } / bars.size
        val y = chartBottom - chartHeight * (avg / niceMax).toFloat().coerceIn(0f, 1f)
        drawLine(
            color = avgColor,
            start = Offset(chartLeft, y),
            end = Offset(chartRight, y),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        )
        val avgLayout = textMeasurer.measure("avg ${formatNumber(avg)}", axisStyle)
        drawText(
            textLayoutResult = avgLayout,
            topLeft = Offset(chartRight - avgLayout.size.width, y - avgLayout.size.height - 2f),
        )
    }
}

/** Draws a bar whose top corners are rounded and bottom sits flat on the axis. */
private fun DrawScope.drawRoundedTopBar(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    brush: Brush,
    cornerRadius: Float,
) {
    val r = min(cornerRadius, min(width / 2f, height))
    val bottom = top + height
    val path = Path().apply {
        moveTo(left, bottom)
        lineTo(left, top + r)
        quadraticBezierTo(left, top, left + r, top)
        lineTo(left + width - r, top)
        quadraticBezierTo(left + width, top, left + width, top + r)
        lineTo(left + width, bottom)
        close()
    }
    drawPath(path, brush)
}

/** Round a value up to a "nice" axis maximum (1, 2, 2.5, 5 × 10^n). */
private fun niceCeil(value: Double): Double {
    if (value <= 0) return 1.0
    val exponent = floor(log10(value))
    val base = 10.0.pow(exponent)
    val fraction = value / base
    val niceFraction = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return niceFraction * base
}
