package com.scrivtech.heartrate.ui

/**
 * Formats a duration for display, dropping units that would read as noise: an hour-long
 * session does not need its seconds, and a 45-second one does not need a leading "0m".
 *
 * Shared by the session summary and the live zone breakdown so the two cannot drift into
 * formatting the same duration differently.
 */
fun formatDuration(ms: Long): String {
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
