package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.AthleteProfile
import com.triforge.domain.FormZone
import com.triforge.domain.Formatters
import com.triforge.domain.LoadResponsePattern
import com.triforge.domain.RecoveryDay
import com.triforge.domain.RecoveryLight
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The meal Oggi points at: the one that matters most around the day's training. */
data class KeyMeal(val title: String, val text: String)

/**
 * What Oggi tells the athlete to do: the sessions still to do today, or
 * tomorrow's once today is done, free or over, with one sentence that puts
 * recovery and form together and the meal that counts.
 */
data class TodayCard(
    val date: LocalDate,
    /** The card is about tomorrow: today is done, free or over. */
    val tomorrow: Boolean,
    /** Recovery and form in one sentence; blank when the app knows neither. */
    val sentence: String,
    /** The sessions of [date] still to do, the coaches' and the athlete's own; empty on a free day. */
    val sessions: List<PlannedSession>,
    /** The usual time of the first session, when the profile gives one. */
    val time: LocalTime?,
    val meal: KeyMeal?,
    /** Tomorrow in a line, when the card is about today and the week reaches tomorrow. */
    val next: String?,
)

/**
 * The card on top of Oggi. Recovery and form are two cards a screen apart
 * and can seem to say opposite things («recuperato» and «Sovraccarico»):
 * here they are read together, once, with what follows for the day.
 */
object TodayAdvice {

    fun card(
        plan: WeekPlan?,
        activities: List<Activity>,
        today: LocalDate,
        zone: ZoneId,
        now: LocalTime,
        recovery: RecoveryDay?,
        form: FormZone?,
        availability: TrainingAvailability,
        nutrition: Map<LocalDate, NutritionDay> = emptyMap(),
        /** The swim threshold is in doubt: a high load may be higher than the truth. */
        loadInDoubt: Boolean = false,
    ): TodayCard? {
        if (plan == null) return null
        val review = WeekReview.of(plan, activities, today, zone)
        fun open(date: LocalDate): List<PlannedSession> = review.day(date)?.items.orEmpty()
            .filter { it.state == ReviewState.TODO }
            .mapNotNull { it.session }
            .filter { it.sport != null && it.durationMin > 0 }

        val over = CoachTable.planningDay(today, now) != today
        val todays = if (over) emptyList() else open(today).filter { stillAhead(it, availability, now) }
        val nextDay = today.plusDays(1)
        val tomorrows = open(nextDay)
        val aboutTomorrow = todays.isEmpty()
        // Sunday is over and next week is not written yet: nothing to say of tomorrow.
        if (aboutTomorrow && plan.day(nextDay) == null) return null
        val date = if (aboutTomorrow) nextDay else today
        val sessions = if (aboutTomorrow) tomorrows else todays
        return TodayCard(
            date = date,
            tomorrow = aboutTomorrow,
            sentence = sentence(recovery, form, hasSession = todays.isNotEmpty(), loadInDoubt = loadInDoubt),
            sessions = sessions,
            time = sessions.firstOrNull()?.let { time(it, availability) },
            meal = nutrition[date]?.let(::keyMeal),
            next = if (aboutTomorrow || plan.day(nextDay) == null) null else line(tomorrows, availability),
        )
    }

    private fun time(session: PlannedSession, availability: TrainingAvailability): LocalTime? =
        session.sport?.let { availability.slot(it)?.time }

    /**
     * Whether a session of today not recorded yet can still be meant for
     * today: until an hour after it would have ended at its usual time. The
     * watch is read in the morning, so a session done at lunch is not known
     * as done until the next day; without a usual time it stays until the evening.
     */
    private fun stillAhead(session: PlannedSession, availability: TrainingAvailability, now: LocalTime): Boolean {
        val start = time(session, availability) ?: return true
        return now.toSecondOfDay() < start.toSecondOfDay() + (session.durationMin + GRACE_MIN) * 60
    }

    private const val GRACE_MIN = 60

    /** «Domani: Palestra, 1 h 30′ alle 19:00 (seduta tua).» */
    private fun line(sessions: List<PlannedSession>, availability: TrainingAvailability): String =
        if (sessions.isEmpty()) "Domani: riposo."
        else "Domani: " + sessions.joinToString("; ") { session ->
            val at = time(session, availability)?.let { " alle %d:%02d".format(it.hour, it.minute) }.orEmpty()
            val own = if (session.own) " (seduta tua)" else ""
            "${session.title}, ${Formatters.duration(session.durationMin * 60L)}$at$own"
        } + "."

    /** «1 h 00′ · 2.400 m · quasi tutta in Z2»: a session in a line. */
    fun summary(session: PlannedSession, profile: AthleteProfile): String {
        val sport = session.sport
        val distance = Workouts.distanceM(session, profile)?.takeIf { it > 0 && sport != null }?.let { Formatters.distance(sport!!, it.toDouble()) }
        val zones = Workouts.zoneMinutes(session, profile)
        val total = zones.values.sum()
        val main = zones.maxByOrNull { it.value }?.takeIf { total > 0 && it.value >= total * MOSTLY }?.let { "quasi tutta in ${it.key}" }
        return listOfNotNull(Formatters.duration(session.durationMin * 60L), distance, main).joinToString(" · ")
    }

