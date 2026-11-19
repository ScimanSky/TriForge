package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.AthleteProfile
import com.triforge.domain.RaceGoal
import com.triforge.domain.Sport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the week sits in the season, from the race date and the recent volume. */
enum class CyclePhase(val key: String, val label: String) {
    RETURN("return", "ripresa"),
    GENERAL("general", "generale"),
    SPECIFIC("specific", "specifica"),
    TAPER("taper", "scarico pre-gara"),
    RACE_WEEK("race_week", "settimana di gara"),
}

/** What the athlete actually did in the four weeks before the one being planned: the three disciplines and the gym. */
data class RecentTraining(
    /** Average minutes a week, per sport. */
    val weeklyMinutes: Map<Sport, Double> = emptyMap(),
    /** Longest session of the last six weeks, per sport, in minutes. */
    val longestMinutes: Map<Sport, Int> = emptyMap(),
    /** Median session of the last six weeks, per sport, in minutes. */
    val medianMinutes: Map<Sport, Int> = emptyMap(),
) {
    val totalWeeklyMinutes: Double get() = weeklyMinutes.values.sum()

    /** Average minutes a week in the gym. */
    val gymWeeklyMinutes: Double get() = weeklyMinutes[Sport.STRENGTH] ?: 0.0

    companion object {
        const val VOLUME_WEEKS = 4L
        const val SESSION_WEEKS = 6L

        fun of(activities: List<Activity>, weekStart: LocalDate, zone: ZoneId): RecentTraining {
            fun between(weeks: Long) = activities.filter {
                val day = it.startTime.atZone(zone).toLocalDate()
                !day.isBefore(weekStart.minusWeeks(weeks)) && day.isBefore(weekStart)
            }.filter { (it.sport in TriRegia.SPORTS || it.sport == Sport.STRENGTH) && it.durationSec > 0 }
            val volume = between(VOLUME_WEEKS).groupBy { it.sport }
                .mapValues { (_, list) -> list.sumOf { it.durationSec } / 60.0 / VOLUME_WEEKS }
            val sessions = between(SESSION_WEEKS).groupBy { it.sport }
                .mapValues { (_, list) -> list.map { (it.durationSec / 60).toInt() }.sorted() }
            return RecentTraining(
                weeklyMinutes = volume,
                longestMinutes = sessions.mapValues { it.value.last() },
                medianMinutes = sessions.mapValues { it.value[it.value.size / 2] },
            )
        }
    }
}

/** One sport's part of the week. */
data class SportBudget(
    val sport: Sport,
    val share: Double,
    /** Minutes the regia gives the sport this week. */
    val minutes: Int,
    /** The longest single session allowed, or null without history. */
    val longestMin: Int?,
    val maxQuality: Int,
)

/** The shape of the week, decided by the app before any coach writes a session. */
data class WeekBudget(
    val phase: CyclePhase,
    val availableMinutes: Int,
    val totalMinutes: Int,
    val maxHardSessions: Int,
    val sports: Map<Sport, SportBudget>,
    val weakSport: Sport?,
    /** The weak sport came from the data, not from the athlete. */
    val weakFromData: Boolean,
    val bricksAllowed: Boolean,
    /** The weekly hours were not given and came from the recent volume or a default. */
    val hoursEstimated: Boolean,
    /** Minutes of the week the athlete's gym takes: inside [availableMinutes], outside [totalMinutes]. */
    val gymMinutes: Int = 0,
    /** The disciplines the athlete is not doing in this period: no minutes, no sessions. */
    val paused: Set<Sport> = emptySet(),
    /**
     * Minutes of the week of each discipline the athlete does with their own
     * trainer: inside [availableMinutes], outside [totalMinutes], like the gym.
     */
    val ownMinutes: Map<Sport, Int> = emptyMap(),
    /** How long one session with the trainer usually lasts, per discipline. */
    val ownSessionMinutes: Map<Sport, Int> = emptyMap(),
) {
    /** The disciplines the athlete does with their own trainer. */
    val own: Set<Sport> get() = ownMinutes.keys

    /** The disciplines an AI coach writes this week. */
    val coached: List<Sport> get() = TriRegia.SPORTS.filter { it !in paused && it !in own }

    /** Minutes the athlete's own sessions take: the gym and the sports done with their trainer. */
    val fixedMinutes: Int get() = gymMinutes + ownMinutes.values.sum()

    /** What the available time leaves to the AI coaches once the athlete's own sessions are in. */
    val sportMinutesAvailable: Int get() = (availableMinutes - fixedMinutes).coerceAtLeast(0)
}

