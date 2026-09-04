package com.scrivtech.heartrate.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val deviceName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val minBpm: Int? = null,
    val maxBpm: Int? = null,
    val avgBpm: Int? = null,
    val readingCount: Int = 0
)
