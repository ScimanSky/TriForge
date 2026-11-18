package com.triforge.domain

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Text for the numbers athletes read: duration, distance and pace. */
object Formatters {

    /** "1 h 05′", or "45′" under an hour. */
    fun duration(seconds: Long): String {
        val totalMinutes = (seconds.coerceAtLeast(0) / 60.0).roundToLong()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "%d h %02d′".format(Locale.ROOT, hours, minutes) else "$minutes′"
    }

    /** Exact time as on a watch: "1:38:12", or "44:05" under an hour. */
    fun clock(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }

    /** Swims are read in metres, rides and runs in kilometres. */
    fun distance(sport: Sport, meters: Double, locale: Locale = Locale.ITALY): String =
        if (sport == Sport.SWIM) {
            "%,d m".format(locale, meters.roundToInt())
        } else {
            "%.1f km".format(locale, meters / 1000.0)
        }

    /** Kilometres with one decimal and no unit, for charts and compact totals. */
    fun kilometers(meters: Double, locale: Locale = Locale.ITALY): String =
        "%.1f".format(locale, meters / 1000.0)

    /** Hours with one decimal and no unit, for chart labels. */
    fun hours(seconds: Long, locale: Locale = Locale.ITALY): String =
        "%.1f".format(locale, seconds / 3600.0)

    /**
     * The speed measure each discipline uses: time per 100 m for swimming,
     * time per km for running, km/h for cycling. Null when there is no
     * distance or time to compute it from, or the sport has no natural pace.
     */
    fun pace(sport: Sport, meters: Double, seconds: Long, locale: Locale = Locale.ITALY): String? {
        val value = paceValue(sport, meters, seconds, locale) ?: return null
        return "$value ${paceUnit(sport)}"
    }

    /** The number part of [pace]: "5:20", or "30,0" for cycling. */
    fun paceValue(sport: Sport, meters: Double, seconds: Long, locale: Locale = Locale.ITALY): String? {
        if (meters <= 0.0 || seconds <= 0) return null
        return when (sport) {
            Sport.SWIM -> minutesAndSeconds(seconds / (meters / 100.0))
            Sport.RUN -> minutesAndSeconds(seconds / (meters / 1000.0))
            Sport.BIKE -> "%.1f".format(locale, meters / seconds * 3.6)
            Sport.OTHER -> null
        }
    }

    fun paceUnit(sport: Sport): String = when (sport) {
        Sport.SWIM -> "/100 m"
        Sport.RUN -> "/km"
        Sport.BIKE -> "km/h"
        Sport.OTHER -> ""
    }

    /** "5:20" from a number of seconds. */
    fun minutesAndSeconds(seconds: Double): String {
        val total = seconds.roundToLong().coerceAtLeast(0)
        return "%d:%02d".format(Locale.ROOT, total / 60, total % 60)
    }

    /** Up to two initials for an avatar: "Luca Rossi" gives "LR". */
    fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }
}
