package com.diprish.utilitymeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.diprish.utilitymeter.ui.formatNumber
import kotlin.math.max

/** One bar in the usage chart: consumption over a period with a short label. */
data class UsageBar(
    val label: String,
    val value: Double,
)

/**
 * Simple, dependency-free bar chart. Draws one bar per [UsageBar], scaled to
 * the largest value, with the value printed above each bar and the label below.
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
    val textMeasurer = rememberTextMeasurer()
    val maxValue = max(bars.maxOf { it.value }, 0.0001)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(top = 8.dp),
        ) {
            drawBars(bars, maxValue, barColor, labelColor, textMeasurer)
        }
        Text(
            text = "Usage in $unit per period",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun DrawScope.drawBars(
    bars: List<UsageBar>,
    maxValue: Double,
    barColor: Color,
    labelColor: Color,
    textMeasurer: TextMeasurer,
) {
    val count = bars.size
    val topPadding = 28f      // room for value labels
    val bottomPadding = 34f   // room for x-axis labels
    val chartHeight = size.height - topPadding - bottomPadding
    val slot = size.width / count
    val barWidth = slot * 0.55f

    // Baseline
    drawLine(
        color = labelColor.copy(alpha = 0.4f),
        start = Offset(0f, topPadding + chartHeight),
        end = Offset(size.width, topPadding + chartHeight),
        strokeWidth = 2f,
    )

    bars.forEachIndexed { index, bar ->
        val fraction = (bar.value / maxValue).toFloat().coerceIn(0f, 1f)
        val barHeight = chartHeight * fraction
        val left = slot * index + (slot - barWidth) / 2f
        val top = topPadding + (chartHeight - barHeight)

        drawRoundRect(
            color = barColor,
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(6f, 6f),
        )

        // Value above the bar
        val valueLayout = textMeasurer.measure(formatNumber(bar.value))
        drawText(
            textLayoutResult = valueLayout,
            color = labelColor,
            topLeft = Offset(
                x = slot * index + (slot - valueLayout.size.width) / 2f,
                y = top - valueLayout.size.height - 2f,
            ),
        )

        // Period label below the baseline
        val labelLayout = textMeasurer.measure(bar.label)
        drawText(
            textLayoutResult = labelLayout,
            color = labelColor,
            topLeft = Offset(
                x = slot * index + (slot - labelLayout.size.width) / 2f,
                y = topPadding + chartHeight + 6f,
            ),
        )
    }
}
