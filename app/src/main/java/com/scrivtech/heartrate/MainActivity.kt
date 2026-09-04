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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.scrivtech.heartrate.data.HeartRateRepository
import com.scrivtech.heartrate.ui.HeartRateScreen
import com.scrivtech.heartrate.ui.HistoryScreen
import com.scrivtech.heartrate.ui.ScanScreen
import com.scrivtech.heartrate.ui.SessionDetailScreen
import com.scrivtech.heartrate.ui.theme.HeartRateMirrorTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleHeartRateManager
    private lateinit var repository: HeartRateRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = HeartRateRepository(this)
        bleManager = BleHeartRateManager(this, repository)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )

        setContent {
            val state by bleManager.state.collectAsState()
            var screen by rememberSaveable { mutableStateOf("main") }
            var selectedSessionId by rememberSaveable { mutableStateOf(-1L) }

            LaunchedEffect(state) {
                if (state == ConnectionState.CONNECTED) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                if (state == ConnectionState.CONNECTING || state == ConnectionState.CONNECTED) {
                    screen = "main"
                }
            }

            HeartRateMirrorTheme {
                when {
                    state == ConnectionState.CONNECTING ||
                        state == ConnectionState.CONNECTED -> HeartRateScreen(bleManager)

                    screen == "history" -> HistoryScreen(
                        repository = repository,
                        onBack = { screen = "main" },
                        onSessionClick = { id ->
                            selectedSessionId = id
                            screen = "session_detail"
                        }
                    )

                    screen == "session_detail" && selectedSessionId >= 0 -> SessionDetailScreen(
                        sessionId = selectedSessionId,
                        repository = repository,
                        onBack = { screen = "history" }
                    )

                    else -> ScanScreen(
                        bleManager = bleManager,
                        repository = repository,
                        onHistoryClick = { screen = "history" }
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
