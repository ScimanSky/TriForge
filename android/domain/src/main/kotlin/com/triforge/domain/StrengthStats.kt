package com.triforge.domain

import java.time.Instant
import java.util.Locale

/** One exercise over a period: in how many sessions it was done and how much of it. */
data class ExerciseTotal(
    val category: String,
    val sessions: Int,
    val sets: Int,
    val reps: Int,
    /** Weight moved in all its sets, in kilograms; null when it was always bodyweight or not recorded. */
    val volumeKg: Double?,
)

/** What the gym sessions say when the watch counted sets, repetitions and weights. */
object StrengthStats {

    /** The exercises of the gym sessions from [since] on, the most frequent first. */
    fun frequentExercises(activities: List<Activity>, since: Instant): List<ExerciseTotal> =
        activities
            .filter { it.sport == Sport.STRENGTH && !it.startTime.isBefore(since) }
            .flatMap { session -> session.exercises.groupBy { it.category }.map { (category, sets) -> category to sets } }
            .groupBy({ it.first }, { it.second })
            .map { (category, sessions) ->
                val all = sessions.flatten()
                ExerciseTotal(
                    category = category,
                    sessions = sessions.size,
                    sets = all.sumOf { it.sets },
                    reps = all.sumOf { it.reps ?: 0 },
                    volumeKg = all.mapNotNull { it.volumeKg }.takeIf { it.isNotEmpty() }?.sum(),
                )
            }
            .sortedWith(compareBy({ -it.sessions }, { -it.sets }, { it.category }))

    private val NAMES = mapOf(
        "bench_press" to "Panca",
        "calf_raise" to "Polpacci",
        "cardio" to "Cardio",
        "carry" to "Trasporti",
        "chop" to "Chop",
        "core" to "Core",
        "crunch" to "Crunch",
        "curl" to "Curl",
        "deadlift" to "Stacco",
        "flye" to "Croci",
        "hip_raise" to "Ponte glutei",
        "hip_stability" to "Stabilità delle anche",
        "hip_swing" to "Swing",
        "hyperextension" to "Iperestensioni",
        "lateral_raise" to "Alzate laterali",
        "leg_curl" to "Leg curl",
        "leg_raise" to "Alzate delle gambe",
        "lunge" to "Affondi",
        "olympic_lift" to "Sollevamento olimpico",
        "plank" to "Plank",
        "plyo" to "Pliometria",
        "pull_up" to "Trazioni",
        "push_up" to "Piegamenti",
        "row" to "Rematore",
        "shoulder_press" to "Spinte per le spalle",
        "shoulder_stability" to "Stabilità delle spalle",
        "shrug" to "Scrollate",
        "sit_up" to "Sit-up",
        "squat" to "Squat",
        "total_body" to "Corpo intero",
        "triceps_extension" to "Estensioni dei tricipiti",
        "warm_up" to "Riscaldamento",
        "unknown" to "Altro esercizio",
    )

    /** The exercise in Italian; a category the app does not know keeps the watch's name, tidied. */
    fun exerciseName(category: String): String {
        val key = category.trim().lowercase()
        return NAMES[key] ?: key.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ITALY) }.ifBlank { NAMES.getValue("unknown") }
    }
}
