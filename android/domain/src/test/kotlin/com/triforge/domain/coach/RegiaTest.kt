package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.ActivitySource
import com.triforge.domain.AthleteProfile
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
import kotlin.test.assertTrue

class RegiaTest {

    private val monday = LocalDate.of(2026, 11, 16)
    private val zone: ZoneId = ZoneOffset.UTC

    private fun activity(sport: Sport, day: LocalDate, minutes: Int) = Activity(
        id = "$sport-$day-$minutes", source = ActivitySource.GARMIN, sport = sport, name = "x",
        startTime = day.atTime(7, 0).toInstant(ZoneOffset.UTC), durationSec = minutes * 60L, distanceMeters = 0.0,
    )

    /** Four weeks of 1 h swim, 2 × 1h30 bike (one 2 h), 3 × 40' run. */
    private val history: List<Activity> = (1..4).flatMap { week ->
        val start = monday.minusWeeks(week.toLong())
        listOf(
            activity(Sport.SWIM, start, 60),
            activity(Sport.BIKE, start.plusDays(2), 90),
            activity(Sport.BIKE, start.plusDays(5), 120),
            activity(Sport.RUN, start.plusDays(1), 40),
            activity(Sport.RUN, start.plusDays(3), 40),
            activity(Sport.RUN, start.plusDays(6), 40),
        )
    }

    private val recent = RecentTraining.of(history, monday, zone)
    private val profile = AthleteProfile(goal = RaceGoal.HALF, raceDate = LocalDate.of(2027, 6, 7), weeklyHours = 9.0)

    // ---- Availability --------------------------------------------------------

    @Test
    fun `availability survives the round trip and drops wrong values`() {
        val availability = TrainingAvailability(
            swim = SportSlot(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(7, 0)),
            bike = SportSlot(setOf(DayOfWeek.SATURDAY), LocalTime.of(18, 30)),
            restDay = DayOfWeek.THURSDAY,
            weakSport = Sport.BIKE,
            poolLengthM = 50,
            longestRideMin = 210,
            indoorTrainer = true,
        )
        assertEquals(availability, AvailabilityMapper.fromFields(AvailabilityMapper.toFields(availability)))
        val broken = AvailabilityMapper.fromFields(
            mapOf("availability" to mapOf("swimDays" to listOf("mon", "xyz"), "swimTime" to "25:99", "poolLengthM" to 33, "weakSport" to "other", "longestRideMin" to 5000)),
        )
        assertEquals(setOf(DayOfWeek.MONDAY), broken.swim.days)
        assertNull(broken.swim.time)
        assertEquals(25, broken.poolLengthM)
        assertNull(broken.weakSport)
        assertNull(broken.longestRideMin)
        assertFalse(AvailabilityMapper.fromFields(emptyMap()).isSet)
        assertTrue(TrainingAvailability().allows(Sport.RUN, DayOfWeek.SUNDAY), "no days given means any day")
        assertFalse(availability.allows(Sport.SWIM, DayOfWeek.TUESDAY))
    }

    // ---- Budget --------------------------------------------------------------

    @Test
    fun `recent training reads volume and sessions`() {
        assertEquals(60.0, recent.weeklyMinutes[Sport.SWIM])
        assertEquals(210.0, recent.weeklyMinutes[Sport.BIKE])
        assertEquals(120, recent.longestMinutes[Sport.BIKE])
        assertEquals(40, recent.medianMinutes[Sport.RUN])
    }

    @Test
    fun `shares follow the distance and the weak sport`() {
        assertEquals(0.50, TriRegia.baseShares(RaceGoal.FULL)[Sport.BIKE])
        assertEquals(0.20, TriRegia.baseShares(RaceGoal.SPRINT)[Sport.SWIM])
        val weakSwim = TriRegia.shares(RaceGoal.OLYMPIC, Sport.SWIM)
        assertEquals(0.27, weakSwim.getValue(Sport.SWIM), 1e-9)
        assertEquals(1.0, weakSwim.values.sum(), 1e-9)
        assertTrue(weakSwim.getValue(Sport.BIKE) < 0.45)
        // 60 swim, 210 bike, 120 run: swim is the furthest below its 15%? 60/390 = 15.4%; run 30.8% of 35%.
        assertEquals(Sport.RUN, TriRegia.weakFromData(recent, RaceGoal.HALF))
        assertNull(TriRegia.weakFromData(RecentTraining(), RaceGoal.HALF))
    }

