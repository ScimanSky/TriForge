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

/** What is left of today when the athlete reads Oggi. */
enum class DayLeft {
    /** A session still to do. */
    SESSION,

    /** No session in the programme, and no training recorded. */
    FREE,

    /** Today's training is recorded: the day is done. */
    DONE,

    /** A session whose time is gone and that is not known as done: skipped, or not synced yet. */
    OPEN,
}

/**
 * What Oggi tells the athlete to do. It is always about today first: the
 * sessions still to do, or in a word how the day stands («Giorno libero»,
 * «Allenamento fatto»), with one sentence that puts recovery and form
 * together. Tomorrow comes after, and is never mixed with today: in a line,
 * or in full once today has nothing left to do.
 */
data class TodayCard(
    val left: DayLeft,
    /** Recovery and form in one sentence, with what follows for today; blank when the app knows neither. */
    val sentence: String,
    /** Today's sessions still to do, the coaches' and the athlete's own. */
    val sessions: List<PlannedSession>,
    /** The usual time of the first of [sessions], when the profile gives one. */
    val time: LocalTime?,
    /** The meal that counts around the sessions shown: today's, or tomorrow's when [tomorrow] is. */
    val meal: KeyMeal?,
    /** Tomorrow in a line: beside today's sessions, or when tomorrow is free. Null when [tomorrow] says it, or the week ends today. */
    val next: String?,
    /**
     * Today's sessions whose time is gone and that are not known as done,
     * in a line: the card does not pretend they were not there.
     */
    val earlier: String? = null,
    /** Tomorrow's sessions in full, once today has none left to do. */
    val tomorrow: List<PlannedSession> = emptyList(),
    /** The usual time of the first of [tomorrow]. */
    val tomorrowTime: LocalTime? = null,
)

