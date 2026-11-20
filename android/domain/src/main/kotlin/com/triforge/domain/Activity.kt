package com.triforge.domain

import java.time.Instant

/**
 * What a session counts as. The three disciplines, the gym (strength work
 * that supports them) and [OTHER] for everything else the watch records:
 * walks, hikes, yoga, cardio machines. See [OtherKind] for what an OTHER was.
 */
enum class Sport { SWIM, BIKE, RUN, STRENGTH, OTHER }

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
    /** Garmin's average distance per stroke over the swim, in metres. */
    val strokeDistanceMeters: Double? = null,
    /** A swim's laps, in order, when the source recorded them. Rests are laps with no distance. */
    val laps: List<SwimLap> = emptyList(),
    /** The source's own name for the activity type: Garmin's typeKey ("strength_training", "hiking"). */
    val type: String = "",
    /** What an [Sport.OTHER] session was; [OtherKind.GENERIC] when the source did not say. */
    val kind: OtherKind = OtherKind.GENERIC,
    /** Sets and repetitions of a gym session, when the watch counted them. */
    val sets: Int? = null,
    val reps: Int? = null,
    /** A gym session's exercises, most work first, when the watch recognised them. */
    val exercises: List<ExerciseSummary> = emptyList(),
    /**
     * What the athlete said about the equipment of a swim: no watch records
     * it. Null while nobody has answered; empty marks mean "none".
     */
    val gear: SwimGearMarks? = null,
) {
    /** Kilograms lifted over the gym session (weight x repetitions), or null when no weights were recorded. */
    val liftedKg: Double? get() = exercises.mapNotNull { it.volumeKg }.takeIf { it.isNotEmpty() }?.sum()


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

/**
 * Equipment that makes a swimmer faster than their own stroke does: what
 * is swum with it says nothing about the threshold or about progress.
 */
enum class SwimGear(val key: String) {
    FINS("fins"), PULL_BUOY("pull_buoy"), PADDLES("paddles");

    companion object {
        fun of(key: Any?): SwimGear? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The equipment of one swim, as the athlete marked it: over the whole swim
 * ([session]) or interval by interval ([repeats], by [SwimRepeat.number]).
 */
data class SwimGearMarks(
    val session: Set<SwimGear> = emptySet(),
    val repeats: Map<Int, Set<SwimGear>> = emptyMap(),
) {
    /** The equipment of the swum interval [number], counted from 1. */
    fun of(number: Int): Set<SwimGear> = session + repeats[number].orEmpty()

    /** True when some equipment was marked: false is the athlete saying "none". */
    val any: Boolean get() = session.isNotEmpty() || repeats.values.any { it.isNotEmpty() }

    companion object {
        /** The athlete's answer "no equipment". */
        val NONE = SwimGearMarks()
    }
}

/** Metres and seconds a pace is worked out from. */
data class PaceBase(val meters: Double, val seconds: Double) {
    val secondsPerMeter: Double get() = seconds / meters
}

/**
 * One lap of a swim. In the pool it is one interval between presses of the
 * lap button, and a rest at the wall is a lap with no distance; in open water
 * it is one of the watch's auto laps.
 */
data class SwimLap(
    val distanceMeters: Double,
    val durationSec: Double,
    val strokes: Int? = null,
    val swolf: Int? = null,
    val avgHr: Int? = null,
    /** "free", "back", "breast", "fly", "drill", "mixed" or null. */
    val stroke: String? = null,
    val lengths: List<SwimLength> = emptyList(),
) {
    val isRest: Boolean get() = distanceMeters <= 0.0
}

data class SwimLength(
    val distanceMeters: Double,
    val durationSec: Double,
    val strokes: Int? = null,
    val swolf: Int? = null,
    val stroke: String? = null,
)

/** One exercise of a gym session, summed over its sets. */
data class ExerciseSummary(
    /** The source's category, lower case: "bench_press", "squat", "pull_up". */
    val category: String,
    val sets: Int,
    val reps: Int? = null,
    /** Weight x repetitions in kilograms; null for bodyweight work. */
    val volumeKg: Double? = null,
)
