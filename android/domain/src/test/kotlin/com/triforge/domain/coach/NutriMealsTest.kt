package com.triforge.domain.coach

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutriMealsTest {

    private fun dish(name: String, quantity: Double, kcal: Int, carbs: Double, protein: Double, fat: Double, vararg ingredients: String) =
        Dish(name, quantity, DishUnit.GRAM, kcal, carbs, protein, fat, ingredients.toList())

    private val spaghetti = dish("Spaghetti al pomodoro", 130.0, 490, 92.0, 16.0, 6.0, "pasta di semola", "pomodoro", "olio")
    private val chicken = dish("Petto di pollo alla piastra", 150.0, 250, 0.0, 46.0, 6.0)
    private val zucchini = dish("Zucchine saltate con olio", 200.0, 130, 8.0, 4.0, 10.0)
    private val apple = Dish("Mela", 1.0, DishUnit.PIECE, 60, 14.0, 0.0, 0.0)
    private val minestrone = dish("Minestrone di verdure con pasta", 350.0, 210, 33.0, 9.0, 4.5, "verdure", "pasta", "olio")
    private val bread = dish("Pane integrale", 100.0, 250, 50.0, 9.0, 2.0, "farina integrale")

    private fun day(vararg meals: MealSuggestion, kcal: Int = 930) = NutritionDay(
        date = LocalDate.of(2026, 11, 24),
        dayType = DayType.HEAVY,
        headline = "Giorno duro · nuoto soglia",
        kcal = kcal,
        carbsG = 122,
        proteinG = 55,
        fatG = 25,
        fueling = emptyList(),
        meals = meals.toList(),
        rationale = "",
    )

    private fun lunch(vararg dishes: Dish) = NutriMeals.rebuilt(MealSuggestion("Pranzo", "", null), dishes.toList())

    private val monday = day(lunch(spaghetti, chicken, zucchini, apple))

    // ---- Numbers ---------------------------------------------------------------

    @Test
    fun `a meal is the sum of its dishes and is described by their names`() {
        val meal = monday.meals.single()
        assertEquals(930, meal.kcal)
        assertEquals("Spaghetti al pomodoro, petto di pollo alla piastra, zucchine saltate con olio, mela", meal.description)
        assertEquals(MealTotals(930, 114, 66, 22), NutriMeals.totals(monday))
        assertNull(NutriMeals.totals(day(MealSuggestion("Pranzo", "Riso e pollo", 900))))
    }

    @Test
    fun `a dish scales in proportion and quantities are rounded as a plan writes them`() {
        val half = NutriMeals.scaled(spaghetti, 65.0)
        assertEquals(245, half.kcal)
        assertEquals(46.0, half.carbsG)
        assertEquals(125.0, NutriMeals.roundQuantity(127.0, DishUnit.GRAM))
        assertEquals(1.5, NutriMeals.roundQuantity(1.4, DishUnit.PIECE))
        assertEquals(0.5, NutriMeals.roundQuantity(0.1, DishUnit.PIECE))
        assertEquals("130 g", NutriMeals.amount(spaghetti))
        assertEquals("1", NutriMeals.amount(apple))
        assertEquals("1,5", NutriMeals.amount(apple.copy(quantity = 1.5)))
    }

    @Test
    fun `minus and plus move by steps that grow with the quantity`() {
        assertEquals(85.0, NutriMeals.nextQuantity(80.0, DishUnit.GRAM, 1))
        assertEquals(95.0, NutriMeals.nextQuantity(100.0, DishUnit.GRAM, -1))
        assertEquals(110.0, NutriMeals.nextQuantity(100.0, DishUnit.GRAM, 1))
        assertEquals(410.0 + 15.0, NutriMeals.nextQuantity(400.0, DishUnit.GRAM, 1))
        assertEquals(375.0, NutriMeals.nextQuantity(400.0, DishUnit.GRAM, -1))
        assertEquals(5.0, NutriMeals.nextQuantity(5.0, DishUnit.GRAM, -1))
        assertEquals(1.5, NutriMeals.nextQuantity(1.0, DishUnit.PIECE, 1))
        assertEquals(0.5, NutriMeals.nextQuantity(0.5, DishUnit.PIECE, -1))
    }

    @Test
    fun `calories that disagree with the macronutrients are the app's`() {
        val wrong = dish("Riso basmati", 100.0, 900, 78.0, 8.0, 1.0)
        assertEquals(353, NutriMeals.sane(wrong).kcal)
        assertEquals(490, NutriMeals.sane(spaghetti).kcal)
        // Without macronutrients the calories stay as written.
        assertEquals(60, NutriMeals.sane(Dish("Mela", 1.0, DishUnit.PIECE, 60, 0.0, 0.0, 0.0)).kcal)
    }

    @Test
    fun `meals far from the day's energy are scaled, the athlete's dishes stay`() {
        val chosen = minestrone.copy(origin = DishOrigin.ATHLETE)
        val over = day(lunch(spaghetti, chicken, zucchini, chosen), kcal = 900)
        val settled = NutriMeals.settle(over)
        val total = NutriMeals.totals(settled)!!.kcal
        assertTrue(kotlin.math.abs(total - 900) <= 45, "total $total")
        assertEquals(chosen, settled.meals.single().dishes.last())
        // Within the tolerance nothing moves.
        assertEquals(monday, NutriMeals.settle(monday))
    }

    // ---- The food profile ------------------------------------------------------

    @Test
    fun `a banned dish is marked, not removed, and ingredients count`() {
        val lactose = NutritionProfile(intolerances = setOf(Intolerance.LACTOSE))
        val risotto = dish("Risotto allo zafferano", 120.0, 520, 90.0, 12.0, 12.0, "riso", "burro", "parmigiano")
        val flags = NutriMeals.flags(day(lunch(risotto, chicken)), lactose)
        assertEquals(setOf(DishKey(0, 0)), flags.keys)
        assertEquals(BanReason.INTOLERANCE, flags.getValue(DishKey(0, 0)).reason)
        // A meal stored before dishes existed is read as a whole.
        val old = day(MealSuggestion("Colazione", "Latte parzialmente scremato e biscotti", 400))
        assertEquals(setOf(DishKey(0, -1)), NutriMeals.flags(old, lactose).keys)
    }

    @Test
    fun `cleaning a day drops the banned dishes and keeps the meal`() {
        val nuts = NutritionProfile(allergies = setOf(Allergen.NUTS))
        val pesto = dish("Trofie al pesto di noci", 120.0, 560, 85.0, 16.0, 18.0)
        val broken = day(lunch(pesto, chicken), lunch(pesto).copy(name = "Cena"))
        assertEquals(2, FoodBans.violations(broken, nuts).size)
        assertContains(FoodBans.violations(broken, nuts).first().text(), "Trofie al pesto di noci")
        val clean = FoodBans.clean(broken, nuts)
        assertEquals(listOf("Pranzo"), clean.meals.map { it.name })
        assertEquals(listOf(chicken), clean.meals.single().dishes)
        assertEquals(250, clean.meals.single().kcal)
    }

    @Test
    fun `allergies and intolerances block, what the athlete does not eat asks`() {
        val profile = NutritionProfile(
            allergies = setOf(Allergen.NUTS),
            dislikes = listOf(DislikeEntry("funghi")),
            noCaffeine = true,
        )
        assertEquals(DishVerdict.BLOCKED, NutriMeals.verdict(FoodBans.check("trofie al pesto di noci", profile)))
        assertEquals(DishVerdict.CONFIRM, NutriMeals.verdict(FoodBans.check("risotto ai funghi", profile)))
        assertEquals(DishVerdict.CONFIRM, NutriMeals.verdict(FoodBans.check("caffè", profile)))
        assertEquals(DishVerdict.OK, NutriMeals.verdict(FoodBans.check("minestrone", profile)))
        assertNotNull(NutriMeals.precheck("trofie al pesto di noci", profile))
        assertNull(NutriMeals.precheck("risotto ai funghi", profile))
        // Confirmed by the athlete: no more a warning. An allergy is never confirmed away.
        val mushrooms = dish("Risotto ai funghi", 120.0, 500, 88.0, 12.0, 10.0)
        assertNotNull(NutriMeals.check(mushrooms, profile))
        assertNull(NutriMeals.check(mushrooms.copy(confirmed = true), profile))
        assertNotNull(NutriMeals.check(dish("Torta di noci", 80.0, 320, 30.0, 6.0, 20.0).copy(confirmed = true), profile))
    }

    // ---- Changing a dish -------------------------------------------------------

    @Test
    fun `alternatives are filtered by the profile and brought to the dish's calories`() {
        val profile = NutritionProfile(allergies = setOf(Allergen.NUTS), dislikes = listOf(DislikeEntry("funghi")))
        val candidates = listOf(
            dish("Riso basmati al pomodoro", 100.0, 390, 80.0, 8.0, 4.0),
            dish("Trofie al pesto", 120.0, 560, 85.0, 16.0, 18.0, "pasta", "basilico", "pinoli", "noci"),
            dish("Risotto ai funghi", 120.0, 500, 88.0, 12.0, 10.0),
            dish("Spaghetti al pomodoro", 130.0, 490, 92.0, 16.0, 6.0),
            dish("Gnocchi di patate al pomodoro", 300.0, 450, 88.0, 10.0, 6.0),
            dish("riso basmati al pomodoro", 110.0, 400, 82.0, 8.0, 4.0),
            dish("Cous cous con verdure", 120.0, 480, 90.0, 14.0, 6.0),
        )
        val offered = NutriMeals.alternatives(candidates, spaghetti, profile, exclude = listOf("Cous cous con verdure"))
        assertEquals(listOf("Riso basmati al pomodoro", "Gnocchi di patate al pomodoro"), offered.map { it.name })
        val rice = offered.first()
        assertEquals(125.0, rice.quantity)
        assertTrue(kotlin.math.abs(rice.kcal - 490) <= 10, "kcal ${rice.kcal}")
        assertEquals(DishOrigin.NUTRI_AI, rice.origin)
    }

    @Test
    fun `a lighter dish written by the athlete comes with what it leaves out and a complement`() {
        val estimate = DishEstimate(understood = true, dish = minestrone, complement = bread, complementWhy = "Stasera hai la soglia a nuoto.")
        val ready = assertIs<TypedDish.Ready>(NutriMeals.typed("minestrone", estimate, spaghetti, NutritionProfile()))
        // A usual portion, a quarter more at most: not a kilo of minestrone.
        assertEquals(440.0, ready.dish.quantity)
        assertEquals(DishOrigin.ATHLETE, ready.dish.origin)
        assertEquals(ready.dish.kcal - 490, ready.gap.kcal)
        assertTrue(ready.gap.carbsG < -40)
        val complement = assertNotNull(ready.complement)
        assertEquals(DishOrigin.ADDED, complement.origin)
        assertEquals(ready.dish.name, complement.addedWith)
        assertTrue(kotlin.math.abs(ready.dish.kcal + complement.kcal - 490) <= 15)
        assertNull(ready.confirm)
        // More minestrone, less bread; none when the dish is as rich as the old one.
        val more = NutriMeals.requantified(ready, 525.0, spaghetti)
        assertTrue(more.complement!!.kcal < complement.kcal)
        val same = NutriMeals.typed("riso", DishEstimate(true, dish("Riso basmati", 120.0, 430, 94.0, 9.0, 1.0), bread), spaghetti, NutritionProfile())
        assertNull(assertIs<TypedDish.Ready>(same).complement)
    }

    @Test
    fun `a written dish is blocked by an allergy in its ingredients too`() {
        val nuts = NutritionProfile(allergies = setOf(Allergen.NUTS), dislikes = listOf(DislikeEntry("funghi")))
        val pesto = DishEstimate(true, dish("Trofie al pesto", 120.0, 560, 85.0, 16.0, 18.0, "pasta", "basilico", "noci"))
        val blocked = assertIs<TypedDish.Blocked>(NutriMeals.typed("trofie al pesto", pesto, spaghetti, nuts))
        assertEquals(BanReason.ALLERGY, blocked.hit.reason)
        val mushrooms = DishEstimate(true, dish("Risotto ai funghi", 120.0, 500, 88.0, 12.0, 10.0))
        val ask = assertIs<TypedDish.Ready>(NutriMeals.typed("risotto ai funghi", mushrooms, spaghetti, nuts))
        assertEquals(BanReason.DISLIKE, ask.confirm?.reason)
        assertIs<TypedDish.Unknown>(NutriMeals.typed("asdf", DishEstimate(false, null), spaghetti, nuts))
        // A complement the profile bans is not offered.
        val nutty = DishEstimate(true, minestrone, dish("Noci", 30.0, 200, 2.0, 5.0, 19.0))
        assertNull(assertIs<TypedDish.Ready>(NutriMeals.typed("minestrone", nutty, spaghetti, nuts)).complement)
    }

    @Test
    fun `replacing a dish recomputes the meal and remembers the dish of before`() {
        val ready = assertIs<TypedDish.Ready>(NutriMeals.typed("minestrone", DishEstimate(true, minestrone, bread), spaghetti, NutritionProfile()))
        val changed = assertNotNull(NutriMeals.replace(monday, DishKey(0, 0), ready.dish, ready.complement))
        val meal = changed.meals.single()
        assertEquals(listOf("Minestrone di verdure con pasta", "Pane integrale", "Petto di pollo alla piastra", "Zucchine saltate con olio", "Mela"), meal.dishes.map { it.name })
        assertEquals(meal.dishes.sumOf { it.kcal }, meal.kcal)
        assertEquals(spaghetti, meal.dishes.first().replaced)
        assertEquals(listOf("Pranzo" to meal.dishes.take(2)), NutriMeals.fixed(changed))

        // A second change keeps the plan's dish as the one to put back, and takes the bread away.
        val rice = dish("Riso basmati al pomodoro", 125.0, 485, 96.0, 9.0, 4.0).copy(origin = DishOrigin.NUTRI_AI)
        val again = assertNotNull(NutriMeals.replace(changed, DishKey(0, 0), rice))
        assertEquals(listOf("Riso basmati al pomodoro", "Petto di pollo alla piastra", "Zucchine saltate con olio", "Mela"), again.meals.single().dishes.map { it.name })
        assertEquals(spaghetti, again.meals.single().dishes.first().replaced)

        // «Rimetti» brings the day back as it was.
        assertEquals(monday, NutriMeals.restore(changed, DishKey(0, 0)))
        assertEquals(monday, NutriMeals.restore(again, DishKey(0, 0)))
        assertNull(NutriMeals.restore(monday, DishKey(0, 0)))
        assertNull(NutriMeals.replace(monday, DishKey(3, 0), rice))
    }

    @Test
    fun `a day written again keeps the dishes the athlete chose`() {
        val chosen = minestrone.copy(origin = DishOrigin.ATHLETE, replaced = spaghetti)
        val old = day(lunch(chosen, chicken))
        val fresh = day(lunch(dish("Farro con verdure", 120.0, 480, 85.0, 16.0, 8.0), dish("minestrone di verdure con pasta", 300.0, 180, 28.0, 8.0, 4.0)))
        val kept = NutriMeals.keepFixed(fresh, old)
        assertEquals(listOf("Minestrone di verdure con pasta", "Farro con verdure"), kept.meals.single().dishes.map { it.name })
        assertEquals(chosen, kept.meals.single().dishes.first())
        assertEquals(fresh, NutriMeals.keepFixed(fresh, null))
        assertEquals(fresh, NutriMeals.keepFixed(fresh, monday))
    }

    @Test
    fun `meals written for other numbers are outdated`() {
        fun targets(kcal: Int, carbs: Int) = DayTargets(DayType.HEAVY, kcal, carbs, 55, 25, 1650, 500, false)
        assertFalse(NutriMeals.outdated(monday, targets(930, 122)))
        assertFalse(NutriMeals.outdated(monday, targets(950, 125)))
        assertTrue(NutriMeals.outdated(monday, targets(1010, 122)))
        assertTrue(NutriMeals.outdated(monday, targets(930, 105)))
        assertFalse(NutriMeals.outdated(day(), targets(1500, 300)))
    }

    // ---- Store and prompts -----------------------------------------------------

    @Test
    fun `a day with dishes survives the store, with what was replaced`() {
        val ready = assertIs<TypedDish.Ready>(NutriMeals.typed("minestrone", DishEstimate(true, minestrone, bread), spaghetti, NutritionProfile()))
        val changed = NutriMeals.replace(monday, DishKey(0, 0), ready.dish.copy(confirmed = true), ready.complement)!!
        assertEquals(changed, CoachMapper.nutritionDayFromFields(CoachMapper.nutritionDayToFields(changed)))
        // A day stored before dishes existed still reads.
        val old = day(MealSuggestion("Pranzo", "Riso e pollo", 900))
        assertEquals(old, CoachMapper.nutritionDayFromFields(CoachMapper.nutritionDayToFields(old)))
    }

    @Test
    fun `the model's dishes are read loosely and never carry the app's marks`() {
        val fields = mapOf(
            "alternatives" to listOf(
                mapOf("name" to "Riso basmati", "quantity" to 120, "unit" to "g", "kcal" to 430, "carbsG" to 94, "proteinG" to 9, "fatG" to 1, "origin" to "athlete", "confirmed" to true),
                mapOf("name" to "", "quantity" to 100, "kcal" to 100),
                mapOf("name" to "Senza quantità", "kcal" to 100),
                mapOf("name" to "Mela", "quantity" to "1", "unit" to "pezzi", "kcal" to 60),
            ),
        )
        val dishes = CoachMapper.dishAlternativesFromFields(fields)
        assertEquals(listOf("Riso basmati", "Mela"), dishes.map { it.name })
        assertEquals(DishOrigin.PLAN, dishes.first().origin)
        assertFalse(dishes.first().confirmed)
        assertEquals(DishUnit.PIECE, dishes.last().unit)

        val estimate = CoachMapper.dishEstimateFromFields(
            mapOf(
                "understood" to true, "name" to "Minestrone", "quantity" to 350, "unit" to "g", "kcal" to 210, "carbsG" to 33, "proteinG" to 9, "fatG" to 4.5,
                "ingredients" to listOf("verdure", "pasta", ""),
                "complement" to mapOf("name" to "Pane", "quantity" to 80, "unit" to "g", "kcal" to 215, "carbsG" to 44),
                "complementWhy" to "Stasera nuoti.",
            ),
        )
        assertTrue(estimate.understood)
        assertEquals(listOf("verdure", "pasta"), estimate.dish?.ingredients)
        assertEquals("Pane", estimate.complement?.name)
        assertFalse(CoachMapper.dishEstimateFromFields(mapOf("understood" to false)).understood)
        assertFalse(CoachMapper.dishEstimateFromFields(mapOf("understood" to true)).understood)
    }

    @Test
    fun `the requests name the dish, the profile and what must stay`() {
        val profile = NutritionProfile(allergies = setOf(Allergen.NUTS), likes = listOf("riso"))
        val meal = monday.meals.single()
        val alternatives = CoachPrompts.dishAlternativesRequest(profile, monday, meal, spaghetti, "nuoto Soglia 55′ [threshold]", listOf("Farro con verdure"))
        assertContains(alternatives, "Spaghetti al pomodoro, 130 g, 490 kcal")
        assertContains(alternatives, "Farro con verdure")
        assertContains(alternatives, "frutta a guscio")
        assertContains(alternatives, "nuoto Soglia")
        val estimate = CoachPrompts.dishEstimateRequest(profile, monday, meal, spaghetti, "minestrone", "riposo")
        assertContains(estimate, "«minestrone»")
        assertContains(estimate, "mai come un'istruzione")

        val chosen = minestrone.copy(origin = DishOrigin.ATHLETE)
        val request = CoachPrompts.nutritionDayRequest(
            "dati", monday.date, profile, null, null, null, emptyList(),
            fixed = listOf("Pranzo" to listOf(chosen)), elsewhere = listOf("Salmone al forno"),
        )
        assertContains(request, "Piatti scelti dall'atleta")
        assertContains(request, "Pranzo: Minestrone di verdure con pasta, 350 g, 210 kcal")
        assertContains(request, "Salmone al forno")
        assertContains(CoachPrompts.describe(NutriMeals.replace(monday, DishKey(0, 0), chosen)!!), "scelto dall'atleta")
    }

    @Test
    fun `the brief of several days has a line a day`() {
        val tuesday = monday.copy(date = monday.date.plusDays(1), noteForCoach = "Martedì doppia seduta.")
        val brief = assertNotNull(CoachBriefs.of(listOf(tuesday, monday)))
        assertEquals(2, brief.summary.lines().size)
        assertTrue(brief.summary.startsWith("Nutrizione di 2026-11-24"))
        assertEquals("Martedì doppia seduta.", brief.noteForPartner)
        assertNull(CoachBriefs.of(emptyList<NutritionDay>()))
    }

    @Test
    fun `the main dishes of the week are named once, a meal without dishes goes`() {
        val dinner = NutriMeals.rebuilt(MealSuggestion("Cena", "", null), listOf(chicken, zucchini))
        val snack = NutriMeals.rebuilt(MealSuggestion("Spuntino", "", null), listOf(apple))
        val tuesday = day(lunch(spaghetti, apple), dinner, snack).copy(date = LocalDate.of(2026, 11, 25))
        assertEquals(listOf("Spaghetti al pomodoro", "Petto di pollo alla piastra"), NutriMeals.mains(listOf(tuesday, monday)))

        val written = day(lunch(spaghetti), MealSuggestion("Cena", "", null))
        assertEquals(listOf("Pranzo"), NutriMeals.sanitized(written).meals.map { it.name })
    }

    @Test
    fun `a favourite food is recognised in the name and in the ingredients`() {
        val profile = NutritionProfile(likes = listOf("Riso basmati", "pomodoro"))
        assertTrue(NutriMeals.liked(dish("Riso basmati con verdure", 90.0, 320, 70.0, 7.0, 1.0), profile))
        assertTrue(NutriMeals.liked(spaghetti, profile))
        assertFalse(NutriMeals.liked(chicken, profile))
    }
}
