package com.triforge.domain.coach

import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One session of a coach's proposal, as the athlete reads it before accepting. */
data class PreviewLine(
    val date: LocalDate,
    val session: PlannedSession,
    val mark: Mark,
    /** For a [Mark.MOVED] session, the day it comes from. */
    val movedFrom: LocalDate? = null,
) {
    enum class Mark { NEW, MOVED, CHANGED, SAME, REMOVED }
}

/** One line of what the app says it changed in the week, with the sport it is about. */
data class NoticeLine(val sport: Sport?, val text: String)

/**
 * The week as it was before the athlete accepted a coach's change, kept
 * until the next change so that one tap puts it back.
 */
data class PlanUndo(
    val plan: WeekPlan,
    /** The coach whose proposal was accepted, and the message it was in. */
    val sport: Sport,
    val messageId: String,
    /** What accepting did to the week, as the athlete read it. */
    val summary: List<NoticeLine> = emptyList(),
    /** What it means for NutriAI, in a line; blank when nothing. */
    val nutri: String = "",
)

/**
 * A change to the week in the athlete's words, before and after it is
 * applied: the coach's own sessions one by one, and what changes besides
 * them («Cambia anche»).
 */
object ProposalPreview {

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN)

    /** «sab 21». */
    fun day(date: LocalDate): String = date.format(dayFormat).replace(".", "")

    /**
     * The sessions of [sport]'s coach from [from] on in the week as it would
     * be, each with what happens to it, and those that go. A session taken
     * off one day and put on another with the same title is one that moved.
     */
    fun lines(revision: Revision, sport: Sport, from: LocalDate): List<PreviewLine> {
        val mine = revision.changes.filter { it.sport == sport && !it.session.own }
        val removed = mine.filter { it.kind == PlanChange.Kind.REMOVED }.toMutableList()
        val kept = revision.plan.days.filter { !it.date.isBefore(from) }.sortedBy { it.date }.flatMap { day ->
            day.sessions.filter { it.sport == sport && !it.own && it.durationMin > 0 }.map { session ->
                val change = mine.firstOrNull { it.date == day.date && it.after == session }
                when (change?.kind) {
                    PlanChange.Kind.ADDED -> {
                        val source = removed.firstOrNull { it.session.title == session.title && it.date != day.date }
                        if (source != null) {
                            removed.remove(source)
                            PreviewLine(day.date, session, PreviewLine.Mark.MOVED, source.date)
                        } else {
                            PreviewLine(day.date, session, PreviewLine.Mark.NEW)
                        }
                    }
                    PlanChange.Kind.CHANGED -> PreviewLine(day.date, session, PreviewLine.Mark.CHANGED)
                    else -> PreviewLine(day.date, session, PreviewLine.Mark.SAME)
                }
            }
        }
        val gone = removed.map { PreviewLine(it.date, it.session, PreviewLine.Mark.REMOVED) }
        return (kept + gone).sortedBy { it.date }
    }

    /**
     * «Cambia anche»: what accepting does outside the sessions the coach
     * of [sport] rewrote, one line per session, with whose it is.
     */
    fun also(revision: Revision, sport: Sport): List<NoticeLine> = revision.sideEffects(sport).map { change ->
        val whose = if (change.session.own) "seduta tua" else change.sport?.let(TriRegia::sportName) ?: "Altro"
        NoticeLine(change.sport, "${capital(day(change.date))} · $whose. ${describe(change)}.")
    }

    /**
     * Everything accepting did, once it is done: the coach's own sessions
     * that changed, then the others'.
     */
    fun summary(revision: Revision, sport: Sport, from: LocalDate): List<NoticeLine> {
        val own = lines(revision, sport, from).mapNotNull { line ->
            val session = line.session
            val time = Formatters.duration(session.durationMin * 60L)
            val text = when (line.mark) {
                PreviewLine.Mark.NEW -> "Aggiunta «${session.title}», $time"
                PreviewLine.Mark.MOVED -> "«${session.title}» spostata da ${day(line.movedFrom ?: line.date)}, $time"
                PreviewLine.Mark.CHANGED ->
                    revision.changes.firstOrNull { it.date == line.date && it.after == session }?.let(::describe) ?: "«${session.title}» cambia"
                PreviewLine.Mark.REMOVED -> "Tolta «${session.title}», $time"
                PreviewLine.Mark.SAME -> return@mapNotNull null
            }
            NoticeLine(sport, "${capital(day(line.date))}. $text.")
        }
        return own + also(revision, sport)
    }

    /**
     * What a change of the week means for NutriAI: the days whose numbers
     * follow the new training, and those of [prepared] (the days with meals
     * already written) to look at again. Null when no day changes.
     */
    fun nutri(changes: List<PlanChange>, prepared: Set<LocalDate>): String? {
        val days = changes.map { it.date }.distinct().sorted()
        if (days.isEmpty()) return null
        val meals = days.filter { it in prepared }
        return "Cambiano i numeri di ${together(days.map(::day))}." +
            if (meals.isEmpty()) "" else " I pasti già preparati di ${together(meals.map(::day))} sono da rivedere."
    }

    private fun capital(text: String) = text.replaceFirstChar { it.uppercase() }

    /** «mar 24, gio 26 e sab 28». */
    private fun together(names: List<String>): String =
        if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " e " + names.last()

    /** One change in words: «Tolta «Bici agile», 1 h 30′», ««Soglia» da 1 h 00′ a 45′». */
    fun describe(change: PlanChange): String {
        val session = change.session
        fun time(s: PlannedSession) = Formatters.duration(s.durationMin * 60L)
        val before = change.before
        val after = change.after
        return when (change.kind) {
            PlanChange.Kind.ADDED -> "Aggiunta «${session.title}», ${time(session)}"
            PlanChange.Kind.REMOVED -> "Tolta «${session.title}», ${time(session)}"
            PlanChange.Kind.CHANGED -> when {
                before == null || after == null -> "«${session.title}» cambia"
                before.title != after.title -> "«${before.title}» diventa «${after.title}», ${time(after)}"
                before.durationMin != after.durationMin -> "«${after.title}» da ${time(before)} a ${time(after)}"
                else -> "«${after.title}» cambia nelle serie"
            }
        }
    }

    /**
     * What the app changed when the profile changed, to say once on the
     * week: one line per kind of change, the days together («mar 24 e gio
     * 26»), each with its reason.
     */
    fun notice(changes: List<PlanChange>, availability: TrainingAvailability): List<NoticeLine> =
        changes.groupBy { Triple(it.kind, it.sport, describe(it) to it.session.own) }.map { (key, group) ->
            val (_, sport, _) = key
            val change = group.first()
            val days = together(group.map { day(it.date) })
            NoticeLine(sport, "${capital(days)}. ${describe(change)}${reason(change, availability)}.")
        }

    private fun reason(change: PlanChange, availability: TrainingAvailability): String {
        val sport = change.sport ?: return ""
        if (change.session.own) {
            return when (change.kind) {
                PlanChange.Kind.ADDED -> ": è una seduta tua, come nel profilo"
                PlanChange.Kind.REMOVED -> ": quel giorno non è più nel profilo"
                PlanChange.Kind.CHANGED -> ": come nel profilo"
            }
        }
        if (change.kind != PlanChange.Kind.REMOVED || sport !in TriRegia.SPORTS) return ""
        val name = TriRegia.sportName(sport).lowercase()
        return when (availability.mode(sport)) {
            SportMode.PAUSED -> ": nel profilo $name è in pausa"
            SportMode.OWN -> ": nel profilo $name lo segui con il tuo allenatore"
            SportMode.AI -> ""
        }
    }
}
