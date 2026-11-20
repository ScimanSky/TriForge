package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.OtherKind
import com.triforge.domain.RaceGoal
import com.triforge.domain.Sport
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the athlete's body looks like to the energy estimate. */
data class BodyData(val weightKg: Double?, val heightCm: Int?, val age: Int?, val sex: Sex?)

/**
 * The day's numbers, computed by the app and never by the model: NutriAI
 * explains them and turns them into meals.
 */
data class DayTargets(
    val dayType: DayType,
    val kcal: Int,
    val carbsG: Int,
    val proteinG: Int,
    val fatG: Int,
    /** Basal metabolism and the training's energy, for the explanation. */
    val bmrKcal: Int,
    val trainingKcal: Int,
    /** Height or age were missing and a typical value stood in. */
    val estimated: Boolean,
    /** Why the carbohydrates are above the day's own level: the eve of a long day or of the race. Empty when they are not. */
    val carbLoad: String = "",
)

/** What surrounds the day in a triathlete's week: tomorrow's sessions and the race. */
data class DayContext(
    val tomorrow: List<PlannedSession> = emptyList(),
    /** Days to the goal race: 0 on race day, null without a race in sight. */
    val daysToRace: Int? = null,
    val race: RaceGoal? = null,
)

/**
 * The energy of a day, as SwimForge does it: basal metabolism by
 * Mifflin-St Jeor, × 1.4 for daily life, plus the planned sessions (MET ×
 * weight × hours), then the goal. Safety: never under the basal metabolism
 * nor under 1500 kcal (1200 for a woman), a loss of at most about 0.7% of
 * the weight a week and no deficit on hard or race days.
 */
object NutriEnergy {

    const val DAILY_LIFE_FACTOR = 1.4

    fun floorKcal(sex: Sex?): Int = if (sex == Sex.FEMALE) 1200 else 1500

    /** Mifflin-St Jeor; a missing height or age takes a typical value. */
    fun bmr(body: BodyData, weightKg: Double): Double {
        val height = body.heightCm?.toDouble() ?: typicalHeight(body.sex)
        val age = body.age?.toDouble() ?: TYPICAL_AGE
        val sexTerm = when (body.sex) {
            Sex.MALE -> 5.0
            Sex.FEMALE -> -161.0
            else -> -78.0
        }
        return 10 * weightKg + 6.25 * height - 5 * age + sexTerm
    }

    private fun typicalHeight(sex: Sex?): Double = when (sex) {
        Sex.FEMALE -> 164.0
        Sex.MALE -> 177.0
        else -> 170.0
    }

    /** Metabolic equivalent of a session (gross): sport and intensity. */
    fun met(sport: Sport?, intensity: Intensity): Double {
        if (sport == null || intensity == Intensity.REST) return 1.0
        val row = when (sport) {
            Sport.SWIM -> listOf(6.0, 7.0, 8.5, 9.5, 11.0, 10.0)
            Sport.BIKE -> listOf(5.5, 7.0, 8.5, 10.0, 11.5, 10.0)
            Sport.RUN -> listOf(8.0, 9.0, 10.5, 11.5, 12.5, 11.5)
            // Resistance training: 3.5 for light work, 6 for heavy lifting.
            Sport.STRENGTH -> listOf(3.5, 4.0, 5.0, 6.0, 6.0, 6.0)
            // Walks and yoga at the low end, hikes and cardio machines in the middle.
            Sport.OTHER -> listOf(3.0, 4.5, 5.5, 6.5, 7.5, 6.5)
        }
        return when (intensity) {
            Intensity.EASY -> row[0]
            Intensity.ENDURANCE -> row[1]
            Intensity.TEMPO -> row[2]
            Intensity.THRESHOLD -> row[3]
            Intensity.VO2MAX -> row[4]
            Intensity.RACE -> row[5]
            Intensity.REST -> 1.0
        }
    }

    /**
     * The energy the sessions add to daily life (the resting part is already
     * counted). A recorded activity counts for what the watch measured, so
     * the app has one number for it and not two; a planned session for its
     * sport, intensity and duration.
     */
    fun trainingKcal(sessions: List<PlannedSession>, weightKg: Double): Double =
        sessions.filter { it.sport != null && it.durationMin > 0 }.sumOf { session ->
            val resting = weightKg * session.durationMin / 60.0
            session.measuredKcal?.let { max(it - resting, 0.0) } ?: ((met(session.sport, session.intensity) - 1.0) * resting)
        }

