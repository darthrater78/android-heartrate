package com.scrivtech.heartrate

import android.content.Context
import com.scrivtech.heartrate.data.HrSession
import com.scrivtech.heartrate.data.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Turns connection state into session lifecycle: starts the foreground service when a
 * session begins, records the device once it connects, and saves the session when it ends.
 *
 * This used to run in Compose effects in [MainActivity]. Compose pauses recomposition while
 * the Activity is stopped, so none of it happened while the app was in the background, and
 * a session that ended there was never saved.
 */
class SessionController(
    private val context: Context,
    private val bleManager: BleHeartRateManager,
    private val storage: Storage,
    private val scope: CoroutineScope
) {
    private var wasConnected = false
    private var serviceRequested = false

    fun start() {
        scope.launch { bleManager.state.collect(::onState) }
    }

    private fun onState(state: ConnectionState) {
        if (state.isInSession && !serviceRequested) {
            // A session starts from a tap on the scan screen, so the app is in the
            // foreground here and allowed to start a foreground service.
            serviceRequested = HeartRateSessionService.start(context)
        }

        if (state == ConnectionState.CONNECTED && !wasConnected) {
            wasConnected = true
            val name = bleManager.connectedDeviceName.value
            val address = bleManager.connectedDeviceAddress.value
            if (name != null && address != null) {
                storage.addRecentDevice(name, address)
            }
        } else if (!state.isInSession) {
            // The service stops itself once it sees the session is over; stopping it from
            // here could land before it has called startForeground.
            serviceRequested = false
            if (wasConnected) {
                wasConnected = false
                saveCompletedSession()
            }
        }
    }

    /**
     * Writes the session that just ended to history. Sessions are saved unnamed — the user
     * names them afterwards from Session History, which shows the date until they do. A
     * session of fewer than two readings has nothing to graph and is dropped.
     */
    private fun saveCompletedSession() {
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
}
