package com.triforge.domain.coach

import com.triforge.domain.ActivitySource
import com.triforge.domain.AthleteProfile
import com.triforge.domain.RaceGoal
import com.triforge.domain.Sport
import com.triforge.domain.activity
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CoachPromptsTest {

    private val today = LocalDate.parse("2026-11-18")
    private val monday = LocalDate.parse("2026-11-16")

    private val plan = WeekPlan(
        weekStart = monday,
        phase = "Costruzione",
        days = (0L until 7L).map { offset ->
            val date = monday.plusDays(offset)
            PlannedDay(date, if (offset == 2L) listOf(PlannedSession(Sport.BIKE, "Soglia 4 × 8′", 75, Intensity.THRESHOLD)) else emptyList())
        },
        rationale = "Alziamo la bici.",
        noteForNutriAi = "Mercoledì soglia alle 18:30.",
    )

    @Test
    fun `the two coaches have distinct instructions that name each other`() {
        assertNotEquals(CoachPrompts.COACH_SYSTEM, CoachPrompts.NUTRI_SYSTEM)
        assertContains(CoachPrompts.COACH_SYSTEM, "NutriAI")
        assertContains(CoachPrompts.NUTRI_SYSTEM, "Coach")
        assertContains(CoachPrompts.NUTRI_SYSTEM, "RED-S")
    }

    @Test
    fun `NutriAI receives the coach plan and note through the brief`() {
        val brief = CoachBriefs.of(plan, today)
        assertContains(brief.summary, "Soglia 4 × 8′")
        assertFalse(brief.summary.contains("lunedì 2026-11-16"), "past days are left out of the brief")
        val request = CoachPrompts.nutritionDayRequest("dati", today, NutritionProfile(intolerances = setOf(Intolerance.LACTOSE)), null, brief, plan, emptyList())
        assertContains(request, "Nota per te: Mercoledì soglia alle 18:30.")
        assertContains(request, "Sedute di mercoledì 2026-11-18: bici Soglia 4 × 8′ 75′ [threshold]")
        assertContains(request, "Intolleranze (divieto assoluto): lattosio")
        assertContains(request, "Manca il peso")
    }

    @Test
    fun `the coach receives NutriAI's note`() {
        val day = NutritionDay(today, DayType.HEAVY, "Carico", 2800, 400, 140, 70, emptyList(), emptyList(), "", "Mangia poco a pranzo: occhio ai lunghi.")
        val request = CoachPrompts.weekPlanRequest("dati", monday, CoachBriefs.of(day), null, emptyList())
        assertContains(request, "Ultimo riepilogo di NutriAI")
        assertContains(request, "Nota per te: Mangia poco a pranzo")
    }

    @Test
    fun `athlete context has the data and never the identity`() {
        val activities = listOf(
            activity("a1", Sport.SWIM, "2026-11-17T07:00:00Z", durationSec = 2700, distanceMeters = 2500.0),
            activity("a2", Sport.RUN, "2026-10-01T07:00:00Z", source = ActivitySource.HEALTH_CONNECT),
        )
        val text = AthleteContext.describe(
            AthleteData(
                today = today,
                zone = ZoneOffset.UTC,
                profile = AthleteProfile(goal = RaceGoal.HALF, raceName = "70.3 Pescara", raceDate = LocalDate.parse("2027-06-13"), ftpWatts = 245),
                activities = activities,
            ),
        )
        assertContains(text, "70.3 Pescara")
        assertContains(text, "FTP bici: 245 W")
        assertContains(text, "2026-11-17 martedì · nuoto · 45′")
        assertFalse(text.contains("2026-10-01"), "only the last two weeks are listed one by one")
        assertContains(text, "Check-in di oggi: non fatto")
        assertTrue(text.startsWith("Oggi è mercoledì 2026-11-18."))
    }

    @Test
    fun `chat turn carries the message last`() {
        val turn = CoachPrompts.chatTurn(CoachRole.COACH, "dati", null, CoachPrompts.describe(plan), null, "  Posso spostare le ripetute?  ")
        assertTrue(turn.endsWith("Posso spostare le ripetute?"))
        assertContains(turn, "NutriAI non ha ancora pubblicato nulla.")
    }
}
