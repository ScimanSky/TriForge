package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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
    /**
     * Volume of the same stretch of the period before: as many days as have
     * passed in this one, so a Wednesday is not measured against a whole
     * week. Null when that period was not loaded.
     */
    val previousDistanceMeters: Double?,
    val previousDurationSec: Long?,
    /** One entry per week, oldest first, weeks without training included. */
    val weekly: List<WeekVolume>,
    /** Averages weighted by session time, see [SportStats.timeWeighted]. */
    val avgHr: Int?,
    val avgCadence: Int?,
    val avgPowerWatts: Int?,
    /** SWOLF of the pool length swum most, see [swolfPoolMeters]. */
    val avgSwolf: Int?,
    /** The pool length [avgSwolf] belongs to: SWOLF in a 50 m pool is not comparable with 25 m. */
    val swolfPoolMeters: Double? = null,
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
        // Days of the current period so far, today included.
        val elapsedDays = ChronoUnit.DAYS.between(periodStart, today) + 1
        val previousEnd = previousStart.plusDays(elapsedDays)
        val previous = if (weeks * 2 <= loadedWeeks) {
            ofSport.filter { val d = dateOf(it); !d.isBefore(previousStart) && d.isBefore(previousEnd) }
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

        val swolf = swolf(current)
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
            avgHr = timeWeighted(current) { it.avgHr },
            avgCadence = timeWeighted(current) { it.avgCadence },
            avgPowerWatts = timeWeighted(current) { it.avgPowerWatts },
            avgSwolf = swolf?.second,
            swolfPoolMeters = swolf?.first,
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
            withDistance.filter { it.distanceMeters >= minimum && it.paceSec > 0 && comparableForSpeed(it) }
                .minByOrNull { it.paceSec / it.distanceMeters }
                ?.let { PersonalRecord(RecordKind.FASTEST, it) },
        )
    }

    /**
     * A pool swim is timed on a known length; open water distance comes from
     * GPS and the conditions change, so its pace does not race a pool record.
     * When there are only open water swims they compete among themselves.
     */
    private fun comparableForSpeed(activity: Activity): Boolean = activity.sport != Sport.SWIM || activity.openWater != true

    /**
     * Average of a per-session value weighted by the time it was measured
     * over. A 3-hour ride and a 20-minute spin do not count the same: the
     * plain mean of session averages would let short sessions pull it.
     */
    fun timeWeighted(activities: List<Activity>, value: (Activity) -> Int?): Int? {
        var weighted = 0.0
        var seconds = 0L
        activities.forEach { activity ->
            val v = value(activity) ?: return@forEach
            val time = activity.durationSec.takeIf { it > 0 } ?: return@forEach
            weighted += v.toDouble() * time
            seconds += time
        }
        return if (seconds == 0L) null else (weighted / seconds).roundToInt()
    }

    /** Pool length swum most (by distance) and the distance-weighted SWOLF there. */
    private fun swolf(activities: List<Activity>): Pair<Double, Int>? {
        val pool = activities.filter { it.swolf != null && it.openWater != true && it.poolLengthMeters != null }
        val length = pool.groupBy { it.poolLengthMeters!! }
            .maxByOrNull { (_, list) -> list.sumOf { it.distanceMeters } }?.key ?: return null
        val same = pool.filter { it.poolLengthMeters == length && it.distanceMeters > 0 }
        if (same.isEmpty()) return null
        val value = same.sumOf { it.swolf!! * it.distanceMeters } / same.sumOf { it.distanceMeters }
        return length to value.roundToInt()
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