/**
 * The card on top of Oggi. Recovery and form are two cards a screen apart
 * and can seem to say opposite things («Nella norma» and «Sovraccarico»):
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
        val gone = open(today).filter { it !in todays }
        // A planned session the watch recorded, or a training outside the programme: either way the day is done.
        val trained = review.day(today)?.items.orEmpty().any { item ->
            item.activity != null && (item.state == ReviewState.DONE || WeekReview.isTraining(item.activity))
        }
        val left = when {
            todays.isNotEmpty() -> DayLeft.SESSION
            gone.isNotEmpty() -> DayLeft.OPEN
            trained -> DayLeft.DONE
            else -> DayLeft.FREE
        }
        val nextDay = today.plusDays(1)
        // Sunday: next week is not written yet, there is nothing to say of tomorrow.
        val reachesTomorrow = plan.day(nextDay) != null
        val tomorrows = if (reachesTomorrow) open(nextDay) else emptyList()
        val inFull = todays.isEmpty() && tomorrows.isNotEmpty()
        return TodayCard(
            left = left,
            // In the evening of a free day there is nothing left to advise: only how the athlete is.
            sentence = if (over && left == DayLeft.FREE) stateOnly(recovery, form, loadInDoubt) else sentence(recovery, form, left, loadInDoubt),
            sessions = todays,
            time = todays.firstOrNull()?.let { time(it, availability) },
            meal = when {
                todays.isNotEmpty() -> nutrition[today]
                inFull -> nutrition[nextDay]
                else -> null
            }?.let(::keyMeal),
            next = if (inFull || !reachesTomorrow) null else line(tomorrows, availability),
            earlier = earlier(gone, availability),
            tomorrow = if (inFull) tomorrows else emptyList(),
            tomorrowTime = if (inFull) time(tomorrows.first(), availability) else null,
        )
    }

    /**
     * «Oggi c'era «Nuoto aerobico» alle 13:20: non risulta ancora fatta…».
     * The app cannot tell a session skipped from one the watch has not sent
     * yet, so it says both, and what to do in each case.
     */
    fun earlier(gone: List<PlannedSession>, availability: TrainingAvailability): String? {
        if (gone.isEmpty()) return null
        val one = gone.size == 1
        val names = gone.map { session ->
            "«${session.title}»" + time(session, availability)?.let { " alle %d:%02d".format(it.hour, it.minute) }.orEmpty()
        }
        val list = if (one) names.first() else names.dropLast(1).joinToString(", ") + " e " + names.last()
        // Only a coach's session can be moved by asking: the athlete's own are theirs to move.
        val coached = gone.any { !it.own }
        return buildString {
            append(if (one) "Oggi c'era $list: non risulta ancora fatta. " else "Oggi c'erano $list: non risultano ancora fatte. ")
            append(if (one) "Se l'hai fatta, comparirà quando l'orologio si sincronizza" else "Se le hai fatte, compariranno quando l'orologio si sincronizza")
            if (coached) append(if (one) "; se l'hai saltata, puoi chiedere al coach di spostarla" else "; se le hai saltate, puoi chiedere al coach di spostarle")
            append(".")
        }
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
        if (sessions.isEmpty()) "Domani: giorno libero."
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
     * How the athlete is, as Oggi says it and without what to do about it:
     * «Hai recuperato bene e il carico è leggero». The coaches are given
     * these words, so the chat and Oggi do not tell two different stories.
     */
    fun state(recovery: RecoveryDay?, form: FormZone?, loadInDoubt: Boolean = false): String =
        sentence(recovery, form, DayLeft.FREE, loadInDoubt).substringBefore(':').trim().trimEnd('.')

    /** [state] as a sentence of its own, for when there is nothing to advise. */
    private fun stateOnly(recovery: RecoveryDay?, form: FormZone?, loadInDoubt: Boolean): String =
        state(recovery, form, loadInDoubt).let { if (it.isBlank()) "" else "$it." }

    /**
     * Recovery and form in one sentence, with what follows for today given
     * what is [left] of it. The body's response comes first: when it shows
     * fatigue, the load only explains it. When the body is fine, a high load
     * still says not to add. With a session not known as done the app cannot
     * tell how the day went, so it says how the athlete is and nothing more.
     */
    fun sentence(recovery: RecoveryDay?, form: FormZone?, left: DayLeft, loadInDoubt: Boolean = false): String {
        if (left == DayLeft.OPEN) return stateOnly(recovery, form, loadInDoubt)
        if (recovery?.pattern == LoadResponsePattern.ILLNESS) {
            return "Hai segnato che non stai bene: oggi riposo, l'allenamento può aspettare."
        }
        val light = recovery?.light
        val high = if (loadInDoubt) "risulta alto" else "è alto"
        fun say(state: String, session: String, free: String, done: String) = state + ": " + when (left) {
            DayLeft.SESSION -> session
            DayLeft.DONE -> done
            else -> free
        } + "."
        return when {
            light == RecoveryLight.RED || (recovery?.level ?: 0) >= 3 ->
                "I segnali di fatica durano da giorni: oggi riposo o solo movimento leggero."
            light == RecoveryLight.ORANGE -> say(
                "Il corpo mostra fatica accumulata",
                "fai la seduta più facile del previsto, oppure spostala",
                "tieni il giorno libero per recuperare",
                "per oggi basta così, ora pensa a recuperare",
            )
            light == RecoveryLight.YELLOW -> when (form) {
                FormZone.OVERLOAD -> say(
                    "Qualche segnale di fatica, e il carico delle ultime settimane $high",
                    "tieni la seduta facile e non aggiungere altro",
                    "oggi non aggiungere allenamenti",
                    "per oggi non aggiungere altro",
                )
                else -> say(
                    "Qualche segnale di fatica, niente di più",
                    "fai la seduta prevista e ascolta le sensazioni",
                    "oggi non serve aggiungere altro",
                    "per oggi basta così",
                )
            }
            light == RecoveryLight.GREEN -> when (form) {
                FormZone.OVERLOAD -> say(
                    "Hai recuperato bene, ma il carico delle ultime settimane $high",
                    "fai la seduta prevista e non aggiungere altro",
                    "oggi non aggiungere allenamenti",
                    "per oggi non aggiungere altro",
                )
                FormZone.PRODUCTIVE -> say(
                    "Hai recuperato bene e il carico è quello giusto per migliorare",
                    "fai la seduta come è scritta",
                    "il giorno libero ci sta",
                    "per oggi va bene così",
                )
                FormZone.NEUTRAL -> say(
                    "Hai recuperato bene e il carico è leggero",
                    "la seduta di oggi puoi farla con convinzione",
                    "c'è spazio per allenarti, se ne hai voglia",
                    "per oggi va bene così",
                )
                FormZone.FRESH -> say(
                    "Hai recuperato del tutto e hai energie da spendere",
                    "è il momento giusto per una seduta di qualità",
                    "c'è spazio per allenarti, se ne hai voglia",
                    "per oggi va bene così",
                )
                FormZone.DETRAINING -> say(
                    "Hai riposato molto e la forma comincia a scendere",
                    "fai la seduta prevista e riprendi con regolarità",
                    "riprendi ad allenarti con regolarità",
                    "continua così, con regolarità",
                )
                null -> say("Hai recuperato bene", "fai la seduta prevista", "il giorno libero ci sta", "per oggi va bene così")
            }
            // No recovery reading yet: only the load can speak.
            form == FormZone.OVERLOAD -> say(
                "Il carico delle ultime settimane $high",
                "fai la seduta prevista e non aggiungere altro",
                "oggi non aggiungere allenamenti",
                "per oggi non aggiungere altro",
            )
            else -> ""
        }
    }
}
