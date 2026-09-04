package com.scrivtech.heartrate.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HeartRateRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val deviceDao = db.deviceDao()
    private val sessionDao = db.sessionDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val recentDevices: Flow<List<DeviceEntity>> = deviceDao.getRecentDevices()
    val sessions: Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    @Volatile
    private var currentSessionId: Long? = null
    private val sessionReadings = mutableListOf<Int>()
    @Volatile
    private var lastRecordedTime = 0L

    fun saveDevice(address: String, name: String) {
        scope.launch {
            deviceDao.upsertDevice(DeviceEntity(address, name, System.currentTimeMillis()))
        }
    }

    fun startSession(deviceAddress: String, deviceName: String) {
        sessionReadings.clear()
        lastRecordedTime = 0L
        currentSessionId = null
        scope.launch {
            currentSessionId = sessionDao.insertSession(
                SessionEntity(
                    deviceAddress = deviceAddress,
                    deviceName = deviceName,
                    startTime = System.currentTimeMillis()
                )
            )
        }
    }

    fun recordHeartRate(bpm: Int) {
        val sessionId = currentSessionId ?: return
        val now = System.currentTimeMillis()
        if (now - lastRecordedTime < 2000) return
        lastRecordedTime = now
        synchronized(sessionReadings) { sessionReadings.add(bpm) }
        scope.launch {
            sessionDao.insertReading(
                HeartRateReadingEntity(
                    sessionId = sessionId,
                    timestamp = now,
                    bpm = bpm
                )
            )
        }
    }

    fun endSession() {
        val sessionId = currentSessionId ?: return
        currentSessionId = null
        val readings = synchronized(sessionReadings) {
            sessionReadings.toList().also { sessionReadings.clear() }
        }
        if (readings.isEmpty()) {
            scope.launch { sessionDao.deleteSession(sessionId) }
            return
        }
        scope.launch {
            sessionDao.endSession(
                sessionId = sessionId,
                endTime = System.currentTimeMillis(),
                minBpm = readings.min(),
                maxBpm = readings.max(),
                avgBpm = readings.average().toInt(),
                readingCount = readings.size
            )
        }
    }

    fun getReadingsForSession(sessionId: Long): Flow<List<HeartRateReadingEntity>> {
        return sessionDao.getReadingsForSession(sessionId)
    }

    fun deleteSession(sessionId: Long) {
        scope.launch { sessionDao.deleteSession(sessionId) }
    }

    fun deleteDevice(address: String) {
        scope.launch { deviceDao.deleteDevice(address) }
    }
}
