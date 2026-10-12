package com.scrivtech.heartrate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.scrivtech.heartrate.data.HrReading
import com.scrivtech.heartrate.data.HrZone
import com.scrivtech.heartrate.data.lowerBpm
import com.scrivtech.heartrate.data.upperBpm
import com.scrivtech.heartrate.ui.theme.Hairline
import com.scrivtech.heartrate.ui.theme.HrmType
import com.scrivtech.heartrate.ui.theme.TextMuted
import com.scrivtech.heartrate.ui.theme.TextPrimary

/**
 * Heart rate over time.
 *
 * The trace itself is never coloured. With a [maxHr] the user's zones are drawn behind it
 * as tinted bands and the axis is labelled at the zone boundaries, so the same age-based
 * model that names the current zone also explains the graph. Without one there are no
 * zones to show, and the graph falls back to plain reference lines.
 */
@Composable
fun HrGraph(
    readings: List<HrReading>,
    modifier: Modifier = Modifier,
    maxHr: Int? = null,
    yMin: Int = 40,
    yMax: Int = 200,
    showTimeAxis: Boolean = true,
    maxDurationMs: Long = 0L
) {
    if (readings.size < 2) return

    val measurer = rememberTextMeasurer()
    val labelStyle = HrmType.NumeralMd.copy(color = TextMuted)

    Canvas(modifier = modifier) {
        val paddingRight = 36.dp.toPx()
        val paddingBottom = if (showTimeAxis) 24.dp.toPx() else 0f
        val graphWidth = size.width - paddingRight
        val graphHeight = size.height - paddingBottom

        val displayReadings = if (maxDurationMs > 0) {
            val cutoff = readings.last().timestampMs - maxDurationMs
            readings.filter { it.timestampMs >= cutoff }
        } else {
            readings
        }

        if (displayReadings.size < 2) return@Canvas

        val timeMin = displayReadings.first().timestampMs
        val timeMax = displayReadings.last().timestampMs
        val timeRange = (timeMax - timeMin).coerceAtLeast(1L)
        val bpmRange = (yMax - yMin).toFloat()

        fun xOf(ts: Long): Float = ((ts - timeMin) / timeRange.toFloat()) * graphWidth
        fun yOf(bpm: Int): Float =
            graphHeight - ((bpm.coerceIn(yMin, yMax) - yMin) / bpmRange) * graphHeight

        fun axisLabel(bpm: Int) {
            val label = measurer.measure("$bpm", labelStyle)
            val y = (yOf(bpm) - label.size.height / 2f)
                .coerceIn(0f, graphHeight - label.size.height)
            drawText(label, topLeft = Offset(graphWidth + 6.dp.toPx(), y))
        }

        if (maxHr != null) {
            HrZone.entries.forEach { zone ->
                val top = yOf(zone.upperBpm(maxHr)?.plus(1) ?: yMax)
                val bottom = yOf(zone.lowerBpm(maxHr))
                drawRect(
                    color = zoneColor(zone),
                    topLeft = Offset(0f, top),
                    size = Size(graphWidth, (bottom - top).coerceAtLeast(0f)),
                    alpha = ZONE_BAND_ALPHA
                )
            }
            HrZone.entries.drop(1).forEach { axisLabel(it.lowerBpm(maxHr)) }
        } else {
            for (threshold in listOf(60, 100, 140, 180)) {
                if (threshold in yMin..yMax) {
                    val y = yOf(threshold)
                    drawLine(Hairline, Offset(0f, y), Offset(graphWidth, y), strokeWidth = 1.dp.toPx())
                    axisLabel(threshold)
                }
            }
        }

        val trace = Path().apply {
            displayReadings.forEachIndexed { i, reading ->
                val x = xOf(reading.timestampMs)
                val y = yOf(reading.bpm)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = trace,
            color = TextPrimary,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val last = displayReadings.last()
        drawCircle(TextPrimary, radius = 4.dp.toPx(), center = Offset(xOf(last.timestampMs), yOf(last.bpm)))

        if (showTimeAxis) {
            val totalSeconds = timeRange / 1000L
            val intervalSeconds = when {
                totalSeconds <= 60 -> 15L
                totalSeconds <= 300 -> 60L
                totalSeconds <= 1800 -> 300L
                else -> 600L
            }
            var t = (timeMin / 1000 / intervalSeconds + 1) * intervalSeconds * 1000
            while (t <= timeMax) {
                val elapsed = (t - readings.first().timestampMs) / 1000
                val min = elapsed / 60
                val sec = elapsed % 60
                val text = if (min > 0) "${min}:${sec.toString().padStart(2, '0')}" else "${sec}s"
                val label = measurer.measure(text, labelStyle)
                val x = (xOf(t) - label.size.width / 2f)
                    .coerceIn(0f, (graphWidth - label.size.width).coerceAtLeast(0f))
                drawText(label, topLeft = Offset(x, size.height - label.size.height))
                t += intervalSeconds * 1000
            }
        }
    }
}
