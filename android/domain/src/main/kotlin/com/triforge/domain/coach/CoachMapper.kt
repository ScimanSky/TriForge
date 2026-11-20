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
    private const val MAX_INGREDIENTS = 12

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
                        "focus" to session.focus,
                        "why" to session.why,
                        "own" to session.own,
                        "steps" to session.steps.map(::stepToFields),
                    )
                },
            )
        },
        "table" to plan.table.map { mapOf("voice" to it.voice.key, "text" to it.text) },
        "rounds" to plan.rounds,
        "availableMin" to plan.availableMin,
        "maxHard" to plan.maxHard,
        "plannedFrom" to plan.plannedFrom?.toString(),
    )

    private fun stepToFields(step: WorkoutStep): Map<String, Any?> = mapOf(
        "block" to step.block.key,
        "reps" to step.reps,
        "distanceM" to step.distanceM,
        "durationSec" to step.durationSec,
        "zone" to step.zone,
        "recoverySec" to step.recoverySec,
        "text" to step.text,
        "target" to step.target,
        "note" to step.note,
    )

    private fun step(fields: Map<*, *>?): WorkoutStep? {
        fields ?: return null
        val metres = number(fields["distanceM"])?.toInt()?.takeIf { it in 1..50_000 }
        val seconds = number(fields["durationSec"])?.toInt()?.takeIf { it in 1..36_000 }
        if (metres == null && seconds == null) return null
        return WorkoutStep(
            block = WorkoutBlock.of(fields["block"]) ?: WorkoutBlock.MAIN,
            reps = number(fields["reps"])?.toInt()?.coerceIn(1, Workouts.MAX_REPS) ?: 1,
            // A step is by distance or by time; with both, the distance is what the athlete counts.
            distanceM = metres,
            durationSec = seconds.takeIf { metres == null },
            // A plan saved with the old swim codes (A1–C3) is read as zones.
            zone = Zones.current(text(fields["zone"], 20).uppercase()),
            recoverySec = number(fields["recoverySec"])?.toInt()?.coerceIn(0, 900) ?: 0,
            text = text(fields["text"], 120),
            target = text(fields["target"], 60),
            note = text(fields["note"], 200),
        )
    }

    /**
     * Reads what one sport coach proposes for the week: only sessions of
     * [sport] inside the week, with the notes for the other coaches.
     */
    fun sportProposalFromFields(fields: Map<String, Any?>, sport: Sport, weekStart: LocalDate): SportProposal? {
        val plan = weekPlanFromFields(fields, weekStart)
        val days = plan?.days.orEmpty().map { day ->
            // The coach is not asked for the sport: every session it writes is of its own.
            day.copy(sessions = day.sessions.filter { it.steps.isNotEmpty() || it.durationMin > 0 }.map { it.copy(sport = sport) })
        }
        return SportProposal(
            sport = sport,
            days = days.filter { it.sessions.isNotEmpty() },
            note = text(fields["noteForCoaches"], 400),
            noteForNutriAi = text(fields["noteForNutriAi"], 400),
            rationale = text(fields["rationale"], 600),
        )
    }

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
            table = list(fields["table"]).mapNotNull { raw ->
                val note = raw as? Map<*, *> ?: return@mapNotNull null
                val voice = TableVoice.of(note["voice"]) ?: return@mapNotNull null
                text(note["text"], 600).takeIf { it.isNotBlank() }?.let { TableNote(voice, it) }
            },
            rounds = number(fields["rounds"])?.toInt()?.coerceIn(0, 9) ?: 0,
            availableMin = number(fields["availableMin"])?.toInt(),
            maxHard = number(fields["maxHard"])?.toInt(),
            plannedFrom = date(fields["plannedFrom"])?.takeIf { !it.isBefore(start) && it.isBefore(end) },
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
            focus = text(fields["focus"], 40),
            steps = list(fields["steps"]).mapNotNull { step(it as? Map<*, *>) }.take(Workouts.MAX_STEPS),
            why = text(fields["why"], 500),
            own = fields["own"] as? Boolean ?: false,
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
        "meals" to day.meals.map { meal ->
            mapOf("name" to meal.name, "description" to meal.description, "kcal" to meal.kcal, "dishes" to meal.dishes.map(::dishToFields))
        },
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
                val dishes = list(meal["dishes"]).mapNotNull { dish(it as? Map<*, *>) }.take(NutriMeals.MAX_DISHES)
                val stored = MealSuggestion(name, text(meal["description"], 200), number(meal["kcal"])?.toInt()?.takeIf { it in 0..3_000 })
                // With dishes the meal is their sum; without (a day stored before dishes existed) it is as written.
                if (dishes.isEmpty()) stored else NutriMeals.rebuilt(stored, dishes)
            },
            rationale = text(fields["rationale"]),
            noteForCoach = text(fields["noteForCoach"]),
            createdAt = fields["createdAt"] as? Instant,
        )
    }

    // ---- Dishes ------------------------------------------------------------

    fun dishToFields(dish: Dish): Map<String, Any?> = buildMap {
        put("name", dish.name)
        put("quantity", dish.quantity)
        put("unit", dish.unit.key)
        put("kcal", dish.kcal)
        put("carbsG", dish.carbsG)
        put("proteinG", dish.proteinG)
        put("fatG", dish.fatG)
        if (dish.ingredients.isNotEmpty()) put("ingredients", dish.ingredients)
        if (dish.origin != DishOrigin.PLAN) put("origin", dish.origin.key)
        dish.replaced?.let { put("replaced", dishToFields(it.copy(replaced = null))) }
        dish.addedWith?.let { put("addedWith", it) }
        if (dish.confirmed) put("confirmed", true)
    }

    /**
     * Reads a dish, from the model or from the store. One without a name, a
     * quantity or believable numbers is dropped rather than shown.
     */
    fun dish(fields: Map<*, *>?, nested: Boolean = false): Dish? {
        fields ?: return null
        val name = text(fields["name"], 80).ifBlank { return null }
        val unit = DishUnit.of(fields["unit"] as? String) ?: DishUnit.GRAM
        val quantity = number(fields["quantity"])?.takeIf { it > 0 && it <= 3_000 } ?: return null
        val kcal = number(fields["kcal"])?.toInt()?.takeIf { it in 0..3_000 } ?: return null
        fun grams(key: String) = number(fields[key])?.takeIf { it in 0.0..600.0 } ?: 0.0
        return Dish(
            name = name,
            quantity = quantity,
            unit = unit,
            kcal = kcal,
            carbsG = grams("carbsG"),
            proteinG = grams("proteinG"),
            fatG = grams("fatG"),
            ingredients = list(fields["ingredients"]).mapNotNull { text(it, 40).takeIf(String::isNotBlank) }.distinct().take(MAX_INGREDIENTS),
            origin = DishOrigin.of(fields["origin"] as? String),
            replaced = if (nested) null else dish(fields["replaced"] as? Map<*, *>, nested = true),
            addedWith = text(fields["addedWith"], 80).takeIf(String::isNotBlank),
            confirmed = fields["confirmed"] == true,
        )
    }

    /** A dish the model proposes: never with the marks only the app sets. */
    private fun proposed(fields: Map<*, *>?): Dish? =
        dish(fields, nested = true)?.copy(origin = DishOrigin.PLAN, addedWith = null, confirmed = false)

    /** NutriAI's alternatives to a dish, as the model wrote them: the app filters and scales them. */
    fun dishAlternativesFromFields(fields: Map<String, Any?>): List<Dish> =
        list(fields["alternatives"]).mapNotNull { proposed(it as? Map<*, *>) }

    /** NutriAI's reading of a dish the athlete wrote. */
    fun dishEstimateFromFields(fields: Map<String, Any?>): DishEstimate {
        val dish = proposed(fields)
        return DishEstimate(
            understood = fields["understood"] != false && dish != null,
            dish = dish,
            complement = proposed(fields["complement"] as? Map<*, *>),
            complementWhy = text(fields["complementWhy"], 300),
        )
    }

    // ---- Nutrition profile -------------------------------------------------

    fun nutritionProfileToFields(profile: NutritionProfile): Map<String, Any?> = mapOf(
        "sex" to profile.sex?.key,
        "diet" to profile.diet.key,
        "goal" to profile.goal.key,
        "mealsPerDay" to profile.mealsPerDay,
        "allergies" to Allergen.entries.filter { it in profile.allergies }.map { it.key },
        "otherAllergies" to profile.otherAllergies,
        "intolerances" to Intolerance.entries.filter { it in profile.intolerances }.map { it.key },
        "otherIntolerances" to profile.otherIntolerances,
        "likes" to profile.likes,
        "dislikes" to profile.dislikes.map { mapOf("food" to it.food, "exceptions" to it.exceptions) },
        "redMeatPerWeek" to profile.redMeatPerWeek,
        "supplements" to profile.supplements.map { mapOf("name" to it.name, "dose" to it.dose, "timing" to it.timing.key) },
        "drinks" to profile.drinks.map { mapOf("name" to it.name, "perDay" to it.perDay) },
        "noCaffeine" to profile.noCaffeine,
        "notes" to profile.notes,
    )

    fun nutritionProfileFromFields(fields: Map<String, Any?>): NutritionProfile {
        val name = { value: Any? -> text(value, NutritionProfile.NAME_MAX) }
        val dislikes = list(fields["dislikes"]).mapNotNull { item ->
            val entry = item as? Map<*, *> ?: return@mapNotNull null
            NutritionProfile.dislike(name(entry["food"]), list(entry["exceptions"]).joinToString(", ") { name(it) })
        }
        // Profiles saved before 19 November 2026 kept one list of foods to avoid.
        val legacy = list(fields["avoid"]).mapNotNull { NutritionProfile.dislike(name(it), "") }
        return NutritionProfile(
            sex = Sex.of(fields["sex"] as? String),
            diet = Diet.of(fields["diet"] as? String) ?: Diet.OMNIVORE,
            goal = BodyGoal.of(fields["goal"] as? String) ?: BodyGoal.MAINTAIN,
            mealsPerDay = number(fields["mealsPerDay"])?.toInt()
                ?.coerceIn(NutritionProfile.MIN_MEALS, NutritionProfile.MAX_MEALS) ?: 4,
            allergies = list(fields["allergies"]).mapNotNull { Allergen.of(it as? String) }.toSet(),
            otherAllergies = text(fields["otherAllergies"], NutritionProfile.TEXT_MAX),
            intolerances = list(fields["intolerances"]).mapNotNull { Intolerance.of(it as? String) }.toSet(),
            otherIntolerances = text(fields["otherIntolerances"], NutritionProfile.TEXT_MAX),
            likes = list(fields["likes"]).map(name).filter(String::isNotEmpty).take(NutritionProfile.MAX_ITEMS),
            dislikes = (dislikes + legacy).distinctBy { it.food.lowercase() }.take(NutritionProfile.MAX_ITEMS),
            redMeatPerWeek = number(fields["redMeatPerWeek"])?.toInt()?.takeIf { it in 0..NutritionProfile.MAX_RED_MEAT },
            supplements = list(fields["supplements"]).mapNotNull { item ->
                val s = item as? Map<*, *> ?: return@mapNotNull null
                val n = name(s["name"]).ifEmpty { return@mapNotNull null }
                Supplement(n, name(s["dose"]), SupplementTiming.of(s["timing"] as? String) ?: SupplementTiming.DAILY)
            }.take(NutritionProfile.MAX_SUPPLEMENTS),
            drinks = list(fields["drinks"]).mapNotNull { item ->
                val d = item as? Map<*, *> ?: return@mapNotNull null
                Drink(name(d["name"]).ifEmpty { return@mapNotNull null }, name(d["perDay"]))
            }.take(NutritionProfile.MAX_DRINKS),
            noCaffeine = fields["noCaffeine"] == true,
            notes = text(fields["notes"], 500),
        )
    }

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
        Sport.STRENGTH -> "strength"
        Sport.OTHER -> "other"
        null -> "rest"
    }

    private fun sport(value: Any?): Sport? = when ((value as? String)?.trim()?.lowercase()) {
        "swim" -> Sport.SWIM
        "bike" -> Sport.BIKE
        "run" -> Sport.RUN
        "strength" -> Sport.STRENGTH
        "other", "mobility" -> Sport.OTHER
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
