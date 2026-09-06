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
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.BleHeartRateManager
import com.scrivtech.heartrate.ConnectionState
import com.scrivtech.heartrate.data.timeInZones
import com.scrivtech.heartrate.data.zoneFor

/**
 * @param maxHr the user's estimated maximum heart rate, or null when no age has been set.
 *   Null hides every zone element rather than assuming an age, so the screen never shows
 *   someone a stranger's zones.
 */
@Composable
fun HeartRateScreen(bleManager: BleHeartRateManager, maxHr: Int?) {
    val state by bleManager.state.collectAsState()
    val heartRate by bleManager.heartRate.collectAsState()
    val deviceName by bleManager.connectedDeviceName.collectAsState()
    val sessionReadings by bleManager.sessionReadings.collectAsState()

    val currentBpm = heartRate ?: 72
    val pulseDuration = (30_000 / currentBpm).coerceIn(150, 1000)

    val currentZone = if (maxHr != null && heartRate != null) {
        zoneFor(heartRate!!, maxHr)
    } else {
        null
    }

    // Recomputed from the whole reading list on each new sample rather than accumulated
    // incrementally. That is O(n) per second, which at one sample a second stays trivial
    // even for a long session, and it keeps one definition of time-in-zone shared with
    // the session summary instead of a live counter that could drift from it.
    val zoneTimes = remember(sessionReadings, maxHr) {
        if (maxHr != null) timeInZones(sessionReadings, maxHr) else emptyMap()
    }

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

                    if (currentZone != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CurrentZoneLabel(zone = currentZone)
                    }
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

        // Time in zone, live. Compact mode lists only the zones actually reached, so this
        // starts as a single row and grows as the session moves through the bands rather
        // than showing three empty rows for most of a warm-up.
        if (zoneTimes.values.sum() > 0L) {
            ZoneBreakdown(
                timeInZones = zoneTimes,
                compact = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .padding(horizontal = 32.dp)
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
