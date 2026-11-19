package com.triforge.domain

import java.time.LocalDate

/**
 * Where the gap between fitness and fatigue puts the athlete, read as a
 * share of fitness so it means the same at 30 and at 120 of fitness.
 * Thresholds follow the bands commonly used with the performance management
 * chart (form as a percentage of chronic load).
 */
enum class FormZone {
    /** Above +25%: so rested that fitness is starting to fade. */
    DETRAINING,

    /** +5% to +25%: fresh, the window for a race. */
    FRESH,

    /** −10% to +5%: neither loading nor recovering much. */
    NEUTRAL,

    /** −30% to −10%: the load that builds fitness. */
    PRODUCTIVE,

    /** Below −30%: more fatigue than the body can absorb for long. */
    OVERLOAD,
    ;

    companion object {
        fun of(formPct: Double): FormZone = when {
            formPct > 0.25 -> DETRAINING
            formPct > 0.05 -> FRESH
            formPct >= -0.10 -> NEUTRAL
            formPct >= -0.30 -> PRODUCTIVE
            else -> OVERLOAD
        }
    }
}

/**
 * One day of the fitness chart, all in TSS-equivalents per day.
 *
 * [formPct] is the form the athlete wakes up with: fitness minus fatigue as
 * of the evening before, as a share of fitness with a floor of 20 so a
 * beginner's small numbers do not swing it. Today's session does not make
 * today's form look worse. It is the same value the recovery balance uses.
 */
data class FitnessPoint(
    val date: LocalDate,
    val fitness: Double,
    val fatigue: Double,
    val dailyLoad: Double,
    val formPct: Double,
) {
    val zone: FormZone get() = FormZone.of(formPct)
}

/**
 * Fitness, fatigue and form on one scale for all three sports.
 *
 * Built from the same day-by-day load the recovery balance uses (each
 * session in TSS-equivalents, from power, pace, CSS or the watch load), so
 * the dashboard never shows two different "fitness" numbers.
 */
data class FitnessTrend(val points: List<FitnessPoint>) {
    val latest: FitnessPoint? get() = points.lastOrNull()

    /** Fitness change over the last [days] days, or null without enough points. */
    fun fitnessChange(days: Int): Double? {
        if (points.size <= days) return null
        return points.last().fitness - points[points.size - 1 - days].fitness
    }

    /** Fitness change per week over the last week, the "ramp". */
    val ramp: Double? get() = fitnessChange(7)

    /** Training load of the last [days] days, today included. */
    fun loadOfLast(days: Int): Double = points.takeLast(days).sumOf { it.dailyLoad }

    companion object {
        fun from(history: List<RecoveryDay>): FitnessTrend = FitnessTrend(
            history.mapNotNull { day ->
                day.load?.let { FitnessPoint(it.date, it.ctl, it.atl, it.dailyTss, it.formPct) }
            },
        )
    }
}
