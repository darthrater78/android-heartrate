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
import com.scrivtech.heartrate.ui.HeartRateScreen
import com.scrivtech.heartrate.ui.ScanScreen
import com.scrivtech.heartrate.ui.theme.HeartRateMirrorTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleHeartRateManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bleManager = BleHeartRateManager(this)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )

        setContent {
            val state by bleManager.state.collectAsState()

            LaunchedEffect(state) {
                if (state == ConnectionState.CONNECTED) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            HeartRateMirrorTheme {
                when (state) {
                    ConnectionState.CONNECTING,
                    ConnectionState.CONNECTED -> HeartRateScreen(bleManager)
                    ConnectionState.IDLE,
                    ConnectionState.SCANNING,
                    ConnectionState.DISCONNECTED,
                    ConnectionState.FAILED -> ScanScreen(bleManager)
                }
            }
        }
    }

    override fun onDestroy() {
        bleManager.disconnect()
        super.onDestroy()
    }
}
