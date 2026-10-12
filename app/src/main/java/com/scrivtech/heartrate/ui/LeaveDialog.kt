package com.scrivtech.heartrate.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Asked when a back gesture would take the user out of the app.
 *
 * Staying is the confirm action and leaving the quiet one: the gesture that opens this is
 * usually accidental, so the easy tap should be the one that undoes it. Mid-session it also
 * says what leaving does, because "leave" could otherwise be read as ending the session.
 */
@Composable
fun LeaveDialog(inSession: Boolean, onStay: () -> Unit, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(text = "Leave Heart Rate Mirror?") },
        text = {
            Text(
                text = if (inSession) {
                    "Your session keeps recording in the background. Use End session " +
                        "to stop it."
                } else {
                    "No session is running."
                }
            )
        },
        confirmButton = { TextButton(onClick = onStay) { Text(text = "Stay") } },
        dismissButton = { MutedTextButton(text = "Leave", onClick = onLeave) }
    )
}
