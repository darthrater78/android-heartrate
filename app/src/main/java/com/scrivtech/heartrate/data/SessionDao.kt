package com.scrivtech.heartrate.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query(
        "UPDATE sessions SET endTime = :endTime, minBpm = :minBpm, maxBpm = :maxBpm, " +
            "avgBpm = :avgBpm, readingCount = :readingCount WHERE id = :sessionId"
    )
    suspend fun endSession(
        sessionId: Long,
        endTime: Long,
        minBpm: Int,
        maxBpm: Int,
        avgBpm: Int,
        readingCount: Int
    )

    @Insert
    suspend fun insertReading(reading: HeartRateReadingEntity)

    @Query("SELECT * FROM readings WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getReadingsForSession(sessionId: Long): Flow<List<HeartRateReadingEntity>>

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}