enum class RegiaRule {
    TOTAL_TIME, SPORT_TIME, HARD_SESSIONS, SPORT_QUALITY, HARD_IN_A_ROW, NO_REST_DAY, REST_DAY,
    TOO_MANY_SPORTS, BRICK, UNAVAILABLE_DAY, LONGEST_SESSION, HIGH_ZONE_TIME,
}

/** A rule the week breaks, said so the coach can fix it. */
data class RegiaConflict(val rule: RegiaRule, val date: LocalDate?, val text: String, val sport: Sport? = null)

/**
 * The triathlon regia: rules of the app that set the shape of the week (total
 * time, the share of each sport, how many hard sessions and where, rest,
 * bricks, safety limits) and check what the coaches write. The coaches fill
 * the sessions; the only ones the regia writes are the athlete's own (the
 * gym days, the sports done with their trainer), as fixed blocks the
 * coaches plan around.
 */
object TriRegia {

    val SPORTS = listOf(Sport.SWIM, Sport.BIKE, Sport.RUN)

    const val MAX_HARD_SESSIONS = 3
    const val MAX_QUALITY_PER_SPORT = 2
    const val MAX_SPORTS_PER_DAY = 2

    /** Points the weak sport takes from the other two. */
    const val WEAK_BONUS = 0.07

    /** Weekly volume at most this many times the recent average (running is the most injury prone). */
    fun growth(sport: Sport): Double = if (sport == Sport.RUN) 1.10 else 1.25

    /** Minutes at threshold and above a sport may hold in the week: a fifth of the swim, 15% on land, never under 15. */
    fun highZoneBudget(sport: Sport, minutes: Int): Int =
        max(15, (minutes * (if (sport == Sport.SWIM) 0.20 else 0.15)).roundToInt())

    /** A sport with less than this a week has no history to grow from. */
    const val HISTORY_MINUTES = 30.0

    /** Minutes a sport starts from when it has no history. */
    const val NEW_SPORT_MINUTES = 90

    /** The longest gym session of a taper week. */
    const val TAPER_GYM_MINUTES = 45

    /** The focus of the athlete's own gym session, as the week shows it. */
    const val GYM_FOCUS = "Forza"

    /** The focus of a session the athlete does with their own trainer. */
    const val OWN_FOCUS = "Tuo allenatore"

    /** How long a session with the trainer is taken to last without history. */
    const val OWN_SESSION_MINUTES = 60

    private val OWN_SESSION_RANGE = 20..180

    const val DEFAULT_WEEKLY_HOURS = 6.0

    private val italian = Locale.ITALIAN

    fun isHard(session: PlannedSession): Boolean = session.sport != null && session.durationMin > 0 &&
        (session.intensity == Intensity.THRESHOLD || session.intensity == Intensity.VO2MAX || session.intensity == Intensity.RACE)

    /** Starting shares by race distance: the longer the race, the more bike. */
    fun baseShares(goal: RaceGoal?): Map<Sport, Double> = when (goal) {
        RaceGoal.HALF, RaceGoal.FULL -> mapOf(Sport.SWIM to 0.15, Sport.BIKE to 0.50, Sport.RUN to 0.35)
        else -> mapOf(Sport.SWIM to 0.20, Sport.BIKE to 0.45, Sport.RUN to 0.35)
    }

    /**
     * The shares with the weak sport brought up, the others scaled down. A
     * sport not in [active] (paused by the athlete) has no share: the others
     * split the week in the same proportions they had.
     */
    fun shares(goal: RaceGoal?, weak: Sport?, active: List<Sport> = SPORTS): Map<Sport, Double> {
        val all = baseShares(goal)
        val sum = active.sumOf { all.getValue(it) }
        val base = SPORTS.associateWith { if (it in active && sum > 0) all.getValue(it) / sum else 0.0 }
        val weakShare = weak?.takeIf { it in active && active.size > 1 }?.let { base.getValue(it) } ?: return base
        val scale = (1 - weakShare - WEAK_BONUS) / (1 - weakShare)
        return base.mapValues { (sport, share) -> if (sport == weak) share + WEAK_BONUS else share * scale }
    }