    /** The longest step of the main block, in words: «10 × 100 m Z2, rec. 20″». Null for a session without steps. */
    fun mainSet(session: PlannedSession, profile: AthleteProfile): String? {
        val sport = session.sport ?: return null
        val main = session.steps.filter { it.block == WorkoutBlock.MAIN }.ifEmpty { session.steps }
        return main.maxByOrNull { Workouts.stepSec(sport, it, profile) }?.let(Workouts::describe)
    }

    /** The share of the work from which a session is called «quasi tutta» in one zone. */
    private const val MOSTLY = 0.7

    /**
     * The meal that counts on [day]: what comes after the training when
     * NutriAI wrote it, otherwise the last step around the session, otherwise
     * the biggest meal of the day.
     */
    fun keyMeal(day: NutritionDay): KeyMeal? {
        val step = day.fueling.firstOrNull { it.time.contains("dopo", ignoreCase = true) } ?: day.fueling.lastOrNull()
        if (step != null && step.text.isNotBlank()) return KeyMeal(step.time.trim().replaceFirstChar { it.uppercase() }, step.text.trim())
        val meal = day.meals.maxByOrNull { it.kcal ?: 0 } ?: return null
        val text = if (meal.dishes.isNotEmpty()) meal.dishes.joinToString(", ") { it.name } else meal.description
        return KeyMeal(meal.name, text).takeIf { text.isNotBlank() }
    }

    /**
     * Recovery and form in one sentence, with what follows for today. The
     * body's response comes first: when it shows fatigue, the load only
     * explains it. When the body is fine, a high load still says not to add.
     */
    fun sentence(recovery: RecoveryDay?, form: FormZone?, hasSession: Boolean, loadInDoubt: Boolean = false): String {
        if (recovery?.pattern == LoadResponsePattern.ILLNESS) {
            return "Hai segnato che non stai bene: oggi riposo, l'allenamento può aspettare."
        }
        val light = recovery?.light
        val high = if (loadInDoubt) "risulta alto" else "è alto"
        fun say(state: String, withSession: String, without: String) = "$state: ${if (hasSession) withSession else without}."
        return when {
            light == RecoveryLight.RED || (recovery?.level ?: 0) >= 3 ->
                "I segnali di fatica durano da giorni: oggi riposo o solo movimento leggero."
            light == RecoveryLight.ORANGE -> say(
                "Il corpo mostra fatica accumulata",
                "fai la seduta più facile del previsto, oppure spostala",
                "tieni il giorno libero per recuperare",
            )
            light == RecoveryLight.YELLOW -> when (form) {
                FormZone.OVERLOAD -> say(
                    "Qualche segnale di fatica, e il carico delle ultime settimane $high",
                    "tieni la seduta facile e non aggiungere altro",
                    "oggi non aggiungere allenamenti",
                )
                else -> say(
                    "Qualche segnale di fatica, niente di più",
                    "fai la seduta prevista e ascolta le sensazioni",
                    "oggi non serve aggiungere altro",
                )
            }
            light == RecoveryLight.GREEN -> when (form) {
                FormZone.OVERLOAD -> say(
                    "Sei recuperato, ma il carico delle ultime settimane $high",
                    "fai la seduta prevista e non aggiungere altro",
                    "oggi non aggiungere allenamenti",
                )
                FormZone.PRODUCTIVE -> say(
                    "Sei recuperato e il carico è quello che costruisce",
                    "fai la seduta come è scritta",
                    "il giorno libero ci sta",
                )
                FormZone.NEUTRAL -> say(
                    "Sei recuperato e il carico è leggero",
                    "la seduta di oggi puoi farla con convinzione",
                    "c'è spazio per allenarti, se ne hai voglia",
                )
                FormZone.FRESH -> say(
                    "Sei fresco e riposato",
                    "è il momento giusto per una seduta di qualità",
                    "c'è spazio per allenarti, se ne hai voglia",
                )
                FormZone.DETRAINING -> say(
                    "Sei molto riposato e la forma comincia a scendere",
                    "fai la seduta prevista e riprendi con regolarità",
                    "riprendi ad allenarti con regolarità",
                )
                null -> say("Sei recuperato", "fai la seduta prevista", "il giorno libero ci sta")
            }
            // No recovery reading yet: only the load can speak.
            form == FormZone.OVERLOAD -> say(
                "Il carico delle ultime settimane $high",
                "fai la seduta prevista e non aggiungere altro",
                "oggi non aggiungere allenamenti",
            )
            else -> ""
        }
    }
}