    private fun isHard(session: PlannedSession) =
        session.intensity == Intensity.THRESHOLD || session.intensity == Intensity.VO2MAX || session.intensity == Intensity.RACE

    /**
     * The day's sessions for the energy count: the coaches' plan, plus what
     * the watch recorded today that the plan did not have (a walk, a hike, a
     * gym session on a day without one, a sport the plan left out). A sport
     * that was planned is counted as planned, so nothing is counted twice.
     */
    fun withDone(planned: List<PlannedSession>, done: List<Activity>): List<PlannedSession> {
        val plannedSports = planned.filter { it.sport != null && it.durationMin > 0 }.mapNotNull { it.sport }.toSet()
        val extra = done.filter { it.durationSec >= MIN_DONE_SEC && it.sport !in plannedSports }.sortedBy { it.startTime }.map { activity ->
            PlannedSession(
                sport = activity.sport,
                title = activity.name.ifBlank { AthleteContext.activityName(activity) }.take(80),
                durationMin = (activity.durationSec / 60).toInt(),
                intensity = doneIntensity(activity),
                focus = CoachTable.DONE,
                measuredKcal = activity.calories?.takeIf { it > 0 },
            )
        }
        return planned + extra
    }

    /** How hard a recorded session outside the plan counts for the energy. */
    fun doneIntensity(activity: Activity): Intensity = when (activity.sport) {
        Sport.STRENGTH -> Intensity.TEMPO
        Sport.OTHER -> when (activity.kind) {
            OtherKind.WALK, OtherKind.MOBILITY -> Intensity.EASY
            OtherKind.HIKE, OtherKind.CARDIO -> Intensity.TEMPO
            OtherKind.GENERIC -> Intensity.ENDURANCE
        }
        else -> Intensity.ENDURANCE
    }

    /** A walk or a yoga class costs energy but does not make the day a training day for the carbohydrates. */
    private fun fuelsTheDay(session: PlannedSession) =
        session.sport != null && session.durationMin > 0 && !(session.sport == Sport.OTHER && session.intensity == Intensity.EASY)

    /** The kind of day from its sessions. */
    fun dayType(sessions: List<PlannedSession>): DayType {
        val real = sessions.filter(::fuelsTheDay)
        if (real.isEmpty()) return DayType.REST
        if (real.any { it.intensity == Intensity.RACE }) return DayType.RACE
        val minutes = real.sumOf { it.durationMin }
        val hard = real.count(::isHard)
        // The energy of the sessions for each kilo: seventy minutes in the pool cost more than an hour
        // and a half in the gym, and a day is not «light» when it burns more than a «moderate» one.
        val energy = trainingKcal(real, 1.0)
        return when {
            minutes >= 150 || hard >= 2 || (hard >= 1 && minutes >= 90) -> DayType.HEAVY
            minutes >= 75 || hard >= 1 || energy >= MODERATE_KCAL_PER_KG -> DayType.MODERATE
            else -> DayType.LIGHT
        }
    }

    /** Carbohydrates in g/kg by the kind of day. */
    fun carbsPerKg(type: DayType): Double = when (type) {
        DayType.REST -> 3.0
        DayType.LIGHT -> 4.5
        DayType.MODERATE -> 6.0
        DayType.HEAVY -> 8.0
        DayType.RACE -> 9.0
    }

    fun proteinPerKg(goal: BodyGoal): Double = if (goal == BodyGoal.LOSE || goal == BodyGoal.GAIN) 2.0 else 1.8

