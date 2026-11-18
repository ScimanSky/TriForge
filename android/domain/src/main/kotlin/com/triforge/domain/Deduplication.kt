package com.triforge.domain

import java.time.Duration

/**
 * Drops Health Connect sessions that are the same workout as a Garmin activity.
 *
 * Garmin Connect can write its workouts into Health Connect, so an athlete who
 * uses both sources would otherwise see every session twice. The Garmin copy
 * wins because it carries more detail.
 */
fun List<Activity>.withoutDuplicates(tolerance: Duration = Duration.ofMinutes(5)): List<Activity> {
    val garmin = filter { it.source == ActivitySource.GARMIN }
    if (garmin.isEmpty()) return this
    return filter { candidate ->
        candidate.source != ActivitySource.HEALTH_CONNECT || garmin.none { it.isSameWorkoutAs(candidate, tolerance) }
    }
}

private fun Activity.isSameWorkoutAs(other: Activity, tolerance: Duration): Boolean =
    sport == other.sport &&
        Duration.between(startTime, other.startTime).abs() <= tolerance
