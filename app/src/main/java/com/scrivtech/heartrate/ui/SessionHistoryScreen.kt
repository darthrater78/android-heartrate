package com.scrivtech.heartrate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.data.HrSession
import com.scrivtech.heartrate.data.Storage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionHistoryScreen(
    storage: Storage,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var sessions by remember { mutableStateOf(storage.getSessions()) }
    var renamingSession by remember { mutableStateOf<HrSession?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deletingSession by remember { mutableStateOf<HrSession?>(null) }

    if (renamingSession != null) {
        AlertDialog(
            onDismissRequest = {
                renamingSession = null
                renameInput = ""
            },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(text = "Rename Session", color = Color.White)
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    placeholder = {
                        Text(
                            text = "Session name",
                            color = Color.White.copy(alpha = 0.3f)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFE53935),
                        focusedBorderColor = Color(0xFFE53935),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renamingSession?.let { session ->
                            storage.renameSession(session.id, renameInput.trim())
                            sessions = storage.getSessions()
                        }
                        renamingSession = null
                        renameInput = ""
                    }
                ) {
                    Text("Save", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        renamingSession = null
                        renameInput = ""
                    }
                ) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }

    if (deletingSession != null) {
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(text = "Delete Session", color = Color.White)
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this session? This cannot be undone.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingSession?.let { session ->
                            storage.deleteSession(session.id)
                            sessions = storage.getSessions()
                        }
                        deletingSession = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSession = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "← Back",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Session History",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No sessions recorded yet",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        } else {
            LazyColumn {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onRename = {
                            renamingSession = session
                            renameInput = session.sessionName
                        },
                        onDelete = { deletingSession = session }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: HrSession, onRename: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }
    val dateStr = remember(session.startTime) { dateFormat.format(Date(session.startTime)) }
    val durationStr = remember(session.durationMs) { formatDuration(session.durationMs) }
    val displayName = session.sessionName.ifEmpty { dateStr }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onRename() }
                ) {
                    Text(
                        text = displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    if (session.sessionName.isNotEmpty()) {
                        Text(
                            text = "$dateStr  ·  ${session.deviceName}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    } else {
                        Text(
                            text = session.deviceName,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = durationStr,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Naming happens here rather than at connect, so this needs to be
                    // visible — tapping the title also works but nothing advertises it.
                    Text(
                        text = "Rename",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { onRename() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Delete",
                        fontSize = 12.sp,
                        color = Color(0xFFE53935).copy(alpha = 0.6f),
                        modifier = Modifier.clickable { onDelete() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Avg", session.avgBpm)
                StatItem("Max", session.maxBpm)
                StatItem("Min", session.minBpm)
            }

            if (session.readings.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                HrGraph(
                    readings = session.readings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    showZoneColors = true,
                    showTimeAxis = true
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = hrZoneColor(value)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
