package com.scrivtech.heartrate.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scrivtech.heartrate.BleHeartRateManager
import com.scrivtech.heartrate.ConnectionState
import com.scrivtech.heartrate.data.HrZone
import com.scrivtech.heartrate.data.MIN_SAVED_SESSION_MS
import com.scrivtech.heartrate.data.recordedSpanMs
import com.scrivtech.heartrate.data.lowerBpm
import com.scrivtech.heartrate.data.timeInZones
import com.scrivtech.heartrate.data.zoneFor
import com.scrivtech.heartrate.ui.theme.Dimens
import com.scrivtech.heartrate.ui.theme.Ground
import com.scrivtech.heartrate.ui.theme.HrmType
import com.scrivtech.heartrate.ui.theme.TextMuted
import com.scrivtech.heartrate.ui.theme.TextPrimary

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
    val reconnectAttempt by bleManager.reconnectAttemptState.collectAsState()

    val bpm = heartRate
    val live = bpm != null && state == ConnectionState.CONNECTED
    val currentZone = if (maxHr != null && bpm != null && live) zoneFor(bpm, maxHr) else null

    // Recomputed from the whole reading list on each new sample rather than accumulated
    // incrementally. That is O(n) per second, which at one sample a second stays trivial
    // even for a long session, and it keeps one definition of time-in-zone shared with
    // the session summary instead of a live counter that could drift from it.
    val zoneTimes = remember(sessionReadings, maxHr) {
        if (maxHr != null) timeInZones(sessionReadings, maxHr) else emptyMap()
    }
    val elapsedMs = sessionReadings.recordedSpanMs()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ground)
            .safeDrawingPadding()
            .padding(Dimens.ScreenEdge)
    ) {
        // Everything but the button scrolls, so a short screen or a large font setting
        // pushes content down instead of stacking one element on top of another.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.Section)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PulseDot(bpm = if (live) bpm else null)
                    Spacer(modifier = Modifier.width(Dimens.Sm))
                    Text(
                        text = deviceName ?: "",
                        style = HrmType.Body,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = formatClock(elapsedMs), style = HrmType.NumeralLg, color = TextPrimary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // An invisible twin of the unit label on the left, so the digits sit
                    // on the screen's centre line rather than the digits-plus-unit group.
                    Text(
                        text = "bpm",
                        style = HrmType.Body,
                        modifier = Modifier
                            .alpha(0f)
                            .clearAndSetSemantics {}
                    )
                    Spacer(modifier = Modifier.width(Dimens.Sm))
                    Text(
                        text = if (live) "$bpm" else "--",
                        style = HrmType.DisplayBpm,
                        color = if (live) TextPrimary else TextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(Dimens.Sm))
                    Text(
                        text = "bpm",
                        style = HrmType.Body,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = Dimens.Md)
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Md))
                when {
                    !live -> Text(
                        text = when (state) {
                            ConnectionState.RECONNECTING ->
                                "Reconnecting ($reconnectAttempt of " +
                                    "${BleHeartRateManager.MAX_RECONNECT_ATTEMPTS})"
                            ConnectionState.CONNECTING -> "Connecting"
                            else -> "Waiting for heart rate"
                        },
                        style = HrmType.Body,
                        color = TextMuted
                    )
                    currentZone != null && maxHr != null -> CurrentZone(
                        zone = currentZone,
                        bpm = bpm!!,
                        maxHr = maxHr
                    )
                    else -> Text(
                        text = "Set your age on the home screen to see heart rate zones",
                        style = HrmType.Body,
                        color = TextMuted
                    )
                }
            }

            if (maxHr != null) {
                ZoneScale(maxHr = maxHr, bpm = if (live) bpm else null, current = currentZone)
            }

            // Keep the trace on screen while the manager reconnects — the session is still
            // running, so blanking the graph would make a brief dropout look like a reset.
            if (sessionReadings.size >= 2) {
                Column {
                    Text(text = "Last 5 minutes", style = HrmType.BodySm, color = TextMuted)
                    Spacer(modifier = Modifier.height(Dimens.Sm))
                    HrGraph(
                        readings = sessionReadings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        maxHr = maxHr,
                        maxDurationMs = 300_000L
                    )
                }
            }

            // Compact mode lists only the zones actually reached, so this starts as a
            // single row and grows as the session moves through the bands rather than
            // showing three empty rows for most of a warm-up.
            ZoneBreakdown(timeInZones = zoneTimes, compact = true, currentZone = currentZone)
        }

        Spacer(modifier = Modifier.height(Dimens.Md))
        // Said up front, so ending early is a choice rather than a session that silently
        // fails to appear in history.
        if (elapsedMs < MIN_SAVED_SESSION_MS) {
            Text(
                text = "Sessions under a minute are not saved",
                style = HrmType.BodySm,
                color = TextMuted,
                modifier = Modifier.padding(bottom = Dimens.Sm)
            )
        }
        SecondaryButton(text = "End session", onClick = { bleManager.disconnect() })
    }
}

/**
 * The current zone as a filled chip, how far the next zone is, and the one line saying
 * what training here achieves, so "where should I be" reads at a glance mid-workout.
 */
@Composable
private fun CurrentZone(zone: HrZone, bpm: Int, maxHr: Int) {
    val next = HrZone.entries.getOrNull(zone.ordinal + 1)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoneChip(zone = zone)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (next != null) {
                    "${next.lowerBpm(maxHr) - bpm} bpm to ${next.displayName}"
                } else {
                    "${zone.lowerBpm(maxHr)} bpm and above"
                },
                style = HrmType.Body,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.height(Dimens.Sm))
        Text(text = zone.goal, style = HrmType.Body, color = TextMuted)
    }
}

/**
 * Connection indicator that beats in time with the reading. It replaces the old pulse on
 * the number itself, which made a 200sp figure harder to read at exactly the moment it
 * mattered. With no [bpm] it sits still and muted.
 */
@Composable
private fun PulseDot(bpm: Int?) {
    val pulseDuration = (30_000 / (bpm ?: 60).coerceAtLeast(1)).coerceIn(150, 1000)
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (bpm != null) 1.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (bpm != null) TextPrimary else TextMuted)
    )
}
