package com.scrivtech.heartrate.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.BleHeartRateManager
import com.scrivtech.heartrate.ConnectionState

@Composable
fun HeartRateScreen(bleManager: BleHeartRateManager) {
    val state by bleManager.state.collectAsState()
    val heartRate by bleManager.heartRate.collectAsState()
    val deviceName by bleManager.connectedDeviceName.collectAsState()
    val sessionReadings by bleManager.sessionReadings.collectAsState()

    val currentBpm = heartRate ?: 72
    val pulseDuration = (30_000 / currentBpm).coerceIn(150, 1000)

    val animatedBpmColor by animateColorAsState(
        targetValue = if (heartRate != null) hrZoneColor(heartRate!!) else Color.White,
        animationSpec = tween(durationMillis = 500),
        label = "bpmColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (heartRate != null) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = pulseDuration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            if (deviceName != null) {
                Text(
                    text = deviceName!!,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            when {
                state == ConnectionState.CONNECTING ||
                    state == ConnectionState.RECONNECTING -> {
                    Text(
                        text = "--",
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (state == ConnectionState.RECONNECTING) {
                            "Reconnecting..."
                        } else {
                            "Connecting..."
                        },
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                heartRate != null -> {
                    Text(
                        text = "$heartRate",
                        fontSize = 160.sp,
                        fontWeight = FontWeight.Bold,
                        color = animatedBpmColor,
                        modifier = Modifier.scale(scale)
                    )
                    Text(
                        text = "BPM",
                        fontSize = 32.sp,
                        color = animatedBpmColor.copy(alpha = 0.6f)
                    )
                }

                else -> {
                    Text(
                        text = "--",
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Waiting for heart rate...",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Keep the trace on screen while the manager reconnects — the session is still
        // running, so blanking the graph would make a brief dropout look like a reset.
        if (sessionReadings.size >= 2) {
            HrGraph(
                readings = sessionReadings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 16.dp)
                    .align(Alignment.Center)
                    .padding(top = 60.dp),
                showZoneColors = true,
                maxDurationMs = 300_000L
            )
        }

        TextButton(
            onClick = { bleManager.disconnect() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = "Disconnect",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
    }
}
