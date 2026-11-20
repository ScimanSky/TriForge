package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.OtherKind
import com.triforge.domain.Sport
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** How one line of the week went. */
enum class ReviewState {
    /** A planned session the watch recorded. */
    DONE,

    /** A planned session of a day gone that the watch did not record. */
    MISSED,

    /**
     * A planned session of a day gone that the athlete did not do, with what
     * they trained instead: the gym in place of the swim, say.
     */
    REPLACED,

    /** A planned session still to do, or a rest line. */
    TODO,

    /** Something recorded on a planned day that was not in the programme. */
    EXTRA,

    /** Something recorded on a day the week had no programme for yet. */
    RECORD,
}

/** One line of a day: what was planned, what was recorded for it, or both. */
data class ReviewedSession(
    val state: ReviewState,
    val session: PlannedSession? = null,
    val activity: Activity? = null,
) {
    val sport: Sport? get() = session?.sport ?: activity?.sport
    val plannedMin: Int? get() = session?.durationMin?.takeIf { it > 0 }
    val doneMin: Int? get() = activity?.let { (it.durationSec / 60).toInt() }

    /** Done, but for a time worth telling apart from the one planned. */
    val differs: Boolean
        get() {
            if (state != ReviewState.DONE) return false
            val planned = plannedMin ?: return false
            val done = doneMin ?: return false
            return abs(done - planned) >= WeekReview.NOTABLE_MINUTES
        }
}

data class ReviewedDay(val date: LocalDate, val items: List<ReviewedSession>)

/**
 * The week as planned next to the week as it went. A planned session is
 * done when the watch recorded the same sport on the same day; what the
 * watch recorded besides is shown too, so the athlete and the coaches see
 * what really happened and not only what was written.
 */
data class WeekReview(val days: List<ReviewedDay>) {

    fun day(date: LocalDate): ReviewedDay? = days.firstOrNull { it.date == date }

    /** Minutes recorded in the week so far, everything the watch saw. */
    val doneMinutes: Int get() = days.sumOf { day -> day.items.sumOf { it.doneMin ?: 0 } }

    val missed: List<Pair<LocalDate, PlannedSession>>
        get() = days.flatMap { day -> day.items.filter { it.state == ReviewState.MISSED }.mapNotNull { item -> item.session?.let { day.date to it } } }

    /** Whether there is anything to say about the days gone. */
    val hasPast: Boolean get() = days.any { day -> day.items.any { it.state != ReviewState.TODO } }

    companion object {
        /** A difference from the plan smaller than this is not worth a line. */
        const val NOTABLE_MINUTES = 5

        /** A recording shorter than this does not take the place of a planned session. */
        const val MIN_REPLACEMENT_SEC = 20 * 60L

        /** A recording shorter than this is a watch started by mistake, not a session. */
        const val MIN_ACTIVITY_SEC = 5 * 60L

        fun of(plan: WeekPlan, activities: List<Activity>, today: LocalDate, zone: ZoneId): WeekReview {
            val byDay = activities
                .filter { it.durationSec >= MIN_ACTIVITY_SEC }
                .groupBy { it.startTime.atZone(zone).toLocalDate() }
            return WeekReview(
                plan.days.sortedBy { it.date }.map { day ->
                    val recorded = if (day.date.isAfter(today)) emptyList() else byDay[day.date].orEmpty().sortedBy { it.startTime }
                    ReviewedDay(day.date, review(day, recorded, covered(plan, day), day.date.isBefore(today)))
                },
            )
        }

        /** Whether [day] had a programme: from the day the week was planned on. */
        private fun covered(plan: WeekPlan, day: PlannedDay): Boolean =
            plan.plannedFrom?.let { !day.date.isBefore(it) } ?: day.sessions.none { it.focus == CoachTable.DONE }

        /** Whether [activity] is a training that can stand for a planned one: not a walk, not a stretch. */
        private fun isTraining(activity: Activity): Boolean = activity.durationSec >= MIN_REPLACEMENT_SEC &&
            (activity.sport != Sport.OTHER || activity.kind == OtherKind.CARDIO || activity.kind == OtherKind.HIKE)

        private fun review(day: PlannedDay, recorded: List<Activity>, covered: Boolean, gone: Boolean): List<ReviewedSession> {
            // On a day without a programme the plan holds a copy of what was done: the recordings say it better.
            val planned = day.sessions.filter { covered && it.focus != CoachTable.DONE }
            val free = recorded.toMutableList()
            val lines = planned.map { session ->
                val match = if (session.sport == null || session.durationMin <= 0) null
                else free.filter { it.sport == session.sport }.minByOrNull { abs(it.durationSec / 60 - session.durationMin) }
                if (match != null) free.remove(match)
                when {
                    match != null -> ReviewedSession(ReviewState.DONE, session, match)
                    gone && session.sport != null && session.durationMin > 0 -> ReviewedSession(ReviewState.MISSED, session)
                    else -> ReviewedSession(ReviewState.TODO, session)
                }
            }
            // A session missed on a day with another training: that one took its place.
            val settled = lines.map { line ->
                val session = line.session
                if (line.state != ReviewState.MISSED || session == null) return@map line
                val other = free.filter(::isTraining).minByOrNull { abs(it.durationSec / 60 - session.durationMin) } ?: return@map line
                free.remove(other)
                ReviewedSession(ReviewState.REPLACED, session, other)
            }
            return settled + free.map { ReviewedSession(if (covered) ReviewState.EXTRA else ReviewState.RECORD, activity = it) }
        }
    }

    /** The days gone in words, for the coaches: what was planned and what the athlete did of it. */
    fun describe(today: LocalDate): String = buildString {
        days.filter { !it.date.isAfter(today) }.forEach { day ->
            day.items.forEach { item ->
                val session = item.session
                val activity = item.activity
                val text = when (item.state) {
                    ReviewState.DONE -> {
                        val what = "${TriRegia.sportName(session!!.sport!!).lowercase()} ${session.focus.ifBlank { session.title }}".trim()
                        val whose = if (session.own) " (seduta dell'atleta)" else ""
                        "$what$whose: previsti ${session.durationMin} min, fatti ${item.doneMin} min"
                    }
                    ReviewState.MISSED -> "${TriRegia.sportName(session!!.sport!!).lowercase()} ${session.focus.ifBlank { session.title }}".trim() +
                        " di ${session.durationMin} min" + (if (session.own) " (seduta dell'atleta)" else "") + ": non fatta"
                    ReviewState.REPLACED -> "${TriRegia.sportName(session!!.sport!!).lowercase()} ${session.focus.ifBlank { session.title }}".trim() +
                        " di ${session.durationMin} min" + (if (session.own) " (seduta dell'atleta)" else "") +
                        ": non fatta, al suo posto ${AthleteContext.activityName(activity!!)}, ${item.doneMin} min"
                    ReviewState.EXTRA -> "fuori programma: ${AthleteContext.activityName(activity!!)}, ${item.doneMin} min"
                    ReviewState.RECORD -> "${AthleteContext.activityName(activity!!)}, ${item.doneMin} min (prima che la settimana fosse scritta)"
                    ReviewState.TODO -> null
                }
                text?.let { appendLine("${AthleteContext.dayLabel(day.date)} ${day.date}: $it") }
            }
        }
    }.trimEnd()
}
