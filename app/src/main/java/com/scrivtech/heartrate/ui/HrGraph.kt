package com.scrivtech.heartrate.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.scrivtech.heartrate.data.HrReading

@Composable
fun HrGraph(
    readings: List<HrReading>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White,
    showZoneColors: Boolean = true,
    yMin: Int = 40,
    yMax: Int = 200,
    showTimeAxis: Boolean = true,
    maxDurationMs: Long = 0L
) {
    if (readings.size < 2) return

    Canvas(modifier = modifier) {
        val paddingRight = 36.dp.toPx()
        val paddingBottom = if (showTimeAxis) 20.dp.toPx() else 0f
        val graphWidth = size.width - paddingRight
        val graphHeight = size.height - paddingBottom

        val displayReadings = if (maxDurationMs > 0 && readings.isNotEmpty()) {
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
        fun yOf(bpm: Int): Float = graphHeight - ((bpm - yMin) / bpmRange) * graphHeight

        val labelPaint = Paint().apply {
            color = android.graphics.Color.argb(80, 255, 255, 255)
            textSize = 10.dp.toPx()
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }

        for (threshold in listOf(60, 100, 130, 160, 180)) {
            if (threshold in yMin..yMax) {
                val y = yOf(threshold)
                drawLine(
                    Color.White.copy(alpha = 0.08f),
                    Offset(0f, y),
                    Offset(graphWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "$threshold",
                    graphWidth + 4.dp.toPx(),
                    y + 4.dp.toPx(),
                    labelPaint
                )
            }
        }

        for (i in 1 until displayReadings.size) {
            val prev = displayReadings[i - 1]
            val curr = displayReadings[i]
            val segColor = if (showZoneColors) hrZoneColor(curr.bpm) else lineColor
            drawLine(
                color = segColor,
                start = Offset(xOf(prev.timestampMs), yOf(prev.bpm)),
                end = Offset(xOf(curr.timestampMs), yOf(curr.bpm)),
                strokeWidth = 2.dp.toPx()
            )
        }

        if (showTimeAxis) {
            val totalSeconds = timeRange / 1000L
            val intervalSeconds = when {
                totalSeconds <= 60 -> 15L
                totalSeconds <= 300 -> 60L
                totalSeconds <= 1800 -> 300L
                else -> 600L
            }
            val timeLabelPaint = Paint().apply {
                color = android.graphics.Color.argb(80, 255, 255, 255)
                textSize = 9.dp.toPx()
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.CENTER
            }
            var t = (timeMin / 1000 / intervalSeconds + 1) * intervalSeconds * 1000
            while (t <= timeMax) {
                val x = xOf(t)
                val elapsed = (t - readings.first().timestampMs) / 1000
                val min = elapsed / 60
                val sec = elapsed % 60
                val label = if (min > 0) "${min}:${sec.toString().padStart(2, '0')}" else "${sec}s"
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x,
                    size.height - 2.dp.toPx(),
                    timeLabelPaint
                )
                t += intervalSeconds * 1000
            }
        }
    }
}
