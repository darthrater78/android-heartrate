package com.scrivtech.heartrate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.data.HeartRateReadingEntity
import com.scrivtech.heartrate.data.HeartRateRepository
import com.scrivtech.heartrate.data.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionDetailScreen(
    sessionId: Long,
    repository: HeartRateRepository,
    onBack: () -> Unit
) {
    val sessions by repository.sessions.collectAsState(initial = emptyList())
    val readings by repository.getReadingsForSession(sessionId).collectAsState(initial = emptyList())
    val session = sessions.find { it.id == sessionId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text(
                        text = "< Back",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Session Details",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            TextButton(
                onClick = {
                    repository.deleteSession(sessionId)
                    onBack()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350))
            ) {
                Text("Delete", fontSize = 14.sp)
            }
        }

        if (session == null) return

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = session.deviceName,
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Text(
            text = formatSessionDate(session.startTime),
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LargeStat("Avg BPM", session.avgBpm?.toString() ?: "--")
            LargeStat("Min", session.minBpm?.toString() ?: "--")
            LargeStat("Max", session.maxBpm?.toString() ?: "--")
            LargeStat("Duration", formatSessionDuration(session))
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (readings.size >= 2) {
            Text(
                text = "Heart Rate",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            HeartRateChart(readings = readings)
        } else {
            Text(
                text = "Not enough data to display chart.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.3f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "${session.readingCount} readings recorded",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun LargeStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE53935)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun HeartRateChart(readings: List<HeartRateReadingEntity>) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        val paddingLeft = 40.dp.toPx()
        val paddingBottom = 24.dp.toPx()
        val paddingTop = 8.dp.toPx()
        val chartWidth = size.width - paddingLeft
        val chartHeight = size.height - paddingBottom - paddingTop

        val minBpm = (readings.minOf { it.bpm } - 5).coerceAtLeast(0).toFloat()
        val maxBpm = (readings.maxOf { it.bpm } + 5).toFloat()
        val bpmRange = (maxBpm - minBpm).coerceAtLeast(10f)

        val startTime = readings.first().timestamp
        val endTime = readings.last().timestamp
        val duration = (endTime - startTime).coerceAtLeast(1L)

        // Grid lines
        val gridColor = Color.White.copy(alpha = 0.1f)
        val labelStyle = TextStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val y = paddingTop + chartHeight * (1f - i.toFloat() / gridSteps)
            drawLine(gridColor, Offset(paddingLeft, y), Offset(size.width, y), strokeWidth = 1f)
            val bpmLabel = (minBpm + bpmRange * i / gridSteps).toInt().toString()
            drawText(
                textMeasurer = textMeasurer,
                text = bpmLabel,
                topLeft = Offset(0f, y - 6.dp.toPx()),
                style = labelStyle
            )
        }

        // Time labels
        val totalMinutes = duration / 60_000
        val timeSteps = when {
            totalMinutes < 5 -> 1
            totalMinutes < 30 -> 5
            else -> 10
        }.coerceAtLeast(1)
        var minute = 0L
        while (minute <= totalMinutes) {
            val x = paddingLeft + (minute.toFloat() / totalMinutes.coerceAtLeast(1)) * chartWidth
            drawText(
                textMeasurer = textMeasurer,
                text = "${minute}m",
                topLeft = Offset(x, size.height - paddingBottom + 4.dp.toPx()),
                style = labelStyle
            )
            minute += timeSteps
        }

        // Heart rate line
        val path = Path()
        readings.forEachIndexed { index, reading ->
            val x = paddingLeft + ((reading.timestamp - startTime).toFloat() / duration) * chartWidth
            val y = paddingTop + chartHeight * (1f - (reading.bpm - minBpm) / bpmRange)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color(0xFFE53935), style = Stroke(width = 2.dp.toPx()))

        // Average line
        val avgBpm = readings.map { it.bpm }.average().toFloat()
        val avgY = paddingTop + chartHeight * (1f - (avgBpm - minBpm) / bpmRange)
        drawLine(
            Color(0xFFE53935).copy(alpha = 0.3f),
            Offset(paddingLeft, avgY),
            Offset(size.width, avgY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(8.dp.toPx(), 4.dp.toPx())
            )
        )
    }
}

private fun formatSessionDate(timestamp: Long): String {
    val format = SimpleDateFormat("EEEE, MMM d 'at' h:mm a", Locale.getDefault())
    return format.format(Date(timestamp))
}

private fun formatSessionDuration(session: SessionEntity): String {
    val end = session.endTime ?: return "--"
    val seconds = (end - session.startTime) / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
