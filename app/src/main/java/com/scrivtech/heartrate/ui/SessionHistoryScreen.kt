package com.scrivtech.heartrate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.scrivtech.heartrate.R
import com.scrivtech.heartrate.data.HrSession
import com.scrivtech.heartrate.data.Storage
import com.scrivtech.heartrate.data.maxHrForAge
import com.scrivtech.heartrate.data.timeInZones
import com.scrivtech.heartrate.ui.theme.Dimens
import com.scrivtech.heartrate.ui.theme.Ground
import com.scrivtech.heartrate.ui.theme.Hairline
import com.scrivtech.heartrate.ui.theme.HrmType
import com.scrivtech.heartrate.ui.theme.TextMuted
import com.scrivtech.heartrate.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionHistoryScreen(
    storage: Storage,
    onBack: () -> Unit
) {
    var sessions by remember { mutableStateOf(storage.getSessions()) }
    // Zones are derived at render time from the stored readings, so sessions recorded
    // before an age was set still show a breakdown once one is, and changing the age
    // re-scores the whole history rather than leaving old sessions on stale bands.
    val maxHr = remember { storage.getAge()?.let { maxHrForAge(it) } }
    var renamingSession by remember { mutableStateOf<HrSession?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deletingSession by remember { mutableStateOf<HrSession?>(null) }

    if (renamingSession != null) {
        val dismiss = {
            renamingSession = null
            renameInput = ""
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(text = "Rename session") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    // Bounded so a pasted wall of text cannot bloat the stored history.
                    onValueChange = { renameInput = it.take(MAX_SESSION_NAME_LENGTH) },
                    label = { Text(text = "Session name") },
                    singleLine = true,
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
                        dismiss()
                    }
                ) {
                    Text(text = "Save")
                }
            },
            dismissButton = { MutedTextButton(text = "Cancel", onClick = dismiss) }
        )
    }

    if (deletingSession != null) {
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            title = { Text(text = "Delete session") },
            text = { Text(text = "This session will be deleted. This cannot be undone.") },
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
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                MutedTextButton(text = "Cancel", onClick = { deletingSession = null })
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ground)
            .safeDrawingPadding()
            .padding(horizontal = Dimens.ScreenEdge)
    ) {
        IconButton(
            onClick = onBack,
            // Pulls the 48dp target left so the arrow itself sits on the screen edge.
            modifier = Modifier
                .padding(top = Dimens.Sm)
                .offset(x = (-12).dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = "Session history",
            style = HrmType.Title,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = Dimens.Md)
        )
        HorizontalDivider(color = Hairline)

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No sessions recorded yet",
                    style = HrmType.Body,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn {
                items(sessions, key = { it.id }) { session ->
                    SessionEntry(
                        session = session,
                        maxHr = maxHr,
                        onRename = {
                            renamingSession = session
                            renameInput = session.sessionName
                        },
                        onDelete = { deletingSession = session }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionEntry(
    session: HrSession,
    maxHr: Int?,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }
    val dateStr = remember(session.startTime) { dateFormat.format(Date(session.startTime)) }
    val durationStr = remember(session.durationMs) { formatDuration(session.durationMs) }

    Column(modifier = Modifier.padding(top = Dimens.ScreenEdge)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.sessionName.ifEmpty { dateStr },
                    style = HrmType.LabelLg,
                    color = TextPrimary
                )
                Text(
                    text = if (session.sessionName.isNotEmpty()) {
                        "$dateStr · ${session.deviceName}"
                    } else {
                        session.deviceName
                    },
                    style = HrmType.BodySm,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.width(Dimens.Md))
            Text(text = durationStr, style = HrmType.NumeralLg, color = TextPrimary)
        }

        Spacer(modifier = Modifier.height(Dimens.Md))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Section)) {
            StatItem("Avg", session.avgBpm)
            StatItem("Max", session.maxBpm)
            StatItem("Min", session.minBpm)
        }

        if (maxHr != null) {
            val zoneTimes = remember(session.id, maxHr) {
                timeInZones(session.readings, maxHr)
            }
            Spacer(modifier = Modifier.height(Dimens.ScreenEdge))
            ZoneBreakdown(timeInZones = zoneTimes)
        }

        if (session.readings.size >= 2) {
            Spacer(modifier = Modifier.height(Dimens.ScreenEdge))
            HrGraph(
                readings = session.readings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxHr = maxHr
            )
        }

        // Rename and Delete sit at opposite ends of a full-height row, so a destructive
        // action can never be hit by a thumb aiming for the benign one.
        Row(modifier = Modifier.fillMaxWidth()) {
            MutedTextButton(
                text = "Rename",
                onClick = onRename,
                modifier = Modifier.offset(x = -Dimens.Sm)
            )
            Spacer(modifier = Modifier.weight(1f))
            MutedTextButton(
                text = "Delete",
                onClick = onDelete,
                modifier = Modifier.offset(x = Dimens.Sm)
            )
        }
        HorizontalDivider(color = Hairline)
    }
}

/** A labelled figure. Plain text colour: an average is not a zone, so it takes no colour. */
@Composable
private fun StatItem(label: String, value: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = label, style = HrmType.BodySm, color = TextMuted)
        Spacer(modifier = Modifier.width(Dimens.Xs))
        Text(text = "$value", style = HrmType.NumeralLg, color = TextPrimary)
    }
}

private const val MAX_SESSION_NAME_LENGTH = 60