    @Test
    fun `the phase follows the race date and the recent volume`() {
        assertEquals(CyclePhase.GENERAL, TriRegia.phase(profile, monday, recent))
        assertEquals(CyclePhase.SPECIFIC, TriRegia.phase(profile.copy(raceDate = monday.plusWeeks(6)), monday, recent))
        assertEquals(CyclePhase.TAPER, TriRegia.phase(profile.copy(raceDate = monday.plusDays(13)), monday, recent))
        assertEquals(CyclePhase.RACE_WEEK, TriRegia.phase(profile.copy(raceDate = monday.plusDays(6)), monday, recent))
        assertEquals(CyclePhase.RETURN, TriRegia.phase(profile.copy(weeklyHours = 20.0), monday, recent))
        assertEquals(CyclePhase.GENERAL, TriRegia.phase(profile.copy(raceDate = monday.minusDays(1)), monday, recent), "a past race is ignored")
    }

    @Test
    fun `the budget grows gently from what the athlete did`() {
        val budget = TriRegia.budget(profile, TrainingAvailability(weakSport = Sport.BIKE), recent, monday)
        assertEquals(540, budget.availableMinutes)
        assertEquals(Sport.BIKE, budget.weakSport)
        assertFalse(budget.weakFromData)
        val run = budget.sports.getValue(Sport.RUN)
        assertTrue(run.minutes <= 120 * 1.10 + 3, "running grows at most 10%: ${run.minutes}")
        val swim = budget.sports.getValue(Sport.SWIM)
        assertTrue(swim.minutes <= 75 + 3, "swimming at most 25%: ${swim.minutes}")
        assertTrue(budget.totalMinutes <= budget.availableMinutes)
        assertEquals(180, budget.sports.getValue(Sport.BIKE).longestMin, "1.5 × the 2 h ride")
        assertEquals(90, swim.longestMin, "1.5 × the median swim")
        assertEquals(3, budget.maxHardSessions)
        assertFalse(budget.bricksAllowed)

        val shortRide = TriRegia.budget(profile, TrainingAvailability(longestRideMin = 150), recent, monday)
        assertEquals(150, shortRide.sports.getValue(Sport.BIKE).longestMin)

        val raceWeek = TriRegia.budget(profile.copy(raceDate = monday.plusDays(6)), TrainingAvailability(), recent, monday)
        assertEquals(1, raceWeek.maxHardSessions)
        assertTrue(raceWeek.totalMinutes < budget.totalMinutes)

        val newcomer = TriRegia.budget(AthleteProfile(), TrainingAvailability(), RecentTraining(), monday)
        assertTrue(newcomer.hoursEstimated)
        assertTrue(newcomer.sports.values.all { it.minutes <= TriRegia.NEW_SPORT_MINUTES })
    }

    // ---- Checks --------------------------------------------------------------

    private fun day(offset: Long, vararg sessions: PlannedSession) = PlannedDay(monday.plusDays(offset), sessions.toList())
    private fun s(sport: Sport, minutes: Int, intensity: Intensity = Intensity.ENDURANCE) = PlannedSession(sport, "x", minutes, intensity)
    private fun rest(offset: Long) = day(offset, PlannedSession(null, "Riposo", 0, Intensity.REST))

    private val budget = TriRegia.budget(profile, TrainingAvailability(), recent, monday)

    private fun good() = WeekPlan(
        monday, "", listOf(
            rest(0),
            day(1, s(Sport.RUN, 45, Intensity.THRESHOLD)),
            day(2, s(Sport.SWIM, 50), s(Sport.BIKE, 60)),
            day(3, s(Sport.RUN, 40)),
            day(4, s(Sport.SWIM, 30, Intensity.THRESHOLD)),
            day(5, s(Sport.BIKE, 120)),
            day(6, s(Sport.RUN, 45)),
        ), "",
    )

    @Test
    fun `a good week passes`() {
        assertEquals(emptyList(), TriRegia.check(good(), budget, TrainingAvailability()))
    }

