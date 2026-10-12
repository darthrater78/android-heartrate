package com.scrivtech.heartrate.ui

import androidx.compose.ui.graphics.Color
import com.scrivtech.heartrate.data.HrZone
import com.scrivtech.heartrate.ui.theme.ZoneBelow
import com.scrivtech.heartrate.ui.theme.ZoneCardio
import com.scrivtech.heartrate.ui.theme.ZoneFatBurn
import com.scrivtech.heartrate.ui.theme.ZonePeak

/**
 * The colour standing for a zone, and the only way any colour reaches the screen.
 *
 * There used to be a second mapping straight from bpm to a gradient with fixed thresholds.
 * It ignored the user's age, so the live number and the graph could show one colour while
 * the zone label, which is age-based, named a different zone. Colour is now always derived
 * from the zone, and the zone always from [com.scrivtech.heartrate.data.zoneFor].
 */
fun zoneColor(zone: HrZone): Color = when (zone) {
    HrZone.BELOW -> ZoneBelow
    HrZone.FAT_BURN -> ZoneFatBurn
    HrZone.CARDIO -> ZoneCardio
    HrZone.PEAK -> ZonePeak
}

/** Opacity of the zone bands behind the trace graph and of the scale's inactive segments. */
const val ZONE_BAND_ALPHA = 0.14f
const val ZONE_INACTIVE_ALPHA = 0.35f