    /** The day's targets; null without a weight, which NutriAI then asks for. */
    fun targets(profile: NutritionProfile, body: BodyData, sessions: List<PlannedSession>, context: DayContext = DayContext()): DayTargets? {
        val weight = body.weightKg?.takeIf { it in 30.0..250.0 } ?: return null
        val type = if (context.daysToRace == 0 && sessions.any { it.sport in TriRegia.SPORTS && it.durationMin > 0 }) DayType.RACE else dayType(sessions)
        val load = carbLoad(type, context)
        val bmr = bmr(body, weight)
        val training = trainingKcal(sessions, weight)
        val tdee = bmr * DAILY_LIFE_FACTOR + training
        var target = when (profile.goal) {
            BodyGoal.MAINTAIN, BodyGoal.PERFORMANCE -> tdee
            // No deficit when the day asks for fuel.
            BodyGoal.LOSE -> if (type == DayType.HEAVY || type == DayType.RACE || load != null) tdee else tdee - min(0.15 * tdee, 7.7 * weight)
            BodyGoal.GAIN -> tdee + min(0.10 * tdee, 350.0)
        }
        target = max(target, max(bmr, floorKcal(body.sex).toDouble()))

        var carbs = max(carbsPerKg(type), load?.first ?: 0.0) * weight
        val protein = proteinPerKg(profile.goal) * weight
        var fat = (target - 4 * carbs - 4 * protein) / 9
        val fatMin = 0.8 * weight
        if (fat < fatMin) {
            // Training comes first: the energy goes up rather than the fuel down.
            fat = fatMin
            target = 4 * carbs + 4 * protein + 9 * fat
        } else if (9 * fat > 0.35 * target) {
            val extra = 9 * fat - 0.35 * target
            fat -= extra / 9
            carbs += extra / 4
        }
        return DayTargets(
            dayType = type,
            kcal = ((4 * carbs + 4 * protein + 9 * fat) / 10).roundToInt() * 10,
            carbsG = carbs.roundToInt(),
            proteinG = protein.roundToInt(),
            fatG = fat.roundToInt(),
            bmrKcal = bmr.roundToInt(),
            trainingKcal = training.roundToInt(),
            estimated = body.heightCm == null || body.age == null,
            carbLoad = load?.second?.takeIf { load.first > carbsPerKg(type) }.orEmpty(),
        )
    }

    /**
     * Carbohydrates the day needs for what comes next, in g/kg, and why. The
     * two days before a middle or long distance race are loading days, the
     * day before an olympic one too; the eve of a heavy day never starts low.
     */
    fun carbLoad(type: DayType, context: DayContext): Pair<Double, String>? {
        val toRace = context.daysToRace
        val race = when (context.race) {
            RaceGoal.FULL -> if (toRace in 1..2) 10.0 else null
            RaceGoal.HALF -> if (toRace in 1..2) 8.0 else null
            RaceGoal.OLYMPIC -> if (toRace == 1) 7.0 else null
            else -> null
        }
        if (race != null) return race to "carico di carboidrati prima della gara"
        val next = dayType(context.tomorrow)
        if (toRace == 1 || next == DayType.RACE) return EVE_CARBS to "vigilia della gara"
        if (next == DayType.HEAVY && type != DayType.HEAVY && type != DayType.RACE) return EVE_CARBS to "vigilia di un giorno pesante"
        return null
    }

    /** [day] with the app's numbers in place of the model's. */
    fun apply(day: NutritionDay, targets: DayTargets?): NutritionDay = targets?.let {
        day.copy(dayType = it.dayType, kcal = it.kcal, carbsG = it.carbsG, proteinG = it.proteinG, fatG = it.fatG)
    } ?: day

    /** The lines of the request that give NutriAI the numbers to use. */
    fun describe(targets: DayTargets, goal: BodyGoal): String = buildString {
        appendLine("Tipo di giorno: ${targets.dayType.key}.")
        appendLine("Obiettivi calcolati dall'app, da usare esattamente: ${targets.kcal} kcal, carboidrati ${targets.carbsG} g, proteine ${targets.proteinG} g, grassi ${targets.fatG} g.")
        appendLine("Come sono calcolati: metabolismo basale ${targets.bmrKcal} kcal (Mifflin-St Jeor) × $DAILY_LIFE_FACTOR per la vita quotidiana, più ${targets.trainingKcal} kcal per le sedute del giorno; obiettivo «${goal.label}».")
        if (targets.carbLoad.isNotBlank()) appendLine("I carboidrati sono più alti del solito per questo tipo di giorno: ${targets.carbLoad}. Spiegalo all'atleta e mettili soprattutto nella cena.")
        if (targets.estimated) append("Altezza o età mancano: il calcolo usa valori tipici, dillo all'atleta.")
    }.trimEnd()

    private const val TYPICAL_AGE = 35.0

    /** Training energy, in kcal for each kilo of weight, from which a day is no more a light one: 65 minutes of steady swimming. */
    const val MODERATE_KCAL_PER_KG = 6.5

    /** A recorded activity shorter than this is not worth a line in the day. */
    private const val MIN_DONE_SEC = 10 * 60

    /** Carbohydrates the eve of a heavy day or of a race starts from, in g/kg. */
    const val EVE_CARBS = 6.0
}
