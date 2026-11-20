package com.triforge.domain.coach

import com.triforge.domain.AthleteProfile
import com.triforge.domain.CheckIn
import com.triforge.domain.FormZone
import com.triforge.domain.LoadResponsePattern
import com.triforge.domain.RaceGoal
import com.triforge.domain.RecoveryDay
import com.triforge.domain.RecoveryLight
import com.triforge.domain.RecoveryPhase
import com.triforge.domain.RecoveryReport
import com.triforge.domain.Sport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a coach says and what the app then does must be one story: the days
 * of each sport are the athlete's, a session outside them stays out and the
 * week says so, and the coaches read how the athlete is in Oggi's own words.
 */
class CoachDaysTest {

    private val monday = LocalDate.of(2026, 11, 23)
    private val profile = AthleteProfile(goal = RaceGoal.HALF, raceDate = LocalDate.of(2027, 6, 7), weeklyHours = 9.0, thresholdPaceSecPerKm = 265, maxHr = 185)

    /** The run on Tuesday and Saturday, Thursday free: nothing else is the run coach's to fill. */
    private val availability = TrainingAvailability(
        swim = SportSlot(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), LocalTime.of(13, 20)),
        run = SportSlot(setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), LocalTime.of(12, 30)),
        restDay = DayOfWeek.THURSDAY,
        paused = setOf(Sport.BIKE),
    )

    private val budget = TriRegia.budget(profile, availability, RecentTraining(), monday)

    private fun easyRun(minutes: Int, title: String = "Corsa facile") = PlannedSession(
        Sport.RUN, title, 0, focus = "Facile",
        steps = listOf(WorkoutStep(WorkoutBlock.MAIN, 1, null, minutes * 60, "Z2", 0)),
    )

    private fun day(offset: Long, vararg sessions: PlannedSession) = PlannedDay(monday.plusDays(offset), sessions.toList())

    private val week = CoachTable.merge(monday, monday, emptyList(), listOf(SportProposal(Sport.RUN, listOf(day(1, easyRun(40))))), budget, profile, availability).copy(rounds = 1)

    // ---- A day that is not the athlete's ---------------------------------------

    @Test
    fun `a session proposed on a day that is not the sport's stays out, and the week says what really happened`() {
        // «Spostamela a domenica»: the coach says it does, but Sunday is not a day of the run.
        val toSunday = SportProposal(Sport.RUN, listOf(day(6, easyRun(40))), note = "Ho spostato la corsa di martedì a domenica.")
        val revision = CoachTable.revision(week, toSunday, monday, budget, profile, availability)

        assertTrue(revision.plan.days.flatMap { it.sessions }.none { it.sport == Sport.RUN }, "Sunday stays empty, and Tuesday's run goes as the coach asked")
        val refused = revision.refused.single()
        assertEquals(
            "La seduta di corsa di domenica non entra nel piano: domenica non è tra i tuoi giorni di corsa (mar, sab). " +
                "Puoi aggiungere il giorno in Profilo › Giorni, orari e gara.",
            refused,
        )
        // The card: what the proposal really does, a session taken off and nothing put in.
        assertEquals(listOf(PreviewLine.Mark.REMOVED), ProposalPreview.lines(revision, Sport.RUN, monday).map { it.mark })
        // The minutes: never the coach's «ho spostato», but what happened and why.
        val table = revision.plan.table.map { it.voice to it.text }
        assertTrue(table.none { it.second.contains("Ho spostato") }, table.toString())
        assertEquals(TableVoice.RUN to "Modifica chiesta in chat: mar 24, tolta «Corsa facile», 40′.", table.first { it.first == TableVoice.RUN })
        assertTrue(table.any { it.first == TableVoice.REGIA && it.second == refused }, table.toString())
    }

    @Test
    fun `the rest day the athlete chose is refused in its own words`() {
        val onThursday = SportProposal(Sport.RUN, listOf(day(1, easyRun(40)), day(3, easyRun(30, "Corsa breve"))), note = "Aggiunta una corsa giovedì.")
        val revision = CoachTable.revision(week, onThursday, monday, budget, profile, availability)

        assertContains(revision.refused.single(), "giovedì è il giorno di riposo che hai scelto")
        assertTrue(revision.plan.day(monday.plusDays(3))!!.sessions.isEmpty())
        // Nothing of the proposal went in: the session of Tuesday is the one already there.
        assertTrue(revision.changes.isEmpty())
        assertEquals("Modifica chiesta in chat: le sedute di corsa restano come erano.", revision.plan.table.first { it.voice == TableVoice.RUN }.text)
        assertNull(CoachTable.refusal(Sport.RUN, monday.plusDays(5), availability), "Saturday is a day of the run")
        assertNotNull(CoachTable.refusal(Sport.RUN, monday, availability))
    }

    @Test
    fun `a proposal that goes in as written keeps the coach's own words`() {
        val toSaturday = SportProposal(Sport.RUN, listOf(day(5, easyRun(40))), note = "Corsa spostata a sabato.", noteForNutriAi = "Sabato corsa a pranzo.")
        val revision = CoachTable.revision(week, toSaturday, monday, budget, profile, availability)

        assertTrue(revision.refused.isEmpty())
        assertEquals("Modifica chiesta in chat: Corsa spostata a sabato.", revision.plan.table.first { it.voice == TableVoice.RUN }.text)
        assertEquals("Sabato corsa a pranzo.", revision.plan.noteForNutriAi)
        assertEquals(listOf(PreviewLine.Mark.MOVED), ProposalPreview.lines(revision, Sport.RUN, monday).map { it.mark })
    }

    @Test
    fun `a session the regia only shortens keeps the coach's words, with the regia's line after them`() {
        // Far more than the run's minutes: the regia shortens it, and says so itself.
        val huge = SportProposal(Sport.RUN, listOf(day(1, easyRun(400))), note = "Lungo di quasi sette ore.")
        val revision = CoachTable.revision(week, huge, monday, budget, profile, availability)

        val run = revision.plan.day(monday.plusDays(1))!!.sessions.single { it.sport == Sport.RUN }
        assertTrue(run.durationMin < 400, "the regia shortened it: ${run.durationMin}")
        assertEquals(listOf(TableVoice.RUN, TableVoice.REGIA), revision.plan.table.map { it.voice }.distinct())
        assertEquals("Modifica chiesta in chat: Lungo di quasi sette ore.", revision.plan.table.first { it.voice == TableVoice.RUN }.text)
        assertTrue(revision.refused.isEmpty())
    }

    // ---- What the coach is told --------------------------------------------------

    @Test
    fun `the coach is told its days as a limit of its own, and what to do when asked for another`() {
        val days = SportCoachPrompts.days(Sport.RUN, availability)
        assertContains(days, "Giorni in cui l'atleta può fare corsa: mar, sab; giovedì è il suo giorno di riposo.")
        assertContains(days, "Profilo › Giorni, orari e gara")
        // Without days of its own a sport fits every day but the rest day.
        assertContains(SportCoachPrompts.days(Sport.RUN, availability.copy(run = SportSlot())), "tutti i giorni, tranne giovedì, il suo giorno di riposo.")
        assertContains(SportCoachPrompts.days(Sport.RUN, TrainingAvailability()), "può fare corsa: tutti i giorni.")

        val turn = SportCoachPrompts.chatTurn(
            Sport.RUN, "dati", monday, monday, null, TriRegia.describe(budget, availability), budget, null,
            "Spostami la corsa a domenica", availability = availability,
        )
        assertContains(turn.substringBefore("## Regia della settimana"), days)
        assertContains(SportCoachPrompts.system(Sport.RUN), "non prometterlo e non scrivere che sposti lì la seduta")
        assertContains(SportCoachPrompts.system(Sport.SWIM), "Quello che scrivi in \"reply\" deve corrispondere a quello che metti in \"proposal\"")
        // A coach in pause proposes nothing: it has no days to be told.
        val paused = SportCoachPrompts.chatTurn(Sport.BIKE, "dati", monday, monday, null, "", budget, null, "Ciao", availability = availability)
        assertFalse(paused.contains("Giorni in cui l'atleta può fare bici"))
    }

    @Test
    fun `NutriAI does not send the athlete to a calendar that is not there`() {
        assertContains(CoachPrompts.NUTRI_SYSTEM, "non c'è un calendario")
        assertContains(CoachPrompts.NUTRI_SYSTEM, "chiederlo al coach di quello sport")
    }

    // ---- How the athlete is: one story -------------------------------------------

    private fun recoveryDay(light: RecoveryLight) = RecoveryDay(
        date = monday, phase = RecoveryPhase.FULL, score = 9, smoothedScore = 9, light = light, level = 0, pattern = LoadResponsePattern.BASELINE,
        confidence = 0.9, deviations = emptyMap(), contributions = emptyMap(), load = null, missing = emptySet(),
    )

    @Test
    fun `the check-in reaches the coaches on its real scale, with the number the athlete sees`() {
        assertEquals(5.0, CheckIn(monday, 1, 1, 1, 1, 1).feel)
        assertEquals(1.0, CheckIn(monday, 7, 7, 7, 7, 7).feel)
        // A middling morning: 4 out of 7 on sleep and will to train, 3 on the rest. Oggi shows 3 out of 5.
        val checkIn = CheckIn(monday, sleepQuality = 4, fatigue = 3, soreness = 3, stress = 3, motivation = 4)
        val today = recoveryDay(RecoveryLight.GREEN)
        val report = RecoveryReport(today, listOf(today), emptyList(), emptyList(), emptyList(), runLoadJump = false, latestWellness = null)
        val text = AthleteContext.describe(AthleteData(monday, ZoneOffset.UTC, profile, emptyList(), recovery = report, checkIn = checkIn))

        assertContains(text, "ogni voce da 1 = molto bene a 7 = molto male")
        assertFalse(text.contains("7 = benissimo"), "the scale was told upside down")
        assertContains(text, "sonno 4, fatica 3, dolori 3, stress 3, poca voglia di allenarsi 4")
        assertContains(text, "«Come ti senti 3 su 5»")
        // And the judgement is Oggi's, so the chat cannot say «parecchia fatica» over a card that says «hai recuperato bene».
        assertContains(text, "Giudizio dell'app, quello che l'atleta legge in Oggi: «Hai recuperato bene»")
    }

    @Test
    fun `how the athlete is, in Oggi's words and without what to do`() {
        assertEquals("Hai recuperato bene e il carico è leggero", TodayAdvice.state(recoveryDay(RecoveryLight.GREEN), FormZone.NEUTRAL))
        assertEquals("Il corpo mostra fatica accumulata", TodayAdvice.state(recoveryDay(RecoveryLight.ORANGE), FormZone.NEUTRAL))
        assertEquals("I segnali di fatica durano da giorni", TodayAdvice.state(recoveryDay(RecoveryLight.RED), null))
        assertEquals("Hai recuperato bene, ma il carico delle ultime settimane risulta alto", TodayAdvice.state(recoveryDay(RecoveryLight.GREEN), FormZone.OVERLOAD, loadInDoubt = true))
        assertEquals("", TodayAdvice.state(null, null))
    }

    // ---- The profile changed, or did it ------------------------------------------

    @Test
    fun `a session that only comes out a minute apart is not a change to tell`() {
        fun saved(minutes: Int) = week.copy(
            days = week.days.map { d -> d.copy(sessions = d.sessions.map { if (it.sport == Sport.RUN) it.copy(durationMin = minutes) else it }) },
        )
        // Written as 39 minutes, worked out again as 40: the week stays as it is and nobody is told.
        assertNull(CoachTable.realign(saved(39), monday, budget, profile, availability))
        assertNull(CoachTable.realign(saved(44), monday, budget, profile, availability))
        // Five minutes and more are worth a line.
        val revision = assertNotNull(CoachTable.realign(saved(30), monday, budget, profile, availability))
        assertEquals(listOf(30 to 40), revision.changes.map { it.before!!.durationMin to it.after!!.durationMin })
    }
}