    /** The sport furthest below its share in the recent weeks, among the [active] ones, or null without data. */
    fun weakFromData(recent: RecentTraining, goal: RaceGoal?, active: List<Sport> = SPORTS): Sport? {
        if (active.size < 2) return null
        val total = active.sumOf { recent.weeklyMinutes[it] ?: 0.0 }
        if (total < HISTORY_MINUTES * 2) return null
        val base = baseShares(goal)
        return active.minByOrNull { (recent.weeklyMinutes[it] ?: 0.0) / total / base.getValue(it) }
    }

    /** How long one of the athlete's gym sessions is planned for: shorter in the taper. */
    fun gymSessionMinutes(availability: TrainingAvailability, phase: CyclePhase): Int = when (phase) {
        CyclePhase.RACE_WEEK -> 0
        CyclePhase.TAPER -> min(availability.gym.minutes, TAPER_GYM_MINUTES)
        else -> availability.gym.minutes
    }

    /**
     * The athlete's gym session of [date], or null: the gym days of board 8,
     * never the preferred rest day, none in race week and a light one in the
     * taper. It is theirs, so it has no steps and no coach.
     */
    fun gymSession(availability: TrainingAvailability, phase: CyclePhase, date: LocalDate): PlannedSession? {
        if (date.dayOfWeek !in availability.gymDays) return null
        val minutes = gymSessionMinutes(availability, phase).takeIf { it > 0 } ?: return null
        val light = phase == CyclePhase.TAPER
        return PlannedSession(
            sport = Sport.STRENGTH,
            title = if (light) "Palestra leggera" else "Palestra",
            durationMin = minutes,
            intensity = if (light) Intensity.EASY else Intensity.TEMPO,
            focus = GYM_FOCUS,
            why = if (light) "Scarico prima della gara: in palestra solo lavoro leggero, niente carichi massimi."
            else "Il tuo giorno di palestra: i coach hanno scritto le loro sedute tenendone conto.",
            own = true,
        )
    }

    /**
     * Minutes of the week the gym takes: the days the athlete gave, or
     * without days what they did in the last weeks.
     */
    fun gymMinutes(availability: TrainingAvailability, phase: CyclePhase, recent: RecentTraining): Int {
        if (phase == CyclePhase.RACE_WEEK) return 0
        val days = availability.gymDays
        if (days.isNotEmpty()) return days.size * gymSessionMinutes(availability, phase)
        return (recent.gymWeeklyMinutes / 5).roundToInt() * 5
    }

    /** How long one session with the athlete's trainer usually lasts: the median of the last weeks. */
    fun ownSessionMinutes(sport: Sport, recent: RecentTraining): Int =
        recent.medianMinutes[sport]?.coerceIn(OWN_SESSION_RANGE)?.let { (it / 5.0).roundToInt() * 5 } ?: OWN_SESSION_MINUTES

    /**
     * Minutes of the week a sport done with the trainer takes: the days the
     * athlete gave, or without days what they did in the last weeks.
     */
    fun ownMinutes(sport: Sport, availability: TrainingAvailability, recent: RecentTraining): Int {
        val days = availability.ownDays(sport)
        if (days.isNotEmpty()) return days.size * ownSessionMinutes(sport, recent)
        return ((recent.weeklyMinutes[sport] ?: 0.0) / 5).roundToInt() * 5
    }

    /**
     * The session [sport] has on [date] with the athlete's trainer, or null.
     * What is in it only the trainer knows: the week shows the time it takes
     * and counts it as a medium session until the watch says what it was.
     */
    fun ownSession(sport: Sport, availability: TrainingAvailability, budget: WeekBudget, date: LocalDate): PlannedSession? {
        if (date.dayOfWeek !in availability.ownDays(sport)) return null
        val minutes = budget.ownSessionMinutes[sport]?.takeIf { it > 0 } ?: return null
        return PlannedSession(
            sport = sport,
            title = sportName(sport),
            durationMin = minutes,
            intensity = Intensity.TEMPO,
            focus = OWN_FOCUS,
            why = "La seduta la decide il tuo allenatore: i coach hanno scritto le loro tenendone conto.",
            own = true,
        )
    }

