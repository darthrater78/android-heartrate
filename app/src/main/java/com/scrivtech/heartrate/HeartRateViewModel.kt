package com.scrivtech.heartrate

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.scrivtech.heartrate.data.HrSession
import com.scrivtech.heartrate.data.Storage
import java.util.UUID

/**
 * Owns the BLE connection and the current session across configuration changes.
 *
 * Previously the manager was created in [MainActivity.onCreate] and torn down in onDestroy,
 * so rotating the phone dropped a live session. A ViewModel outlives the Activity through a
 * config change and is cleared only when the Activity goes away for good.
 */
class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = BleHeartRateManager(application)
    val storage = Storage(application)

    /**
     * Name the user gave the current session. Backed by Compose state so the UI observes it,
     * and held here rather than in a `remember` so it survives rotation along with the
     * connection it describes.
     */
    var currentSessionName by mutableStateOf("")

    /**
     * Writes the session that just ended to history and clears the name, ready for the next
     * one. A session of fewer than two readings has nothing to graph and is dropped.
     */
    fun saveCompletedSession() {
        val readings = bleManager.sessionReadings.value
        if (readings.size >= 2) {
            storage.saveSession(
                HrSession(
                    id = UUID.randomUUID().toString(),
                    sessionName = currentSessionName,
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
        currentSessionName = ""
    }

    override fun onCleared() {
        bleManager.release()
        super.onCleared()
    }
}
