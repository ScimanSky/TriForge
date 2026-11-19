package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.AthleteProfile
import com.triforge.domain.Sport
import java.time.LocalDate
import java.time.ZoneId

/** What one sport coach brings to the table: its sessions and its notes. */
data class SportProposal(
    val sport: Sport,
    /** Only the days with a session of [sport]. */
    val days: List<PlannedDay>,
    /** What the other two coaches should know or do. */
    val note: String = "",
    val noteForNutriAi: String = "",
    val rationale: String = "",
)

/**
 * The table where the three sport coaches agree on the week. Nobody at the
 * table is in charge: the regia's rules are. The app joins the proposals,
 * checks them, sends them back once, and what the second round leaves open
 * it settles itself, the sport nearest to the goal first, and says so.
 */
object CoachTable {

    /** At most this many rounds of proposals. */
    const val MAX_ROUNDS = 2

    /** A session the app shortens never goes under this. */
    const val MIN_SESSION_MIN = 20

    private const val MAX_FIXES = 24

    /** Lines kept in the minutes: the latest are the ones that explain the week as it is. */
    private const val MAX_TABLE = 12

    /** The focus of a session of a day already gone: what the athlete did, not a plan. */
    const val DONE = "Fatta"

    /** Who wins a conflict: the weak sport, then the sports by their share of the week. */
    fun priority(budget: WeekBudget): List<Sport> =
        TriRegia.SPORTS.sortedWith(compareBy({ it != budget.weakSport }, { -budget.sports.getValue(it).share }))

    /** The days of the week already gone, as what the athlete really did. */
    fun pastDays(activities: List<Activity>, weekStart: LocalDate, today: LocalDate, zone: ZoneId): List<PlannedDay> {
        val byDay = activities.filter { it.sport in TriRegia.SPORTS }.groupBy { it.startTime.atZone(zone).toLocalDate() }
        return generateSequence(weekStart) { it.plusDays(1) }.takeWhile { it.isBefore(today) && it.isBefore(weekStart.plusDays(7)) }.map { date ->
            PlannedDay(
                date,
                byDay[date].orEmpty().sortedBy { it.startTime }.map {
                    PlannedSession(it.sport, it.name.ifBlank { TriRegia.sportName(it.sport) }.take(80), (it.durationSec / 60).toInt(), Intensity.ENDURANCE, focus = DONE)
                },
            )
        }.toList()
    }

    /** Minutes already done this week, per sport. */
    fun doneMinutes(past: List<PlannedDay>): Map<Sport, Int> = past.flatMap { it.sessions }
        .filter { it.sport != null }
        .groupBy { it.sport!! }
        .mapValues { (_, sessions) -> sessions.sumOf { it.durationMin } }

    /**
     * The week from the three proposals: past days as they went, then every
     * coach's sessions from [today] on, each finished by the app (zones,
     * targets, totals). On a day with bike and run the bike comes first.
     */
    fun merge(
        weekStart: LocalDate,
        today: LocalDate,
        past: List<PlannedDay>,
        proposals: List<SportProposal>,
        budget: WeekBudget,
        profile: AthleteProfile,
        availability: TrainingAvailability,
    ): WeekPlan {
        val days = (0L until 7L).map { offset ->
            val date = weekStart.plusDays(offset)
            if (date.isBefore(today)) return@map past.firstOrNull { it.date == date } ?: PlannedDay(date, emptyList())
            val sessions = proposals.flatMap { proposal ->
                proposal.days.filter { it.date == date }.flatMap { it.sessions }.map { it.copy(sport = proposal.sport) }
            }
            PlannedDay(date, order(sessions.map { Workouts.finalize(it, profile, availability.poolLengthM) }, availability))
        }
        return WeekPlan(
            weekStart = weekStart,
            phase = budget.phase.label.replaceFirstChar { it.uppercase() },
            days = days,
            rationale = proposals.filter { it.rationale.isNotBlank() }.joinToString("\n") { "${TriRegia.sportName(it.sport)}: ${it.rationale}" },
            noteForNutriAi = proposals.filter { it.noteForNutriAi.isNotBlank() }.joinToString(" ") { "${TriRegia.sportName(it.sport)}: ${it.noteForNutriAi}" },
            availableMin = budget.availableMinutes,
            maxHard = budget.maxHardSessions,
        )
    }

    /**
     * What one coach proposes in its chat, as a week with only its sessions
     * from [today] on, finished by the app: what the athlete sees on the
     * card and what is kept until they accept it.
     */
    fun preview(proposal: SportProposal, weekStart: LocalDate, today: LocalDate, budget: WeekBudget, profile: AthleteProfile, availability: TrainingAvailability): WeekPlan =
        merge(weekStart, today, emptyList(), listOf(proposal), budget, profile, availability)
            .copy(rationale = proposal.note.ifBlank { proposal.rationale })

