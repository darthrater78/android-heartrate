package com.scrivtech.heartrate

import android.Manifest
import android.os.Build
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
import com.scrivtech.heartrate.data.Storage
import com.scrivtech.heartrate.data.maxHrForAge
import com.scrivtech.heartrate.ui.HeartRateScreen
import com.scrivtech.heartrate.ui.ScanScreen
import com.scrivtech.heartrate.ui.SessionHistoryScreen
import com.scrivtech.heartrate.ui.theme.HeartRateMirrorTheme

private enum class Screen { SCAN, HEART_RATE, SESSION_HISTORY }

class MainActivity : ComponentActivity() {

    // Held by the Application so neither a rotation nor the Activity finishing ends a session.
    private val bleManager: BleHeartRateManager get() = (application as HeartRateApp).bleManager
    private val storage: Storage get() = (application as HeartRateApp).storage

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
            // Only needed to show the live session notification. A denial does not stop the
            // foreground service from running, so the session still works without it.
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(permissions.toTypedArray())

        setContent {
            val state by bleManager.state.collectAsState()
            var currentScreen by remember { mutableStateOf(Screen.SCAN) }
            // currentScreen re-derives itself from state below, so losing it to a rotation or
            // to the Activity being recreated mid-session is harmless.
            // Held here rather than read inside each screen so that setting an age on the
            // scan screen immediately gives the live screen its zones, without a round
            // trip back through storage on every recomposition.
            var age by remember { mutableStateOf(storage.getAge()) }
            val maxHr = age?.let { maxHrForAge(it) }

            val inSession = state.isInSession

            LaunchedEffect(state) {
                if (inSession) {
                    currentScreen = Screen.HEART_RATE
                } else if (currentScreen == Screen.HEART_RATE) {
                    currentScreen = Screen.SCAN
                }
            }

            // Only the screen lives here. The foreground service, recent devices and saving
            // the session are SessionController's, because Compose pauses these effects while
            // the app is in the background.
            LaunchedEffect(state) {
                if (state == ConnectionState.CONNECTED) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else if (!inSession) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            HeartRateMirrorTheme {
                when (currentScreen) {
                    Screen.HEART_RATE -> HeartRateScreen(
                        bleManager = bleManager,
                        maxHr = maxHr
                    )
                    Screen.SESSION_HISTORY -> SessionHistoryScreen(
                        storage = storage,
                        onBack = { currentScreen = Screen.SCAN }
                    )
                    Screen.SCAN -> ScanScreen(
                        bleManager = bleManager,
                        storage = storage,
                        age = age,
                        onAgeChange = {
                            storage.setAge(it)
                            age = it
                        },
                        onShowHistory = { currentScreen = Screen.SESSION_HISTORY }
                    )
                }
            }
        }
    }
}
