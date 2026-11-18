package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId

/** Fitness and fatigue on one day. [form] is the gap between them. */
data class FormPoint(val date: LocalDate, val fitness: Double, val fatigue: Double) {
    /** Positive when fresh, negative when carrying fatigue. */
    val form: Double get() = fitness - fatigue
}

/**
 * The classic fitness and fatigue model: two running averages of daily
 * training load, a slow one (fitness) and a fast one (fatigue).
 *
 * The load comes from the watch ([Activity.trainingLoad]). Sessions without
 * one count as zero, so the curves are only as complete as the source.
 */
object FitnessModel {

    const val FITNESS_DAYS = 42
    const val FATIGUE_DAYS = 7

    /**
     * One point per day for the last [days] days, ending on [today]. Empty
     * when no activity carries a training load, because there is nothing to
     * draw. The averages start from zero at the first loaded activity.
     */
    fun series(activities: List<Activity>, today: LocalDate, zone: ZoneId, days: Int = FITNESS_DAYS): List<FormPoint> {
        require(days > 0) { "days must be positive" }
        val loadByDay = activities
            .filter { (it.trainingLoad ?: 0.0) > 0.0 }
            .groupBy { it.startTime.atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.trainingLoad ?: 0.0 } }
        if (loadByDay.isEmpty()) return emptyList()

        val firstShown = today.minusDays(days - 1L)
        var day = minOf(loadByDay.keys.min(), firstShown)
        var fitness = 0.0
        var fatigue = 0.0
        val points = mutableListOf<FormPoint>()
        while (!day.isAfter(today)) {
            val load = loadByDay[day] ?: 0.0
            fitness += (load - fitness) / FITNESS_DAYS
            fatigue += (load - fatigue) / FATIGUE_DAYS
            if (!day.isBefore(firstShown)) points += FormPoint(day, fitness, fatigue)
            day = day.plusDays(1)
        }
        return points
    }
}
