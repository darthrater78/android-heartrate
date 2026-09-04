package com.scrivtech.heartrate.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.BleHeartRateManager
import com.scrivtech.heartrate.ConnectionState
import com.scrivtech.heartrate.data.RecentDevice
import com.scrivtech.heartrate.data.Storage
import kotlinx.coroutines.delay

@SuppressLint("MissingPermission")
@Composable
fun ScanScreen(
    bleManager: BleHeartRateManager,
    storage: Storage,
    onShowHistory: () -> Unit,
    onSessionNameSet: (String) -> Unit
) {
    val state by bleManager.state.collectAsState()
    val devices by bleManager.devices.collectAsState()
    val errorMessage by bleManager.errorMessage.collectAsState()
    val recentDevices = remember { storage.getRecentDevices() }
    val hasSessions = remember { storage.getSessions().isNotEmpty() }
    val context = LocalContext.current
    val bluetoothAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    var pendingDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var pendingRecentDevice by remember { mutableStateOf<RecentDevice?>(null) }
    var sessionNameInput by remember { mutableStateOf("") }

    val showNamingDialog = pendingDevice != null || pendingRecentDevice != null

    LaunchedEffect(state) {
        if (state == ConnectionState.SCANNING) {
            delay(30_000)
            bleManager.stopScan()
        }
    }

    if (showNamingDialog) {
        AlertDialog(
            onDismissRequest = {
                pendingDevice = null
                pendingRecentDevice = null
                sessionNameInput = ""
            },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "Name This Session",
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "Optional — e.g. \"Morning Run\", \"Cycling\"",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = sessionNameInput,
                        onValueChange = { sessionNameInput = it },
                        placeholder = {
                            Text(
                                text = "Session name",
                                color = Color.White.copy(alpha = 0.3f)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFE53935),
                            focusedBorderColor = Color(0xFFE53935),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSessionNameSet(sessionNameInput.trim())
                        pendingDevice?.let { bleManager.connect(it) }
                        pendingRecentDevice?.let {
                            val btDevice = bluetoothAdapter.getRemoteDevice(it.address)
                            bleManager.connect(btDevice)
                        }
                        pendingDevice = null
                        pendingRecentDevice = null
                        sessionNameInput = ""
                    }
                ) {
                    Text("Start", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onSessionNameSet("")
                        pendingDevice?.let { bleManager.connect(it) }
                        pendingRecentDevice?.let {
                            val btDevice = bluetoothAdapter.getRemoteDevice(it.address)
                            bleManager.connect(btDevice)
                        }
                        pendingDevice = null
                        pendingRecentDevice = null
                        sessionNameInput = ""
                    }
                ) {
                    Text("Skip", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
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

        if (hasSessions) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onShowHistory) {
                Text(
                    text = "Session History",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
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
                text = "Connection lost. Scan to reconnect.",
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

        LazyColumn {
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
                        onClick = { pendingRecentDevice = device }
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
                    onClick = { pendingDevice = device }
                )
            }
        }
    }
}

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
