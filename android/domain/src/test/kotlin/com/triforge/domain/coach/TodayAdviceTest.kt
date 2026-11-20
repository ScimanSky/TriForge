package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.AthleteProfile
import com.triforge.domain.ActivitySource
import com.triforge.domain.FormZone
import com.triforge.domain.LoadResponsePattern
import com.triforge.domain.RecoveryDay
import com.triforge.domain.RecoveryLight
import com.triforge.domain.RecoveryPhase
import com.triforge.domain.Sport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The card on top of Oggi: what to do today, or tomorrow, in one sentence. */
class TodayAdviceTest {

    private val monday = LocalDate.of(2026, 11, 16)
    private val friday = monday.plusDays(4)
    private val zone: ZoneId = ZoneOffset.UTC

    private val availability = TrainingAvailability(
        swim = SportSlot(setOf(DayOfWeek.FRIDAY), LocalTime.of(13, 0)),
        gym = GymSlot(enabled = true, days = setOf(DayOfWeek.SATURDAY), time = LocalTime.of(19, 0), minutes = 90),
        paused = setOf(Sport.BIKE, Sport.RUN),
    )

    private val swim = PlannedSession(Sport.SWIM, "Nuoto tecnica e fondo", 60)
    private val gym = PlannedSession(Sport.STRENGTH, "Palestra", 90, own = true)

    private val plan = WeekPlan(
        weekStart = monday,
        phase = "",
        days = (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            PlannedDay(date, when (offset) { 4L -> listOf(swim); 5L -> listOf(gym); else -> emptyList() })
        },
        rationale = "",
        plannedFrom = monday,
    )

    private fun recovery(light: RecoveryLight?, level: Int = 0, pattern: LoadResponsePattern = LoadResponsePattern.BASELINE) = RecoveryDay(
        date = friday, phase = RecoveryPhase.FULL, score = 20, smoothedScore = 20, light = light, level = level, pattern = pattern,
        confidence = 0.9, deviations = emptyMap(), contributions = emptyMap(), load = null, missing = emptySet(),
    )

    private fun card(now: LocalTime, activities: List<Activity> = emptyList(), today: LocalDate = friday) =
        TodayAdvice.card(plan, activities, today, zone, now, recovery(RecoveryLight.GREEN), FormZone.OVERLOAD, availability)

    @Test
    fun `in the morning the card is about today's session`() {
        val card = assertNotNull(card(LocalTime.of(8, 0)))
        assertFalse(card.tomorrow)
        assertEquals(friday, card.date)
        assertEquals(listOf(swim), card.sessions)
        assertEquals(LocalTime.of(13, 0), card.time)
        assertEquals("Hai recuperato bene, ma il carico delle ultime settimane è alto: fai la seduta prevista e non aggiungere altro.", card.sentence)
        assertEquals("Domani: Palestra, 1 h 30′ alle 19:00 (seduta tua).", card.next)
    }

    @Test
    fun `once the swim is recorded the card moves to tomorrow`() {
        val done = Activity(
            id = "s", source = ActivitySource.GARMIN, sport = Sport.SWIM, name = "",
            startTime = friday.atTime(13, 0).toInstant(ZoneOffset.UTC), durationSec = 3600, distanceMeters = 2400.0,
        )
        val card = assertNotNull(card(LocalTime.of(15, 0), listOf(done)))
        assertTrue(card.tomorrow)
        assertEquals(listOf(gym), card.sessions)
        assertEquals(LocalTime.of(19, 0), card.time)
        assertNull(card.next)
        assertTrue(card.sentence.endsWith("oggi non aggiungere allenamenti."))
    }

    @Test
    fun `well after its usual time, and in the evening, a session is no longer today's`() {
        assertFalse(assertNotNull(card(LocalTime.of(14, 30))).tomorrow)
        assertTrue(assertNotNull(card(LocalTime.of(15, 5))).tomorrow)
        assertTrue(assertNotNull(card(LocalTime.of(21, 30), today = friday.minusDays(1))).let { it.tomorrow && it.sessions == listOf(swim) })
    }

