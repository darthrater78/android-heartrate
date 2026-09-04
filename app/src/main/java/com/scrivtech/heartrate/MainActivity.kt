package com.scrivtech.heartrate

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.scrivtech.heartrate.data.HrSession
import com.scrivtech.heartrate.data.Storage
import com.scrivtech.heartrate.ui.HeartRateScreen
import com.scrivtech.heartrate.ui.ScanScreen
import com.scrivtech.heartrate.ui.SessionHistoryScreen
import com.scrivtech.heartrate.ui.theme.HeartRateMirrorTheme

private enum class Screen { SCAN, HEART_RATE, SESSION_HISTORY }

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleHeartRateManager
    private lateinit var storage: Storage

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bleManager = BleHeartRateManager(this)
        storage = Storage(this)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )

        setContent {
            val state by bleManager.state.collectAsState()
            var currentScreen by remember { mutableStateOf(Screen.SCAN) }
            var wasConnected by remember { mutableStateOf(false) }
            var currentSessionName by remember { mutableStateOf("") }

            LaunchedEffect(state) {
                when (state) {
                    ConnectionState.CONNECTING,
                    ConnectionState.CONNECTED -> {
                        currentScreen = Screen.HEART_RATE
                    }
                    else -> {
                        if (currentScreen == Screen.HEART_RATE) {
                            currentScreen = Screen.SCAN
                        }
                    }
                }
            }

            LaunchedEffect(state) {
                if (state == ConnectionState.CONNECTED) {
                    wasConnected = true
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                    val name = bleManager.connectedDeviceName.value
                    val address = bleManager.connectedDeviceAddress.value
                    if (name != null && address != null) {
                        storage.addRecentDevice(name, address)
                    }
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                    if (wasConnected && state != ConnectionState.CONNECTING) {
                        wasConnected = false
                        val readings = bleManager.sessionReadings.value
                        if (readings.size >= 2) {
                            val session = HrSession(
                                id = java.util.UUID.randomUUID().toString(),
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
                            storage.saveSession(session)
                        }
                        currentSessionName = ""
                    }
                }
            }

            HeartRateMirrorTheme {
                when (currentScreen) {
                    Screen.HEART_RATE -> HeartRateScreen(
                        bleManager = bleManager,
                        sessionName = currentSessionName
                    )
                    Screen.SESSION_HISTORY -> SessionHistoryScreen(
                        storage = storage,
                        onBack = { currentScreen = Screen.SCAN }
                    )
                    Screen.SCAN -> ScanScreen(
                        bleManager = bleManager,
                        storage = storage,
                        onShowHistory = { currentScreen = Screen.SESSION_HISTORY },
                        onSessionNameSet = { currentSessionName = it }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        bleManager.disconnect()
        super.onDestroy()
    }
}
