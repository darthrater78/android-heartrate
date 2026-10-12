package com.scrivtech.heartrate.data

data class RecentDevice(
    val name: String,
    val address: String,
    val lastConnectedAt: Long
)

data class HrReading(
    val timestampMs: Long,
    val bpm: Int
)

data class HrSession(
    val id: String,
    val sessionName: String,
    val deviceName: String,
    val deviceAddress: String,
    val startTime: Long,
    val endTime: Long,
    val readings: List<HrReading>,
    val avgBpm: Int,
    val maxBpm: Int,
    val minBpm: Int
) {
    val durationMs: Long get() = endTime - startTime
}

/**
 * Shortest recording worth keeping. Anything under this is a connection test or a false
 * start rather than a workout, and saving it only buries the real sessions in history.
 */
const val MIN_SAVED_SESSION_MS = 60_000L

/**
 * Time actually covered by readings, first to last. This is what a session is measured by
 * for saving, rather than wall-clock time since connecting, so time spent waiting for the
 * first reading or retrying a dropped link cannot carry an empty session over the minimum.
 */
fun List<HrReading>.recordedSpanMs(): Long =
    if (size >= 2) last().timestampMs - first().timestampMs else 0L
