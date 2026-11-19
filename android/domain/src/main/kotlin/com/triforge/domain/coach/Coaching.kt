package com.triforge.domain.coach

import com.triforge.domain.Sport
import java.time.Instant
import java.time.LocalDate

/**
 * The two AI coaches. They are separate agents: each has its own
 * instructions, its own state and its own conversation with the athlete.
 * What one knows of the other is only what the other published on the shared
 * board ([CoachBrief]).
 */
enum class CoachRole(val key: String) {
    /** The training coach: plans the week, adapts it to load and recovery. */
    COACH("coach"),

    /** NutriAI, the nutritionist: fuels the plan the coach wrote. */
    NUTRI_AI("nutriai"),
    ;

    val partner: CoachRole get() = if (this == COACH) NUTRI_AI else COACH

    companion object {
        fun of(key: String?): CoachRole? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What a coach publishes for the other one: a short summary of what it
 * decided, and a note written for its colleague. This is the only channel
 * between them, so each keeps reasoning on its own.
 */
data class CoachBrief(
    val from: CoachRole,
    val summary: String,
    val noteForPartner: String = "",
    val updatedAt: Instant? = null,
)

/** How hard a planned session is meant to be. */
enum class Intensity(val key: String) {
    REST("rest"),
    EASY("easy"),
    ENDURANCE("endurance"),
    TEMPO("tempo"),
    THRESHOLD("threshold"),
    VO2MAX("vo2max"),
    RACE("race"),
    ;

    companion object {
        fun of(key: String?): Intensity? = entries.firstOrNull { it.key == key?.trim()?.lowercase() }
    }
}

/**
 * One planned session. [sport] is null for rest, mobility or strength work,
 * which the plan still lists so the athlete sees the whole day.
 */
data class PlannedSession(
    val sport: Sport?,
    val title: String,
    val durationMin: Int,
    val intensity: Intensity = Intensity.EASY,
    /** How to do it: sets, targets in watts, pace or zones. */
    val details: String = "",
    /** What the session is for, in the sport coach's words: "Soglia", "Tecnica", "Lungo". */
    val focus: String = "",
    /** The structured workout; empty for rest and for sessions written as text only. */
    val steps: List<WorkoutStep> = emptyList(),
    /** Why this session is here, shown under the workout. */
    val why: String = "",
)

/** Who spoke at the coaches' table: a sport coach, NutriAI or the app's regia. */
enum class TableVoice(val key: String) {
    SWIM("swim"), BIKE("bike"), RUN("run"), NUTRI("nutri"), REGIA("regia");

    companion object {
        fun of(key: Any?): TableVoice? = entries.firstOrNull { it.key == key }
        fun of(sport: Sport): TableVoice? = when (sport) {
            Sport.SWIM -> SWIM
            Sport.BIKE -> BIKE
            Sport.RUN -> RUN
            Sport.OTHER -> null
        }
    }
}

/** One line of the minutes of how the coaches agreed on the week. */
data class TableNote(val voice: TableVoice, val text: String)

data class PlannedDay(val date: LocalDate, val sessions: List<PlannedSession>) {
    val isRest: Boolean get() = sessions.all { it.sport == null }
}

/** The training coach's week, Monday to Sunday. */
data class WeekPlan(
    val weekStart: LocalDate,
    /** Training phase in the athlete's words, e.g. "Costruzione · sett. 2 di 3". */
    val phase: String,
    val days: List<PlannedDay>,
    /** Why this week looks the way it does, shown under the plan. */
    val rationale: String,
    /** What NutriAI should know about this week. */
    val noteForNutriAi: String = "",
    val createdAt: Instant? = null,
    /** How the sport coaches agreed, in order; empty for a plan one coach wrote alone. */
    val table: List<TableNote> = emptyList(),
    /** Rounds the coaches needed, 0 when there was no table. */
    val rounds: Int = 0,
    /** The regia's numbers the week was built on, for the summary. */
    val availableMin: Int? = null,
    val maxHard: Int? = null,
) {
    /** Whether the three sport coaches wrote this plan. */
    val fromTable: Boolean get() = rounds > 0

    fun day(date: LocalDate): PlannedDay? = days.firstOrNull { it.date == date }

    /** Sessions and minutes per sport, for the summary card. */
    fun totals(): Map<Sport, Pair<Int, Int>> = days
        .flatMap { it.sessions }
        .filter { it.sport != null }
        .groupBy { it.sport!! }
        .mapValues { (_, sessions) -> sessions.size to sessions.sumOf { it.durationMin } }
}

/** One fuelling moment around a session: before, during, after. */
data class FuelingStep(val time: String, val text: String)

/** A meal NutriAI suggests for the day. */
data class MealSuggestion(val name: String, val description: String, val kcal: Int?)

/** What kind of day it is for nutrition, following the training plan. */
enum class DayType(val key: String) {
    REST("rest"),
    LIGHT("light"),
    MODERATE("moderate"),
    HEAVY("heavy"),
    RACE("race"),
    ;

    companion object {
        fun of(key: String?): DayType? = entries.firstOrNull { it.key == key?.trim()?.lowercase() }
    }
}

/** NutriAI's plan for one day. */
data class NutritionDay(
    val date: LocalDate,
    val dayType: DayType,
    /** A few words on the day, e.g. "Giorno di carico · bici soglia". */
    val headline: String,
    val kcal: Int,
    val carbsG: Int,
    val proteinG: Int,
    val fatG: Int,
    val fueling: List<FuelingStep>,
    val meals: List<MealSuggestion>,
    val rationale: String,
    /** What the training coach should know, e.g. a risk of low energy availability. */
    val noteForCoach: String = "",
    val createdAt: Instant? = null,
)

/** One line of a conversation with a coach. */
data class ChatMessage(
    val fromAthlete: Boolean,
    val text: String,
    val at: Instant? = null,
)

/**
 * A coach's answer in the chat. When the athlete asked for a change, the
 * coach also proposes the updated plan, which the athlete applies or not.
 */
data class CoachReply<P>(
    val text: String,
    val proposal: P? = null,
    /** A note for the other coach, when the change concerns them too. */
    val noteForPartner: String = "",
    /** Who should take the question instead, when it is not this coach's: a sport key or "nutriai". */
    val handoff: String = "",
)
