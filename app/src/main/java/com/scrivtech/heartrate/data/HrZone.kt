package com.scrivtech.heartrate.data

import kotlin.math.roundToInt

/**
 * Heart rate zones as Google Health and Fitbit define them: bands expressed as a
 * percentage of maximum heart rate, with the maximum estimated from age.
 *
 * Only each zone's lower bound is stored. The upper bound is read from the next zone, so
 * the bands cannot drift out of agreement the way two independently maintained numbers
 * eventually would.
 */
enum class HrZone(
    val displayName: String,
    /** One line on what training in this zone actually achieves. */
    val goal: String,
    val lowerPercent: Int
) {
    BELOW("Below zones", "Warm-up pace — not yet training", 0),
    FAT_BURN("Fat Burn", "Burns fat, builds base endurance", 50),
    CARDIO("Cardio", "Builds aerobic fitness and stamina", 70),
    PEAK("Peak", "Short efforts — builds speed and power", 85);

    /** Exclusive upper percentage, or null for [PEAK], which has no ceiling. */
    val upperPercent: Int?
        get() = HrZone.entries.getOrNull(ordinal + 1)?.lowerPercent
}

/**
 * The standard age-predicted maximum heart rate.
 *
 * It is only an estimate and the spread across individuals is wide, but it is the same
 * estimate Google Health uses. Agreeing with the number the user already sees elsewhere
 * matters more here than adopting a marginally better formula they would not recognise.
 */
fun maxHrForAge(age: Int): Int = 220 - age

/** Lowest BPM that counts as this zone at [maxHr]. */
fun HrZone.lowerBpm(maxHr: Int): Int = (maxHr * lowerPercent / 100.0).roundToInt()

/** Highest BPM still inside this zone at [maxHr], or null for [HrZone.PEAK]. */
fun HrZone.upperBpm(maxHr: Int): Int? =
    upperPercent?.let { (maxHr * it / 100.0).roundToInt() - 1 }

/** The zone [bpm] falls in. [HrZone.BELOW] starts at zero, so this always matches. */
fun zoneFor(bpm: Int, maxHr: Int): HrZone =
    HrZone.entries.last { bpm >= it.lowerBpm(maxHr) }

/**
 * Milliseconds spent in each zone across [readings], keyed by zone with every zone
 * present so callers can render a stable set of rows.
 *
 * A reading holds its zone until the next one arrives, so the time is carried by the gaps
 * between samples rather than by the samples themselves. Gaps are capped at
 * [MAX_ATTRIBUTED_GAP_MS]: a reconnect can leave minutes between two readings, and
 * crediting all of that to whichever zone happened to precede the dropout would quietly
 * convert a connection problem into a training statistic.
 */
fun timeInZones(readings: List<HrReading>, maxHr: Int): Map<HrZone, Long> {
    val totals = linkedMapOf<HrZone, Long>()
    HrZone.entries.forEach { totals[it] = 0L }

    readings.zipWithNext { current, next ->
        val gap = (next.timestampMs - current.timestampMs)
            .coerceIn(0L, MAX_ATTRIBUTED_GAP_MS)
        val zone = zoneFor(current.bpm, maxHr)
        totals[zone] = (totals[zone] ?: 0L) + gap
    }
    return totals
}

/**
 * Longest gap between two readings that still counts as time spent training. Readings
 * arrive about once a second, so anything approaching this is already a dropout.
 */
private const val MAX_ATTRIBUTED_GAP_MS = 10_000L
