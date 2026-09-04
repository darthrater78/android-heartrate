package com.scrivtech.heartrate.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.BleHeartRateManager
import com.scrivtech.heartrate.ConnectionState
import com.scrivtech.heartrate.data.DeviceEntity
import com.scrivtech.heartrate.data.HeartRateRepository
import kotlinx.coroutines.delay

@Composable
fun ScanScreen(
    bleManager: BleHeartRateManager,
    repository: HeartRateRepository,
    onHistoryClick: () -> Unit
) {
    val state by bleManager.state.collectAsState()
    val devices by bleManager.devices.collectAsState()
    val errorMessage by bleManager.errorMessage.collectAsState()
    val recentDevices by repository.recentDevices.collectAsState(initial = emptyList())

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

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
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

            TextButton(onClick = onHistoryClick) {
                Text(
                    text = "History",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.6f)
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
            if (recentDevices.isNotEmpty() && state != ConnectionState.SCANNING) {
                item {
                    Text(
                        text = "Recent Devices",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(recentDevices, key = { "recent-${it.address}" }) { device ->
                    RecentDeviceItem(
                        device = device,
                        onClick = { bleManager.connectByAddress(device.address) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (devices.isNotEmpty()) {
                item {
                    Text(
                        text = if (state == ConnectionState.SCANNING) "Discovered Devices"
                        else "Paired Devices",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(devices, key = { it.address }) { device ->
                    DeviceItem(
                        device = device,
                        isPaired = device.bondState == BluetoothDevice.BOND_BONDED,
                        onClick = { bleManager.connect(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentDeviceItem(device: DeviceEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A1A))
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
                    text = formatRelativeTime(device.lastConnected),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }
            Text(
                text = "Reconnect",
                color = Color(0xFF66BB6A),
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

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
