package com.scrivtech.heartrate.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.scrivtech.heartrate.data.HrZone

private val ZoneRest = Color(0xFF42A5F5)
private val ZoneLight = Color(0xFF66BB6A)
private val ZoneModerate = Color(0xFFFFEE58)
private val ZoneHard = Color(0xFFFFA726)
private val ZoneMax = Color(0xFFEF5350)

fun hrZoneColor(bpm: Int): Color {
    return when {
        bpm <= 80 -> ZoneRest
        bpm <= 100 -> lerp(ZoneRest, ZoneLight, (bpm - 80) / 20f)
        bpm <= 130 -> lerp(ZoneLight, ZoneModerate, (bpm - 100) / 30f)
        bpm <= 160 -> lerp(ZoneModerate, ZoneHard, (bpm - 130) / 30f)
        bpm <= 180 -> lerp(ZoneHard, ZoneMax, (bpm - 160) / 20f)
        else -> ZoneMax
    }
}

/**
 * The flat colour standing for a whole zone, used wherever a zone is named or measured
 * rather than plotted.
 *
 * [hrZoneColor] stays a continuous gradient because the graph and the live BPM readout
 * benefit from moving smoothly as the rate drifts. Zone rows and legends need the
 * opposite: four fixed, obviously distinct colours the eye can match between the live
 * screen and a session summary. Yellow is skipped so the four sit further apart.
 */
fun zoneColor(zone: HrZone): Color = when (zone) {
    HrZone.BELOW -> ZoneRest
    HrZone.FAT_BURN -> ZoneLight
    HrZone.CARDIO -> ZoneHard
    HrZone.PEAK -> ZoneMax
}
