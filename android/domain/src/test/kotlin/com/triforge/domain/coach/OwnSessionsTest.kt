package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.ActivitySource
import com.triforge.domain.AthleteProfile
import com.triforge.domain.OtherKind
import com.triforge.domain.RaceGoal
import com.triforge.domain.Sport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The athlete's own sessions: the gym, the sports on hold and the sports
 * done with a trainer, in the regia, at the coaches' table and in the week
 * as it went.
 */
class OwnSessionsTest {

    private val monday = LocalDate.of(2026, 11, 23)
    private val zone: ZoneId = ZoneOffset.UTC

    private fun activity(sport: Sport, day: LocalDate, minutes: Int, kind: OtherKind = OtherKind.GENERIC, hour: Int = 7) = Activity(
        id = "$sport-$day-$minutes", source = ActivitySource.GARMIN, sport = sport, name = "",
        startTime = day.atTime(hour, 0).toInstant(ZoneOffset.UTC), durationSec = minutes * 60L, distanceMeters = 0.0, kind = kind,
    )

    /** Four weeks of pool and gym three times a week, and one short run in all. */
    private val history: List<Activity> = (1..4).flatMap { week ->
        val start = monday.minusWeeks(week.toLong())
        listOf(0L, 2L, 4L).flatMap { day ->
            listOf(activity(Sport.SWIM, start.plusDays(day), 60), activity(Sport.STRENGTH, start.plusDays(day), 60, hour = 18))
        }
    } + activity(Sport.RUN, monday.minusDays(5), 30)

    private val recent = RecentTraining.of(history, monday, zone)
    private val profile = AthleteProfile(goal = RaceGoal.HALF, raceDate = LocalDate.of(2027, 6, 7), weeklyHours = 9.0, thresholdPaceSecPerKm = 265, maxHr = 185)

    private val gymDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    /** Pool and gym, the bike on hold, the run now and then: the AI coaches write swim and run. */
    private val coached = TrainingAvailability(
        swim = SportSlot(gymDays + DayOfWeek.SATURDAY, LocalTime.of(7, 0)),
        run = SportSlot(setOf(DayOfWeek.TUESDAY), LocalTime.of(12, 30)),
        restDay = DayOfWeek.THURSDAY,
        gym = GymSlot(enabled = true, days = gymDays, time = LocalTime.of(18, 30)),
        paused = setOf(Sport.BIKE),
    )

    /** The same athlete, swimming with their own trainer. */
    private val withTrainer = coached.withMode(Sport.SWIM, SportMode.OWN)

    private fun budget(availability: TrainingAvailability) = TriRegia.budget(profile, availability, recent, monday)

    private fun easyRun(minutes: Int) = PlannedSession(
        Sport.RUN, "Corsa facile", 0, focus = "Facile",
        steps = listOf(WorkoutStep(WorkoutBlock.MAIN, 1, null, minutes * 60, "Z2", 0)),
    )

    private fun day(offset: Long, vararg sessions: PlannedSession) = PlannedDay(monday.plusDays(offset), sessions.toList())

    // ---- Board 8 -------------------------------------------------------------

