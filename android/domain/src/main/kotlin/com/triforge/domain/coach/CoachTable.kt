package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.AthleteProfile
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import java.time.LocalDate
import java.time.LocalTime
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

/** One thing a change does to the week: a session added, removed or rewritten. */
data class PlanChange(
    val date: LocalDate,
    val kind: Kind,
    val sport: Sport?,
    val before: PlannedSession? = null,
    val after: PlannedSession? = null,
) {
    enum class Kind { ADDED, REMOVED, CHANGED }

    /** The session the change is about: the new one, or the one that goes. */
    val session: PlannedSession get() = after ?: before!!
}

/**
 * What accepting a coach's proposal does to the week, worked out before the
 * athlete accepts it: the week as it would be, every session that changes
 * (the coach's own and any other the regia has to touch) and the regia's
 * reasons. Nothing changes that is not listed here.
 */
data class Revision(val plan: WeekPlan, val changes: List<PlanChange>, val notes: List<String>) {
    /** The changes outside the sessions the coach of [sport] rewrote: what the athlete did not ask for. */
    fun sideEffects(sport: Sport): List<PlanChange> = changes.filter { it.sport != sport || it.session.own }
}

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

    /** How the regia's first line of the minutes begins, see [opening]. */
    const val OPENING = "Punto di partenza: "

    /** From this hour on, today is over for training: the week is planned from tomorrow. */
    val DAY_OVER: LocalTime = LocalTime.of(21, 0)

    /**
     * The first day a week can still be written for at [now]: [today], or
     * tomorrow once the evening is gone. Nothing is ever planned in a time
     * already past, so a session never appears on a day that is over.
     */
    fun planningDay(today: LocalDate, now: LocalTime): LocalDate = if (now.isBefore(DAY_OVER)) today else today.plusDays(1)

    /**
     * Whether the athlete's own [session] of today can no longer start at
     * [now]: its usual time is gone. One without a usual time can still be
     * done until the evening.
     */
    fun isGone(session: PlannedSession, availability: TrainingAvailability, now: LocalTime?): Boolean {
        now ?: return false
        val sport = session.sport ?: return false
        val time = if (sport == Sport.STRENGTH) availability.gym.time else availability.slot(sport)?.time
        return time != null && !now.isBefore(time)
    }

    /**
     * The regia's first line of the minutes when part of the week is not the
     * coaches' to write: what is already on the calendar because it is the
     * athlete's own, what is in pause, and what that leaves. Null in a week
     * of three AI coaches and no gym, where there is nothing to say.
     */
    fun opening(budget: WeekBudget, availability: TrainingAvailability): TableNote? {
        // With days of their own the sessions are on the calendar; without, the regia only keeps their usual time aside.
        val own = TriRegia.SPORTS.filter { (budget.ownMinutes[it] ?: 0) > 0 }
        fun trainer(sport: Sport) = "di ${TriRegia.sportName(sport).lowercase()} con il tuo allenatore"
        val gym = budget.gymMinutes > 0
        val placed = own.filter { availability.ownDays(it).isNotEmpty() }.map(::trainer) +
            listOfNotNull("di palestra".takeIf { gym && availability.gymDays.isNotEmpty() })
        val aside = own.filter { availability.ownDays(it).isEmpty() }.map(::trainer) +
            listOfNotNull("di palestra".takeIf { gym && availability.gymDays.isEmpty() })
        val paused = TriRegia.SPORTS.filter { it in budget.paused }.map { TriRegia.sportName(it).lowercase() }
        if (placed.isEmpty() && aside.isEmpty() && paused.isEmpty()) return null
        val coached = budget.coached
        val left = Formatters.duration(budget.totalMinutes * 60L)
        val sentences = listOfNotNull(
            "sono già in calendario le tue sedute ${inWords(placed)}, nessun coach le scrive.".takeIf { placed.isNotEmpty() },
            "per le tue sedute ${inWords(aside)}, che non hanno giorni fissi, è tenuto da parte il tempo che ci dedichi di solito.".takeIf { aside.isNotEmpty() },
            "${inWords(paused)} in pausa.".takeIf { paused.isNotEmpty() },
            when (coached.size) {
                0 -> "questa settimana nessun coach scrive sedute."
                1 -> "al ${SportCoachPrompts.coachName(coached.first())} restano $left."
                else -> "ai coach di ${inWords(coached.map { TriRegia.sportName(it).lowercase() })} restano $left."
            },
        )
        val text = sentences.mapIndexed { index, sentence -> if (index == 0) sentence else sentence.replaceFirstChar { it.uppercase() } }.joinToString(" ")
        return TableNote(TableVoice.REGIA, OPENING + text)
    }

    /** Whether [note] is the regia's opening line and not something it had to settle. */
    fun isOpening(note: TableNote): Boolean = note.voice == TableVoice.REGIA && note.text.startsWith(OPENING)

    private fun inWords(items: List<String>): String =
        if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " e " + items.last()

    /** Who wins a conflict: the weak sport, then the sports by their share of the week. */
    fun priority(budget: WeekBudget): List<Sport> =
        TriRegia.SPORTS.sortedWith(compareBy({ it != budget.weakSport }, { -budget.sports.getValue(it).share }))

    /** The days of the week already gone, as what the athlete really did: the three sports and the gym. */
    fun pastDays(activities: List<Activity>, weekStart: LocalDate, today: LocalDate, zone: ZoneId): List<PlannedDay> {
        val byDay = activities.filter { it.sport in TriRegia.SPORTS || it.sport == Sport.STRENGTH }.groupBy { it.startTime.atZone(zone).toLocalDate() }
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
     * targets, totals), and with [withGym] the athlete's own sessions (the
     * gym, the sports done with their trainer). A coach whose sport is not
     * its to write has its sessions left out. On a day with bike and run the
     * bike comes first.
     */
    fun merge(
        weekStart: LocalDate,
        today: LocalDate,
        past: List<PlannedDay>,
        proposals: List<SportProposal>,
        budget: WeekBudget,
        profile: AthleteProfile,
        availability: TrainingAvailability,
        withGym: Boolean = true,
        now: LocalTime? = null,
        keptToday: List<PlannedSession> = emptyList(),
    ): WeekPlan {
        val days = (0L until 7L).map { offset ->
            val date = weekStart.plusDays(offset)
            if (date.isBefore(today)) return@map past.firstOrNull { it.date == date } ?: PlannedDay(date, emptyList())
            val sessions = proposals.filter { it.sport in budget.coached }.flatMap { proposal ->
                proposal.days.filter { it.date == date }.flatMap { it.sessions }.map { it.copy(sport = proposal.sport, own = false) }
            }
            val fixed = (if (withGym) TriRegia.fixedSessions(availability, budget, date) else emptyList()).filter { session ->
                date != today || !isGone(session, availability, now) || keptToday.any { it.own && it.sport == session.sport }
            }
            PlannedDay(date, order(sessions.map { Workouts.finalize(it, profile, availability.poolLengthM) } + fixed, availability))
        }
        return WeekPlan(
            weekStart = weekStart,
            phase = budget.phase.label.replaceFirstChar { it.uppercase() },
            days = days,
            rationale = proposals.filter { it.rationale.isNotBlank() }.joinToString("\n") { "${TriRegia.sportName(it.sport)}: ${it.rationale}" },
            noteForNutriAi = proposals.filter { it.noteForNutriAi.isNotBlank() }.joinToString(" ") { "${TriRegia.sportName(it.sport)}: ${it.noteForNutriAi}" },
            availableMin = budget.availableMinutes,
            maxHard = budget.maxHardSessions,
            plannedFrom = today.coerceAtLeast(weekStart),
        )
    }

    /**
     * [plan] with the days before [today] as [kept] has them. The regia
     * checks a week against what the athlete did, so while the coaches work
     * the days gone hold the activities; the plan that is saved keeps what
     * was planned for them, so the week can show what was done of it.
     */
    fun keepPlanned(plan: WeekPlan, kept: WeekPlan?, today: LocalDate): WeekPlan {
        if (kept == null || kept.weekStart != plan.weekStart) return plan
        val from = kept.plannedFrom ?: return plan
        return plan.copy(
            days = plan.days.map { day ->
                if (day.date.isBefore(today) && !day.date.isBefore(from)) kept.day(day.date) ?: day else day
            },
            plannedFrom = from,
        )
    }

    /**
     * What one coach proposes in its chat, as a week with only its sessions
     * from [today] on, finished by the app: what the athlete sees on the
     * card and what is kept until they accept it.
     */
    fun preview(proposal: SportProposal, weekStart: LocalDate, today: LocalDate, budget: WeekBudget, profile: AthleteProfile, availability: TrainingAvailability): WeekPlan =
        merge(weekStart, today, emptyList(), listOf(proposal), budget, profile, availability, withGym = false)
            .copy(rationale = proposal.note.ifBlank { proposal.rationale })

    /**
     * The week after the athlete accepted a change from one coach's chat:
     * that coach's sessions from [today] on are replaced, the other two keep
     * theirs, the athlete's own are put back as they are, and the regia
     * checks the whole again, against what was really done in the days gone
     * ([done], or what the plan holds for them). What it has to settle goes
     * in the minutes, after the coach's own line.
     */
    fun revise(
        current: WeekPlan,
        proposal: SportProposal,
        today: LocalDate,
        budget: WeekBudget,
        profile: AthleteProfile,
        availability: TrainingAvailability,
        done: List<PlannedDay>? = null,
        now: LocalTime? = null,
    ): WeekPlan = revision(current, proposal, today, budget, profile, availability, done, now).plan

    /**
     * [revise] with everything it changes spelled out, so the athlete reads
     * it before accepting: see [Revision]. [today] is the first day that can
     * still change ([planningDay]).
     */
    fun revision(
        current: WeekPlan,
        proposal: SportProposal,
        today: LocalDate,
        budget: WeekBudget,
        profile: AthleteProfile,
        availability: TrainingAvailability,
        done: List<PlannedDay>? = null,
        now: LocalTime? = null,
    ): Revision {
        if (proposal.sport !in budget.coached) return Revision(current, emptyList(), emptyList())
        val upcoming = current.days.filter { !it.date.isBefore(today) }
        val others = TriRegia.SPORTS.filter { it != proposal.sport }.map { sport ->
            SportProposal(sport, upcoming.map { day -> day.copy(sessions = day.sessions.filter { it.sport == sport && !it.own }) }.filter { it.sessions.isNotEmpty() })
        }
        val mine = proposal.copy(days = proposal.days.filter { !it.date.isBefore(today) })
        val past = done ?: current.days.filter { it.date.isBefore(today) }
        val keptToday = current.day(today)?.sessions.orEmpty().filter { it.own }
        val joined = merge(current.weekStart, today, past, others + mine, budget, profile, availability, now = now, keptToday = keptToday)
        val (checked, fixes) = settle(joined, budget, availability, profile, today)
        val settled = keepPlanned(checked, current.copy(plannedFrom = current.plannedFrom ?: current.weekStart), today)
        val line = TableVoice.of(proposal.sport)?.let { voice ->
            TableNote(voice, "Modifica chiesta in chat" + (proposal.note.ifBlank { proposal.rationale }.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."))
        }
        val changes = changes(current, settled, today)
        // What changed without the regia settling anything follows from the profile: say that too.
        val profileNotes = profileNotes(changes.filter { it.sport != proposal.sport || it.session.own }, availability)
        val plan = settled.copy(
            plannedFrom = current.plannedFrom,
            phase = current.phase,
            rationale = current.rationale,
            noteForNutriAi = proposal.noteForNutriAi.ifBlank { current.noteForNutriAi },
            table = (current.table + listOfNotNull(line) + profileNotes.map { TableNote(TableVoice.REGIA, it) } + fixes).takeLast(MAX_TABLE),
            rounds = current.rounds.coerceAtLeast(1),
        )
        return Revision(plan, changes, profileNotes + fixes.map { it.text })
    }

    /**
     * Every session from [today] on that differs between [before] and
     * [after], day by day. Sessions are matched by sport and by whose they
     * are (a coach's, or the athlete's own), in the order of the day.
     */
    fun changes(before: WeekPlan, after: WeekPlan, today: LocalDate): List<PlanChange> {
        fun sessions(plan: WeekPlan, date: LocalDate) = plan.day(date)?.sessions.orEmpty().filter { it.sport != null && it.durationMin > 0 }
        return after.days.map { it.date }.filter { !it.isBefore(today) }.sorted().flatMap { date ->
            val old = sessions(before, date).groupBy { it.sport to it.own }
            val new = sessions(after, date).groupBy { it.sport to it.own }
            (old.keys + new.keys).flatMap { key ->
                val was = old[key].orEmpty()
                val now = new[key].orEmpty()
                (0 until maxOf(was.size, now.size)).mapNotNull { index ->
                    val a = was.getOrNull(index)
                    val b = now.getOrNull(index)
                    when {
                        a == null -> PlanChange(date, PlanChange.Kind.ADDED, key.first, after = b)
                        b == null -> PlanChange(date, PlanChange.Kind.REMOVED, key.first, before = a)
                        !same(a, b) -> PlanChange(date, PlanChange.Kind.CHANGED, key.first, a, b)
                        else -> null
                    }
                }
            }
        }
    }

    /** Whether two sessions are the same to the athlete: what, how long, how hard, which sets. */
    private fun same(a: PlannedSession, b: PlannedSession): Boolean =
        a.title == b.title && a.durationMin == b.durationMin && a.intensity == b.intensity &&
            a.steps.map { it.copy(target = "", note = "") } == b.steps.map { it.copy(target = "", note = "") } && a.details == b.details

    /**
     * Why sessions outside a coach's change appear or go although no rule
     * of the regia was broken: the profile says so (a sport in pause or with
     * the trainer, the gym days). One line per reason.
     */
    private fun profileNotes(changes: List<PlanChange>, availability: TrainingAvailability): List<String> {
        val notes = mutableListOf<String>()
        TriRegia.SPORTS.forEach { sport ->
            val gone = changes.filter { it.sport == sport && it.kind == PlanChange.Kind.REMOVED && !it.session.own }
            if (gone.isEmpty()) return@forEach
            val name = TriRegia.sportName(sport).lowercase()
            when (availability.mode(sport)) {
                SportMode.PAUSED -> notes += "Tolte le sedute di $name: nel profilo è in pausa."
                SportMode.OWN -> notes += "Tolte le sedute di $name scritte dal coach: nel profilo lo segui con il tuo allenatore."
                SportMode.AI -> Unit
            }
        }
        val own = changes.filter { it.session.own }
        if (own.any { it.kind == PlanChange.Kind.ADDED }) notes += "Aggiunte le tue sedute fisse dei giorni che hai indicato nel profilo."
        if (own.any { it.kind == PlanChange.Kind.REMOVED }) notes += "Tolte le tue sedute fisse dei giorni che non sono più nel profilo."
        if (own.any { it.kind == PlanChange.Kind.CHANGED }) notes += "Aggiornata la durata delle tue sedute fisse, come nel profilo."
        return notes
    }

    /**
     * The week brought in line with the profile without asking any coach,
     * when the athlete changes "Giorni, orari e gara": the sessions of a
     * sport now in pause or with the trainer go, the athlete's own sessions
     * follow their new days, and the regia settles what that breaks. The
     * coaches' other sessions stay as written; null when nothing changes.
     */
    fun realign(
        current: WeekPlan,
        today: LocalDate,
        budget: WeekBudget,
        profile: AthleteProfile,
        availability: TrainingAvailability,
        now: LocalTime? = null,
    ): Revision? {
        val upcoming = current.days.filter { !it.date.isBefore(today) }
        val kept = TriRegia.SPORTS.map { sport ->
            SportProposal(sport, upcoming.map { day -> day.copy(sessions = day.sessions.filter { it.sport == sport && !it.own }) }.filter { it.sessions.isNotEmpty() })
        }
        val keptToday = current.day(today)?.sessions.orEmpty().filter { it.own }
        val joined = merge(current.weekStart, today, current.days.filter { it.date.isBefore(today) }, kept, budget, profile, availability, now = now, keptToday = keptToday)
        val changes = changes(current, joined, today)
        if (changes.isEmpty()) return null
        val notes = profileNotes(changes, availability)
        val (settled, fixes) = settle(joined, budget, availability, profile, today)
        val plan = settled.copy(
            plannedFrom = current.plannedFrom,
            phase = current.phase,
            rationale = current.rationale,
            noteForNutriAi = current.noteForNutriAi,
            createdAt = current.createdAt,
            table = (current.table + notes.map { TableNote(TableVoice.REGIA, it) } + fixes).takeLast(MAX_TABLE),
            rounds = current.rounds,
            availableMin = budget.availableMinutes,
            maxHard = budget.maxHardSessions,
        )
        return Revision(plan, changes(current, plan, today), notes + fixes.map { it.text })
    }

    /** By the athlete's usual time when every sport of the day has one, else swim, bike, run, and the gym last. */
    private fun order(sessions: List<PlannedSession>, availability: TrainingAvailability): List<PlannedSession> {
        val times = sessions.map { it.sport?.let(availability::slot)?.time }
        return if (times.all { it != null }) sessions.sortedBy { it.sport?.let(availability::slot)?.time }
        else sessions.sortedBy { (TriRegia.SPORTS + Sport.STRENGTH).indexOf(it.sport) }
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
            // The first conflict the app can do something about: one it cannot settle must not stop the others.
            val (next, note) = TriRegia.check(current, budget, availability, today, profile)
                .firstNotNullOfOrNull { conflict -> fix(current, conflict, budget, availability, profile, today, losers)?.takeIf { it.first != current } }
                ?: return current to notes
            current = next
            notes += TableNote(TableVoice.REGIA, note)
        }
        return current to notes
    }

    private data class Spot(val day: Int, val index: Int, val session: PlannedSession, val date: LocalDate)

    private fun spots(plan: WeekPlan, today: LocalDate): List<Spot> = plan.days.flatMapIndexed { d, day ->
        if (day.date.isBefore(today)) emptyList()
        else day.sessions.mapIndexedNotNull { i, session -> session.takeIf { it.sport in TriRegia.SPORTS && it.durationMin > 0 && !it.own }?.let { Spot(d, i, it, day.date) } }
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
                // Only a day without the athlete's own sessions can become rest: those are theirs, not the coaches'.
                val gymDates = plan.days.filter { day -> day.sessions.any(TriRegia::isFixed) }.map { it.date }.toSet()
                val days = all.filter { it.date !in gymDates }.groupBy { it.date }
                val date = availability.restDay?.let { rest -> days.keys.firstOrNull { it.dayOfWeek == rest } }
                    ?: days.minByOrNull { (_, list) -> list.sumOf { it.session.durationMin } }?.key
                    ?: return null
                val cleared = plan.copy(days = plan.days.map { if (it.date == date) it.copy(sessions = emptyList()) else it })
                cleared to "${AthleteContext.dayLabel(date).replaceFirstChar { it.uppercase() }} diventa riposo: mancava un giorno libero."
            }

            RegiaRule.REST_DAY -> conflict.date?.let { date ->
                plan.copy(days = plan.days.map { if (it.date == date) it.copy(sessions = it.sessions.filter(TriRegia::isFixed)) else it }) to
                    "${AthleteContext.dayLabel(date).replaceFirstChar { it.uppercase() }} resta libero: è il giorno di riposo che hai scelto."
            }

            RegiaRule.TOO_MANY_SPORTS -> all.filter { it.date == conflict.date }.minByOrNull(::rank)
                ?.let { drop(it, "erano troppe sedute diverse nello stesso giorno") }

            RegiaRule.BRICK -> all.firstOrNull { it.date == conflict.date && it.session.sport == Sport.RUN }
                ?.let { drop(it, "la corsa subito dopo la bici è solo in fase specifica") }

            RegiaRule.UNAVAILABLE_DAY -> all.firstOrNull { it.date == conflict.date && it.session.sport == conflict.sport }
                ?.let {
                    drop(
                        it,
                        when (availability.mode(it.session.sport!!)) {
                            SportMode.PAUSED -> "questo sport è in pausa"
                            SportMode.OWN -> "questo sport lo segui con il tuo allenatore"
                            SportMode.AI -> "in quel giorno non puoi fare questo sport"
                        },
                    )
                }

            RegiaRule.LONGEST_SESSION -> {
                val spot = all.firstOrNull { it.date == conflict.date && it.session.sport == conflict.sport } ?: return null
                val limit = budget.sports[conflict.sport]?.longestMin ?: return null
                plan.replace(spot, Workouts.shorten(spot.session, limit, profile)) to "Accorciata la ${name(spot)} a $limit minuti, il massimo per ora."
            }

            RegiaRule.SPORT_TIME, RegiaRule.TOTAL_TIME -> {
                val sport = conflict.sport ?: losers.firstOrNull { loser -> all.any { it.session.sport == loser } } ?: return null
                val mine = all.filter { it.session.sport == sport }
                val used = plan.days.flatMap { it.sessions }.filter { it.sport in budget.coached && !it.own }
                val excess = if (conflict.rule == RegiaRule.SPORT_TIME) {
                    used.filter { it.sport == sport }.sumOf { it.durationMin } - budget.sports.getValue(sport).minutes
                } else {
                    used.sumOf { it.durationMin } - minOf(budget.totalMinutes, budget.sportMinutesAvailable)
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
