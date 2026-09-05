package com.scrivtech.heartrate

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.scrivtech.heartrate.data.Storage
import com.scrivtech.heartrate.ui.HeartRateScreen
import com.scrivtech.heartrate.ui.ScanScreen
import com.scrivtech.heartrate.ui.SessionHistoryScreen
import com.scrivtech.heartrate.ui.theme.HeartRateMirrorTheme

private enum class Screen { SCAN, HEART_RATE, SESSION_HISTORY }

class MainActivity : ComponentActivity() {

    // Held by the ViewModel so a rotation does not tear down a live session.
    private val viewModel: HeartRateViewModel by viewModels()
    private val bleManager: BleHeartRateManager get() = viewModel.bleManager
    private val storage: Storage get() = viewModel.storage

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Only needed to show the session notification. A denial does not stop the
            // foreground service from running, so the session still works without it.
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(permissions.toTypedArray())

        setContent {
            val state by bleManager.state.collectAsState()
            var currentScreen by remember { mutableStateOf(Screen.SCAN) }
            // currentScreen and wasConnected both re-derive themselves from state below, so
            // losing them to a rotation is harmless.
            var wasConnected by remember { mutableStateOf(false) }

            // CONNECTING and RECONNECTING both count as "still in a session" — a dropped
            // link that the manager is recovering must not bounce the user back to Scan or
            // close out the session partway through.
            val inSession = state == ConnectionState.CONNECTING ||
                state == ConnectionState.RECONNECTING ||
                state == ConnectionState.CONNECTED

            LaunchedEffect(state) {
                if (inSession) {
                    currentScreen = Screen.HEART_RATE
                } else if (currentScreen == Screen.HEART_RATE) {
                    currentScreen = Screen.SCAN
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
                    // Foreground for the rest of the session, so Doze cannot drop the link
                    // once the screen goes off.
                    HeartRateSessionService.start(this@MainActivity, name)
                } else if (!inSession) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    HeartRateSessionService.stop(this@MainActivity)

                    if (wasConnected) {
                        wasConnected = false
                        viewModel.saveCompletedSession()
                    }
                }
            }

            HeartRateMirrorTheme {
                when (currentScreen) {
                    Screen.HEART_RATE -> HeartRateScreen(bleManager = bleManager)
                    Screen.SESSION_HISTORY -> SessionHistoryScreen(
                        storage = storage,
                        onBack = { currentScreen = Screen.SCAN }
                    )
                    Screen.SCAN -> ScanScreen(
                        bleManager = bleManager,
                        storage = storage,
                        onShowHistory = { currentScreen = Screen.SESSION_HISTORY }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        // Deliberately does not disconnect: onDestroy also fires on rotation, and the
        // ViewModel outlives it. Teardown happens in HeartRateViewModel.onCleared, which
        // runs only when the Activity is finishing for good.
        if (isFinishing) {
            HeartRateSessionService.stop(this)
        }
        super.onDestroy()
    }
}