    /** The athlete's own sessions of [date]: the sports done with their trainer, then the gym. */
    fun fixedSessions(availability: TrainingAvailability, budget: WeekBudget, date: LocalDate): List<PlannedSession> =
        SPORTS.filter { it in budget.own }.mapNotNull { ownSession(it, availability, budget, date) } +
            listOfNotNull(gymSession(availability, budget.phase, date))

    fun phase(profile: AthleteProfile, weekStart: LocalDate, recent: RecentTraining): CyclePhase {
        val race = profile.raceDate?.takeIf { profile.goal != RaceGoal.FITNESS && !it.isBefore(weekStart) }
        if (race != null) {
            val days = ChronoUnit.DAYS.between(weekStart, race)
            when {
                days <= 6 -> return CyclePhase.RACE_WEEK
                days <= 20 -> return CyclePhase.TAPER
                days <= 62 -> return CyclePhase.SPECIFIC
            }
        }
        val hours = profile.weeklyHours
        if (hours != null && recent.totalWeeklyMinutes < 0.4 * hours * 60) return CyclePhase.RETURN
        return CyclePhase.GENERAL
    }

    private fun phaseFactor(phase: CyclePhase): Double = when (phase) {
        CyclePhase.RETURN -> 0.7
        CyclePhase.GENERAL, CyclePhase.SPECIFIC -> 1.0
        CyclePhase.TAPER -> 0.65
        CyclePhase.RACE_WEEK -> 0.45
    }

    private fun maxHard(phase: CyclePhase): Int = when (phase) {
        CyclePhase.RETURN, CyclePhase.RACE_WEEK -> 1
        CyclePhase.TAPER -> 2
        CyclePhase.GENERAL, CyclePhase.SPECIFIC -> MAX_HARD_SESSIONS
    }

    /**
     * The week's budget: total time, each sport's minutes and limits.
     *
     * The weekly hours hold all the training, so the athlete's own sessions
     * come off first: the gym and the sports done with their trainer. What
     * is left is split among the sports the AI coaches write, by the shares,
     * each stopping at what it can safely grow to from its recent weeks; the
     * time a sport cannot use (it is paused, or it would grow too fast) goes
     * to the others, which stop at their own limit too. So nobody is cut
     * below their habit while time is free, and nothing grows faster than
     * its rule.
     */
    fun budget(profile: AthleteProfile, availability: TrainingAvailability, recent: RecentTraining, weekStart: LocalDate): WeekBudget {
        val phase = phase(profile, weekStart, recent)
        val active = SPORTS.filter(availability::isCoached)
        val own = availability.ownSports
        val weak = availability.weakSport?.takeIf { it in active } ?: weakFromData(recent, profile.goal, active)
        val shares = shares(profile.goal, weak, active)
        val hours = profile.weeklyHours
            ?: (recent.totalWeeklyMinutes / 60).takeIf { it >= 1 }
            ?: DEFAULT_WEEKLY_HOURS
        val available = (hours * 60).roundToInt()
        val gym = min(gymMinutes(availability, phase, recent), available)
        val ownMinutes = own.associateWith { ownMinutes(it, availability, recent) }
        val planned = (available - gym - ownMinutes.values.sum()).coerceAtLeast(0) * phaseFactor(phase)
        val hard = maxHard(phase)
        val caps = active.associateWith { sport ->
            val history = recent.weeklyMinutes[sport] ?: 0.0
            if (history >= HISTORY_MINUTES) history * growth(sport) else NEW_SPORT_MINUTES.toDouble()
        }
        val given = split(planned, shares, caps)
        val sports = SPORTS.associateWith { sport ->
            val minutes = given[sport] ?: 0.0
            SportBudget(
                sport = sport,
                share = shares.getValue(sport),
                minutes = (minutes / 5).roundToInt() * 5,
                longestMin = if (sport in active) longest(sport, minutes, recent, availability) else null,
                maxQuality = if (sport in active) min(MAX_QUALITY_PER_SPORT, hard) else 0,
            )
        }
        return WeekBudget(
            phase = phase,
            availableMinutes = available,
            totalMinutes = sports.values.sumOf { it.minutes },
            maxHardSessions = hard,
            sports = sports,
            weakSport = weak,
            weakFromData = availability.weakSport?.takeIf { it in active } == null && weak != null,
            bricksAllowed = phase == CyclePhase.SPECIFIC && Sport.BIKE in active && Sport.RUN in active,
            hoursEstimated = profile.weeklyHours == null,
            gymMinutes = gym,
            paused = SPORTS.filterNot(availability::isActive).toSet(),
            ownMinutes = ownMinutes,
            ownSessionMinutes = own.associateWith { ownSessionMinutes(it, recent) },
        )
    }

