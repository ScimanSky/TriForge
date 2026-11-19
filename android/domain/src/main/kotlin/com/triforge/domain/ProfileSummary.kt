package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The headline numbers of the profile page, over the activities the app has loaded. */
data class ProfileSummary(
    val sessions: Int,
    val durationSec: Long,
    val distanceMeters: Double,
    /** Swim, bike and run, in race order, even when one of them is zero. */
    val bySport: List<SportTotals>,
    /** Weeks with at least one activity, out of [weeks]. */
    val activeWeeks: Int,
    val weeks: Int,
) {
    /** Share of the training time of each sport, zero when there is no time at all. */
    fun timeShare(sport: Sport): Float {
        if (durationSec <= 0) return 0f
        val time = bySport.firstOrNull { it.sport == sport }?.durationSec ?: 0
        return time.toFloat() / durationSec
    }
}

object ProfileStats {

    fun summary(activities: List<Activity>, weeks: Int, today: LocalDate, zone: ZoneId): ProfileSummary {
        val firstWeek = TrainingStats.weekStart(today).minusWeeks(weeks - 1L)
        val inWindow = activities.filter {
            !it.startTime.atZone(zone).toLocalDate().isBefore(firstWeek)
        }
        val bySport = TrainingStats.triathlonSports.map { sport ->
            val ofSport = inWindow.filter { it.sport == sport }
            SportTotals(
                sport = sport,
                sessions = ofSport.size,
                distanceMeters = ofSport.sumOf { it.distanceMeters },
                durationSec = ofSport.sumOf { it.durationSec },
                elevationGainMeters = ofSport.sumOf { it.elevationGainMeters ?: 0.0 },
            )
        }
        val activeWeeks = inWindow
            .map { TrainingStats.weekStart(it.startTime.atZone(zone).toLocalDate()) }
            .distinct()
            .size
        return ProfileSummary(
            sessions = inWindow.size,
            durationSec = inWindow.sumOf { it.durationSec },
            distanceMeters = inWindow.sumOf { it.distanceMeters },
            bySport = bySport,
            activeWeeks = activeWeeks,
            weeks = weeks,
        )
    }

    /** Days left to the goal race; null when there is none or it is past. */
    fun daysToRace(raceDate: LocalDate?, today: LocalDate): Long? {
        if (raceDate == null || raceDate.isBefore(today)) return null
        return ChronoUnit.DAYS.between(today, raceDate)
    }
}
