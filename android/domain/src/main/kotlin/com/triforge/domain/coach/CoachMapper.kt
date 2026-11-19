package com.triforge.domain.coach

import com.triforge.domain.Sport
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Reads and writes the coaches' plans as plain field maps. The same shape is
 * stored in Firestore and asked of the model as JSON, so one mapper checks
 * both: whatever the model returns is validated here before the app uses it.
 */
object CoachMapper {

    private const val MAX_SESSION_MIN = 600
    private const val MAX_TEXT = 1_200

    // ---- Week plan ---------------------------------------------------------

    fun weekPlanToFields(plan: WeekPlan): Map<String, Any?> = mapOf(
        "weekStart" to plan.weekStart.toString(),
        "phase" to plan.phase,
        "rationale" to plan.rationale,
        "noteForNutriAi" to plan.noteForNutriAi,
        "days" to plan.days.map { day ->
            mapOf(
                "date" to day.date.toString(),
                "sessions" to day.sessions.map { session ->
                    mapOf(
                        "sport" to sportKey(session.sport),
                        "title" to session.title,
                        "durationMin" to session.durationMin,
                        "intensity" to session.intensity.key,
                        "details" to session.details,
                    )
                },
            )
        },
    )

    /**
     * Reads a week plan. [weekStart] is the Monday the plan must cover: days
     * outside it are dropped and missing days become rest days, so the plan
     * always has seven days in order. Returns null when nothing usable is left.
     */
    fun weekPlanFromFields(fields: Map<String, Any?>, weekStart: LocalDate? = null): WeekPlan? {
        val start = weekStart ?: date(fields["weekStart"]) ?: return null
        val end = start.plusDays(7)
        val byDate = list(fields["days"]).mapNotNull { raw ->
            val day = raw as? Map<*, *> ?: return@mapNotNull null
            val date = date(day["date"]) ?: return@mapNotNull null
            if (date.isBefore(start) || !date.isBefore(end)) return@mapNotNull null
            date to list(day["sessions"]).mapNotNull { session(it as? Map<*, *>) }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, lists) -> lists.flatten() }
        if (byDate.values.all { it.isEmpty() }) return null
        val days = (0L until 7L).map { offset ->
            val date = start.plusDays(offset)
            PlannedDay(date, byDate[date].orEmpty())
        }
        return WeekPlan(
            weekStart = start,
            phase = text(fields["phase"], 80),
            days = days,
            rationale = text(fields["rationale"]),
            noteForNutriAi = text(fields["noteForNutriAi"]),
            createdAt = fields["createdAt"] as? Instant,
        )
    }

    private fun session(fields: Map<*, *>?): PlannedSession? {
        fields ?: return null
        val title = text(fields["title"], 80).ifBlank { return null }
        val minutes = number(fields["durationMin"])?.toInt()?.coerceIn(0, MAX_SESSION_MIN) ?: 0
        return PlannedSession(
            sport = sport(fields["sport"]),
            title = title,
            durationMin = minutes,
            intensity = Intensity.of(fields["intensity"] as? String) ?: Intensity.EASY,
            details = text(fields["details"], 400),
        )
    }

    // ---- Nutrition day -----------------------------------------------------

    fun nutritionDayToFields(day: NutritionDay): Map<String, Any?> = mapOf(
        "date" to day.date.toString(),
        "dayType" to day.dayType.key,
        "headline" to day.headline,
        "kcal" to day.kcal,
        "carbsG" to day.carbsG,
        "proteinG" to day.proteinG,
        "fatG" to day.fatG,
        "fueling" to day.fueling.map { mapOf("time" to it.time, "text" to it.text) },
        "meals" to day.meals.map { mapOf("name" to it.name, "description" to it.description, "kcal" to it.kcal) },
        "rationale" to day.rationale,
        "noteForCoach" to day.noteForCoach,
    )

    /**
     * Reads a nutrition day. Targets outside what any athlete could need are
     * treated as a broken answer and rejected rather than shown.
     */
    fun nutritionDayFromFields(fields: Map<String, Any?>, date: LocalDate? = null): NutritionDay? {
        val day = date ?: date(fields["date"]) ?: return null
        val kcal = number(fields["kcal"])?.toInt()?.takeIf { it in 800..8_000 } ?: return null
        val carbs = number(fields["carbsG"])?.toInt()?.takeIf { it in 0..1_500 } ?: return null
        val protein = number(fields["proteinG"])?.toInt()?.takeIf { it in 0..500 } ?: return null
        val fat = number(fields["fatG"])?.toInt()?.takeIf { it in 0..400 } ?: return null
        return NutritionDay(
            date = day,
            dayType = DayType.of(fields["dayType"] as? String) ?: DayType.MODERATE,
            headline = text(fields["headline"], 80),
            kcal = kcal,
            carbsG = carbs,
            proteinG = protein,
            fatG = fat,
            fueling = list(fields["fueling"]).mapNotNull { raw ->
                val step = raw as? Map<*, *> ?: return@mapNotNull null
                val stepText = text(step["text"], 300).ifBlank { return@mapNotNull null }
                FuelingStep(text(step["time"], 20), stepText)
            },
            meals = list(fields["meals"]).mapNotNull { raw ->
                val meal = raw as? Map<*, *> ?: return@mapNotNull null
                val name = text(meal["name"], 40).ifBlank { return@mapNotNull null }
                MealSuggestion(name, text(meal["description"], 200), number(meal["kcal"])?.toInt()?.takeIf { it in 0..3_000 })
            },
            rationale = text(fields["rationale"]),
            noteForCoach = text(fields["noteForCoach"]),
            createdAt = fields["createdAt"] as? Instant,
        )
    }

    // ---- Nutrition profile -------------------------------------------------

    fun nutritionProfileToFields(profile: NutritionProfile): Map<String, Any?> = mapOf(
        "diet" to profile.diet.key,
        "avoid" to profile.avoid,
        "mealsPerDay" to profile.mealsPerDay,
        "goal" to profile.goal.key,
        "notes" to profile.notes,
    )

    fun nutritionProfileFromFields(fields: Map<String, Any?>): NutritionProfile = NutritionProfile(
        diet = Diet.of(fields["diet"] as? String) ?: Diet.OMNIVORE,
        avoid = list(fields["avoid"]).mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) },
        mealsPerDay = number(fields["mealsPerDay"])?.toInt()
            ?.coerceIn(NutritionProfile.MIN_MEALS, NutritionProfile.MAX_MEALS) ?: 4,
        goal = BodyGoal.of(fields["goal"] as? String) ?: BodyGoal.MAINTAIN,
        notes = text(fields["notes"], 500),
    )

    // ---- Brief and chat ----------------------------------------------------

    fun briefToFields(brief: CoachBrief): Map<String, Any?> = mapOf(
        "summary" to brief.summary,
        "noteForPartner" to brief.noteForPartner,
    )

    fun briefFromFields(role: CoachRole, fields: Map<String, Any?>?): CoachBrief? {
        fields ?: return null
        val summary = text(fields["summary"], 2_000).ifBlank { return null }
        return CoachBrief(role, summary, text(fields["noteForPartner"]), fields["updatedAt"] as? Instant)
    }

    fun messageFromFields(fields: Map<String, Any?>): ChatMessage? {
        val body = (fields["text"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ChatMessage(fromAthlete = fields["from"] == "athlete", text = body, at = fields["at"] as? Instant)
    }

    // ---- Helpers -----------------------------------------------------------

    fun sportKey(sport: Sport?): String = when (sport) {
        Sport.SWIM -> "swim"
        Sport.BIKE -> "bike"
        Sport.RUN -> "run"
        Sport.OTHER -> "other"
        null -> "rest"
    }

    private fun sport(value: Any?): Sport? = when ((value as? String)?.trim()?.lowercase()) {
        "swim" -> Sport.SWIM
        "bike" -> Sport.BIKE
        "run" -> Sport.RUN
        "other", "strength", "mobility" -> Sport.OTHER
        else -> null
    }

    private fun date(value: Any?): LocalDate? = try {
        (value as? String)?.trim()?.take(10)?.let(LocalDate::parse)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }

    private fun list(value: Any?): List<Any?> = value as? List<*> ?: emptyList()

    private fun text(value: Any?, max: Int = MAX_TEXT): String = (value as? String)?.trim()?.take(max).orEmpty()
}
