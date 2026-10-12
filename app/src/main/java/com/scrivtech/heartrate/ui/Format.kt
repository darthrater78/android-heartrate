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

/**
 * Formats a running time as a clock ("24:10", "1:02:05") for the live session timer, where
 * the digits must hold their place from one second to the next instead of changing shape
 * the way [formatDuration] does when it drops a unit.
 */
fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$seconds"
    } else {
        "$minutes:$seconds"
    }
}
