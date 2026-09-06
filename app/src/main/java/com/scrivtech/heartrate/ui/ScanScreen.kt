package com.scrivtech.heartrate.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.BleHeartRateManager
import com.scrivtech.heartrate.BuildConfig
import com.scrivtech.heartrate.ConnectionState
import com.scrivtech.heartrate.data.RecentDevice
import com.scrivtech.heartrate.data.Storage
import com.scrivtech.heartrate.data.maxHrForAge
import kotlinx.coroutines.delay

@SuppressLint("MissingPermission")
@Composable
fun ScanScreen(
    bleManager: BleHeartRateManager,
    storage: Storage,
    age: Int?,
    onAgeChange: (Int) -> Unit,
    onShowHistory: () -> Unit
) {
    val state by bleManager.state.collectAsState()
    val devices by bleManager.devices.collectAsState()
    val errorMessage by bleManager.errorMessage.collectAsState()
    val recentDevices = remember { storage.getRecentDevices() }
    val sessionCount = remember { storage.getSessions().size }
    val hasSessions = sessionCount > 0
    var editingAge by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val bluetoothAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    if (editingAge) {
        AgeDialog(
            initialAge = age,
            onDismiss = { editingAge = false },
            onSave = {
                onAgeChange(it)
                editingAge = false
            }
        )
    }

    LaunchedEffect(state) {
        if (state == ConnectionState.SCANNING) {
            delay(30_000)
            bleManager.stopScan()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        Text(
            text = "Heart Rate Mirror",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Scan for BLE devices or start\nHR Mirror on your watch",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.5f),
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (state == ConnectionState.SCANNING) {
                    bleManager.stopScan()
                } else {
                    bleManager.startScan()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state == ConnectionState.SCANNING)
                    Color(0xFF424242) else Color(0xFFE53935)
            )
        ) {
            Text(
                text = if (state == ConnectionState.SCANNING) "Stop Scan" else "Scan for Devices",
                fontSize = 16.sp
            )
        }

        // Previously a dim text link that read as a footnote next to the scan button.
        // Past sessions are the main reason to open the app when not about to train, so
        // this is a card with the same weight as a device row, and it says how much is in
        // there rather than making the user open it to find out.
        if (hasSessions) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowHistory() },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Session History",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (sessionCount == 1) {
                                "1 saved session"
                            } else {
                                "$sessionCount saved sessions"
                            },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                    Text(
                        text = "›",
                        fontSize = 24.sp,
                        color = Color(0xFFE53935).copy(alpha = 0.8f)
                    )
                }
            }
        }

        if (state == ConnectionState.SCANNING) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(
                color = Color(0xFFE53935),
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }

        if (state == ConnectionState.DISCONNECTED) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage ?: "Connection lost. Scan to reconnect.",
                color = Color(0xFFE53935),
                fontSize = 14.sp
            )
        }

        if (state == ConnectionState.FAILED && errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage!!,
                color = Color(0xFFFF8A65),
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // weight(1f) lets the device list take the remaining height so the version
        // footer stays pinned to the bottom instead of floating under a short list.
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (recentDevices.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Devices",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(recentDevices, key = { "recent_${it.address}" }) { device ->
                    RecentDeviceItem(
                        device = device,
                        onClick = {
                            bleManager.connect(bluetoothAdapter.getRemoteDevice(device.address))
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (devices.isNotEmpty()) {
                item {
                    Text(
                        text = if (recentDevices.isNotEmpty()) "Scanned Devices" else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            items(devices, key = { it.address }) { device ->
                DeviceItem(
                    device = device,
                    isPaired = device.bondState == BluetoothDevice.BOND_BONDED,
                    onClick = { bleManager.connect(device) }
                )
            }
        }

        ZoneSettingsRow(age = age, onClick = { editingAge = true })

        AppVersionFooter()
    }
}

/**
 * Entry point for the one setting the zone model needs.
 *
 * With no age set this is a prompt rather than a status line, because until it is answered
 * every zone feature in the app is silently absent and nothing else would explain why.
 * Once set it recedes to a status line, showing the derived maximum so the number behind
 * the zones is inspectable rather than magic.
 */
@Composable
private fun ZoneSettingsRow(age: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (age == null) {
            Text(
                text = "Set your age to see heart rate zones",
                color = Color(0xFFE53935).copy(alpha = 0.85f),
                fontSize = 13.sp
            )
        } else {
            Text(
                text = "Zones for age $age  ·  max ${maxHrForAge(age)} bpm",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Version line and outbound links, shown at the foot of the scan screen.
 *
 * The version comes from [BuildConfig.VERSION_NAME] rather than a literal, so it and the
 * release-notes URL both follow versionName in app/build.gradle.kts and cannot go stale on
 * the next bump. The release-notes link 404s until that version's release is published,
 * which is the intended behaviour: the link is correct for the build it ships in.
 */
@Composable
private fun AppVersionFooter() {
    val uriHandler = LocalUriHandler.current
    val version = BuildConfig.VERSION_NAME

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "v$version",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 11.sp
        )
        FooterSeparator()
        FooterLink("Release notes") {
            uriHandler.openUri("$REPO_URL/releases/tag/v$version")
        }
        FooterSeparator()
        FooterLink("GitHub") {
            uriHandler.openUri(REPO_URL)
        }
    }
}

@Composable
private fun FooterSeparator() {
    Text(
        text = "  ·  ",
        color = Color.White.copy(alpha = 0.2f),
        fontSize = 11.sp
    )
}

/**
 * Deliberately quiet: neutral rather than accent-coloured, and not underlined.
 *
 * These links are for the rare occasion someone wants the source or the changelog, but
 * red-and-underlined gave them more pull than the session history sitting above them.
 * They stay tappable and legible; they just stop competing.
 */
@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White.copy(alpha = 0.35f),
        fontSize = 11.sp,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private const val REPO_URL = "https://github.com/darthrater78/android-heartrate"

@Composable
private fun RecentDeviceItem(device: RecentDevice, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = device.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Previously connected",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }
            Text(
                text = "Connect",
                color = Color(0xFFE53935),
                fontSize = 14.sp
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceItem(device: BluetoothDevice, isPaired: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = device.name ?: "Unknown Device",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isPaired) "Paired" else device.address,
                    color = if (isPaired) Color(0xFFE53935).copy(alpha = 0.7f)
                            else Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }
            Text(
                text = "Connect",
                color = Color(0xFFE53935),
                fontSize = 14.sp
            )
        }
    }
}
