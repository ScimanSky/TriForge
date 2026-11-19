package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

/** One week of one sport. */
data class WeekVolume(
    val weekStart: LocalDate,
    val sessions: Int,
    val distanceMeters: Double,
    val durationSec: Long,
    /** Seconds the week's pace is measured over, see [Activity.paceSec]. */
    val paceSec: Long = durationSec,
) {
    /** ISO week number, the label athletes know from their watch. */
    val weekNumber: Int get() = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** Seconds per metre over the whole week, or null without distance. */
    val secondsPerMeter: Double? get() = if (distanceMeters > 0 && paceSec > 0) paceSec / distanceMeters else null
}

enum class RecordKind { LONGEST_DISTANCE, LONGEST_DURATION, FASTEST }

data class PersonalRecord(val kind: RecordKind, val activity: Activity)

/** Everything the per-sport screen shows for one sport and one period. */
data class SportReport(
    val sport: Sport,
    val weeks: Int,
    val sessions: Int,
    val distanceMeters: Double,
    val durationSec: Long,
    /** Seconds the period's pace is measured over, see [Activity.paceSec]. */
    val paceSec: Long,
    val elevationGainMeters: Double,
    /** Volume of the period before this one; null when it was not loaded. */
    val previousDistanceMeters: Double?,
    val previousDurationSec: Long?,
    /** One entry per week, oldest first, weeks without training included. */
    val weekly: List<WeekVolume>,
    val avgHr: Int?,
    val avgCadence: Int?,
    val avgPowerWatts: Int?,
    val avgSwolf: Int?,
    val records: List<PersonalRecord>,
) {
    val sessionsPerWeek: Double get() = sessions.toDouble() / weeks

    /** Change in the sport's volume measure against the previous period. */
    val volumeChangePercent: Int?
        get() = if (SportStats.measuresVolumeByTime(sport)) {
            previousDurationSec?.let { TrainingStats.percentChange(durationSec.toDouble(), it.toDouble()) }
        } else {
            previousDistanceMeters?.let { TrainingStats.percentChange(distanceMeters, it) }
        }
}

object SportStats {

    /** Cyclists count hours; swimmers and runners count distance. */
    fun measuresVolumeByTime(sport: Sport): Boolean = sport == Sport.BIKE || sport == Sport.OTHER

    /** A record for speed only counts over a distance worth racing. */
    fun minimumDistanceForSpeedRecord(sport: Sport): Double = when (sport) {
        Sport.SWIM -> 400.0
        Sport.BIKE -> 20_000.0
        Sport.RUN -> 5_000.0
        Sport.OTHER -> Double.MAX_VALUE
    }

    /**
     * Report for [sport] over the last [weeks] whole weeks ending with the
     * week of [today]. [loadedWeeks] is how many weeks [activities] cover: the
     * comparison with the previous period is only made when that period was
     * loaded in full, otherwise it would look like a jump in volume.
     */
    fun report(
        activities: List<Activity>,
        sport: Sport,
        weeks: Int,
        today: LocalDate,
        zone: ZoneId,
        loadedWeeks: Int,
    ): SportReport {
        require(weeks > 0) { "weeks must be positive" }
        val ofSport = activities.filter { it.sport == sport }
        val periodStart = TrainingStats.weekStart(today).minusWeeks(weeks - 1L)
        val previousStart = periodStart.minusWeeks(weeks.toLong())

        fun dateOf(activity: Activity) = activity.startTime.atZone(zone).toLocalDate()
        val current = ofSport.filter { !dateOf(it).isBefore(periodStart) }
        val previous = if (weeks * 2 <= loadedWeeks) {
            ofSport.filter { val d = dateOf(it); !d.isBefore(previousStart) && d.isBefore(periodStart) }
        } else {
            null
        }

        val weekly = (0 until weeks).map { index ->
            val start = periodStart.plusWeeks(index.toLong())
            val end = start.plusWeeks(1)
            val inWeek = current.filter { val d = dateOf(it); !d.isBefore(start) && d.isBefore(end) }
            WeekVolume(
                weekStart = start,
                sessions = inWeek.size,
                distanceMeters = inWeek.sumOf { it.distanceMeters },
                durationSec = inWeek.sumOf { it.durationSec },
                paceSec = paceSeconds(inWeek),
            )
        }

        return SportReport(
            sport = sport,
            weeks = weeks,
            sessions = current.size,
            distanceMeters = current.sumOf { it.distanceMeters },
            durationSec = current.sumOf { it.durationSec },
            paceSec = paceSeconds(current),
            elevationGainMeters = current.sumOf { it.elevationGainMeters ?: 0.0 },
            previousDistanceMeters = previous?.sumOf { it.distanceMeters },
            previousDurationSec = previous?.sumOf { it.durationSec },
            weekly = weekly,
            avgHr = average(current) { it.avgHr },
            avgCadence = average(current) { it.avgCadence },
            avgPowerWatts = average(current) { it.avgPowerWatts },
            avgSwolf = average(current) { it.swolf },
            records = records(ofSport, sport),
        )
    }

    /**
     * Pace time of the sessions that have a distance. One without it (an
     * indoor ride with no speed sensor) would slow the average for nothing.
     */
    private fun paceSeconds(activities: List<Activity>): Long =
        activities.filter { it.distanceMeters > 0 }.sumOf { it.paceSec }

    /** Best efforts among all loaded activities of the sport. */
    private fun records(activities: List<Activity>, sport: Sport): List<PersonalRecord> {
        val withDistance = activities.filter { it.distanceMeters > 0 }
        val minimum = minimumDistanceForSpeedRecord(sport)
        return listOfNotNull(
            withDistance.maxByOrNull { it.distanceMeters }?.let { PersonalRecord(RecordKind.LONGEST_DISTANCE, it) },
            activities.filter { it.durationSec > 0 }.maxByOrNull { it.durationSec }
                ?.let { PersonalRecord(RecordKind.LONGEST_DURATION, it) },
            withDistance.filter { it.distanceMeters >= minimum && it.paceSec > 0 }
                .minByOrNull { it.paceSec / it.distanceMeters }
                ?.let { PersonalRecord(RecordKind.FASTEST, it) },
        )
    }

    private fun average(activities: List<Activity>, value: (Activity) -> Int?): Int? {
        val values = activities.mapNotNull(value)
        return if (values.isEmpty()) null else values.average().roundToInt()
    }
}

/**
 * Activities split into training weeks, newest week first, keeping the order
 * of the activities inside each week.
 */
fun List<Activity>.groupedByWeek(zone: ZoneId): List<Pair<LocalDate, List<Activity>>> =
    groupBy { TrainingStats.weekStart(it.startTime.atZone(zone).toLocalDate()) }
        .toList()
        .sortedByDescending { it.first }