    @Test
    fun `a free day with nothing tomorrow still gives the sentence`() {
        val card = assertNotNull(card(LocalTime.of(9, 0), today = monday))
        assertTrue(card.tomorrow)
        assertTrue(card.sessions.isEmpty())
        assertTrue(card.sentence.isNotBlank())
    }

    @Test
    fun `no week, no card`() {
        assertNull(TodayAdvice.card(null, emptyList(), friday, zone, LocalTime.NOON, null, null, availability))
        assertNull(card(LocalTime.of(9, 0), today = monday.plusDays(6)), "a free Sunday: next week is not written yet")
    }

    @Test
    fun `the body's response comes before the load`() {
        assertTrue(TodayAdvice.sentence(recovery(RecoveryLight.RED, level = 3), FormZone.FRESH, hasSession = true).contains("riposo"))
        assertTrue(TodayAdvice.sentence(recovery(RecoveryLight.GREEN, pattern = LoadResponsePattern.ILLNESS), FormZone.FRESH, true).contains("non stai bene"))
        assertTrue(TodayAdvice.sentence(recovery(RecoveryLight.ORANGE, level = 2), FormZone.NEUTRAL, true).contains("più facile"))
        assertEquals("", TodayAdvice.sentence(null, null, true))
        assertTrue(TodayAdvice.sentence(null, FormZone.OVERLOAD, true).startsWith("Il carico delle ultime settimane è alto"))
    }

    @Test
    fun `a threshold in doubt softens the load`() {
        assertTrue(TodayAdvice.sentence(recovery(RecoveryLight.GREEN), FormZone.OVERLOAD, true, loadInDoubt = true).contains("risulta alto"))
    }

    @Test
    fun `the meal that counts is the one after the training`() {
        val day = NutritionDay(
            date = friday, dayType = DayType.MODERATE, headline = "", kcal = 2880, carbsG = 380, proteinG = 140, fatG = 85,
            fueling = listOf(FuelingStep("Prima, 11:30", "Banana e yogurt"), FuelingStep("dopo, entro le 14:30", "Riso, pollo e verdure")),
            meals = listOf(MealSuggestion("Cena", "Pasta e salmone", 900)),
            rationale = "",
        )
        assertEquals(KeyMeal("Dopo, entro le 14:30", "Riso, pollo e verdure"), TodayAdvice.keyMeal(day))
        assertEquals(KeyMeal("Cena", "Pasta e salmone"), TodayAdvice.keyMeal(day.copy(fueling = emptyList())))
        assertNull(TodayAdvice.keyMeal(day.copy(fueling = emptyList(), meals = emptyList())))
    }

    @Test
    fun `a session is told in a line, with its main set`() {
        val profile = AthleteProfile(cssSecPer100m = 120)
        val session = swim.copy(
            steps = listOf(
                WorkoutStep(WorkoutBlock.WARMUP, distanceM = 400, zone = "A1"),
                WorkoutStep(WorkoutBlock.MAIN, reps = 10, distanceM = 100, zone = "A2", recoverySec = 20),
                WorkoutStep(WorkoutBlock.MAIN, reps = 4, distanceM = 50, zone = "B2", recoverySec = 30),
            ),
        )
        assertEquals("10 × 100 m A2, rec. 20″", TodayAdvice.mainSet(session, profile))
        val line = TodayAdvice.summary(session, profile)
        assertTrue(line.startsWith("1 h 00′ · 1.600 m") || line.startsWith("1 h 00′ · 1600 m"), line)
        assertFalse(line.contains("quasi tutta"), "a third of the work is in other zones")
        assertTrue(TodayAdvice.summary(session.copy(steps = session.steps.take(2).drop(1)), profile).endsWith("quasi tutta in A2"))
        assertNull(TodayAdvice.mainSet(gym, profile))
        assertEquals("1 h 30′", TodayAdvice.summary(gym, profile))
    }
}
