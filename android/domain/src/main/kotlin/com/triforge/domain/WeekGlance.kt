package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One day of the current week: training time per sport. */
data class DayTraining(val date: LocalDate, val secondsBySport: Map<Sport, Long>) {
    val totalSec: Long get() = secondsBySport.values.sum()
}

/**
 * One discipline this week against the athlete's own habit.
 *
 * Values are in the sport's volume measure: metres for swim and run,
 * seconds for the bike (see [SportStats.measuresVolumeByTime]).
 */
data class SportWeekGlance(
    val sport: Sport,
    val sessions: Int,
    val done: Double,
    /** Average of the reference weeks up to the same weekday; null without history. */
    val usualToDate: Double?,
    /** Average whole reference week; null without history. */
    val usualWeek: Double?,
) {
    val byTime: Boolean get() = SportStats.measuresVolumeByTime(sport)
}

/**
 * The current week at a glance, compared fairly: on a Wednesday the athlete
 * is measured against what they usually have done by Wednesday, not against
 * a whole week, which would make every week look short until Sunday.
 */
data class WeekGlance(
    val weekStart: LocalDate,
    val today: LocalDate,
    /** Monday to Sunday; days after today are present and empty. */
    val days: List<DayTraining>,
    val sports: List<SportWeekGlance>,
    val sessions: Int,
    val durationSec: Long,
    val usualDurationToDateSec: Long?,
    val usualDurationWeekSec: Long?,
    /** How many past weeks the habit is measured on (0 means no comparison). */
    val referenceWeeks: Int,
) {
    /** Days of the week that have passed, today included (1 on Monday). */
    val elapsedDays: Int get() = ChronoUnit.DAYS.between(weekStart, today).toInt() + 1
}

object WeekGlanceModel {

    /** Weeks the current one is compared with. */
    const val REFERENCE_WEEKS = 4

    fun compute(activities: List<Activity>, today: LocalDate, zone: ZoneId): WeekGlance {
        fun dateOf(a: Activity) = a.startTime.atZone(zone).toLocalDate()

        val weekStart = TrainingStats.weekStart(today)
        val weekEnd = weekStart.plusWeeks(1)
        val thisWeek = activities.filter { val d = dateOf(it); !d.isBefore(weekStart) && d.isBefore(weekEnd) }
        val elapsed = ChronoUnit.DAYS.between(weekStart, today) + 1

        // Reference weeks: the last four whole weeks, but never weeks before
        // the first loaded activity, so a new user is not compared with
        // weeks the app knows nothing about.
        val first = activities.minOfOrNull { dateOf(it) }
        val references = (1..REFERENCE_WEEKS).map { weekStart.minusWeeks(it.toLong()) }
            .filter { first != null && !it.isBefore(TrainingStats.weekStart(first)) }

        fun inRange(from: LocalDate, until: LocalDate) =
            activities.filter { val d = dateOf(it); !d.isBefore(from) && d.isBefore(until) }

        val refToDate = references.map { inRange(it, it.plusDays(elapsed)) }
        val refWhole = references.map { inRange(it, it.plusWeeks(1)) }

        fun volume(list: List<Activity>, sport: Sport): Double {
            val ofSport = list.filter { it.sport == sport }
            return if (SportStats.measuresVolumeByTime(sport)) {
                ofSport.sumOf { it.durationSec }.toDouble()
            } else {
                ofSport.sumOf { it.distanceMeters }
            }
        }

        fun meanOf(weeks: List<List<Activity>>, measure: (List<Activity>) -> Double): Double? =
            if (weeks.isEmpty()) null else weeks.sumOf(measure) / weeks.size

        // The three disciplines always have a row; the gym and the other
        // activities only when the athlete does them.
        val sports = TrainingStats.allSports.map { sport ->
            SportWeekGlance(
                sport = sport,
                sessions = thisWeek.count { it.sport == sport },
                done = volume(thisWeek, sport),
                usualToDate = meanOf(refToDate) { volume(it, sport) },
                usualWeek = meanOf(refWhole) { volume(it, sport) },
            )
        }.filter { it.sport in TrainingStats.triathlonSports || it.sessions > 0 || (it.usualWeek ?: 0.0) > 0.0 }

        val days = (0 until 7).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val ofDay = thisWeek.filter { dateOf(it) == date }
            DayTraining(date, ofDay.groupBy { it.sport }.mapValues { (_, list) -> list.sumOf { it.durationSec } })
        }

        return WeekGlance(
            weekStart = weekStart,
            today = today,
            days = days,
            sports = sports,
            sessions = thisWeek.size,
            durationSec = thisWeek.sumOf { it.durationSec },
            usualDurationToDateSec = meanOf(refToDate) { list -> list.sumOf { it.durationSec }.toDouble() }?.toLong(),
            usualDurationWeekSec = meanOf(refWhole) { list -> list.sumOf { it.durationSec }.toDouble() }?.toLong(),
            referenceWeeks = references.size,
        )
    }
}
