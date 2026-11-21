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

    private val swimDone = Activity(
        id = "s", source = ActivitySource.GARMIN, sport = Sport.SWIM, name = "",
        startTime = friday.atTime(13, 0).toInstant(ZoneOffset.UTC), durationSec = 3600, distanceMeters = 2400.0,
    )

    @Test
    fun `in the morning the card is about today's session, with tomorrow in a line`() {
        val card = assertNotNull(card(LocalTime.of(8, 0)))
        assertEquals(DayLeft.SESSION, card.left)
        assertEquals(listOf(swim), card.sessions)
        assertEquals(LocalTime.of(13, 0), card.time)
        assertEquals("Hai recuperato bene, ma il carico delle ultime settimane è alto: fai la seduta prevista e non aggiungere altro.", card.sentence)
        assertEquals("Domani: Palestra, 1 h 30′ alle 19:00 (seduta tua).", card.next)
        assertTrue(card.tomorrow.isEmpty())
    }

    @Test
    fun `once the swim is recorded today is done and tomorrow is shown in full`() {
        val card = assertNotNull(card(LocalTime.of(15, 0), listOf(swimDone)))
        assertEquals(DayLeft.DONE, card.left)
        assertTrue(card.sessions.isEmpty())
        assertNull(card.time)
        assertEquals(listOf(gym), card.tomorrow)
        assertEquals(LocalTime.of(19, 0), card.tomorrowTime)
        assertNull(card.next, "tomorrow is said once")
        // The sentence is about today, and says so: nothing in it can be read as tomorrow's.
        assertEquals("Hai recuperato bene, ma il carico delle ultime settimane è alto: per oggi non aggiungere altro.", card.sentence)
    }

    @Test
    fun `well after its usual time, and in the evening, a session is no longer today's`() {
        assertEquals(DayLeft.SESSION, assertNotNull(card(LocalTime.of(14, 30))).left)
        assertEquals(DayLeft.OPEN, assertNotNull(card(LocalTime.of(15, 5))).left)
        // Thursday evening: the day was free, Friday's swim is shown in full.
        val evening = assertNotNull(card(LocalTime.of(21, 30), today = friday.minusDays(1)))
        assertEquals(DayLeft.FREE, evening.left)
        assertEquals(listOf(swim), evening.tomorrow)
        assertEquals("Hai recuperato bene, ma il carico delle ultime settimane è alto.", evening.sentence, "nothing left to advise in the evening")
    }

    @Test
    fun `a free day says so, with a sentence about today and tomorrow in a line`() {
        // Thursday morning: free, with the swim tomorrow.
        val thursday = assertNotNull(card(LocalTime.of(9, 0), today = friday.minusDays(1)))
        assertEquals(DayLeft.FREE, thursday.left)
        assertTrue(thursday.sessions.isEmpty())
        assertEquals("Hai recuperato bene, ma il carico delle ultime settimane è alto: oggi non aggiungere allenamenti.", thursday.sentence)
        assertEquals(listOf(swim), thursday.tomorrow)
        assertNull(thursday.next)
        // Monday: free, and tomorrow too.
        val mondayCard = assertNotNull(card(LocalTime.of(9, 0), today = monday))
        assertEquals(DayLeft.FREE, mondayCard.left)
        assertTrue(mondayCard.tomorrow.isEmpty())
        assertEquals("Domani: giorno libero.", mondayCard.next)
        assertTrue(mondayCard.sentence.isNotBlank())
    }

    @Test
    fun `a training outside the programme makes a free day done, a walk does not`() {
        fun recorded(sport: Sport, minutes: Long) = Activity(
            id = "x", source = ActivitySource.GARMIN, sport = sport, name = "",
            startTime = monday.atTime(18, 0).toInstant(ZoneOffset.UTC), durationSec = minutes * 60, distanceMeters = 0.0,
        )
        assertEquals(DayLeft.DONE, assertNotNull(card(LocalTime.of(20, 0), listOf(recorded(Sport.RUN, 40)), today = monday)).left)
        assertEquals(DayLeft.FREE, assertNotNull(card(LocalTime.of(20, 0), listOf(recorded(Sport.OTHER, 40)), today = monday)).left)
        assertEquals(DayLeft.FREE, assertNotNull(card(LocalTime.of(20, 0), listOf(recorded(Sport.RUN, 10)), today = monday)).left)
    }

    @Test
    fun `a session of today not known as done is not skipped in silence`() {
        // 16:24, the swim of 13:00 is not recorded: the card says today had one, and shows tomorrow.
        val card = assertNotNull(card(LocalTime.of(16, 24)))
        assertEquals(DayLeft.OPEN, card.left)
        assertEquals(listOf(gym), card.tomorrow)
        assertEquals(
            "Oggi c'era «Nuoto tecnica e fondo» alle 13:00: non risulta ancora fatta. " +
                "Se l'hai fatta, comparirà quando l'orologio si sincronizza; se l'hai saltata, puoi chiedere al coach di spostarla.",
            card.earlier,
        )
        // Skipped or not synced yet: the app cannot tell, so it says how the athlete is and advises nothing.
        assertEquals("Hai recuperato bene, ma il carico delle ultime settimane è alto.", card.sentence)
        // Before its time, and once it is recorded, there is nothing to say.
        assertNull(assertNotNull(card(LocalTime.of(8, 0))).earlier)
        assertNull(assertNotNull(card(LocalTime.of(16, 24), listOf(swimDone))).earlier)
        // The athlete's own session is theirs to move: no coach to ask. Two sessions are told together.
        assertEquals(
            "Oggi c'era «Palestra» alle 19:00: non risulta ancora fatta. Se l'hai fatta, comparirà quando l'orologio si sincronizza.",
            TodayAdvice.earlier(listOf(gym), availability),
        )
        assertTrue(TodayAdvice.earlier(listOf(swim, gym), availability)!!.startsWith("Oggi c'erano «Nuoto tecnica e fondo» alle 13:00 e «Palestra» alle 19:00: non risultano ancora fatte."))
        assertNull(TodayAdvice.earlier(emptyList(), availability))
    }

    @Test
    fun `no week, no card, and on Sunday nothing is said of tomorrow`() {
        assertNull(TodayAdvice.card(null, emptyList(), friday, zone, LocalTime.NOON, null, null, availability))
        // A free Sunday: next week is not written yet, the card is about today only.
        val sunday = assertNotNull(card(LocalTime.of(9, 0), today = monday.plusDays(6)))
        assertEquals(DayLeft.FREE, sunday.left)
        assertNull(sunday.next)
        assertTrue(sunday.tomorrow.isEmpty())
    }

    @Test
    fun `the advice follows what is left of the day`() {
        val green = recovery(RecoveryLight.GREEN)
        assertEquals("Hai recuperato bene e il carico è quello giusto per migliorare: fai la seduta come è scritta.", TodayAdvice.sentence(green, FormZone.PRODUCTIVE, DayLeft.SESSION))
        assertEquals("Hai recuperato bene e il carico è quello giusto per migliorare: il giorno libero ci sta.", TodayAdvice.sentence(green, FormZone.PRODUCTIVE, DayLeft.FREE))
        assertEquals("Hai recuperato bene e il carico è quello giusto per migliorare: per oggi va bene così.", TodayAdvice.sentence(green, FormZone.PRODUCTIVE, DayLeft.DONE))
        assertEquals("Hai recuperato bene e il carico è quello giusto per migliorare.", TodayAdvice.sentence(green, FormZone.PRODUCTIVE, DayLeft.OPEN))
        assertEquals("I segnali di fatica durano da giorni.", TodayAdvice.sentence(recovery(RecoveryLight.RED, level = 3), null, DayLeft.OPEN))
        assertEquals("", TodayAdvice.sentence(null, null, DayLeft.OPEN))
    }

    @Test
    fun `the body's response comes before the load`() {
        assertTrue(TodayAdvice.sentence(recovery(RecoveryLight.RED, level = 3), FormZone.FRESH, DayLeft.SESSION).contains("riposo"))
        assertTrue(TodayAdvice.sentence(recovery(RecoveryLight.GREEN, pattern = LoadResponsePattern.ILLNESS), FormZone.FRESH, DayLeft.SESSION).contains("non stai bene"))
        assertTrue(TodayAdvice.sentence(recovery(RecoveryLight.ORANGE, level = 2), FormZone.NEUTRAL, DayLeft.SESSION).contains("più facile"))
        assertEquals("", TodayAdvice.sentence(null, null, DayLeft.SESSION))
        assertTrue(TodayAdvice.sentence(null, FormZone.OVERLOAD, DayLeft.SESSION).startsWith("Il carico delle ultime settimane è alto"))
    }

    @Test
    fun `a threshold in doubt softens the load`() {
        assertTrue(TodayAdvice.sentence(recovery(RecoveryLight.GREEN), FormZone.OVERLOAD, DayLeft.SESSION, loadInDoubt = true).contains("risulta alto"))
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