    @Test
    fun `hard days in a row, too many hard sessions and no rest are found`() {
        val plan = good().copy(
            days = listOf(
                day(0, s(Sport.SWIM, 40)),
                day(1, s(Sport.RUN, 45, Intensity.THRESHOLD)),
                day(2, s(Sport.BIKE, 60, Intensity.VO2MAX)),
                day(3, s(Sport.RUN, 40)),
                day(4, s(Sport.SWIM, 30, Intensity.THRESHOLD)),
                day(5, s(Sport.RUN, 30, Intensity.THRESHOLD)),
                day(6, s(Sport.SWIM, 30)),
            ),
        )
        val rules = TriRegia.check(plan, budget, TrainingAvailability()).map { it.rule }.toSet()
        assertTrue(RegiaRule.HARD_IN_A_ROW in rules)
        assertTrue(RegiaRule.HARD_SESSIONS in rules)
        assertTrue(RegiaRule.NO_REST_DAY in rules)
        assertTrue(RegiaRule.SPORT_QUALITY !in rules, "two hard runs are allowed")
    }

    @Test
    fun `days, bricks, three sports and long sessions are found`() {
        val availability = TrainingAvailability(swim = SportSlot(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), restDay = DayOfWeek.MONDAY)
        val plan = good().copy(
            days = listOf(
                rest(0),
                day(1, s(Sport.BIKE, 60), s(Sport.RUN, 20)),
                day(2, s(Sport.SWIM, 30), s(Sport.BIKE, 40), s(Sport.RUN, 20)),
                rest(3),
                day(4, s(Sport.SWIM, 30)),
                day(5, s(Sport.BIKE, 300)),
                rest(6),
            ),
        )
        val found = TriRegia.check(plan, budget, availability)
        val rules = found.map { it.rule }
        assertTrue(RegiaRule.BRICK in rules)
        assertTrue(RegiaRule.TOO_MANY_SPORTS in rules)
        assertTrue(RegiaRule.UNAVAILABLE_DAY in rules)
        assertTrue(RegiaRule.LONGEST_SESSION in rules)
        assertTrue(RegiaRule.REST_DAY !in rules, "Monday is a rest day")
        assertTrue(found.first { it.rule == RegiaRule.UNAVAILABLE_DAY }.text.contains("mercoledì", ignoreCase = true))
        val specific = budget.copy(bricksAllowed = true)
        assertTrue(RegiaRule.BRICK !in TriRegia.check(plan, specific, availability).map { it.rule })
    }

    @Test
    fun `past days are not checked one by one, and the rest day preference is`() {
        val plan = good().copy(
            days = listOf(
                day(0, s(Sport.RUN, 40, Intensity.THRESHOLD)),
                day(1, s(Sport.BIKE, 60, Intensity.THRESHOLD)),
                rest(2),
                day(3, s(Sport.SWIM, 40)),
                day(4, s(Sport.RUN, 40)),
                day(5, s(Sport.BIKE, 90)),
                rest(6),
            ),
        )
        assertTrue(TriRegia.check(plan, budget, TrainingAvailability(), from = monday.plusDays(2)).none { it.rule == RegiaRule.HARD_IN_A_ROW })
        assertTrue(TriRegia.check(plan, budget, TrainingAvailability()).any { it.rule == RegiaRule.HARD_IN_A_ROW })
        val thursday = TriRegia.check(plan, budget, TrainingAvailability(restDay = DayOfWeek.THURSDAY))
        assertTrue(thursday.any { it.rule == RegiaRule.REST_DAY })
    }

    @Test
    fun `time over the budget is found`() {
        val plan = good().copy(days = good().days.map { d -> d.copy(sessions = d.sessions.map { it.copy(durationMin = it.durationMin * 2) }) })
        val rules = TriRegia.check(plan, budget, TrainingAvailability()).map { it.rule }
        assertTrue(RegiaRule.TOTAL_TIME in rules)
        assertTrue(RegiaRule.SPORT_TIME in rules)
    }

    @Test
    fun `the description gives the coach the numbers and the rules`() {
        val availability = TrainingAvailability(
            swim = SportSlot(setOf(DayOfWeek.MONDAY), LocalTime.of(7, 0)),
            restDay = DayOfWeek.THURSDAY,
            weakSport = Sport.BIKE,
            longestRideMin = 210,
            indoorTrainer = true,
        )
        val text = TriRegia.describe(TriRegia.budget(profile, availability, recent, monday), availability)
        assertTrue(text.contains("Fase del ciclo: generale"))
        assertTrue(text.contains("Ore disponibili: 9 h"))
        assertTrue(text.contains("Punto debole: bici (indicato dall'atleta)"))
        assertTrue(text.contains("Giorno di riposo preferito: giovedì"))
        assertTrue(text.contains("Nuoto: solo lun"))
        assertTrue(text.contains("di solito alle 07:00"))
        assertTrue(text.contains("uscita più lunga possibile 3h30"))
        assertTrue(text.contains("Niente brick"))
    }
}
