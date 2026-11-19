package com.triforge.domain.coach

import com.triforge.domain.AthleteProfile
import com.triforge.domain.RaceGoal
import com.triforge.domain.Sport
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoachTableTest {

    private val monday = LocalDate.of(2026, 11, 23)
    private val profile = AthleteProfile(
        goal = RaceGoal.HALF, weeklyHours = 9.0, maxHr = 185,
        cssSecPer100m = 98, ftpWatts = 245, thresholdPaceSecPerKm = 265,
    )

    private fun step(block: WorkoutBlock, reps: Int, zone: String, metres: Int? = null, seconds: Int? = null, recovery: Int = 0) =
        WorkoutStep(block, reps, metres, seconds, zone, recovery)

    // ---- Zones ---------------------------------------------------------------

    @Test
    fun `zones are read and turned into the athlete's numbers`() {
        assertEquals("Z4", Zones.read(Sport.RUN, "z4 soglia"))
        assertEquals("B2", Zones.read(Sport.SWIM, "zona B2"))
        assertEquals(null, Zones.read(Sport.SWIM, "Z4"))
        assertEquals("223 – 257 W", Zones.target(Sport.BIKE, "Z4", profile))
        assertEquals("4:22 – 4:38 /km", Zones.target(Sport.RUN, "Z4", profile))
        assertEquals("1:38 – 1:43 /100 m", Zones.target(Sport.SWIM, "B2", profile))
        assertEquals("152 – 167 bpm", Zones.target(Sport.BIKE, "Z4", profile.copy(ftpWatts = null)))
        assertEquals("", Zones.target(Sport.RUN, "Z2", AthleteProfile()))
        assertTrue(Zones.isHigh("C1") && Zones.isHigh("Z4") && !Zones.isHigh("B1"))
    }

    // ---- Workouts ------------------------------------------------------------

    private val repeats = PlannedSession(
        Sport.RUN, "Ripetute", 0, Intensity.EASY, focus = "Ripetute",
        steps = listOf(
            step(WorkoutBlock.MAIN, 6, "Z4", metres = 800, recovery = 120),
            step(WorkoutBlock.WARMUP, 1, "z2", seconds = 900),
            step(WorkoutBlock.COOLDOWN, 1, "Z1", seconds = 600),
        ),
    )

    @Test
    fun `the app orders the blocks, adds targets and recomputes the totals`() {
        val done = Workouts.finalize(repeats, profile)
        assertEquals(listOf(WorkoutBlock.WARMUP, WorkoutBlock.MAIN, WorkoutBlock.COOLDOWN), done.steps.map { it.block })
        assertEquals("Z2", done.steps.first().zone)
        assertTrue(done.steps.all { it.target.contains("/km") })
        // 15' + 6 × (800 m at about 4:30 /km = 3'36") + 6 × 2' + 10'
        assertTrue(done.durationMin in 57..60, "minutes: ${done.durationMin}")
        assertEquals(Intensity.THRESHOLD, done.intensity)
        assertTrue(Workouts.highMinutes(done, profile) in 21..22)
        assertEquals(listOf("Z1", "Z2", "Z4"), Workouts.zoneMinutes(done, profile).keys.toList())
        assertTrue(Workouts.distanceM(done, profile)!! in 9_000..10_500)
        assertEquals("6 × 800 m Z4, rec. 2′", Workouts.describe(done.steps[1]))
    }

    @Test
    fun `a rep too long for its zone is split`() {
        val swim = PlannedSession(Sport.SWIM, "Soglia", 0, steps = listOf(step(WorkoutBlock.MAIN, 4, "C1", metres = 300, recovery = 20)))
        val split = Workouts.finalize(swim, profile).steps.single()
        assertEquals(8, split.reps)
        assertEquals(150, split.distanceM)
        assertTrue(split.note.isNotBlank())
        val bike = PlannedSession(Sport.BIKE, "Soglia", 0, steps = listOf(step(WorkoutBlock.MAIN, 1, "Z4", seconds = 2400)))
        val halves = Workouts.finalize(bike, profile).steps.single()
        assertEquals(2, halves.reps)
        assertEquals(1200, halves.durationSec)
        val easy = PlannedSession(Sport.BIKE, "Lungo", 0, steps = listOf(step(WorkoutBlock.MAIN, 1, "Z2", seconds = 3 * 3600)))
        assertEquals(1, Workouts.finalize(easy, profile).steps.single().reps)
        assertEquals(180, Workouts.finalize(easy, profile).durationMin)
    }

    @Test
    fun `sessions can be trimmed, softened and shortened`() {
        val done = Workouts.finalize(repeats, profile)
        val trimmed = Workouts.trimHigh(done, 7, profile)
        assertEquals(4, trimmed.steps[1].reps)
        assertTrue(trimmed.durationMin < done.durationMin)
        val soft = Workouts.soften(done, profile)
        assertEquals(0, Workouts.highMinutes(soft, profile))
        assertFalse(TriRegia.isHard(soft))
        val short = Workouts.shorten(done, done.durationMin - 10, profile)
        assertTrue(short.durationMin <= done.durationMin - 9)
        assertEquals(6, short.steps[1].reps, "the hard set stays")
    }

    // ---- Table ---------------------------------------------------------------

    private val budget = WeekBudget(
        phase = CyclePhase.GENERAL, availableMinutes = 540, totalMinutes = 525, maxHardSessions = 3,
        sports = mapOf(
            Sport.SWIM to SportBudget(Sport.SWIM, 0.14, 100, 60, 2),
            Sport.BIKE to SportBudget(Sport.BIKE, 0.54, 255, 180, 2),
            Sport.RUN to SportBudget(Sport.RUN, 0.32, 170, 80, 2),
        ),
        weakSport = Sport.BIKE, weakFromData = false, bricksAllowed = false, hoursEstimated = false,
    )

    private fun session(sport: Sport, vararg steps: WorkoutStep) = PlannedSession(sport, "x", 0, steps = steps.toList())
    private fun day(offset: Long, vararg sessions: PlannedSession) = PlannedDay(monday.plusDays(offset), sessions.toList())
    private fun easyRun(minutes: Int) = session(Sport.RUN, step(WorkoutBlock.MAIN, 1, "Z2", seconds = minutes * 60))
    private fun hardRun() = session(Sport.RUN, step(WorkoutBlock.WARMUP, 1, "Z2", seconds = 900), step(WorkoutBlock.MAIN, 5, "Z4", seconds = 240, recovery = 120))
    private fun hardBike() = session(Sport.BIKE, step(WorkoutBlock.WARMUP, 1, "Z2", seconds = 1200), step(WorkoutBlock.MAIN, 3, "Z4", seconds = 720, recovery = 240))
    private fun longBike() = session(Sport.BIKE, step(WorkoutBlock.MAIN, 1, "Z2", seconds = 150 * 60))
    private fun swim(zone: String) = session(Sport.SWIM, step(WorkoutBlock.WARMUP, 1, "A1", metres = 400), step(WorkoutBlock.MAIN, 10, zone, metres = 100, recovery = 20))

    private fun proposals(runQualityDay: Long) = listOf(
        SportProposal(Sport.SWIM, listOf(day(0, swim("B2")), day(1, swim("A2"))), note = "Soglia lunedì a gambe fresche."),
        SportProposal(Sport.BIKE, listOf(day(2, hardBike()), day(5, longBike())), note = "Lungo sabato."),
        SportProposal(Sport.RUN, listOf(day(1, easyRun(40)), day(runQualityDay, hardRun()), day(6, easyRun(70)))),
    )

    @Test
    fun `the weak sport comes first, then the shares`() {
        assertEquals(listOf(Sport.BIKE, Sport.RUN, Sport.SWIM), CoachTable.priority(budget))
        assertEquals(listOf(Sport.BIKE, Sport.RUN, Sport.SWIM), CoachTable.priority(budget.copy(weakSport = null)))
        assertEquals(Sport.SWIM, CoachTable.priority(budget.copy(weakSport = Sport.SWIM)).first())
    }

    @Test
    fun `proposals that agree become the week untouched`() {
        val plan = CoachTable.merge(monday, monday, emptyList(), proposals(4), budget, profile, TrainingAvailability())
        assertEquals(7, plan.days.size)
        assertEquals(listOf(Sport.SWIM, Sport.RUN), plan.days[1].sessions.map { it.sport })
        assertTrue(plan.days[3].sessions.isEmpty())
        assertEquals(emptyList(), TriRegia.check(plan, budget, TrainingAvailability(), monday, profile))
        val (settled, notes) = CoachTable.settle(plan, budget, TrainingAvailability(), profile, monday)
        assertEquals(plan, settled)
        assertTrue(notes.isEmpty())
        assertEquals(540, plan.availableMin)
    }

    @Test
    fun `two hard days in a row are settled in favour of the sport nearer the goal`() {
        // Run repeats on Thursday, right after the bike threshold of Wednesday.
        val plan = CoachTable.merge(monday, monday, emptyList(), proposals(3), budget, profile, TrainingAvailability())
        val conflicts = TriRegia.check(plan, budget, TrainingAvailability(), monday, profile)
        assertTrue(conflicts.any { it.rule == RegiaRule.HARD_IN_A_ROW })
        val (settled, notes) = CoachTable.settle(plan, budget, TrainingAvailability(), profile, monday)
        assertEquals(emptyList(), TriRegia.check(settled, budget, TrainingAvailability(), monday, profile))
        assertTrue(TriRegia.isHard(settled.days[2].sessions.single()), "the bike, the weak sport, keeps its threshold")
        assertFalse(TriRegia.isHard(settled.days[3].sessions.single()))
        assertTrue(notes.single().text.contains("corsa di giovedì"))
        assertEquals(TableVoice.REGIA, notes.single().voice)
    }

    @Test
    fun `days the athlete cannot do, the rest day and long sessions are settled`() {
        val availability = TrainingAvailability(swim = SportSlot(setOf(DayOfWeek.MONDAY)), restDay = DayOfWeek.SUNDAY)
        val tight = budget.copy(sports = budget.sports + (Sport.BIKE to SportBudget(Sport.BIKE, 0.54, 255, 120, 2)))
        val plan = CoachTable.merge(monday, monday, emptyList(), proposals(4), tight, profile, availability)
        val (settled, notes) = CoachTable.settle(plan, tight, availability, profile, monday)
        assertEquals(emptyList(), TriRegia.check(settled, tight, availability, monday, profile))
        assertEquals(listOf(Sport.RUN), settled.days[1].sessions.map { it.sport }, "no swim on Tuesday")
        assertTrue(settled.days[6].sessions.isEmpty(), "Sunday stays free")
        assertTrue(settled.days[5].sessions.single().durationMin <= 132)
        assertTrue(notes.size >= 3)
    }

    @Test
    fun `too much time in the high zones loses reps`() {
        val greedy = listOf(SportProposal(Sport.RUN, listOf(day(1, session(Sport.RUN, step(WorkoutBlock.WARMUP, 1, "Z2", seconds = 900), step(WorkoutBlock.MAIN, 10, "Z4", seconds = 300, recovery = 90))))))
        val plan = CoachTable.merge(monday, monday, emptyList(), greedy, budget, profile, TrainingAvailability())
        assertTrue(TriRegia.check(plan, budget, TrainingAvailability(), monday, profile).any { it.rule == RegiaRule.HIGH_ZONE_TIME })
        val (settled, _) = CoachTable.settle(plan, budget, TrainingAvailability(), profile, monday)
        val run = settled.days[1].sessions.single()
        assertTrue(Workouts.highMinutes(run, profile) <= TriRegia.highZoneBudget(Sport.RUN, 170))
        assertTrue(TriRegia.isHard(run))
    }

    @Test
    fun `past days stay as they went`() {
        val past = listOf(PlannedDay(monday, listOf(PlannedSession(Sport.RUN, "Corsa mattutina", 45, Intensity.ENDURANCE))))
        val plan = CoachTable.merge(monday, monday.plusDays(1), past, proposals(4), budget, profile, TrainingAvailability())
        assertEquals("Corsa mattutina", plan.days[0].sessions.single().title)
        assertEquals(mapOf(Sport.RUN to 45), CoachTable.doneMinutes(past))
    }

    // ---- Mapper and prompts --------------------------------------------------

    @Test
    fun `a structured plan survives the round trip`() {
        val plan = CoachTable.merge(monday, monday, emptyList(), proposals(4), budget, profile, TrainingAvailability())
            .copy(table = listOf(TableNote(TableVoice.BIKE, "Lungo sabato."), TableNote(TableVoice.REGIA, "Accordo.")), rounds = 2)
        val back = CoachMapper.weekPlanFromFields(CoachMapper.weekPlanToFields(plan))
        assertEquals(plan, back)
        assertTrue(back!!.fromTable)
    }

    @Test
    fun `a coach's answer is read as its own sport only`() {
        val fields = mapOf(
            "days" to listOf(
                mapOf(
                    "date" to monday.plusDays(2).toString(),
                    "sessions" to listOf(
                        mapOf(
                            "sport" to "run", "title" to "Soglia 3 × 12′", "focus" to "Soglia", "why" to "Lavoro a soglia.",
                            "steps" to listOf(
                                mapOf("block" to "warmup", "reps" to 1, "durationSec" to 1200, "zone" to "Z2"),
                                mapOf("block" to "main", "reps" to 3, "durationSec" to 720, "zone" to "Z4", "recoverySec" to 240, "text" to "sui rulli"),
                                mapOf("block" to "main", "zone" to "Z2"),
                            ),
                        ),
                    ),
                ),
                mapOf("date" to monday.plusDays(9).toString(), "sessions" to listOf(mapOf("sport" to "bike", "title" to "Fuori settimana"))),
            ),
            "noteForCoaches" to "Soglia mercoledì.",
        )
        val proposal = assertNotNull(CoachMapper.sportProposalFromFields(fields, Sport.BIKE, monday))
        val session = proposal.days.single().sessions.single()
        assertEquals(Sport.BIKE, session.sport)
        assertEquals(2, session.steps.size)
        assertEquals("Soglia mercoledì.", proposal.note)
        assertTrue(CoachMapper.sportProposalFromFields(emptyMap(), Sport.RUN, monday)!!.days.isEmpty())
    }

    @Test
    fun `each coach is told its limits, and in the second round what the others want`() {
        val system = SportCoachPrompts.system(Sport.RUN)
        assertTrue(system.contains("Coach Corsa") && system.contains("Coach Nuoto e Coach Bici"))
        val all = proposals(3)
        val conflicts = listOf(RegiaConflict(RegiaRule.HARD_IN_A_ROW, monday.plusDays(3), "Mercoledì 25 e Giovedì 26 sono due giorni duri di fila."))
        val text = SportCoachPrompts.weekRequest(
            Sport.RUN, "Atleta di prova", monday, monday.plusDays(1), "Fase del ciclo: generale.", budget, 40, null,
            others = all.filter { it.sport != Sport.RUN }, own = all.first { it.sport == Sport.RUN }, conflicts = conflicts,
        )
        assertTrue(text.contains("da martedì ${monday.plusDays(1)}"))
        assertTrue(text.contains("170, di cui 40 già fatti: te ne restano 130"))
        assertTrue(text.contains("Coach Bici:") && text.contains("Lungo sabato."))
        assertTrue(text.contains("due giorni duri di fila"))
        assertTrue(text.contains("Precedenza nei conflitti: bici, corsa, nuoto"))
        assertTrue(text.contains("Z1, Z2, Z3, Z4, Z5"))
    }
}
