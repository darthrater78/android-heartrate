package com.scrivtech.heartrate.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.data.HeartRateRepository
import com.scrivtech.heartrate.data.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    repository: HeartRateRepository,
    onBack: () -> Unit,
    onSessionClick: (Long) -> Unit
) {
    val sessions by repository.sessions.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "< Back",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Session History",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (sessions.isEmpty()) {
            Text(
                text = "No sessions recorded yet.\nConnect to a device to start tracking.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.4f),
                lineHeight = 24.sp
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onClick = { onSessionClick(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.deviceName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = formatDate(session.startTime),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Duration", formatDuration(session.startTime, session.endTime))
                StatItem("Avg", session.avgBpm?.let { "$it" } ?: "--")
                StatItem("Min", session.minBpm?.let { "$it" } ?: "--")
                StatItem("Max", session.maxBpm?.let { "$it" } ?: "--")
                StatItem("Readings", "${session.readingCount}")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE53935)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return format.format(Date(timestamp))
}

private fun formatDuration(startTime: Long, endTime: Long?): String {
    val end = endTime ?: return "Active"
    val seconds = (end - startTime) / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}