    /**
     * [minutes] split among the sports of [caps] by their [shares], none
     * above its cap: what a capped sport leaves goes to the others, again by
     * share, until the time or the room is finished.
     */
    fun split(minutes: Double, shares: Map<Sport, Double>, caps: Map<Sport, Double>): Map<Sport, Double> {
        val given = caps.keys.associateWith { 0.0 }.toMutableMap()
        var left = minutes
        var open = caps.keys.filter { (shares[it] ?: 0.0) > 0 }
        while (left > 0.5 && open.isNotEmpty()) {
            val weight = open.sumOf { shares.getValue(it) }
            val round = left
            open.forEach { sport ->
                val take = min(round * shares.getValue(sport) / weight, caps.getValue(sport) - given.getValue(sport))
                given[sport] = given.getValue(sport) + take
                left -= take
            }
            open = open.filter { caps.getValue(it) - given.getValue(it) > 0.5 }
        }
        return given
    }

    private fun longest(sport: Sport, minutes: Double, recent: RecentTraining, availability: TrainingAvailability): Int? {
        val fromHistory = when (sport) {
            Sport.SWIM -> recent.medianMinutes[sport]?.let { it * 1.5 }
            Sport.BIKE -> recent.longestMinutes[sport]?.let { it * 1.5 }
            Sport.RUN -> {
                val share = max(0.30 * minutes, 40.0)
                recent.longestMinutes[sport]?.let { min(it * 1.5, share) } ?: share
            }
            Sport.STRENGTH, Sport.OTHER -> null
        }
        val ride = if (sport == Sport.BIKE) availability.longestRideMin?.toDouble() else null
        val limit = listOfNotNull(fromHistory, ride).minOrNull() ?: return null
        return (limit / 5).roundToInt() * 5
    }

    // ---- Checks --------------------------------------------------------------

