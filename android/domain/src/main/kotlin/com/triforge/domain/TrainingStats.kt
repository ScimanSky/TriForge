package com.triforge.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class SportTotals(
    val sport: Sport,
    val sessions: Int = 0,
    val distanceMeters: Double = 0.0,
    val durationSec: Long = 0,
    val elevationGainMeters: Double = 0.0,
)

/** Totals for one training week, Monday to Sunday. */
data class WeekSummary(
    val weekStart: LocalDate,
    val totals: Map<Sport, SportTotals>,
) {
    val sessions: Int get() = totals.values.sumOf { it.sessions }
    val durationSec: Long get() = totals.values.sumOf { it.durationSec }

    fun of(sport: Sport): SportTotals = totals[sport] ?: SportTotals(sport)
}

object TrainingStats {

    /** The three disciplines shown on the dashboard, in race order. */
    val triathlonSports = listOf(Sport.SWIM, Sport.BIKE, Sport.RUN)

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Totals for the week containing [day], in the athlete's time zone. */
    fun weekSummary(activities: List<Activity>, day: LocalDate, zone: ZoneId): WeekSummary {
        val start = weekStart(day)
        val end = start.plusWeeks(1)
        val inWeek = activities.filter {
            val date = it.startTime.atZone(zone).toLocalDate()
            !date.isBefore(start) && date.isBefore(end)
        }
        return WeekSummary(start, totalsBySport(inWeek))
    }

    /**
     * One summary per week for the last [weeks] weeks, oldest first and ending
     * with the week containing [today]. Weeks without training are included,
     * so a chart drawn from the result has no gaps.
     */
    fun weeklyTrend(activities: List<Activity>, weeks: Int, today: LocalDate, zone: ZoneId): List<WeekSummary> {
        require(weeks > 0) { "weeks must be positive" }
        val current = weekStart(today)
        return (weeks - 1 downTo 0).map { back ->
            weekSummary(activities, current.minusWeeks(back.toLong()), zone)
        }
    }

    /**
     * Percent change from [previous] to [current], or null when there is no
     * previous value to compare against.
     */
    fun percentChange(current: Double, previous: Double): Int? {
        if (previous <= 0.0) return null
        return ((current - previous) / previous * 100).roundToInt()
    }

    private fun totalsBySport(activities: List<Activity>): Map<Sport, SportTotals> =
        activities.groupBy { it.sport }.mapValues { (sport, list) ->
            SportTotals(
                sport = sport,
                sessions = list.size,
                distanceMeters = list.sumOf { it.distanceMeters },
                durationSec = list.sumOf { it.durationSec },
                elevationGainMeters = list.sumOf { it.elevationGainMeters ?: 0.0 },
            )
        }
}