    /**
     * The week after the athlete accepted a change from one coach's chat:
     * that coach's sessions from [today] on are replaced, the other two keep
     * theirs, and the regia checks the whole again. What it has to settle
     * goes in the minutes, after the coach's own line.
     */
    fun revise(
        current: WeekPlan,
        proposal: SportProposal,
        today: LocalDate,
        budget: WeekBudget,
        profile: AthleteProfile,
        availability: TrainingAvailability,
    ): WeekPlan {
        val upcoming = current.days.filter { !it.date.isBefore(today) }
        val others = TriRegia.SPORTS.filter { it != proposal.sport }.map { sport ->
            SportProposal(sport, upcoming.map { day -> day.copy(sessions = day.sessions.filter { it.sport == sport }) }.filter { it.sessions.isNotEmpty() })
        }
        val mine = proposal.copy(days = proposal.days.filter { !it.date.isBefore(today) })
        val joined = merge(current.weekStart, today, current.days.filter { it.date.isBefore(today) }, others + mine, budget, profile, availability)
        val (settled, fixes) = settle(joined, budget, availability, profile, today)
        val line = TableVoice.of(proposal.sport)?.let { voice ->
            TableNote(voice, "Modifica chiesta in chat" + (proposal.note.ifBlank { proposal.rationale }.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."))
        }
        return settled.copy(
            phase = current.phase,
            rationale = current.rationale,
            noteForNutriAi = proposal.noteForNutriAi.ifBlank { current.noteForNutriAi },
            table = (current.table + listOfNotNull(line) + fixes).takeLast(MAX_TABLE),
            rounds = current.rounds.coerceAtLeast(1),
        )
    }

    /** By the athlete's usual time when every sport of the day has one, else swim, bike, run. */
    private fun order(sessions: List<PlannedSession>, availability: TrainingAvailability): List<PlannedSession> {
        val times = sessions.map { it.sport?.let(availability::slot)?.time }
        return if (times.all { it != null }) sessions.sortedBy { it.sport?.let(availability::slot)?.time }
        else sessions.sortedBy { TriRegia.SPORTS.indexOf(it.sport) }
    }

    /**
     * Settles what the coaches left open, one rule at a time: the session of
     * the sport furthest from the goal gives way. Days before [today] are
     * never touched. Returns the week and what the app did, for the minutes.
     */
    fun settle(
        plan: WeekPlan,
        budget: WeekBudget,
        availability: TrainingAvailability,
        profile: AthleteProfile,
        today: LocalDate,
    ): Pair<WeekPlan, List<TableNote>> {
        val losers = priority(budget).reversed()
        val notes = mutableListOf<TableNote>()
        var current = plan
        repeat(MAX_FIXES) {
            val conflict = TriRegia.check(current, budget, availability, today, profile).firstOrNull() ?: return current to notes
            val (next, note) = fix(current, conflict, budget, availability, profile, today, losers) ?: return current to notes
            if (next == current) return current to notes
            current = next
            notes += TableNote(TableVoice.REGIA, note)
        }
        return current to notes
    }

    private data class Spot(val day: Int, val index: Int, val session: PlannedSession, val date: LocalDate)

    private fun spots(plan: WeekPlan, today: LocalDate): List<Spot> = plan.days.flatMapIndexed { d, day ->
        if (day.date.isBefore(today)) emptyList()
        else day.sessions.mapIndexedNotNull { i, session -> session.takeIf { it.sport in TriRegia.SPORTS && it.durationMin > 0 }?.let { Spot(d, i, it, day.date) } }
    }

    private fun WeekPlan.replace(spot: Spot, session: PlannedSession?): WeekPlan = copy(
        days = days.mapIndexed { d, day ->
            if (d != spot.day) day
            else day.copy(sessions = day.sessions.flatMapIndexed { i, old -> if (i != spot.index) listOf(old) else listOfNotNull(session) })
        },
    )

    private fun name(spot: Spot): String =
        "seduta di ${TriRegia.sportName(spot.session.sport!!).lowercase()} di ${AthleteContext.dayLabel(spot.date)}"

    private fun fix(
        plan: WeekPlan,
        conflict: RegiaConflict,
        budget: WeekBudget,
        availability: TrainingAvailability,
        profile: AthleteProfile,
        today: LocalDate,
        losers: List<Sport>,
    ): Pair<WeekPlan, String>? {
        val all = spots(plan, today)
        fun rank(spot: Spot) = losers.indexOf(spot.session.sport)
        fun soften(spot: Spot, why: String) = plan.replace(spot, Workouts.soften(spot.session, profile)) to "La ${name(spot)} diventa facile: $why."
        fun drop(spot: Spot, why: String) = plan.replace(spot, null) to "Tolta la ${name(spot)}: $why."
        val hard = all.filter { TriRegia.isHard(it.session) }

        return when (conflict.rule) {
            RegiaRule.HARD_IN_A_ROW -> hard
                .filter { it.date == conflict.date || it.date == conflict.date?.minusDays(1) }
                .minByOrNull(::rank)?.let { soften(it, "erano due giorni duri di fila") }

            RegiaRule.HARD_SESSIONS -> hard.sortedWith(compareBy({ rank(it) }, { -it.day })).firstOrNull()
                ?.let { soften(it, "le sedute dure erano più di ${budget.maxHardSessions}") }

            RegiaRule.SPORT_QUALITY -> hard.lastOrNull { it.session.sport == conflict.sport }
                ?.let { soften(it, "le sedute di qualità dello sport erano troppe") }

            RegiaRule.HIGH_ZONE_TIME -> {
                val sport = conflict.sport ?: return null
                val mine = all.filter { it.session.sport == sport }
                val high = mine.sumOf { Workouts.highMinutes(it.session, profile) }
                val excess = high - TriRegia.highZoneBudget(sport, budget.sports.getValue(sport).minutes)
                val spot = mine.maxByOrNull { Workouts.highMinutes(it.session, profile) } ?: return null
                val trimmed = Workouts.trimHigh(spot.session, excess, profile)
                if (trimmed != spot.session) plan.replace(spot, trimmed) to "Meno ripetute nella ${name(spot)}: il tempo in zona alta superava il limite della settimana."
                else soften(spot, "il tempo in zona alta superava il limite della settimana")
            }

            RegiaRule.NO_REST_DAY -> {
                val days = all.groupBy { it.date }
                val date = availability.restDay?.let { rest -> days.keys.firstOrNull { it.dayOfWeek == rest } }
                    ?: days.minByOrNull { (_, list) -> list.sumOf { it.session.durationMin } }?.key
                    ?: return null
                val cleared = plan.copy(days = plan.days.map { if (it.date == date) it.copy(sessions = emptyList()) else it })
                cleared to "${AthleteContext.dayLabel(date).replaceFirstChar { it.uppercase() }} diventa riposo: mancava un giorno libero."
            }

            RegiaRule.REST_DAY -> conflict.date?.let { date ->
                plan.copy(days = plan.days.map { if (it.date == date) it.copy(sessions = emptyList()) else it }) to
                    "${AthleteContext.dayLabel(date).replaceFirstChar { it.uppercase() }} resta libero: è il giorno di riposo che hai scelto."
            }

            RegiaRule.TOO_MANY_SPORTS -> all.filter { it.date == conflict.date }.minByOrNull(::rank)
                ?.let { drop(it, "erano tre sport nello stesso giorno") }

            RegiaRule.BRICK -> all.firstOrNull { it.date == conflict.date && it.session.sport == Sport.RUN }
                ?.let { drop(it, "la corsa subito dopo la bici è solo in fase specifica") }

            RegiaRule.UNAVAILABLE_DAY -> all.firstOrNull { it.date == conflict.date && it.session.sport == conflict.sport }
                ?.let { drop(it, "in quel giorno non puoi fare questo sport") }

            RegiaRule.LONGEST_SESSION -> {
                val spot = all.firstOrNull { it.date == conflict.date && it.session.sport == conflict.sport } ?: return null
                val limit = budget.sports[conflict.sport]?.longestMin ?: return null
                plan.replace(spot, Workouts.shorten(spot.session, limit, profile)) to "Accorciata la ${name(spot)} a $limit minuti, il massimo per ora."
            }

            RegiaRule.SPORT_TIME, RegiaRule.TOTAL_TIME -> {
                val sport = conflict.sport ?: losers.firstOrNull { loser -> all.any { it.session.sport == loser } } ?: return null
                val mine = all.filter { it.session.sport == sport }
                val used = plan.days.flatMap { it.sessions }.filter { it.sport in TriRegia.SPORTS }
                val excess = if (conflict.rule == RegiaRule.SPORT_TIME) {
                    used.filter { it.sport == sport }.sumOf { it.durationMin } - budget.sports.getValue(sport).minutes
                } else {
                    used.sumOf { it.durationMin } - minOf(budget.totalMinutes, budget.availableMinutes)
                }
                val spot = mine.maxByOrNull { it.session.durationMin } ?: return null
                val target = spot.session.durationMin - excess
                val shorter = Workouts.shorten(spot.session, target.coerceAtLeast(MIN_SESSION_MIN), profile)
                when {
                    target >= MIN_SESSION_MIN && shorter.durationMin < spot.session.durationMin ->
                        plan.replace(spot, shorter) to "Accorciata la ${name(spot)}: i minuti superavano il budget."
                    else -> mine.minByOrNull { it.session.durationMin }?.let { drop(it, "i minuti superavano il budget") }
                }
            }
        }
    }

    /** One coach's sessions in words, for the other coaches' second round. */
    fun describe(proposal: SportProposal): String = buildString {
        proposal.days.sortedBy { it.date }.forEach { day ->
            day.sessions.forEach { session ->
                appendLine("${AthleteContext.dayLabel(day.date)} ${day.date}: ${describe(session)}")
            }
        }
        if (proposal.days.isEmpty()) appendLine("Nessuna seduta.")
        if (proposal.note.isNotBlank()) append("Nota per gli altri coach: ${proposal.note}")
    }.trimEnd()

    fun describe(session: PlannedSession): String {
        val head = "${session.focus.ifBlank { session.title }}, ${session.durationMin} min, ${session.intensity.key}"
        val main = session.steps.filter { it.block == WorkoutBlock.MAIN }.joinToString("; ") { Workouts.describe(it) }
        return if (main.isBlank()) head else "$head ($main)"
    }
}