    @Test
    fun `gym, sports on hold and sports with a trainer survive the round trip`() {
        assertEquals(withTrainer, AvailabilityMapper.fromFields(AvailabilityMapper.toFields(withTrainer)))
        assertEquals(SportMode.OWN, withTrainer.mode(Sport.SWIM))
        assertEquals(SportMode.PAUSED, withTrainer.mode(Sport.BIKE))
        assertEquals(SportMode.AI, withTrainer.mode(Sport.RUN))
        assertEquals(listOf(Sport.RUN), withTrainer.coachedSports)
        assertEquals(listOf(Sport.SWIM), withTrainer.ownSports)
        assertTrue(withTrainer.isActive(Sport.SWIM) && !withTrainer.isCoached(Sport.SWIM))
        assertFalse(withTrainer.allows(Sport.BIKE, DayOfWeek.SATURDAY), "a sport on hold fits no day")
        assertEquals(gymDays, coached.gymDays)
        assertEquals(setOf(DayOfWeek.MONDAY), coached.copy(gym = coached.gym.copy(days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))).gymDays, "never on the rest day")
        assertEquals(emptySet(), coached.copy(gym = coached.gym.copy(enabled = false)).gymDays)
    }

    @Test
    fun `a sport is in one mode at a time and the weak sport is one the coaches write`() {
        val weak = TrainingAvailability(weakSport = Sport.SWIM)
        assertNull(weak.withMode(Sport.SWIM, SportMode.OWN).weakSport)
        assertNull(weak.withMode(Sport.SWIM, SportMode.PAUSED).weakSport)
        assertEquals(Sport.SWIM, weak.withMode(Sport.BIKE, SportMode.PAUSED).weakSport)
        val back = withTrainer.withMode(Sport.SWIM, SportMode.PAUSED)
        assertEquals(setOf(Sport.SWIM, Sport.BIKE), back.paused)
        assertTrue(back.ownCoach.isEmpty())
        assertEquals(coached, withTrainer.withMode(Sport.SWIM, SportMode.AI))
        assertEquals(withTrainer, withTrainer.withMode(Sport.STRENGTH, SportMode.PAUSED), "only the three disciplines have a mode")
        // A sport both on hold and with a trainer in the stored map is on hold.
        val both = AvailabilityMapper.fromFields(mapOf("availability" to mapOf("paused" to listOf("swim"), "ownCoach" to listOf("swim", "run", "strength"), "weakSport" to "run")))
        assertEquals(SportMode.PAUSED, both.mode(Sport.SWIM))
        assertEquals(setOf(Sport.RUN), both.ownCoach)
        assertNull(both.weakSport)
    }

    // ---- Budget --------------------------------------------------------------

    @Test
    fun `the gym comes off the hours and a sport on hold leaves its time to the others`() {
        val budget = budget(coached)
        assertEquals(CyclePhase.GENERAL, budget.phase)
        assertEquals(540, budget.availableMinutes)
        assertEquals(180, budget.gymMinutes)
        assertEquals(360, budget.sportMinutesAvailable)
        assertEquals(setOf(Sport.BIKE), budget.paused)
        assertEquals(listOf(Sport.SWIM, Sport.RUN), budget.coached)
        assertEquals(0, budget.sports.getValue(Sport.BIKE).minutes)
        assertEquals(0, budget.sports.getValue(Sport.BIKE).maxQuality)
        // The run has no history: it starts from 90 minutes. What it cannot take goes to the swim, up to +25% on its 180.
        assertEquals(90, budget.sports.getValue(Sport.RUN).minutes)
        assertEquals(225, budget.sports.getValue(Sport.SWIM).minutes)
        assertEquals(315, budget.totalMinutes)
        assertEquals(Sport.RUN, budget.weakSport, "the weak sport is looked for among the active ones")
        assertFalse(budget.bricksAllowed)
    }

    @Test
    fun `a sport with the trainer has its own minutes and no budget`() {
        val budget = budget(withTrainer)
        assertEquals(setOf(Sport.SWIM), budget.own)
        assertEquals(mapOf(Sport.SWIM to 60), budget.ownSessionMinutes, "the median of the last weeks")
        assertEquals(mapOf(Sport.SWIM to 240), budget.ownMinutes, "four days")
        assertEquals(420, budget.fixedMinutes)
        assertEquals(120, budget.sportMinutesAvailable)
        assertEquals(listOf(Sport.RUN), budget.coached)
        assertEquals(0, budget.sports.getValue(Sport.SWIM).minutes)
        assertEquals(90, budget.sports.getValue(Sport.RUN).minutes)
        assertEquals(1.0, budget.sports.getValue(Sport.RUN).share, 1e-9)
        assertEquals(90, budget.totalMinutes)
        assertNull(budget.weakSport, "one sport left to the coaches: nothing to bring up")
        // Without days the regia keeps the time the athlete has been swimming.
        val noDays = budget(withTrainer.copy(swim = SportSlot()))
        assertEquals(mapOf(Sport.SWIM to 180), noDays.ownMinutes)
        // Without history a session with the trainer is taken to last an hour.
        assertEquals(60, TriRegia.ownSessionMinutes(Sport.BIKE, recent))
    }

    @Test
    fun `time is split by share and nobody goes over their cap`() {
        val shares = mapOf(Sport.SWIM to 0.2, Sport.BIKE to 0.5, Sport.RUN to 0.3)
        val roomy = TriRegia.split(300.0, shares, mapOf(Sport.SWIM to 500.0, Sport.BIKE to 500.0, Sport.RUN to 500.0))
        assertEquals(60.0, roomy.getValue(Sport.SWIM), 0.01)
        assertEquals(150.0, roomy.getValue(Sport.BIKE), 0.01)
        val capped = TriRegia.split(300.0, shares, mapOf(Sport.SWIM to 500.0, Sport.BIKE to 50.0, Sport.RUN to 500.0))
        assertEquals(50.0, capped.getValue(Sport.BIKE), 0.01)
        assertEquals(100.0, capped.getValue(Sport.SWIM), 0.5, "the bike's 100 go to the others, 2 to 3")
        assertEquals(150.0, capped.getValue(Sport.RUN), 0.5)
        val full = TriRegia.split(300.0, shares, mapOf(Sport.SWIM to 40.0, Sport.BIKE to 50.0, Sport.RUN to 60.0))
        assertEquals(150.0, full.values.sum(), 0.01, "when everybody is full the rest stays free")
    }

    @Test
    fun `the gym is lighter in the taper and gone in race week`() {
        val tuesday = monday.plusDays(1)
        assertNull(TriRegia.gymSession(coached, CyclePhase.GENERAL, tuesday))
        val usual = TriRegia.gymSession(coached, CyclePhase.GENERAL, monday)!!
        assertEquals(60, usual.durationMin)
        assertTrue(usual.own && usual.sport == Sport.STRENGTH && usual.steps.isEmpty())
        val light = TriRegia.gymSession(coached, CyclePhase.TAPER, monday)!!
        assertEquals(45, light.durationMin)
        assertEquals(Intensity.EASY, light.intensity)
        assertNull(TriRegia.gymSession(coached, CyclePhase.RACE_WEEK, monday))
        assertEquals(0, TriRegia.gymMinutes(coached, CyclePhase.RACE_WEEK, recent))
        assertEquals(180, TriRegia.gymMinutes(coached.copy(gym = coached.gym.copy(days = emptySet())), CyclePhase.GENERAL, recent), "no days: what was done lately")
    }

    // ---- The table -----------------------------------------------------------

    @Test
    fun `the week holds the athlete's own sessions and only the coached sports' proposals`() {
        val budget = budget(withTrainer)
        val proposals = listOf(
            SportProposal(Sport.RUN, listOf(day(1, easyRun(40)))),
            // A coach whose sport is with the trainer, or on hold, writes nothing that counts.
            SportProposal(Sport.SWIM, listOf(day(1, PlannedSession(Sport.SWIM, "Tecnica", 45)))),
            SportProposal(Sport.BIKE, listOf(day(5, PlannedSession(Sport.BIKE, "Lungo", 120)))),
        )
        val plan = CoachTable.merge(monday, monday, emptyList(), proposals, budget, profile, withTrainer)
        assertEquals(listOf(Sport.SWIM, Sport.STRENGTH), plan.days[0].sessions.map { it.sport }, "by the athlete's usual times")
        assertTrue(plan.days[0].sessions.all { it.own })
        assertEquals(TriRegia.OWN_FOCUS, plan.days[0].sessions.first().focus)
        assertEquals(listOf(Sport.RUN), plan.days[1].sessions.map { it.sport })
        assertFalse(plan.days[1].sessions.single().own)
        assertTrue(plan.days[3].sessions.isEmpty(), "Thursday is the rest day")
        assertEquals(listOf(Sport.SWIM), plan.days[5].sessions.map { it.sport })
        assertEquals(monday, plan.plannedFrom)
        assertEquals(emptyList(), TriRegia.check(plan, budget, withTrainer, monday, profile))
        assertEquals(mapOf(Sport.SWIM to (4 to 240), Sport.STRENGTH to (3 to 180), Sport.RUN to (1 to 40)), plan.totals())
        assertEquals(plan, CoachMapper.weekPlanFromFields(CoachMapper.weekPlanToFields(plan)), "own sessions and the first planned day are kept")
    }

    @Test
    fun `a coach's session gives way to the athlete's own, never the other way`() {
        val budget = budget(withTrainer)
        // A run on Monday, with the pool and the gym: three sports in one day.
        val anyDay = withTrainer.copy(run = SportSlot())
        val plan = CoachTable.merge(monday, monday, emptyList(), listOf(SportProposal(Sport.RUN, listOf(day(0, easyRun(40))))), budget, profile, anyDay)
        val conflict = TriRegia.check(plan, budget, anyDay, monday, profile).single()
        assertEquals(RegiaRule.TOO_MANY_SPORTS, conflict.rule)
        assertTrue(conflict.text.contains("nuoto e palestra dell'atleta compresi"), conflict.text)
        val (settled, notes) = CoachTable.settle(plan, budget, anyDay, profile, monday)
        assertEquals(listOf(Sport.SWIM, Sport.STRENGTH), settled.days[0].sessions.map { it.sport })
        assertTrue(notes.single().text.contains("corsa di lunedì"))
        assertEquals(emptyList(), TriRegia.check(settled, budget, anyDay, monday, profile))
    }

    @Test
    fun `what the athlete did of a sport nobody coaches breaks no budget`() {
        val budget = budget(withTrainer)
        // Wednesday: a long swim with the trainer and a ride on a bike that is on hold are already done.
        val wednesday = monday.plusDays(2)
        val past = CoachTable.pastDays(
            listOf(activity(Sport.SWIM, monday, 95), activity(Sport.BIKE, monday.plusDays(1), 120), activity(Sport.OTHER, monday, 50, OtherKind.WALK)),
            monday, wednesday, zone,
        )
        assertEquals(mapOf(Sport.SWIM to 95, Sport.BIKE to 120), CoachTable.doneMinutes(past), "the walk is not a session of the plan")
        val plan = CoachTable.merge(monday, wednesday, past, emptyList(), budget, profile, withTrainer)
        assertEquals(emptyList(), TriRegia.check(plan, budget, withTrainer, wednesday, profile))
        assertEquals(wednesday, plan.plannedFrom)
    }

    @Test
    fun `a week full of the athlete's own sessions has no day the regia can free`() {
        val everyDay = withTrainer.copy(swim = SportSlot(DayOfWeek.entries.toSet()), restDay = null)
        val budget = budget(everyDay)
        val plan = CoachTable.merge(monday, monday, emptyList(), emptyList(), budget, profile, everyDay)
        assertTrue(plan.days.all { day -> day.sessions.any { it.own } })
        assertEquals(emptyList(), TriRegia.check(plan, budget, everyDay, monday, profile).filter { it.rule == RegiaRule.NO_REST_DAY })
        // With a free day in the athlete's own week, a coach that fills it is sent back.
        val sixDays = everyDay.copy(swim = SportSlot(DayOfWeek.entries.toSet() - DayOfWeek.SUNDAY))
        val sixBudget = budget(sixDays)
        val filled = CoachTable.merge(monday, monday, emptyList(), listOf(SportProposal(Sport.RUN, listOf(day(6, easyRun(30))))), sixBudget, profile, sixDays.copy(run = SportSlot()))
        assertTrue(TriRegia.check(filled, sixBudget, sixDays.copy(run = SportSlot()), monday, profile).any { it.rule == RegiaRule.NO_REST_DAY })
        val (settled, _) = CoachTable.settle(filled, sixBudget, sixDays.copy(run = SportSlot()), profile, monday)
        assertTrue(settled.days[6].sessions.isEmpty())
        assertEquals(6, settled.days.count { day -> day.sessions.any { it.own } })
    }

    @Test
    fun `the minutes open with what is not the coaches' to write`() {
        val note = CoachTable.opening(budget(withTrainer), withTrainer)!!
        assertEquals(TableVoice.REGIA, note.voice)
        assertTrue(CoachTable.isOpening(note))
        assertTrue(note.text.startsWith("Punto di partenza: sono già in calendario le tue sedute di nuoto con il tuo allenatore e di palestra"), note.text)
        assertTrue(note.text.contains("Bici in pausa. Al Coach Corsa restano"), note.text)
        // With the swim written by its coach only the gym is the athlete's own, and two coaches share the rest.
        val two = CoachTable.opening(budget(coached), coached)!!.text
        assertTrue(two.contains("le tue sedute di palestra, nessun coach le scrive. Bici in pausa. Ai coach di nuoto e corsa restano"), two)
        // A gym the athlete goes to but gave no days for is not on the calendar: its time is only kept aside.
        val plain = TrainingAvailability(swim = SportSlot(gymDays))
        val aside = CoachTable.opening(budget(plain), plain)!!.text
        assertTrue(aside.startsWith("Punto di partenza: per le tue sedute di palestra, che non hanno giorni fissi"), aside)
        assertFalse(aside.contains("in calendario"), aside)
        // Three AI coaches and no gym: nothing to say.
        val newcomer = TriRegia.budget(profile, plain, RecentTraining.of(emptyList(), monday, zone), monday)
        assertNull(CoachTable.opening(newcomer, plain))
        assertFalse(CoachTable.isOpening(TableNote(TableVoice.REGIA, "Ho spostato la corsa di martedì.")))
    }

    @Test
    fun `a change from a chat leaves the athlete's own sessions where they are`() {
        val budget = budget(withTrainer)
        val week = CoachTable.merge(monday, monday, emptyList(), listOf(SportProposal(Sport.RUN, listOf(day(1, easyRun(40))))), budget, profile, withTrainer).copy(rounds = 1)
        val shorter = SportProposal(Sport.RUN, listOf(day(1, easyRun(30))), note = "Più corta.")
        val revised = CoachTable.revise(week, shorter, monday, budget, profile, withTrainer)
        assertEquals(30, revised.days[1].sessions.single().durationMin)
        assertEquals(week.days[0], revised.days[0])
        assertEquals(week.days.flatMap { it.sessions }.count { it.own }, revised.days.flatMap { it.sessions }.count { it.own }, "none lost, none doubled")
        // The coach of a sport that is not its to write changes nothing.
        assertEquals(week, CoachTable.revise(week, SportProposal(Sport.SWIM, listOf(day(1, PlannedSession(Sport.SWIM, "Tecnica", 45)))), monday, budget, profile, withTrainer))
    }

    // ---- Nothing in the past, nothing unannounced ----------------------------

    @Test
    fun `once the evening is gone the week is planned from tomorrow`() {
        assertEquals(monday, CoachTable.planningDay(monday, LocalTime.of(20, 59)))
        assertEquals(monday.plusDays(1), CoachTable.planningDay(monday, LocalTime.of(21, 0)))
        assertEquals(monday.plusDays(1), CoachTable.planningDay(monday, LocalTime.of(23, 5)))
    }

    @Test
    fun `the gym is not put on a day whose gym time is already gone`() {
        val budget = budget(coached)
        val late = CoachTable.merge(monday, monday, emptyList(), emptyList(), budget, profile, coached, now = LocalTime.of(19, 0))
        assertTrue(late.days[0].sessions.none { it.sport == Sport.STRENGTH }, "it is 19:00 and the gym was at 18:30")
        assertEquals(1, late.days[2].sessions.count { it.sport == Sport.STRENGTH }, "Wednesday is still to come")
        val early = CoachTable.merge(monday, monday, emptyList(), emptyList(), budget, profile, coached, now = LocalTime.of(12, 0))
        assertEquals(1, early.days[0].sessions.count { it.sport == Sport.STRENGTH })
        // A gym session that was already on today's calendar stays there.
        val kept = CoachTable.merge(monday, monday, emptyList(), emptyList(), budget, profile, coached, now = LocalTime.of(19, 0), keptToday = early.days[0].sessions)
        assertEquals(1, kept.days[0].sessions.count { it.sport == Sport.STRENGTH })
    }

    @Test
    fun `accepting a change says everything it does to the week`() {
        val budget = budget(coached)
        val week = CoachTable.merge(monday, monday, emptyList(), listOf(SportProposal(Sport.RUN, listOf(day(1, easyRun(40))))), budget, profile, coached).copy(rounds = 1)
        val shorter = SportProposal(Sport.RUN, listOf(day(1, easyRun(30))), note = "Più corta.")
        val revision = CoachTable.revision(week, shorter, monday, budget, profile, coached)

        val change = revision.changes.single()
        assertEquals(PlanChange.Kind.CHANGED, change.kind)
        assertEquals(40 to 30, change.before!!.durationMin to change.after!!.durationMin)
        assertTrue(revision.sideEffects(Sport.RUN).isEmpty(), "nothing else moves")
        assertTrue(revision.notes.isEmpty())
        assertEquals(revision.plan, CoachTable.revise(week, shorter, monday, budget, profile, coached))
    }

    @Test
    fun `a change accepted after the profile changed lists what the profile takes away`() {
        // The week was written with bike and run; then the athlete put the run in pause.
        val before = coached.copy(paused = emptySet())
        val week = CoachTable.merge(
            monday, monday, emptyList(),
            listOf(SportProposal(Sport.RUN, listOf(day(1, easyRun(40)))), SportProposal(Sport.SWIM, listOf(day(5, PlannedSession(Sport.SWIM, "Tecnica", 45))))),
            budget(before), profile, before,
        ).copy(rounds = 1)
        val paused = before.copy(paused = setOf(Sport.RUN, Sport.BIKE))
        val longer = SportProposal(Sport.SWIM, listOf(day(5, PlannedSession(Sport.SWIM, "Tecnica e fondo", 60))))
        val revision = CoachTable.revision(week, longer, monday, budget(paused), profile, paused)

        val side = revision.sideEffects(Sport.SWIM)
        assertEquals(listOf(PlanChange.Kind.REMOVED to Sport.RUN), side.map { it.kind to it.sport })
        assertTrue(revision.notes.any { it.contains("corsa") && it.contains("pausa") }, revision.notes.toString())
        assertTrue(revision.plan.table.any { it.voice == TableVoice.REGIA && it.text.contains("pausa") })
    }

    @Test
    fun `changing the profile brings the week in line without any coach`() {
        val before = coached.copy(paused = emptySet(), gym = GymSlot())
        val week = CoachTable.merge(
            monday, monday, emptyList(),
            listOf(SportProposal(Sport.RUN, listOf(day(1, easyRun(40)))), SportProposal(Sport.SWIM, listOf(day(5, PlannedSession(Sport.SWIM, "Tecnica", 45))))),
            budget(before), profile, before,
        ).copy(rounds = 1)
        assertNull(CoachTable.realign(week, monday, budget(before), profile, before), "nothing changed, nothing to do")

        val now = coached.copy(paused = setOf(Sport.RUN, Sport.BIKE))
        val revision = CoachTable.realign(week, monday, budget(now), profile, now, now = LocalTime.of(19, 0))
        assertNotNull(revision)
        assertTrue(revision.plan.days.flatMap { it.sessions }.none { it.sport == Sport.RUN })
        assertEquals(45, revision.plan.days[5].sessions.single { it.sport == Sport.SWIM }.durationMin, "the swim coach's session stays as written")
        // The gym comes in on its days still to come, not on today at 19:00.
        assertEquals(listOf(2, 4), revision.plan.days.indices.filter { i -> revision.plan.days[i].sessions.any { it.sport == Sport.STRENGTH } })
        assertTrue(revision.notes.any { it.contains("corsa") } && revision.notes.any { it.contains("sedute fisse") }, revision.notes.toString())
        assertNull(CoachTable.realign(revision.plan, monday, budget(now), profile, now, now = LocalTime.of(19, 0)), "once in line it stays so")
    }

    // ---- Planned and done ----------------------------------------------------

    private val week = CoachTable.merge(monday, monday, emptyList(), listOf(SportProposal(Sport.RUN, listOf(day(1, easyRun(40))))), budget(withTrainer), profile, withTrainer)

    @Test
    fun `the week says what was done, what was missed and what was not in the programme`() {
        val wednesday = monday.plusDays(2)
        val done = listOf(
            activity(Sport.SWIM, monday, 62),
            // No gym on Monday. On Tuesday the run, four minutes short, and a walk.
            activity(Sport.RUN, monday.plusDays(1), 36),
            activity(Sport.OTHER, monday.plusDays(1), 38, OtherKind.WALK, hour = 18),
            activity(Sport.OTHER, monday.plusDays(1), 3, OtherKind.WALK, hour = 20),
            activity(Sport.SWIM, wednesday, 58),
            // Tomorrow's recordings do not exist yet; one dated in the future is not shown.
            activity(Sport.RUN, monday.plusDays(4), 30),
        )
        val review = WeekReview.of(week, done, wednesday, zone)
        assertEquals(listOf(ReviewState.DONE, ReviewState.MISSED), review.days[0].items.map { it.state })
        assertEquals(62, review.days[0].items[0].doneMin)
        assertFalse(review.days[0].items[0].differs, "two minutes are not worth a line")
        assertEquals(listOf(ReviewState.DONE, ReviewState.EXTRA), review.days[1].items.map { it.state })
        val run = review.days[1].items[0]
        assertEquals(36 to 40, run.doneMin to run.plannedMin)
        assertFalse(run.differs)
        assertEquals(Sport.OTHER, review.days[1].items[1].sport)
        assertEquals(listOf(ReviewState.DONE, ReviewState.TODO), review.days[2].items.map { it.state }, "today's gym is still to do, not missed")
        assertTrue(review.days[3].items.isEmpty())
        assertEquals(listOf(ReviewState.TODO, ReviewState.TODO), review.days[4].items.map { it.state })
        assertEquals(62 + 36 + 38 + 58, review.doneMinutes)
        assertEquals(listOf(monday to week.days[0].sessions[1]), review.missed)
        assertTrue(review.hasPast)

        val text = review.describe(wednesday)
        assertTrue(text.contains("lunedì $monday: nuoto Tuo allenatore (seduta dell'atleta): previsti 60 min, fatti 62 min"), text)
        assertTrue(text.contains("palestra Forza di 60 min (seduta dell'atleta): non fatta"), text)
        assertTrue(text.contains("fuori programma: camminata, 38 min"), text)
        assertFalse(text.contains("venerdì"))
        assertEquals("", WeekReview.of(week, emptyList(), monday, zone).describe(monday))
    }

    @Test
    fun `a session done for a very different time is told apart, and two of a sport are matched by length`() {
        val plan = week.copy(days = week.days.mapIndexed { i, d -> if (i == 0) d.copy(sessions = listOf(PlannedSession(Sport.RUN, "Lungo", 90), PlannedSession(Sport.RUN, "Sciolta", 20))) else d })
        val review = WeekReview.of(plan, listOf(activity(Sport.RUN, monday, 22, hour = 7), activity(Sport.RUN, monday, 70, hour = 18)), monday.plusDays(1), zone)
        val (long, short) = review.days[0].items
        assertEquals(70, long.doneMin)
        assertTrue(long.differs)
        assertEquals(22, short.doneMin)
        assertFalse(short.differs)
    }

    @Test
    fun `days before the week was written show what was done, not a programme`() {
        val wednesday = monday.plusDays(2)
        val activities = listOf(activity(Sport.SWIM, monday, 60), activity(Sport.OTHER, monday.plusDays(1), 45, OtherKind.HIKE))
        val past = CoachTable.pastDays(activities, monday, wednesday, zone)
        val late = CoachTable.merge(monday, wednesday, past, emptyList(), budget(withTrainer), profile, withTrainer)
        val review = WeekReview.of(late, activities, wednesday, zone)
        assertEquals(listOf(ReviewState.RECORD), review.days[0].items.map { it.state })
        assertEquals(listOf(ReviewState.RECORD), review.days[1].items.map { it.state })
        assertTrue(review.missed.isEmpty())
        // A plan saved before the first planned day was kept: its past days are told by the copies of what was done.
        val old = late.copy(plannedFrom = null)
        assertEquals(listOf(ReviewState.RECORD), WeekReview.of(old, activities, wednesday, zone).days[0].items.map { it.state })
        assertEquals(listOf(ReviewState.EXTRA), WeekReview.of(old, activities, wednesday, zone).days[1].items.map { it.state }, "nothing was copied there: it reads as a planned rest day")
    }

    @Test
    fun `a session missed on a day with another training reads as replaced`() {
        val wednesday = monday.plusDays(2)
        val done = listOf(
            // Tuesday: no run, but an hour and a half in the gym, and a short walk.
            activity(Sport.STRENGTH, monday.plusDays(1), 88),
            activity(Sport.OTHER, monday.plusDays(1), 25, OtherKind.WALK, hour = 18),
        )
        val tuesday = WeekReview.of(week, done, wednesday, zone).days[1]
        assertEquals(listOf(ReviewState.REPLACED, ReviewState.EXTRA), tuesday.items.map { it.state })
        assertEquals(Sport.STRENGTH, tuesday.items[0].activity?.sport)
        assertEquals(Sport.RUN, tuesday.items[0].sport, "the line stays the planned run")
        assertFalse(tuesday.items[0].differs)
        assertTrue("non fatta, al suo posto palestra, 88 min" in WeekReview.of(week, done, wednesday, zone).describe(wednesday))
        // A walk alone does not stand for a run, and today's session is still to do.
        val walked = WeekReview.of(week, done.drop(1), wednesday, zone).days[1]
        assertEquals(listOf(ReviewState.MISSED, ReviewState.EXTRA), walked.items.map { it.state })
        val today = WeekReview.of(week, done, monday.plusDays(1), zone).days[1]
        assertEquals(listOf(ReviewState.TODO, ReviewState.EXTRA, ReviewState.EXTRA), today.items.map { it.state })
    }

    @Test
    fun `writing the week again keeps what was planned for the days gone`() {
        val wednesday = monday.plusDays(2)
        val activities = listOf(activity(Sport.SWIM, monday, 62))
        val past = CoachTable.pastDays(activities, monday, wednesday, zone)
        val again = CoachTable.merge(monday, wednesday, past, listOf(SportProposal(Sport.RUN, listOf(day(4, easyRun(30))))), budget(withTrainer), profile, withTrainer.copy(run = SportSlot()))
        assertEquals(listOf(CoachTable.DONE), again.days[0].sessions.map { it.focus }, "while the regia checks, the days gone are what was done")
        val kept = CoachTable.keepPlanned(again, week, wednesday)
        assertEquals(week.days[0], kept.days[0])
        assertEquals(week.days[1], kept.days[1], "the run that was planned and missed stays in the week")
        assertEquals(again.days[4], kept.days[4])
        assertEquals(monday, kept.plannedFrom)
        assertEquals(listOf(ReviewState.MISSED), WeekReview.of(kept, activities, wednesday, zone).days[1].items.map { it.state })
        // Nothing to keep from another week, or from a plan that never said when it started.
        assertEquals(again, CoachTable.keepPlanned(again, week.copy(weekStart = monday.minusWeeks(1)), wednesday))
        assertEquals(again, CoachTable.keepPlanned(again, week.copy(plannedFrom = null), wednesday))
        assertEquals(again, CoachTable.keepPlanned(again, null, wednesday))
    }

    // ---- Words ---------------------------------------------------------------

    @Test
    fun `the coaches are told about the gym, the trainer and the sports on hold`() {
        val budget = budget(withTrainer)
        val regia = TriRegia.describe(budget, withTrainer)
        assertTrue(regia.contains("Sport in pausa: bici"), regia)
        assertTrue(regia.contains("Palestra (forza, la gestisce l'atleta"), regia)
        assertTrue(regia.contains("Nuoto (lo segue l'allenatore dell'atleta"), regia)
        assertTrue(regia.contains("240 minuti nella settimana"), regia)
        assertTrue(regia.contains("Quote e minuti per sport: corsa 100% (90 min"), regia)
        assertTrue(regia.contains("sedute scritte dai coach (corsa): 90 minuti"), regia)
        assertFalse(regia.contains("brick"), "no bike, no brick to talk about")
        assertFalse(regia.contains("Nuoto: solo"), "the swim days are the trainer's, not a coach's to fill")

        val context = AthleteContext.describe(AthleteData(monday, zone, profile, history, availability = withTrainer))
        assertTrue(context.contains("Palestra (forza, programma suo): lun, mer, ven alle 18:30"), context)
        assertTrue(context.contains("Nuoto: lo fa con il suo allenatore, di persona (lun, mer, ven, sab alle 07:00)"), context)
        assertTrue(context.contains("Sport che non pratica in questo periodo: bici"), context)
        assertTrue(context.contains("palestra 3×"), context)

        val review = SportCoachPrompts.reviewRequest(Sport.SWIM, context, monday, regia, null, "lunedì: nuoto fatto")
        assertTrue(review.contains("\"days\" vuoto"))
        assertTrue(review.contains("## Previsto e fatto finora questa settimana\nlunedì: nuoto fatto"))
        val chat = SportCoachPrompts.chatTurn(Sport.SWIM, context, monday, monday, null, regia, budget, null, "Com'è andata ieri?")
        assertTrue(chat.contains("con il suo allenatore"))
        assertFalse(chat.contains("Minuti di nuoto nella settimana"), "no budget for a sport the coach does not write")
        val paused = SportCoachPrompts.chatTurn(Sport.BIKE, context, monday, monday, null, regia, budget, null, "Riprendo?")
        assertTrue(paused.contains("Il tuo sport è in pausa"))
        val run = SportCoachPrompts.weekRequest(Sport.RUN, context, monday, monday, regia, budget, 0, null, review = "martedì: corsa non fatta")
        assertTrue(run.contains("Minuti di corsa nella settimana: 90."))
        assertTrue(run.contains("martedì: corsa non fatta"))
        assertTrue(SportCoachPrompts.system(Sport.RUN).contains("seguito dall'allenatore dell'atleta"))

        val described = CoachPrompts.describe(week)
        assertTrue(described.contains("nuoto Nuoto (con il suo allenatore, contenuto non noto) 60′"), described)
        assertTrue(described.contains("palestra Palestra (la gestisce l'atleta) 60′"), described)
    }

    // ---- NutriAI -------------------------------------------------------------

    @Test
    fun `the day's energy counts what was recorded outside the plan, once`() {
        val planned = week.days[0].sessions
        val done = listOf(
            activity(Sport.SWIM, monday, 62),
            activity(Sport.OTHER, monday, 50, OtherKind.HIKE),
            activity(Sport.OTHER, monday, 8, OtherKind.WALK),
            activity(Sport.RUN, monday, 25),
        )
        val counted = NutriEnergy.withDone(planned, done)
        assertEquals(listOf(Sport.SWIM, Sport.STRENGTH, Sport.OTHER, Sport.RUN), counted.map { it.sport }, "the swim is counted as planned, the short walk not at all")
        assertEquals(Intensity.TEMPO, counted[2].intensity)
        assertEquals(50, counted[2].durationMin)
        assertEquals(planned, NutriEnergy.withDone(planned, emptyList()))
        val weight = 72.0
        assertTrue(NutriEnergy.trainingKcal(counted, weight) > NutriEnergy.trainingKcal(planned, weight) + 300)
        assertEquals((NutriEnergy.met(Sport.STRENGTH, Intensity.TEMPO) - 1) * weight, NutriEnergy.trainingKcal(listOf(planned[1]), weight), 0.01)
        // An easy walk does not turn a rest day into a training day.
        assertEquals(DayType.REST, NutriEnergy.dayType(NutriEnergy.withDone(emptyList(), listOf(activity(Sport.OTHER, monday, 40, OtherKind.WALK)))))
        assertEquals(DayType.LIGHT, NutriEnergy.dayType(listOf(planned[1])))
    }

    @Test
    fun `after the gym there is always protein`() {
        val gym = TriRegia.gymSession(coached, CyclePhase.GENERAL, monday)!!
        val effort = NutriFueling.plan(listOf(gym), 72.0).single()
        assertEquals(22, effort.afterProteinG)
        assertTrue(NutriFueling.describe(listOf(gym), 72.0).contains("22 g di proteine"))
    }
}
