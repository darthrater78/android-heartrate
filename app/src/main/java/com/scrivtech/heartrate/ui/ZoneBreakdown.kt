package com.scrivtech.heartrate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrivtech.heartrate.data.HrZone
import com.scrivtech.heartrate.data.lowerBpm
import com.scrivtech.heartrate.data.upperBpm
import com.scrivtech.heartrate.ui.theme.Dimens
import com.scrivtech.heartrate.ui.theme.Hairline
import com.scrivtech.heartrate.ui.theme.HrmType
import com.scrivtech.heartrate.ui.theme.OnZone
import com.scrivtech.heartrate.ui.theme.ShapeMd
import com.scrivtech.heartrate.ui.theme.ShapeSm
import com.scrivtech.heartrate.ui.theme.TextMuted
import com.scrivtech.heartrate.ui.theme.TextPrimary

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
    height: Dp = 8.dp
) {
    val occupied = timeInZones.filterValues { it > 0L }
    if (occupied.isEmpty()) return

    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        occupied.forEach { (zone, ms) ->
            Box(
                modifier = Modifier
                    .weight(ms.toFloat())
                    .fillMaxHeight()
                    .clip(ShapeSm)
                    .background(zoneColor(zone))
            )
        }
    }
}

/**
 * One hairline-separated row per zone, showing how long was spent there.
 *
 * [compact] trims the row set to zones that were actually reached and drops the bar and
 * the percentages, for the live screen where this sits under the reading and must not
 * compete with it. The session summary shows every zone including the empty ones, so that
 * a session with nothing in Peak visibly says so rather than silently omitting the row.
 *
 * [currentZone] is the zone the user is in right now; its row is emphasised by weight.
 */
@Composable
fun ZoneBreakdown(
    timeInZones: Map<HrZone, Long>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    currentZone: HrZone? = null
) {
    val total = timeInZones.values.sum()
    if (total <= 0L) return

    val rows = if (compact) {
        timeInZones.filterValues { it > 0L }
    } else {
        timeInZones
    }

    Column(modifier = modifier) {
        if (!compact) {
            ZoneBar(timeInZones = timeInZones, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(Dimens.Md))
        }
        HorizontalDivider(color = Hairline)
        rows.forEach { (zone, ms) ->
            ZoneRow(
                zone = zone,
                ms = ms,
                percent = if (compact) null else (ms * 100f / total).toInt(),
                current = zone == currentZone
            )
            HorizontalDivider(color = Hairline)
        }
    }
}

@Composable
private fun ZoneRow(zone: HrZone, ms: Long, percent: Int?, current: Boolean) {
    val weight = if (current) FontWeight.SemiBold else null
    val nameColor = if (current) TextPrimary else TextMuted
    // An empty zone stays legible but recedes, so the rows that carry time read first.
    val valueColor = if (current || (percent != null && ms > 0L)) TextPrimary else TextMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(ShapeSm)
                .background(zoneColor(zone))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = zone.displayName,
            style = HrmType.Body,
            fontWeight = weight,
            color = nameColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatDuration(ms),
            style = HrmType.NumeralMd,
            fontWeight = weight,
            color = valueColor
        )
        if (percent != null) {
            Text(
                text = "$percent%",
                style = HrmType.NumeralMd,
                color = TextMuted,
                modifier = Modifier.widthIn(min = 44.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

/** Names the zone the user is in right now: the one filled, coloured block on the screen. */
@Composable
fun ZoneChip(zone: HrZone, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(ShapeMd)
            .background(zoneColor(zone))
            .padding(horizontal = Dimens.Md),
        contentAlignment = Alignment.Center
    ) {
        Text(text = zone.displayName, style = HrmType.LabelLg, color = OnZone)
    }
}

/**
 * The user's four zones laid out along one bpm axis, sized by each zone's span, with the
 * boundaries labelled beneath.
 *
 * With a [bpm] it is the live "where am I" gauge: the current zone stays solid, the others
 * recede, and a marker sits at the reading. With no [bpm] it is the static legend on the
 * home screen, where every segment is solid.
 */
@Composable
fun ZoneScale(maxHr: Int, modifier: Modifier = Modifier, bpm: Int? = null, current: HrZone? = null) {
    val measurer = rememberTextMeasurer()
    val labelStyle = HrmType.NumeralMd.copy(color = TextMuted)
    val description = HrZone.entries.drop(1).joinToString(", ") { zone ->
        val upper = zone.upperBpm(maxHr)
        val range = if (upper != null) "${zone.lowerBpm(maxHr)} to $upper" else "${zone.lowerBpm(maxHr)} and above"
        "${zone.displayName} $range"
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = "Zones in beats per minute: $description" }
    ) {
        // The axis starts a little under Fat Burn so Below gets a visible sliver rather
        // than the half of the bar its real 0-to-50% span would take.
        val axisMin = maxHr * SCALE_AXIS_START
        val axisMax = maxHr.toFloat()
        fun xOf(value: Float): Float =
            ((value - axisMin) / (axisMax - axisMin)).coerceIn(0f, 1f) * size.width

        val barTop = 10.dp.toPx()
        val barHeight = 14.dp.toPx()
        val gap = 2.dp.toPx()
        val corner = CornerRadius(2.dp.toPx())

        HrZone.entries.forEach { zone ->
            val start = if (zone == HrZone.BELOW) axisMin else zone.lowerBpm(maxHr).toFloat()
            val end = zone.upperBpm(maxHr)?.plus(1)?.toFloat() ?: axisMax
            val left = xOf(start) + if (zone == HrZone.BELOW) 0f else gap / 2
            val right = xOf(end) - if (zone == HrZone.PEAK) 0f else gap / 2
            val solid = current == null || zone == current
            drawRoundRect(
                color = zoneColor(zone),
                topLeft = Offset(left, barTop),
                size = Size((right - left).coerceAtLeast(0f), barHeight),
                cornerRadius = corner,
                alpha = if (solid) 1f else ZONE_INACTIVE_ALPHA
            )
        }

        HrZone.entries.drop(1).forEach { zone ->
            val label = measurer.measure("${zone.lowerBpm(maxHr)}", labelStyle)
            val x = (xOf(zone.lowerBpm(maxHr).toFloat()) - label.size.width / 2f)
                .coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(x, barTop + barHeight + 8.dp.toPx()))
        }

        if (bpm != null) {
            val markerWidth = 3.dp.toPx()
            val x = xOf(bpm.toFloat()).coerceIn(markerWidth / 2, size.width - markerWidth / 2)
            drawRoundRect(
                color = TextPrimary,
                topLeft = Offset(x - markerWidth / 2, 2.dp.toPx()),
                size = Size(markerWidth, 30.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}

/** Where the scale's axis starts, as a fraction of maximum heart rate. */
private const val SCALE_AXIS_START = 0.45f
