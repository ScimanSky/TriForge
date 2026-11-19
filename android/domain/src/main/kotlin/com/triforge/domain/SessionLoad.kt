package com.triforge.domain

import kotlin.math.pow

/** How the load of a session was worked out, best first. */
enum class LoadMethod { POWER, PACE, SWIM_PACE, GARMIN_LOAD, DURATION }

enum class LoadQuality { HIGH, MEDIUM, LOW }

/** One session's load in TSS-equivalents: 100 is one hour at threshold. */
data class SessionLoad(
    val activity: Activity,
    val tss: Double,
    val method: LoadMethod,
    val quality: LoadQuality,
)

/**
 * Puts every session on one load scale, so swim, bike, run, the gym and
 * whatever else the watch recorded can be added up.
 *
 * Order of preference for each session:
 * 1. bike with power and FTP: hours x (NP/FTP)^2 x 100;
 * 2. run with threshold pace: hours x (speed/threshold speed)^2 x 100;
 * 3. swim with CSS: hours x (speed/CSS)^3 x 100 (cube: water drag),
 *    interval by interval when the laps are known;
 * 4. Garmin training load, rescaled to TSS on the athlete's own sessions
 *    that have both measures;
 * 5. duration only, at a typical intensity for the sport or, outside the
 *    three disciplines, for the kind of activity (a walk is not a HIIT class).
 *
 * The gym never counts less than its duration at [STRENGTH_FLOOR_INTENSITY]:
 * the watch works its load out from the heart rate, which stays low between
 * sets and misses most of what lifting costs the muscles.
 *
 * Pace-based loads use average speed over the whole session, pauses
 * included, so interval sessions come out a little low. Swim heart rate from
 * the wrist is unreliable, so Garmin load on a swim counts as low quality.
 */
object SessionLoadModel {

    /** Intensity factor cap: anything above is a sensor or GPS glitch. */
    private const val MAX_IF = 1.3

    /**
     * Garmin load to TSS when the athlete has no session with both. Garmin's
     * EPOC-based load runs higher than TSS for the same session.
     */
    const val DEFAULT_GARMIN_TO_TSS = 0.7

    /** Sessions with both measures needed before the personal ratio is used. */
    const val MIN_CALIBRATION_SESSIONS = 3

    /** The intensity a gym session counts for at least, whatever the heart rate said: about 36 TSS an hour. */
    const val STRENGTH_FLOOR_INTENSITY = 0.60

    fun typicalIntensity(sport: Sport): Double = when (sport) {
        Sport.SWIM -> 0.75
        Sport.BIKE -> 0.70
        Sport.RUN -> 0.80
        Sport.STRENGTH -> STRENGTH_FLOOR_INTENSITY
        Sport.OTHER -> typicalIntensity(OtherKind.GENERIC)
    }

    /** Typical intensity of an activity outside the three disciplines and the gym. */
    fun typicalIntensity(kind: OtherKind): Double = when (kind) {
        OtherKind.MOBILITY -> 0.30
        OtherKind.WALK -> 0.40
        OtherKind.HIKE -> 0.55
        OtherKind.CARDIO -> 0.70
        OtherKind.GENERIC -> 0.60
    }

    fun typicalIntensity(activity: Activity): Double =
        if (activity.sport == Sport.OTHER) typicalIntensity(activity.kind) else typicalIntensity(activity.sport)

    fun estimate(activities: List<Activity>, profile: AthleteProfile): List<SessionLoad> {
        val direct = activities.associateWith { direct(it, profile) }
        val ratios = direct.mapNotNull { (activity, load) ->
            val garmin = activity.trainingLoad ?: return@mapNotNull null
            if (load == null || garmin <= 0) null else load.tss / garmin
        }
        val calibrated = ratios.size >= MIN_CALIBRATION_SESSIONS
        val ratio = if (calibrated) Stats.median(ratios) else DEFAULT_GARMIN_TO_TSS

        return activities.map { activity ->
            direct[activity] ?: fallback(activity, ratio, calibrated)
        }
    }

    private fun direct(activity: Activity, profile: AthleteProfile): SessionLoad? {
        val hours = activity.durationSec / 3600.0
        if (hours <= 0) return null
        when (activity.sport) {
            Sport.BIKE -> {
                val power = activity.normPowerWatts ?: activity.avgPowerWatts
                val ftp = profile.ftpWatts
                if (power != null && power > 0 && ftp != null && ftp > 0) {
                    val intensity = (power.toDouble() / ftp).coerceAtMost(MAX_IF)
                    return SessionLoad(activity, hours * intensity.pow(2) * 100, LoadMethod.POWER, LoadQuality.HIGH)
                }
            }

            Sport.RUN -> {
                val pace = profile.thresholdPaceSecPerKm
                if (pace != null && pace > 0 && activity.distanceMeters > 0) {
                    val speed = activity.distanceMeters / activity.durationSec
                    val intensity = (speed / (1000.0 / pace)).coerceAtMost(MAX_IF)
                    return SessionLoad(activity, hours * intensity.pow(2) * 100, LoadMethod.PACE, LoadQuality.HIGH)
                }
            }

            Sport.SWIM -> {
                val css = profile.cssSecPer100m
                SwimAnalysis.lapTss(activity, css)?.let {
                    return SessionLoad(activity, it, LoadMethod.SWIM_PACE, LoadQuality.HIGH)
                }
                if (css != null && css > 0 && activity.distanceMeters > 0) {
                    // Speed and time are both those of the swimming itself.
                    // Over the whole session the rests at the wall drag the
                    // speed down, and the cube turns that into a third of
                    // the real load.
                    val swimSec = activity.movingSec
                    val speed = activity.distanceMeters / swimSec
                    val intensity = (speed / (100.0 / css)).coerceAtMost(MAX_IF)
                    // Medium without a measured swimming time: the pauses are
                    // then inside the duration.
                    val quality = if (activity.movingDurationSec != null) LoadQuality.HIGH else LoadQuality.MEDIUM
                    return SessionLoad(
                        activity, swimSec / 3600.0 * intensity.pow(3) * 100, LoadMethod.SWIM_PACE, quality,
                    )
                }
            }

            Sport.STRENGTH, Sport.OTHER -> Unit
        }
        return null
    }

    private fun fallback(activity: Activity, ratio: Double, calibrated: Boolean): SessionLoad {
        val hours = activity.durationSec.coerceAtLeast(0) / 3600.0
        val byDuration = SessionLoad(activity, hours * typicalIntensity(activity).pow(2) * 100, LoadMethod.DURATION, LoadQuality.LOW)
        val garmin = activity.trainingLoad
        if (garmin != null && garmin > 0) {
            // Heart rate reads a swim badly at the wrist and a gym session badly by nature.
            val trusted = calibrated && activity.sport != Sport.SWIM && activity.sport != Sport.STRENGTH
            val byWatch = SessionLoad(activity, garmin * ratio, LoadMethod.GARMIN_LOAD, if (trusted) LoadQuality.MEDIUM else LoadQuality.LOW)
            return if (activity.sport == Sport.STRENGTH && byDuration.tss > byWatch.tss) byDuration else byWatch
        }
        return byDuration
    }
}