    /**
     * The rules [plan] breaks. Rules about single days look only at [from]
     * on, since the days already gone describe what the athlete did.
     */
    fun check(
        plan: WeekPlan,
        budget: WeekBudget,
        availability: TrainingAvailability,
        from: LocalDate = plan.weekStart,
        /** With the profile, the time at threshold and above of the structured workouts is checked too. */
        profile: AthleteProfile? = null,
    ): List<RegiaConflict> {
        val found = mutableListOf<RegiaConflict>()
        val days = plan.days.sortedBy { it.date }
        // The budget is of the sports the AI coaches write: the athlete's own sessions have their own minutes.
        val coached = budget.coached
        val sessions = days.flatMap { it.sessions }.filter { it.sport in coached && it.durationMin > 0 && !it.own }

        val total = sessions.sumOf { it.durationMin }
        val free = budget.sportMinutesAvailable
        val totalLimit = min(budget.totalMinutes + tolerance(budget.totalMinutes), free + tolerance(free))
        if (total > totalLimit) {
            val fixed = if (budget.fixedMinutes > 0) ", di cui ${budget.fixedMinutes} minuti già presi dalle sedute dell'atleta (${fixedNames(budget)})" else ""
            found += RegiaConflict(RegiaRule.TOTAL_TIME, null, "Le sedute dei coach durano $total minuti: il budget è ${budget.totalMinutes}, con ${hoursText(budget.availableMinutes)} h disponibili$fixed.")
        }
        coached.forEach { sport ->
            val sportBudget = budget.sports.getValue(sport)
            val minutes = sessions.filter { it.sport == sport }.sumOf { it.durationMin }
            if (minutes > sportBudget.minutes + tolerance(sportBudget.minutes)) {
                found += RegiaConflict(RegiaRule.SPORT_TIME, null, "${sportName(sport)}: $minutes minuti, il budget è ${sportBudget.minutes}.", sport)
            }
            if (profile != null) {
                val high = sessions.filter { it.sport == sport }.sumOf { Workouts.highMinutes(it, profile) }
                val limit = highZoneBudget(sport, sportBudget.minutes)
                if (high > limit) {
                    found += RegiaConflict(RegiaRule.HIGH_ZONE_TIME, null, "${sportName(sport)}: $high minuti in zona alta (soglia e oltre), al massimo $limit nella settimana.", sport)
                }
            }
            val quality = sessions.count { it.sport == sport && isHard(it) }
            if (quality > sportBudget.maxQuality) {
                found += RegiaConflict(RegiaRule.SPORT_QUALITY, null, "${sportName(sport)}: $quality sedute di qualità, al massimo ${sportBudget.maxQuality}.", sport)
            }
        }
        val hard = sessions.count(::isHard)
        if (hard > budget.maxHardSessions) {
            found += RegiaConflict(RegiaRule.HARD_SESSIONS, null, "$hard sedute dure nella settimana, al massimo ${budget.maxHardSessions} in fase ${budget.phase.label}.")
        }
        // A gym day is not a rest day. The athlete's own sessions stay: the day to free is one without them.
        if (days.size == 7 && days.none { day -> day.sessions.none(::isTraining) } && days.any { day -> day.sessions.none(::isFixed) }) {
            found += RegiaConflict(RegiaRule.NO_REST_DAY, null, "Manca un giorno di riposo.")
        }

        days.zipWithNext().forEach { (before, after) ->
            if (!after.date.isBefore(from) && after.date == before.date.plusDays(1) &&
                before.sessions.any(::isHard) && after.sessions.any(::isHard)
            ) {
                found += RegiaConflict(RegiaRule.HARD_IN_A_ROW, after.date, "${label(before.date)} e ${label(after.date)} sono due giorni duri di fila.")
            }
        }
        days.filter { !it.date.isBefore(from) }.forEach { day ->
            val real = day.sessions.filter { it.sport in SPORTS && it.durationMin > 0 && !it.own }
            if (real.isNotEmpty() && day.date.dayOfWeek == availability.restDay) {
                found += RegiaConflict(RegiaRule.REST_DAY, day.date, "${label(day.date)} è il giorno di riposo preferito dall'atleta.")
            }
            if (day.sessions.filter(::isTraining).mapNotNull { it.sport }.distinct().size > MAX_SPORTS_PER_DAY && real.isNotEmpty()) {
                val fixed = day.sessions.filter(::isFixed).mapNotNull { it.sport }.distinct()
                val with = if (fixed.isEmpty()) "" else ", ${fixed.joinToString(" e ") { sportName(it).lowercase() }} dell'atleta compres${if (fixed.size > 1) "i" else "a"}"
                found += RegiaConflict(RegiaRule.TOO_MANY_SPORTS, day.date, "${label(day.date)}: più di $MAX_SPORTS_PER_DAY sport nello stesso giorno$with.")
            }
            if (!budget.bricksAllowed && isBrick(real)) {
                found += RegiaConflict(RegiaRule.BRICK, day.date, "${label(day.date)}: corsa subito dopo la bici, ma i brick sono solo in fase specifica (questa è fase ${budget.phase.label}).")
            }
            real.forEach { session ->
                val sport = session.sport!!
                if (!availability.isActive(sport)) {
                    found += RegiaConflict(RegiaRule.UNAVAILABLE_DAY, day.date, "${label(day.date)}: ${sportName(sport).lowercase()} è in pausa, l'atleta non lo pratica in questo periodo.", sport)
                } else if (!availability.isCoached(sport)) {
                    found += RegiaConflict(RegiaRule.UNAVAILABLE_DAY, day.date, "${label(day.date)}: ${sportName(sport).lowercase()} lo segue l'allenatore dell'atleta, nessun coach ne scrive le sedute.", sport)
                } else if (!availability.allows(sport, day.date.dayOfWeek)) {
                    found += RegiaConflict(RegiaRule.UNAVAILABLE_DAY, day.date, "${label(day.date)}: ${sportName(sport).lowercase()} in un giorno in cui l'atleta non può (${dayList(availability.slot(sport)?.days.orEmpty())}).", sport)
                }
                val longest = budget.sports[sport]?.longestMin
                if (longest != null && session.durationMin > longest + tolerance(longest)) {
                    found += RegiaConflict(RegiaRule.LONGEST_SESSION, day.date, "${label(day.date)}: ${sportName(sport).lowercase()} di ${session.durationMin} minuti, al massimo $longest.", sport)
                }
            }
        }
        return found
    }

