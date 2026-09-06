package com.scrivtech.heartrate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.data.HrZone

/**
 * Proportional stacked bar of time spent in each zone.
 *
 * Zones with no time are dropped rather than given a zero-width slice, because a
 * [Modifier.weight] of zero still reserves space for the spacing between children and
 * leaves visible gaps in what should read as one continuous bar.
 */
@Composable
fun ZoneBar(
    timeInZones: Map<HrZone, Long>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 8.dp
) {
    val occupied = timeInZones.filterValues { it > 0L }
    if (occupied.isEmpty()) return

    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
    ) {
        occupied.forEach { (zone, ms) ->
            Box(
                modifier = Modifier
                    .weight(ms.toFloat())
                    .height(height)
                    .background(zoneColor(zone))
            )
        }
    }
}

/**
 * Bar plus one row per zone, showing how long was spent there and what share of the
 * session that was.
 *
 * [compact] trims the row set to zones that were actually reached and drops the type
 * sizes, for the live screen where this sits under the BPM readout and must not compete
 * with it. The session summary shows every zone including the empty ones, so that a
 * session with nothing in Peak visibly says so rather than silently omitting the row.
 */
@Composable
fun ZoneBreakdown(
    timeInZones: Map<HrZone, Long>,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val total = timeInZones.values.sum()
    if (total <= 0L) return

    val rows = if (compact) {
        timeInZones.filterValues { it > 0L }
    } else {
        timeInZones
    }

    Column(modifier = modifier) {
        ZoneBar(
            timeInZones = timeInZones,
            modifier = Modifier.fillMaxWidth(),
            height = if (compact) 6.dp else 8.dp
        )
        Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

        rows.forEach { (zone, ms) ->
            ZoneRow(
                zone = zone,
                ms = ms,
                percent = (ms * 100f / total),
                compact = compact
            )
            Spacer(modifier = Modifier.height(if (compact) 3.dp else 6.dp))
        }
    }
}

@Composable
private fun ZoneRow(zone: HrZone, ms: Long, percent: Float, compact: Boolean) {
    val nameSize = if (compact) 12.sp else 13.sp
    val valueSize = if (compact) 12.sp else 13.sp
    // An empty zone stays legible but recedes, so the rows that carry time read first.
    val alpha = if (ms > 0L) 1f else 0.35f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 7.dp else 9.dp)
                .clip(CircleShape)
                .background(zoneColor(zone).copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = zone.displayName,
            fontSize = nameSize,
            color = Color.White.copy(alpha = 0.75f * alpha),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatDuration(ms),
            fontSize = valueSize,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f * alpha)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "${percent.toInt()}%",
            fontSize = valueSize,
            color = Color.White.copy(alpha = 0.4f * alpha),
            modifier = Modifier.width(34.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

/**
 * The name of the zone the user is in right now, with the one line saying what training
 * there achieves — the "where should I be" guidance, shown for the current zone only so
 * it can be read at a glance mid-workout.
 */
@Composable
fun CurrentZoneLabel(zone: HrZone, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(zoneColor(zone))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = zone.displayName.uppercase(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = zoneColor(zone)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = zone.goal,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
