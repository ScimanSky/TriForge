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

/** What the athlete actually did in the four weeks before the one being planned. */
data class RecentTraining(
    /** Average minutes a week, per sport. */
    val weeklyMinutes: Map<Sport, Double> = emptyMap(),
    /** Longest session of the last six weeks, per sport, in minutes. */
    val longestMinutes: Map<Sport, Int> = emptyMap(),
    /** Median session of the last six weeks, per sport, in minutes. */
    val medianMinutes: Map<Sport, Int> = emptyMap(),
) {
    val totalWeeklyMinutes: Double get() = weeklyMinutes.values.sum()

    companion object {
        const val VOLUME_WEEKS = 4L
        const val SESSION_WEEKS = 6L

        fun of(activities: List<Activity>, weekStart: LocalDate, zone: ZoneId): RecentTraining {
            fun between(weeks: Long) = activities.filter {
                val day = it.startTime.atZone(zone).toLocalDate()
                !day.isBefore(weekStart.minusWeeks(weeks)) && day.isBefore(weekStart)
            }.filter { it.sport in TriRegia.SPORTS && it.durationSec > 0 }
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
)

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
 * the sessions; the regia never writes one.
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

    const val DEFAULT_WEEKLY_HOURS = 6.0

    private val italian = Locale.ITALIAN

    fun isHard(session: PlannedSession): Boolean = session.sport != null && session.durationMin > 0 &&
        (session.intensity == Intensity.THRESHOLD || session.intensity == Intensity.VO2MAX || session.intensity == Intensity.RACE)

    /** Starting shares by race distance: the longer the race, the more bike. */
    fun baseShares(goal: RaceGoal?): Map<Sport, Double> = when (goal) {
        RaceGoal.HALF, RaceGoal.FULL -> mapOf(Sport.SWIM to 0.15, Sport.BIKE to 0.50, Sport.RUN to 0.35)
        else -> mapOf(Sport.SWIM to 0.20, Sport.BIKE to 0.45, Sport.RUN to 0.35)
    }

    /** The shares with the weak sport brought up, the other two scaled down. */
    fun shares(goal: RaceGoal?, weak: Sport?): Map<Sport, Double> {
        val base = baseShares(goal)
        val weakShare = weak?.let { base[it] } ?: return base
        val scale = (1 - weakShare - WEAK_BONUS) / (1 - weakShare)
        return base.mapValues { (sport, share) -> if (sport == weak) share + WEAK_BONUS else share * scale }
    }

    /** The sport furthest below its share in the recent weeks, or null without data. */
    fun weakFromData(recent: RecentTraining, goal: RaceGoal?): Sport? {
        val total = recent.totalWeeklyMinutes
        if (total < HISTORY_MINUTES * 2) return null
        val base = baseShares(goal)
        return SPORTS.minByOrNull { (recent.weeklyMinutes[it] ?: 0.0) / total / base.getValue(it) }
    }

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

    /** The week's budget: total time, each sport's minutes and limits. */
    fun budget(profile: AthleteProfile, availability: TrainingAvailability, recent: RecentTraining, weekStart: LocalDate): WeekBudget {
        val phase = phase(profile, weekStart, recent)
        val weak = availability.weakSport ?: weakFromData(recent, profile.goal)
        val shares = shares(profile.goal, weak)
        val hours = profile.weeklyHours
            ?: (recent.totalWeeklyMinutes / 60).takeIf { it >= 1 }
            ?: DEFAULT_WEEKLY_HOURS
        val available = (hours * 60).roundToInt()
        val planned = available * phaseFactor(phase)
        val hard = maxHard(phase)
        val sports = SPORTS.associateWith { sport ->
            val share = shares.getValue(sport)
            val history = recent.weeklyMinutes[sport] ?: 0.0
            val cap = if (history >= HISTORY_MINUTES) history * growth(sport) else NEW_SPORT_MINUTES.toDouble()
            val minutes = min(planned * share, cap)
            SportBudget(
                sport = sport,
                share = share,
                minutes = (minutes / 5).roundToInt() * 5,
                longestMin = longest(sport, minutes, recent, availability),
                maxQuality = min(MAX_QUALITY_PER_SPORT, hard),
            )
        }
        return WeekBudget(
            phase = phase,
            availableMinutes = available,
            totalMinutes = sports.values.sumOf { it.minutes },
            maxHardSessions = hard,
            sports = sports,
            weakSport = weak,
            weakFromData = availability.weakSport == null && weak != null,
            bricksAllowed = phase == CyclePhase.SPECIFIC,
            hoursEstimated = profile.weeklyHours == null,
        )
    }

    private fun longest(sport: Sport, minutes: Double, recent: RecentTraining, availability: TrainingAvailability): Int? {
        val fromHistory = when (sport) {
            Sport.SWIM -> recent.medianMinutes[sport]?.let { it * 1.5 }
            Sport.BIKE -> recent.longestMinutes[sport]?.let { it * 1.5 }
            Sport.RUN -> {
                val share = max(0.30 * minutes, 40.0)
                recent.longestMinutes[sport]?.let { min(it * 1.5, share) } ?: share
            }
            Sport.OTHER -> null
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
        val sessions = days.flatMap { it.sessions }.filter { it.sport in SPORTS && it.durationMin > 0 }

        val total = sessions.sumOf { it.durationMin }
        val totalLimit = min(budget.totalMinutes + tolerance(budget.totalMinutes), budget.availableMinutes + tolerance(budget.availableMinutes))
        if (total > totalLimit) {
            found += RegiaConflict(RegiaRule.TOTAL_TIME, null, "La settimana dura $total minuti: il budget è ${budget.totalMinutes}, con ${hoursText(budget.availableMinutes)} h disponibili.")
        }
        SPORTS.forEach { sport ->
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
        if (days.size == 7 && days.none { day -> day.sessions.none { it.sport in SPORTS && it.durationMin > 0 } }) {
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
            val real = day.sessions.filter { it.sport in SPORTS && it.durationMin > 0 }
            if (real.isNotEmpty() && day.date.dayOfWeek == availability.restDay) {
                found += RegiaConflict(RegiaRule.REST_DAY, day.date, "${label(day.date)} è il giorno di riposo preferito dall'atleta.")
            }
            if (real.mapNotNull { it.sport }.distinct().size > MAX_SPORTS_PER_DAY) {
                found += RegiaConflict(RegiaRule.TOO_MANY_SPORTS, day.date, "${label(day.date)}: più di $MAX_SPORTS_PER_DAY sport nello stesso giorno.")
            }
            if (!budget.bricksAllowed && isBrick(real)) {
                found += RegiaConflict(RegiaRule.BRICK, day.date, "${label(day.date)}: corsa subito dopo la bici, ma i brick sono solo in fase specifica (questa è fase ${budget.phase.label}).")
            }
            real.forEach { session ->
                val sport = session.sport!!
                if (!availability.allows(sport, day.date.dayOfWeek)) {
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
                "; budget di questa settimana: ${budget.totalMinutes} minuti in tutto.",
        )
        appendLine(
            "Quote e minuti per sport: " + SPORTS.joinToString(", ") { sport ->
                val b = budget.sports.getValue(sport)
                "${sportName(sport).lowercase()} ${percent(b.share)}% (${b.minutes} min" + (b.longestMin?.let { ", seduta più lunga al massimo $it min" } ?: "") + ")"
            } + ".",
        )
        budget.weakSport?.let {
            appendLine("Punto debole: ${sportName(it).lowercase()} (" + (if (budget.weakFromData) "dai dati degli ultimi allenamenti" else "indicato dall'atleta") + "): ha la precedenza nei conflitti.")
        }
        appendLine(
            "Sedute dure (soglia, VO2max, gara): al massimo ${budget.maxHardSessions} in tutto e al massimo $MAX_QUALITY_PER_SPORT per sport; " +
                "mai due giorni duri di fila, qualunque sport; almeno un giorno di riposo; al massimo $MAX_SPORTS_PER_DAY sport nello stesso giorno.",
        )
        appendLine(
            if (budget.bricksAllowed) "Fase specifica: puoi mettere un brick (corsa subito dopo la bici), elencando la bici prima della corsa."
            else "Niente brick (corsa subito dopo la bici): sono solo in fase specifica.",
        )
        availability.restDay?.let { appendLine("Giorno di riposo preferito: ${it.getDisplayName(TextStyle.FULL, italian)}.") }
        SPORTS.forEach { sport ->
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
