package com.triforge.domain

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Text for the numbers athletes read: duration, distance and pace. */
object Formatters {

    /** "1h 05m", or "45m" under an hour. */
    fun duration(seconds: Long): String {
        val totalMinutes = (seconds.coerceAtLeast(0) / 60.0).roundToLong()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "%dh %02dm".format(Locale.ROOT, hours, minutes) else "${minutes}m"
    }

    /** Swims are read in metres, rides and runs in kilometres. */
    fun distance(sport: Sport, meters: Double, locale: Locale = Locale.ITALY): String =
        if (sport == Sport.SWIM) {
            "%,d m".format(locale, meters.roundToInt())
        } else {
            "%.1f km".format(locale, meters / 1000.0)
        }

    /**
     * The speed measure each discipline uses: time per 100 m for swimming,
     * time per km for running, km/h for cycling. Null when there is no
     * distance or time to compute it from, or the sport has no natural pace.
     */
    fun pace(sport: Sport, meters: Double, seconds: Long, locale: Locale = Locale.ITALY): String? {
        if (meters <= 0.0 || seconds <= 0) return null
        return when (sport) {
            Sport.SWIM -> "${minutesAndSeconds(seconds / (meters / 100.0))} /100m"
            Sport.RUN -> "${minutesAndSeconds(seconds / (meters / 1000.0))} /km"
            Sport.BIKE -> "%.1f km/h".format(locale, meters / seconds * 3.6)
            Sport.OTHER -> null
        }
    }

    private fun minutesAndSeconds(seconds: Double): String {
        val total = seconds.roundToLong()
        return "%d:%02d".format(Locale.ROOT, total / 60, total % 60)
    }
}
