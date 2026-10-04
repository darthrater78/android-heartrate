package com.scrivtech.heartrate

import android.app.Application
import com.scrivtech.heartrate.data.Storage
import kotlinx.coroutines.MainScope

/**
 * Owns the BLE connection and the session for the life of the process.
 *
 * They used to live in an Activity-scoped ViewModel, so anything that finished the Activity
 * (a back gesture, the system reclaiming it while another app was in front) tore down a live
 * session without a word and without saving it. Holding them here means a session ends only
 * when the watch is lost, Bluetooth goes off, or the user disconnects.
 */
class HeartRateApp : Application() {

    lateinit var bleManager: BleHeartRateManager
        private set
    lateinit var storage: Storage
        private set

    override fun onCreate() {
        super.onCreate()
        bleManager = BleHeartRateManager(this)
        storage = Storage(this)
        // Process-scoped: the controller watches for as long as there is a process to watch.
        SessionController(this, bleManager, storage, MainScope()).start()
    }
}
