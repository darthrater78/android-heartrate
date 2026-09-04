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