    /** The athlete's own gym session, which no rule moves. */
    fun isGym(session: PlannedSession): Boolean = session.sport == Sport.STRENGTH && session.durationMin > 0

    /** A session that is the athlete's own, which no rule moves: the gym, or a sport done with their trainer. */
    fun isFixed(session: PlannedSession): Boolean = session.durationMin > 0 && (session.own || session.sport == Sport.STRENGTH)

    /** The athlete's own sessions of the week in words: "nuoto con il suo allenatore, palestra". */
    private fun fixedNames(budget: WeekBudget): String =
        (SPORTS.filter { (budget.ownMinutes[it] ?: 0) > 0 }.map { "${sportName(it).lowercase()} con il suo allenatore" } +
            listOfNotNull("palestra".takeIf { budget.gymMinutes > 0 })).joinToString(", ")

    /** Anything that makes a day a training day: a session of the three sports or the gym. */
    fun isTraining(session: PlannedSession): Boolean = (session.sport in SPORTS || session.sport == Sport.STRENGTH) && session.durationMin > 0

    /** Bike then run on the same day, in the order the plan lists them. */
    fun isBrick(sessions: List<PlannedSession>): Boolean {
        val bike = sessions.indexOfFirst { it.sport == Sport.BIKE }
        return bike >= 0 && sessions.drop(bike + 1).any { it.sport == Sport.RUN }
    }

    private fun tolerance(minutes: Int): Int = max(15, minutes / 10)

    // ---- Text ----------------------------------------------------------------

    fun sportName(sport: Sport): String = when (sport) {
        Sport.SWIM -> "Nuoto"
        Sport.BIKE -> "Bici"
        Sport.RUN -> "Corsa"
        Sport.STRENGTH -> "Palestra"
        Sport.OTHER -> "Altro"
    }

    private fun label(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, italian).replaceFirstChar { c -> c.uppercase() } + " ${date.dayOfMonth}"

    fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, italian)

    private fun dayList(days: Set<DayOfWeek>): String = days.sorted().joinToString(", ") { dayName(it) }

    private fun hoursText(minutes: Int): String {
        val hours = minutes / 60.0
        return if (hours % 1.0 == 0.0) hours.toInt().toString() else "%.1f".format(Locale.ITALY, hours)
    }

    private fun percent(share: Double): Int = (share * 100).roundToInt()

    /** The regia for the coach's request: rules to keep, not suggestions. */
    fun describe(budget: WeekBudget, availability: TrainingAvailability): String = buildString {
        appendLine("Fase del ciclo: ${budget.phase.label}.")
        appendLine(
            "Ore disponibili: ${hoursText(budget.availableMinutes)} h a settimana" +
                (if (budget.hoursEstimated) " (stimate dal volume recente: l'atleta non le ha indicate)" else "") +
                "; budget di questa settimana per le sedute scritte dai coach" +
                (if (budget.coached.size in 1..2) " (${budget.coached.joinToString(", ") { sportName(it).lowercase() }})" else "") +
                ": ${budget.totalMinutes} minuti in tutto.",
        )
        val active = budget.coached
        if (budget.paused.isNotEmpty()) {
            appendLine(
                "Sport in pausa: ${budget.paused.joinToString(", ") { sportName(it).lowercase() }}. L'atleta non li pratica in questo periodo: " +
                    "nessuna seduta e nessun minuto, il loro tempo è passato agli altri sport.",
            )
        }
        if (budget.gymMinutes > 0) {
            val days = availability.gymDays
            val whenText = if (days.isEmpty()) "nei giorni che sceglie l'atleta" else "${dayList(days)}" + (availability.gym.time?.let { " alle $it" } ?: "")
            val each = if (days.isEmpty()) "" else ", ${gymSessionMinutes(availability, budget.phase)} minuti a seduta"
            appendLine(
                "Palestra (forza, la gestisce l'atleta: nessun coach la scrive o la sposta): $whenText$each, ${budget.gymMinutes} minuti nella settimana, già tolti dalle ore disponibili. " +
                    "È fatica vera per i muscoli: la qualità va prima della palestra nello stesso giorno, non il giorno dopo una palestra pesante per gli stessi muscoli.",
            )
        }
        SPORTS.filter { it in budget.own }.forEach { sport ->
            val days = availability.ownDays(sport)
            val name = sportName(sport)
            val whenText = if (days.isEmpty()) "nei giorni che decide con l'allenatore" else dayList(days) + (availability.slot(sport)?.time?.let { " alle $it" } ?: "")
            val each = if (days.isEmpty()) "" else ", circa ${budget.ownSessionMinutes[sport]} minuti a seduta"
            appendLine(
                "$name (lo segue l'allenatore dell'atleta, di persona: nessun coach AI scrive, sposta o corregge le sue sedute): $whenText$each, " +
                    "${budget.ownMinutes[sport]} minuti nella settimana, già tolti dalle ore disponibili. Che cosa farà in ogni seduta non si sa prima: " +
                    "considerala una seduta vera, di media intensità, e guarda nei dati quelle già fatte. Il Coach $name le legge e le commenta.",
            )
        }
        if (active.isNotEmpty()) {
            appendLine(
                "Quote e minuti per sport: " + active.joinToString(", ") { sport ->
                    val b = budget.sports.getValue(sport)
                    "${sportName(sport).lowercase()} ${percent(b.share)}% (${b.minutes} min" + (b.longestMin?.let { ", seduta più lunga al massimo $it min" } ?: "") + ")"
                } + ".",
            )
        }
        budget.weakSport?.let {
            appendLine("Punto debole: ${sportName(it).lowercase()} (" + (if (budget.weakFromData) "dai dati degli ultimi allenamenti" else "indicato dall'atleta") + "): ha la precedenza nei conflitti.")
        }
        appendLine(
            "Sedute dure (soglia, VO2max, gara): al massimo ${budget.maxHardSessions} in tutto e al massimo $MAX_QUALITY_PER_SPORT per sport; " +
                "mai due giorni duri di fila, qualunque sport; almeno un giorno di riposo; al massimo $MAX_SPORTS_PER_DAY sport nello stesso giorno" +
                (if (budget.fixedMinutes > 0) ", comprese le sedute dell'atleta (${fixedNames(budget)})." else "."),
        )
        if (Sport.BIKE in active && Sport.RUN in active) {
            appendLine(
                if (budget.bricksAllowed) "Fase specifica: puoi mettere un brick (corsa subito dopo la bici), elencando la bici prima della corsa."
                else "Niente brick (corsa subito dopo la bici): sono solo in fase specifica.",
            )
        }
        availability.restDay?.let { appendLine("Giorno di riposo preferito: ${it.getDisplayName(TextStyle.FULL, italian)}.") }
        active.forEach { sport ->
            val slot = availability.slot(sport) ?: return@forEach
            val parts = mutableListOf<String>()
            parts += if (slot.days.isEmpty()) "tutti i giorni" else "solo ${dayList(slot.days)}"
            slot.time?.let { parts += "di solito alle $it" }
            if (sport == Sport.SWIM) parts += "vasca da ${availability.poolLengthM} m"
            if (sport == Sport.BIKE) {
                availability.longestRideMin?.let { parts += "uscita più lunga possibile ${it / 60}h${"%02d".format(it % 60)}" }
                if (availability.indoorTrainer) parts += "rulli in settimana"
            }
            appendLine("${sportName(sport)}: ${parts.joinToString(", ")}.")
        }
    }.trimEnd()

    /** The conflicts, for the request that asks the coach to fix them. */
    fun describe(conflicts: List<RegiaConflict>): String = conflicts.joinToString("\n") { "- ${it.text}" }
}
