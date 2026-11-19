package com.triforge.domain.coach

import com.triforge.domain.RaceGoal
import com.triforge.domain.Sport
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutriRulesTest {

    // ---- Bans ----------------------------------------------------------------

    @Test
    fun `allergies are found in names, plurals and accents`() {
        val nuts = NutritionProfile(allergies = setOf(Allergen.NUTS))
        assertEquals(BanReason.ALLERGY, FoodBans.check("Yogurt con Mandorle tostate", nuts)?.reason)
        assertEquals("noce", FoodBans.check("Insalata con noci", nuts)?.term)
        assertNull(FoodBans.check("Pollo alla noce moscata", nuts))
        assertNull(FoodBans.check("Porridge con cocco", nuts))
        val eggs = NutritionProfile(allergies = setOf(Allergen.EGGS))
        assertNotNull(FoodBans.check("Tagliatelle all'uovo", eggs))
        assertNotNull(FoodBans.check("Due uova strapazzate", eggs))
        val fish = NutritionProfile(allergies = setOf(Allergen.FISH))
        assertNotNull(FoodBans.check("Riso con baccalà", fish))
    }

    @Test
    fun `milk allergy is not fooled by plant milks, lactose free is still milk`() {
        val milk = NutritionProfile(allergies = setOf(Allergen.MILK))
        assertNull(FoodBans.check("Porridge con latte di mandorla", milk))
        assertNull(FoodBans.check("Yogurt di soia e frutti di bosco", milk))
        assertNull(FoodBans.check("Pane con burro di arachidi", milk))
        assertNotNull(FoodBans.check("Latte senza lattosio e biscotti", milk))
        assertNotNull(FoodBans.check("Pasta al parmigiano", milk))
    }

    @Test
    fun `lactose intolerance lets lactose free and aged cheese pass`() {
        val lactose = NutritionProfile(intolerances = setOf(Intolerance.LACTOSE))
        assertEquals(BanReason.INTOLERANCE, FoodBans.check("Yogurt greco con miele", lactose)?.reason)
        assertNull(FoodBans.check("Yogurt greco senza lattosio", lactose))
        assertNull(FoodBans.check("Riso con parmigiano reggiano", lactose))
        assertNull(FoodBans.check("Porridge senza latte", lactose))
    }

    @Test
    fun `gluten is checked on cereals and lifted by gluten free foods`() {
        val celiac = NutritionProfile(intolerances = setOf(Intolerance.GLUTEN))
        assertNotNull(FoodBans.check("Pasta al pomodoro", celiac))
        assertNotNull(FoodBans.check("Fette biscottate e marmellata", celiac))
        assertNull(FoodBans.check("Pasta di riso con verdure", celiac))
        assertNull(FoodBans.check("Pane senza glutine", celiac))
        assertNull(FoodBans.check("Polenta di mais e grano saraceno", celiac))
        assertNull(FoodBans.check("Riso, patate e grana", celiac))
        assertNull(FoodBans.check("Tre pasti leggeri", celiac), "«pasti» is not «pasta»")
    }

    @Test
    fun `dislikes with exceptions never lift an allergy`() {
        val profile = NutritionProfile(dislikes = listOf(DislikeEntry("latte", listOf("kefir", "cappuccino")), DislikeEntry("funghi")))
        assertEquals(BanReason.DISLIKE, FoodBans.check("Latte e cereali", profile)?.reason)
        assertNull(FoodBans.check("Kefir di latte con frutta", profile))
        assertNull(FoodBans.check("Cappuccino e cornetto", profile))
        assertNotNull(FoodBans.check("Risotto ai funghi porcini", profile))
        val allergic = profile.copy(allergies = setOf(Allergen.MILK))
        assertEquals(BanReason.ALLERGY, FoodBans.check("Kefir di latte con frutta", allergic)?.reason)
    }

    @Test
    fun `an exception lifts the ban only for itself`() {
        val profile = NutritionProfile(dislikes = listOf(DislikeEntry("latte", listOf("kefir", "cappuccino"))))
        assertNotNull(FoodBans.check("Porridge cotto nel latte, con un bicchiere di kefir", profile))
        assertNotNull(FoodBans.check("Kefir di latte e una tazza di latte caldo", profile))
        assertNull(FoodBans.check("Cappuccino con latte scremato e fette biscottate", profile))
        assertNull(FoodBans.check("Kefir con frutti di bosco", profile))
    }

    @Test
    fun `milk banned and kefir liked`() {
        val profile = NutritionProfile(dislikes = listOf(DislikeEntry("latte")), likes = listOf("kefir"))
        val breakfast = "Colazione Porridge d'avena (80g) cotto in latte parzialmente scremato (250ml), con 25g di proteine del siero del latte"
        assertEquals(BanReason.DISLIKE, FoodBans.check(breakfast, profile)?.reason)
        assertNotNull(FoodBans.check("Proteine del siero del latte", profile))
        assertNull(FoodBans.check("Kefir di latte con avena e banana", profile))
        assertNull(FoodBans.check("Porridge d'avena con kefir e noci", profile))
        assertNotNull(FoodBans.check("Kefir e un bicchiere di latte", profile))
    }

    @Test
    fun `diet, red meat and caffeine`() {
        val veg = NutritionProfile(diet = Diet.VEGETARIAN)
        assertNotNull(FoodBans.check("Petto di pollo alla griglia", veg))
        assertNotNull(FoodBans.check("Spaghetti alle vongole", veg))
        assertNull(FoodBans.check("Ragù di lenticchie", veg))
        assertNull(FoodBans.check("Frittata di zucchine", veg))
        val vegan = NutritionProfile(diet = Diet.VEGAN)
        assertNotNull(FoodBans.check("Frittata di zucchine", vegan))
        assertNotNull(FoodBans.check("Yogurt con miele", vegan))
        assertNull(FoodBans.check("Hamburger vegetale", vegan))
        val noRed = NutritionProfile(redMeatPerWeek = 0)
        assertNotNull(FoodBans.check("Bistecca di manzo", noRed))
        assertNull(FoodBans.check("Pollo al limone", noRed))
        val decaf = NutritionProfile(noCaffeine = true)
        assertNotNull(FoodBans.check("Gel con caffeina", decaf))
        assertNotNull(FoodBans.check("Espresso dopo pranzo", decaf))
        assertNull(FoodBans.check("Caffè d'orzo", decaf))
        assertNull(FoodBans.check("Gel senza caffeina", decaf))
    }

    @Test
    fun `violations name the meal and clean removes it`() {
        val profile = NutritionProfile(allergies = setOf(Allergen.PEANUTS))
        val day = NutritionDay(
            LocalDate.of(2026, 11, 19), DayType.MODERATE, "", 2500, 300, 130, 70,
            listOf(FuelingStep("Prima", "Banana e burro di arachidi"), FuelingStep("Dopo", "Riso e pollo")),
            listOf(MealSuggestion("Colazione", "Porridge e frutta", 500), MealSuggestion("Spuntino", "Noccioline", 200)),
            "",
        )
        val found = FoodBans.violations(day, profile)
        assertEquals(setOf("Prima", "Spuntino"), found.map { it.where }.toSet())
        val clean = FoodBans.clean(day, profile)
        assertEquals(listOf("Colazione"), clean.meals.map { it.name })
        assertEquals(listOf("Dopo"), clean.fueling.map { it.time })
        assertTrue(FoodBans.violations(clean, profile).isEmpty())
    }

    // ---- Energy --------------------------------------------------------------

    private val body = BodyData(weightKg = 72.0, heightCm = 178, age = 40, sex = Sex.MALE)
    private fun session(sport: Sport?, minutes: Int, intensity: Intensity) = PlannedSession(sport, "x", minutes, intensity)

    @Test
    fun `basal metabolism follows Mifflin-St Jeor`() {
        assertEquals(10 * 72 + 6.25 * 178 - 5 * 40 + 5, NutriEnergy.bmr(body, 72.0))
        assertEquals(10 * 60 + 6.25 * 164 - 5 * 35 - 161, NutriEnergy.bmr(BodyData(60.0, null, null, Sex.FEMALE), 60.0))
    }

    @Test
    fun `day type follows the sessions`() {
        assertEquals(DayType.REST, NutriEnergy.dayType(emptyList()))
        assertEquals(DayType.REST, NutriEnergy.dayType(listOf(session(null, 0, Intensity.REST))))
        assertEquals(DayType.LIGHT, NutriEnergy.dayType(listOf(session(Sport.SWIM, 45, Intensity.EASY))))
        assertEquals(DayType.MODERATE, NutriEnergy.dayType(listOf(session(Sport.RUN, 50, Intensity.THRESHOLD))))
        assertEquals(DayType.HEAVY, NutriEnergy.dayType(listOf(session(Sport.BIKE, 180, Intensity.ENDURANCE))))
        assertEquals(DayType.HEAVY, NutriEnergy.dayType(listOf(session(Sport.BIKE, 60, Intensity.THRESHOLD), session(Sport.RUN, 30, Intensity.EASY))))
        assertEquals(DayType.RACE, NutriEnergy.dayType(listOf(session(Sport.RUN, 40, Intensity.RACE))))
    }

    @Test
    fun `targets add the training and periodize carbohydrates`() {
        val profile = NutritionProfile()
        val rest = NutriEnergy.targets(profile, body, emptyList())!!
        val long = NutriEnergy.targets(profile, body, listOf(session(Sport.BIKE, 180, Intensity.ENDURANCE)))!!
        assertEquals(0, rest.trainingKcal)
        assertEquals(((7.0 - 1) * 72 * 3).toInt(), long.trainingKcal)
        assertTrue(rest.carbsG >= 216, "3 g/kg at least; fat over 35% moves to carbohydrates")
        assertEquals(576, long.carbsG)
        assertEquals(130, rest.proteinG)
        assertTrue(long.kcal > rest.kcal + 1000)
        // Energy is the sum of the macros, fat between 0.8 g/kg and 35%.
        listOf(rest, long).forEach { t ->
            assertTrue(kotlin.math.abs(t.kcal - (4 * t.carbsG + 4 * t.proteinG + 9 * t.fatG)) <= 10)
            assertTrue(t.fatG >= 57)
            assertTrue(9.0 * t.fatG <= 0.36 * t.kcal)
        }
        assertNull(NutriEnergy.targets(profile, body.copy(weightKg = null), emptyList()))
    }

    @Test
    fun `losing weight keeps the floors and spares the hard days`() {
        val lose = NutritionProfile(goal = BodyGoal.LOSE)
        val keep = NutritionProfile()
        val restLose = NutriEnergy.targets(lose, body, emptyList())!!
        val restKeep = NutriEnergy.targets(keep, body, emptyList())!!
        assertTrue(restLose.kcal < restKeep.kcal)
        assertTrue(restKeep.kcal - restLose.kcal <= 7.7 * 72 + 10)
        val hard = listOf(session(Sport.BIKE, 120, Intensity.THRESHOLD))
        assertTrue(kotlin.math.abs(NutriEnergy.targets(keep, body, hard)!!.kcal - NutriEnergy.targets(lose, body, hard)!!.kcal) <= 10, "no deficit on hard days")
        val small = BodyData(weightKg = 45.0, heightCm = 150, age = 60, sex = Sex.FEMALE)
        assertTrue(NutriEnergy.targets(lose, small, emptyList())!!.kcal >= 1200)
        assertTrue(NutriEnergy.targets(lose, small.copy(sex = Sex.UNSPECIFIED), emptyList())!!.kcal >= 1500)
    }

    @Test
    fun `the model's numbers are replaced by the app's`() {
        val targets = NutriEnergy.targets(NutritionProfile(), body, emptyList())!!
        val day = NutritionDay(LocalDate.of(2026, 11, 19), DayType.HEAVY, "", 5000, 900, 50, 10, emptyList(), emptyList(), "")
        val fixed = NutriEnergy.apply(day, targets)
        assertEquals(targets.kcal, fixed.kcal)
        assertEquals(DayType.REST, fixed.dayType)
        assertEquals(day, NutriEnergy.apply(day, null))
        val text = NutriEnergy.describe(targets, BodyGoal.MAINTAIN)
        assertTrue(text.contains("${targets.kcal} kcal"))
        assertFalse(text.contains("valori tipici"))
    }

    // ---- Safety --------------------------------------------------------------

    @Test
    fun `signs of an eating disorder stop the model`() {
        listOf(
            "Non mangio da tre giorni, va bene per la gara?",
            "Digiuno da 2 giorni per perdere peso",
            "Salto i pasti per dimagrire più in fretta",
            "Dopo cena mi faccio vomitare",
            "Prendo lassativi dopo le abbuffate",
            "Mi sono abbuffato e mi sento in colpa",
            "Voglio stare a 800 calorie al giorno",
            "Ho perso 4 kg in una settimana",
        ).forEach { assertTrue(NutriSafety.flags(it), it) }
        listOf(
            "Posso fare il lungo in bici a digiuno?",
            "Quanti carboidrati prima della gara?",
            "Mangio 2500 calorie nei giorni di carico",
            "Ho saltato la colazione per fretta",
        ).forEach { assertFalse(NutriSafety.flags(it), it) }
        assertTrue(NutriSafety.REPLY.contains(NutriSafety.HELPLINE))
    }

    // ---- A triathlete's week --------------------------------------------------

    @Test
    fun `the eve of a heavy day and the days before the race carry more carbohydrates`() {
        val profile = NutritionProfile(goal = BodyGoal.LOSE)
        val long = listOf(session(Sport.BIKE, 180, Intensity.ENDURANCE))
        val plain = NutriEnergy.targets(profile, body, emptyList())!!
        val eve = NutriEnergy.targets(profile, body, emptyList(), DayContext(tomorrow = long))!!
        assertEquals("", plain.carbLoad)
        assertEquals((NutriEnergy.EVE_CARBS * 72).toInt(), eve.carbsG)
        assertTrue(eve.carbLoad.contains("vigilia"))
        assertTrue(eve.kcal > plain.kcal, "no deficit on the eve")

        val heavyToday = NutriEnergy.targets(profile, body, long, DayContext(tomorrow = long))!!
        assertEquals("", heavyToday.carbLoad, "a heavy day is already high")

        val half = NutriEnergy.targets(profile, body, emptyList(), DayContext(daysToRace = 2, race = RaceGoal.HALF))!!
        assertEquals(8 * 72, half.carbsG)
        assertTrue(half.carbLoad.contains("gara"))
        assertEquals(10 * 72, NutriEnergy.targets(profile, body, emptyList(), DayContext(daysToRace = 1, race = RaceGoal.FULL))!!.carbsG)
        assertEquals("", NutriEnergy.targets(profile, body, emptyList(), DayContext(daysToRace = 2, race = RaceGoal.OLYMPIC))!!.carbLoad)
        assertEquals((NutriEnergy.EVE_CARBS * 72).toInt(), NutriEnergy.targets(profile, body, emptyList(), DayContext(daysToRace = 1, race = RaceGoal.SPRINT))!!.carbsG)

        val raceDay = NutriEnergy.targets(profile, body, listOf(session(Sport.RUN, 60, Intensity.TEMPO)), DayContext(daysToRace = 0, race = RaceGoal.SPRINT))!!
        assertEquals(DayType.RACE, raceDay.dayType)
        assertTrue(NutriEnergy.describe(eve, BodyGoal.LOSE).contains("vigilia di un giorno pesante"))
    }

    @Test
    fun `fuelling follows the length of the effort, and a brick is one effort`() {
        assertNull(NutriFueling.duringCarbs(45, hard = true, swimOnly = false))
        assertNull(NutriFueling.duringCarbs(75, hard = false, swimOnly = false))
        assertEquals(30..60, NutriFueling.duringCarbs(75, hard = true, swimOnly = false))
        assertEquals(30..60, NutriFueling.duringCarbs(120, hard = false, swimOnly = false))
        assertEquals(60..90, NutriFueling.duringCarbs(180, hard = false, swimOnly = false))
        assertNull(NutriFueling.duringCarbs(75, hard = true, swimOnly = true))

        val swim = session(Sport.SWIM, 45, Intensity.EASY)
        val bike = session(Sport.BIKE, 120, Intensity.ENDURANCE)
        val run = session(Sport.RUN, 20, Intensity.EASY)
        val day = NutriFueling.plan(listOf(swim, bike, run), 72.0)
        assertEquals(2, day.size)
        assertEquals(listOf(Sport.BIKE, Sport.RUN), day[1].sessions.map { it.sport })
        assertEquals(140, day[1].minutes)
        assertEquals(30..60, day[1].duringCarbsPerHour)
        assertNull(day[0].beforeCarbsG)
        assertEquals(72, day[0].afterCarbsG, "another session follows: the recovery cannot wait")
        assertEquals(22, day[0].afterProteinG)
        assertTrue(day[1].sodium)

        val text = NutriFueling.describe(listOf(swim, bike, run), 72.0, DayContext(daysToRace = 1))
        assertTrue(text.contains("bici e corsa di seguito"))
        assertTrue(text.contains("Più sedute nello stesso giorno"))
        assertTrue(text.contains("Il giorno dopo c'è la gara"))
        assertTrue(NutriFueling.describe(emptyList(), 72.0).contains("resta vuoto"))
        assertNull(NutriFueling.plan(listOf(session(Sport.RUN, 40, Intensity.EASY)), 72.0).single().afterCarbsG)
    }
}
