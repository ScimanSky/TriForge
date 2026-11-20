package com.triforge.domain.coach

import com.triforge.domain.Sport
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachMapperTest {

    private val monday = LocalDate.parse("2026-11-16")

    private fun day(date: String, vararg sessions: Map<String, Any?>) = mapOf("date" to date, "sessions" to sessions.toList())

    private fun session(sport: String, title: String, minutes: Any?, intensity: String = "easy") =
        mapOf("sport" to sport, "title" to title, "durationMin" to minutes, "intensity" to intensity)

    @Test
    fun `week plan always has seven days in order and drops days outside the week`() {
        val fields = mapOf(
            "weekStart" to "2026-11-16",
            "phase" to "Costruzione",
            "rationale" to "Perché sì",
            "days" to listOf(
                day("2026-11-18", session("run", "Corsa facile", 45)),
                day("2026-11-16", session("swim", "Tecnica", 45.0), session("rest", "Mobilità", "20")),
                day("2026-11-22", session("bike", "Lungo", 150, "endurance")),
                day("2026-11-23", session("bike", "Fuori settimana", 60)),
            ),
        )
        val plan = assertNotNull(CoachMapper.weekPlanFromFields(fields))
        assertEquals(7, plan.days.size)
        assertEquals((0L until 7L).map { monday.plusDays(it) }, plan.days.map { it.date })
        assertEquals(listOf(Sport.SWIM, null), plan.days[0].sessions.map { it.sport })
        assertEquals(20, plan.days[0].sessions[1].durationMin)
        assertTrue(plan.days[1].sessions.isEmpty())
        assertEquals(Intensity.ENDURANCE, plan.days[6].sessions.single().intensity)
        assertEquals(mapOf(Sport.SWIM to (1 to 45), Sport.RUN to (1 to 45), Sport.BIKE to (1 to 150)), plan.totals())
    }

    @Test
    fun `the requested week wins over what the model wrote`() {
        val fields = mapOf("weekStart" to "2020-01-06", "days" to listOf(day("2026-11-17", session("run", "Ripetute", 55))))
        val plan = assertNotNull(CoachMapper.weekPlanFromFields(fields, monday))
        assertEquals(monday, plan.weekStart)
        assertEquals("Ripetute", plan.days[1].sessions.single().title)
    }

    @Test
    fun `a plan without sessions is rejected`() {
        assertNull(CoachMapper.weekPlanFromFields(mapOf("weekStart" to "2026-11-16", "days" to emptyList<Any>())))
        assertNull(CoachMapper.weekPlanFromFields(mapOf("weekStart" to "bad")))
    }

    @Test
    fun `sessions are sanitised`() {
        val fields = mapOf(
            "weekStart" to "2026-11-16",
            "days" to listOf(day("2026-11-16", session("run", "", 30), session("BIKE", "Folle", 5000, "unknown"))),
        )
        val session = assertNotNull(CoachMapper.weekPlanFromFields(fields)).days[0].sessions.single()
        assertEquals(Sport.BIKE, session.sport)
        assertEquals(600, session.durationMin)
        assertEquals(Intensity.EASY, session.intensity)
    }

    @Test
    fun `week plan round trips`() {
        val plan = WeekPlan(
            weekStart = monday,
            phase = "Base",
            days = (0L until 7L).map { PlannedDay(monday.plusDays(it), listOf(PlannedSession(Sport.RUN, "Facile", 40, Intensity.EASY, "Z2"))) },
            rationale = "r",
            noteForNutriAi = "n",
        )
        assertEquals(plan, CoachMapper.weekPlanFromFields(CoachMapper.weekPlanToFields(plan)))
    }

    @Test
    fun `a swim saved with Z1 to Z5 is read with the codes of the pool`() {
        fun session(sport: Sport) = PlannedSession(
            sport, "Serie", 40, Intensity.EASY,
            steps = listOf(
                WorkoutStep(WorkoutBlock.WARMUP, distanceM = 400, zone = "Z1"),
                WorkoutStep(WorkoutBlock.MAIN, reps = 10, distanceM = 100, zone = "Z2", recoverySec = 20),
                WorkoutStep(WorkoutBlock.MAIN, reps = 4, distanceM = 100, zone = "Z4", recoverySec = 30),
                WorkoutStep(WorkoutBlock.MAIN, reps = 4, distanceM = 50, zone = "C3", recoverySec = 40),
            ),
        )
        val plan = WeekPlan(
            weekStart = monday,
            phase = "Base",
            days = (0L until 7L).map { PlannedDay(monday.plusDays(it), if (it == 0L) listOf(session(Sport.SWIM), session(Sport.RUN)) else emptyList()) },
            rationale = "r",
            noteForNutriAi = "n",
        )

        val read = assertNotNull(CoachMapper.weekPlanFromFields(CoachMapper.weekPlanToFields(plan))).days.first().sessions

        assertEquals(listOf("A1", "A2", "B2", "C3"), read.first { it.sport == Sport.SWIM }.steps.map { it.zone })
        // The run keeps its zones; a code that is not one of its own is left as it was written.
        assertEquals(listOf("Z1", "Z2", "Z4", "C3"), read.first { it.sport == Sport.RUN }.steps.map { it.zone })
    }

    @Test
    fun `nutrition day round trips and rejects absurd targets`() {
        val day = NutritionDay(
            date = monday,
            dayType = DayType.HEAVY,
            headline = "Giorno di carico",
            kcal = 2850,
            carbsG = 420,
            proteinG = 140,
            fatG = 75,
            fueling = listOf(FuelingStep("17:00", "60 g di carboidrati")),
            meals = listOf(MealSuggestion("Pranzo", "Riso e pollo", 1020)),
            rationale = "r",
            noteForCoach = "",
        )
        assertEquals(day, CoachMapper.nutritionDayFromFields(CoachMapper.nutritionDayToFields(day)))
        val broken = CoachMapper.nutritionDayToFields(day) + ("kcal" to 25_000)
        assertNull(CoachMapper.nutritionDayFromFields(broken))
    }

    @Test
    fun `nutrition profile defaults and limits`() {
        val profile = CoachMapper.nutritionProfileFromFields(mapOf("diet" to "vegan", "avoid" to listOf(" lattosio ", ""), "mealsPerDay" to 12L))
        assertEquals(Diet.VEGAN, profile.diet)
        assertEquals(listOf(DislikeEntry("lattosio")), profile.dislikes)
        assertEquals(NutritionProfile.MAX_MEALS, profile.mealsPerDay)
        assertEquals(listOf("Vegano", "6 pasti"), profile.chips())
        assertEquals("Vegano · 1 cibo escluso", profile.summary())
        assertEquals(
            "Onnivoro · 1 allergia · 2 cibi esclusi · creatina 3 g",
            NutritionProfile(
                otherAllergies = "kiwi",
                dislikes = listOf(DislikeEntry("latte", listOf("kefir")), DislikeEntry("funghi")),
                supplements = listOf(Supplement("Creatina", "3 g")),
            ).summary(),
        )
        assertEquals(NutritionProfile(), CoachMapper.nutritionProfileFromFields(emptyMap()))
        assertEquals(listOf("Lattosio", "noci"), NutritionProfile.parseList("Lattosio, noci;\nlattosio"))
    }

    @Test
    fun `full nutrition profile survives a round trip`() {
        val profile = NutritionProfile(
            sex = Sex.MALE,
            diet = Diet.PESCATARIAN,
            goal = BodyGoal.PERFORMANCE,
            mealsPerDay = 5,
            allergies = setOf(Allergen.NUTS, Allergen.SESAME),
            otherAllergies = "kiwi",
            intolerances = setOf(Intolerance.LACTOSE),
            likes = listOf("riso", "salmone"),
            dislikes = listOf(DislikeEntry("latte", listOf("kefir", "cappuccino")), DislikeEntry("funghi")),
            redMeatPerWeek = 2,
            supplements = listOf(Supplement("Creatina", "3 g", SupplementTiming.DAILY)),
            drinks = listOf(Drink("Caffè", "2 al giorno")),
            noCaffeine = false,
            notes = "pranzo in ufficio",
        )
        assertEquals(profile, CoachMapper.nutritionProfileFromFields(CoachMapper.nutritionProfileToFields(profile)))
        val bad = CoachMapper.nutritionProfileToFields(profile) + ("redMeatPerWeek" to 9L) + ("allergies" to listOf("nuts", "unknown"))
        val read = CoachMapper.nutritionProfileFromFields(bad)
        assertNull(read.redMeatPerWeek)
        assertEquals(setOf(Allergen.NUTS), read.allergies)
        assertEquals(DislikeEntry("latte", listOf("kefir")), NutritionProfile.dislike(" latte (", "kefir, KEFIR"))
        assertEquals("latte (tranne kefir, cappuccino)", profile.dislikes.first().text())
    }

    @Test
    fun `roles know their partner`() {
        assertEquals(CoachRole.NUTRI_AI, CoachRole.COACH.partner)
        assertEquals(CoachRole.COACH, CoachRole.NUTRI_AI.partner)
        assertEquals(CoachRole.NUTRI_AI, CoachRole.of("nutriai"))
    }
}
