package com.scrivtech.heartrate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.scrivtech.heartrate.data.HrSession
import com.scrivtech.heartrate.data.Storage
import java.util.UUID

/**
 * Owns the BLE connection across configuration changes.
 *
 * Previously the manager was created in [MainActivity.onCreate] and torn down in onDestroy,
 * so rotating the phone dropped a live session. A ViewModel outlives the Activity through a
 * config change and is cleared only when the Activity goes away for good.
 */
class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = BleHeartRateManager(application)
    val storage = Storage(application)

    /**
     * Writes the session that just ended to history. Sessions are saved unnamed — the user
     * names them afterwards from Session History, which shows the date until they do. A
     * session of fewer than two readings has nothing to graph and is dropped.
     */
    fun saveCompletedSession() {
        val readings = bleManager.sessionReadings.value
        if (readings.size >= 2) {
            storage.saveSession(
                HrSession(
                    id = UUID.randomUUID().toString(),
                    sessionName = "",
                    deviceName = bleManager.connectedDeviceName.value ?: "Unknown",
                    deviceAddress = bleManager.connectedDeviceAddress.value ?: "",
                    startTime = bleManager.currentSessionStartTime,
                    endTime = System.currentTimeMillis(),
                    readings = readings,
                    avgBpm = readings.map { it.bpm }.average().toInt(),
                    maxBpm = readings.maxOf { it.bpm },
                    minBpm = readings.minOf { it.bpm }
                )
            )
        }
    }

    override fun onCleared() {
        bleManager.release()
        super.onCleared()
    }
}
