package com.scrivtech.heartrate.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val address: String,
    val name: String,
    val lastConnected: Long
)
