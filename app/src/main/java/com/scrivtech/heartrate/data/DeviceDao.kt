package com.scrivtech.heartrate.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastConnected DESC LIMIT 10")
    fun getRecentDevices(): Flow<List<DeviceEntity>>

    @Upsert
    suspend fun upsertDevice(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE address = :address")
    suspend fun deleteDevice(address: String)
}
