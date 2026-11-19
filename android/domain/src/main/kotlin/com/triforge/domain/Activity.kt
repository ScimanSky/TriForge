package com.triforge.domain

import java.time.Instant

enum class Sport { SWIM, BIKE, RUN, OTHER }

enum class ActivitySource { GARMIN, HEALTH_CONNECT }

/**
 * One training session, whatever source it came from.
 *
 * Everything after [distanceMeters] is optional: Garmin fills most of it,
 * Health Connect only a few fields.
 */
data class Activity(
    val id: String,
    val source: ActivitySource,
    val sport: Sport,
    val name: String,
    val startTime: Instant,
    val durationSec: Long,
    val distanceMeters: Double,
    /** Seconds in motion, when the source measured them. Never more than [durationSec]. */
    val movingDurationSec: Long? = null,
    val elevationGainMeters: Double? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val calories: Int? = null,
    val trainingLoad: Double? = null,
    val avgPowerWatts: Int? = null,
    val normPowerWatts: Int? = null,
    val avgCadence: Int? = null,
    val aerobicTe: Double? = null,
    val anaerobicTe: Double? = null,
    val vo2max: Double? = null,
    val openWater: Boolean? = null,
    val poolLengthMeters: Double? = null,
    val swolf: Int? = null,
    val strokes: Int? = null,
    val lengths: Int? = null,
) {
    /** Time in motion when the source measured it, the whole duration otherwise. */
    val movingSec: Long
        get() {
            val moving = movingDurationSec?.takeIf { it > 0 } ?: return durationSec
            return if (durationSec > 0) minOf(moving, durationSec) else moving
        }

    /**
     * The seconds pace and speed are measured over.
     *
     * A swim is read on the time spent swimming: the rests at the wall
     * between sets belong to the session, not to the pace, and counting them
     * makes a 1:50 swimmer look like a 2:50 one. Rides and runs keep the
     * timer, which is the pace the watch shows.
     */
    val paceSec: Long get() = if (sport == Sport.SWIM) movingSec else durationSec

    /** Seconds stopped inside the session, or null when the source did not measure them. */
    val restSec: Long? get() = movingDurationSec?.let { (durationSec - movingSec).coerceAtLeast(0) }
}
