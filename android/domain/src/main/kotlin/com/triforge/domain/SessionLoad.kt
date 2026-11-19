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
 * Puts swim, bike and run on one load scale, so the three can be added up.
 *
 * Order of preference for each session:
 * 1. bike with power and FTP: hours x (NP/FTP)^2 x 100;
 * 2. run with threshold pace: hours x (speed/threshold speed)^2 x 100;
 * 3. swim with CSS: hours x (speed/CSS)^3 x 100 (cube: water drag);
 * 4. Garmin training load, rescaled to TSS on the athlete's own sessions
 *    that have both measures;
 * 5. duration only, at a typical intensity for the sport.
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

    fun typicalIntensity(sport: Sport): Double = when (sport) {
        Sport.SWIM -> 0.75
        Sport.BIKE -> 0.70
        Sport.RUN -> 0.80
        Sport.OTHER -> 0.60
    }

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
                if (css != null && css > 0 && activity.distanceMeters > 0) {
                    val speed = activity.distanceMeters / activity.durationSec
                    val intensity = (speed / (100.0 / css)).coerceAtMost(MAX_IF)
                    // Medium: pauses at the wall are inside the duration.
                    return SessionLoad(activity, hours * intensity.pow(3) * 100, LoadMethod.SWIM_PACE, LoadQuality.MEDIUM)
                }
            }

            Sport.OTHER -> Unit
        }
        return null
    }

    private fun fallback(activity: Activity, ratio: Double, calibrated: Boolean): SessionLoad {
        val garmin = activity.trainingLoad
        if (garmin != null && garmin > 0) {
            val quality = if (calibrated && activity.sport != Sport.SWIM) LoadQuality.MEDIUM else LoadQuality.LOW
            return SessionLoad(activity, garmin * ratio, LoadMethod.GARMIN_LOAD, quality)
        }
        val hours = activity.durationSec.coerceAtLeast(0) / 3600.0
        val tss = hours * typicalIntensity(activity.sport).pow(2) * 100
        return SessionLoad(activity, tss, LoadMethod.DURATION, LoadQuality.LOW)
    }
}
