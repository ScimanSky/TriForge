package com.triforge.domain.coach

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** How a dish is counted: grams, millilitres or pieces (an apple, an egg). */
enum class DishUnit(val key: String) {
    GRAM("g"),
    MILLILITRE("ml"),
    PIECE("pezzo"),
    ;

    companion object {
        fun of(key: String?): DishUnit? = when (key?.trim()?.lowercase()) {
            "g", "gr", "grammi" -> GRAM
            "ml" -> MILLILITRE
            "pezzo", "pezzi", "pz", "porzione", "porzioni" -> PIECE
            else -> null
        }
    }
}

/** Where a dish of a meal comes from. */
enum class DishOrigin(val key: String) {
    /** NutriAI wrote it with the day. */
    PLAN("plan"),

    /** The athlete picked it among NutriAI's alternatives. */
    NUTRI_AI("nutriai"),

    /** The athlete wrote it. */
    ATHLETE("athlete"),

    /** NutriAI added it beside a lighter dish the athlete wrote, to keep the day's fuel. */
    ADDED("added"),
    ;

    /** A dish the athlete chose: «Rifai» leaves it where it is. */
    val fixed: Boolean get() = this != PLAN

    companion object {
        fun of(key: String?): DishOrigin = entries.firstOrNull { it.key == key } ?: PLAN
    }
}

/**
 * One dish of a meal: the first course, the main, the side, the bread, the
 * fruit. The numbers are those of its [quantity].
 */
data class Dish(
    val name: String,
    val quantity: Double,
    val unit: DishUnit,
    val kcal: Int,
    val carbsG: Double,
    val proteinG: Double,
    val fatG: Double,
    /** What a composed dish is made of, read by the bans. */
    val ingredients: List<String> = emptyList(),
    val origin: DishOrigin = DishOrigin.PLAN,
    /** The dish this one took the place of, for «Rimetti». */
    val replaced: Dish? = null,
    /** For an [DishOrigin.ADDED] dish: the dish it came with, and leaves with. */
    val addedWith: String? = null,
    /** The athlete wrote a food they said they do not eat and confirmed it: no more a warning. */
    val confirmed: Boolean = false,
) {
    /** Name and ingredients, as the bans read them. */
    fun text(): String = if (ingredients.isEmpty()) name else "$name: ${ingredients.joinToString(", ")}"
}

data class MealTotals(val kcal: Int, val carbsG: Int, val proteinG: Int, val fatG: Int)

/** A dish of a day, by position; [dish] is -1 for a meal stored without dishes. */
data class DishKey(val meal: Int, val dish: Int)

/** What a dish adds or takes away compared with the one it replaces. */
data class DishGap(val kcal: Int, val carbsG: Int, val proteinG: Int)

/** Whether the athlete may put a dish in a meal. */
enum class DishVerdict {
    OK,

    /** A food the athlete said they do not eat, their diet or caffeine: it is their choice, the app asks. */
    CONFIRM,

    /** An allergy or an intolerance: never, not even written by the athlete. */
    BLOCKED,
}

/** What NutriAI makes of a dish the athlete wrote. */
data class DishEstimate(
    /** False when the text is not a food. */
    val understood: Boolean,
    /** The dish in a usual portion. */
    val dish: Dish?,
    /** A food to add when the dish is much lighter than the one it replaces. */
    val complement: Dish? = null,
    val complementWhy: String = "",
)

/** The answer of the app to a dish the athlete wrote. */
sealed interface TypedDish {
    /** An allergy or an intolerance. */
    data class Blocked(val hit: BanHit) : TypedDish

    /** Not a food NutriAI knows. */
    data object Unknown : TypedDish

    data class Ready(
        /** The dish in NutriAI's usual portion: quantities are scaled from it. */
        val base: Dish,
        val dish: Dish,
        val gap: DishGap,
        val complementBase: Dish?,
        val complement: Dish?,
        val complementWhy: String,
        /** Set when the dish holds a food the athlete does not eat: they confirm before it goes in. */
        val confirm: BanHit?,
    ) : TypedDish
}

/**
 * The rules of NutriAI's meals, all the app's: the model proposes dishes, the
 * app sums them, scales them, checks them against the food profile and keeps
 * track of what the athlete changed. No I/O.
 */
object NutriMeals {

    /** The meals of a day are in line within this share of its energy. */
    const val TOLERANCE = 0.05
    const val MAX_ALTERNATIVES = 4
    const val MAX_DISHES = 8
    const val TYPED_MAX = 80

    private val LIMITS: Map<DishUnit, ClosedFloatingPointRange<Double>> = mapOf(
        DishUnit.GRAM to 5.0..1500.0,
        DishUnit.MILLILITRE to 5.0..1500.0,
        DishUnit.PIECE to 0.5..12.0,
    )

    // ---- Numbers -----------------------------------------------------------

    fun totals(dishes: List<Dish>): MealTotals = MealTotals(
        kcal = dishes.sumOf { it.kcal },
        carbsG = dishes.sumOf { it.carbsG }.roundToInt(),
        proteinG = dishes.sumOf { it.proteinG }.roundToInt(),
        fatG = dishes.sumOf { it.fatG }.roundToInt(),
    )

    /** What the day's meals add up to; null when no meal has dishes (a day stored before they existed). */
    fun totals(day: NutritionDay): MealTotals? =
        day.meals.flatMap { it.dishes }.takeIf { it.isNotEmpty() }?.let(::totals)

    /** A quantity as the plan writes it: 5 g or ml, half a piece, within the limits of its unit. */
    fun roundQuantity(quantity: Double, unit: DishUnit): Double {
        val step = if (unit == DishUnit.PIECE) 0.5 else 5.0
        val limits = LIMITS.getValue(unit)
        return (Math.round(quantity / step) * step).coerceIn(limits.start, limits.endInclusive)
    }

    /** «−» and «+»: 5 up to 100 g, 10 up to 300, then 25; half a piece. */
    fun nextQuantity(quantity: Double, unit: DishUnit, direction: Int): Double {
        val step = when {
            unit == DishUnit.PIECE -> 0.5
            quantity < 100 || (quantity == 100.0 && direction < 0) -> 5.0
            quantity < 300 || (quantity == 300.0 && direction < 0) -> 10.0
            else -> 25.0
        }
        val limits = LIMITS.getValue(unit)
        val next = if (direction > 0) Math.floor(quantity / step + 1e-9) * step + step else Math.ceil(quantity / step - 1e-9) * step - step
        return next.coerceIn(limits.start, limits.endInclusive)
    }

    /** [dish] in another [quantity]: its numbers follow in proportion. */
    fun scaled(dish: Dish, quantity: Double): Dish {
        if (dish.quantity <= 0 || quantity == dish.quantity) return dish
        val factor = quantity / dish.quantity
        return dish.copy(
            quantity = quantity,
            kcal = (dish.kcal * factor).roundToInt(),
            carbsG = tenth(dish.carbsG * factor),
            proteinG = tenth(dish.proteinG * factor),
            fatG = tenth(dish.fatG * factor),
        )
    }

    private fun tenth(value: Double): Double = (value * 10).roundToInt() / 10.0

    /**
     * A dish as the model gave it, with energy the app trusts: when the
     * calories and the macronutrients disagree by more than a fifth, the
     * macronutrients win (4, 4 and 9 kcal a gram).
     */
    fun sane(dish: Dish): Dish {
        val fromMacros = 4 * dish.carbsG + 4 * dish.proteinG + 9 * dish.fatG
        val rounded = dish.copy(quantity = roundQuantity(dish.quantity, dish.unit))
        return if (fromMacros > 0 && (dish.kcal <= 0 || abs(dish.kcal - fromMacros) > 0.2 * fromMacros)) rounded.copy(kcal = fromMacros.roundToInt()) else rounded
    }

    /**
     * [dish] in the quantity that gives about [kcal], never under [floor]
     * nor over [cap] times the portion it came in: 1 kg of minestrone is no
     * lunch.
     */
    fun matchKcal(dish: Dish, kcal: Int, floor: Double = 0.6, cap: Double = 1.5): Dish {
        if (dish.kcal <= 0 || kcal <= 0) return dish
        val factor = (kcal.toDouble() / dish.kcal).coerceIn(floor, cap)
        return scaled(dish, roundQuantity(dish.quantity * factor, dish.unit))
    }

    fun gap(new: Dish, old: Dish): DishGap = DishGap(
        kcal = new.kcal - old.kcal,
        carbsG = (new.carbsG - old.carbsG).roundToInt(),
        proteinG = (new.proteinG - old.proteinG).roundToInt(),
    )

    /** «130 g», «250 ml», «1», «1,5». */
    fun amount(dish: Dish): String {
        val number = if (dish.quantity % 1.0 == 0.0) dish.quantity.toInt().toString() else "%.1f".format(Locale.ITALY, dish.quantity)
        return if (dish.unit == DishUnit.PIECE) number else "$number ${dish.unit.key}"
    }

    // ---- Meals -------------------------------------------------------------

    /** [meal] with other [dishes]: its calories are their sum, its description their names. */
    fun rebuilt(meal: MealSuggestion, dishes: List<Dish>): MealSuggestion =
        if (dishes.isEmpty()) meal.copy(dishes = dishes) else meal.copy(description = describe(dishes), kcal = dishes.sumOf { it.kcal }, dishes = dishes)

    /** «Spaghetti al pomodoro, petto di pollo alla piastra, mela». */
    fun describe(dishes: List<Dish>): String =
        dishes.mapIndexed { index, dish -> if (index == 0) dish.name else dish.name.replaceFirstChar { it.lowercase(Locale.ITALIAN) } }.joinToString(", ")

    /**
     * The day as NutriAI wrote it, made the app's: every dish with energy the
     * app trusts and every meal the sum of its dishes. A meal the model left
     * without dishes has nothing to show and goes.
     */
    fun sanitized(day: NutritionDay): NutritionDay = day.copy(
        meals = day.meals.filter { it.dishes.isNotEmpty() }.map { meal -> rebuilt(meal, meal.dishes.take(MAX_DISHES).map(::sane)) },
    )

    /**
     * Brings the meals in line with the day's energy when the model missed
     * it by more than [TOLERANCE]: the dishes the athlete chose stay as they
     * are, the others are scaled, by a quarter at most.
     */
    fun settle(day: NutritionDay): NutritionDay {
        val all = day.meals.flatMap { it.dishes }
        if (all.isEmpty() || day.kcal <= 0) return day
        val total = all.sumOf { it.kcal }
        if (abs(total - day.kcal) <= TOLERANCE * day.kcal) return day
        val fixed = all.filter { it.origin.fixed }.sumOf { it.kcal }
        val free = total - fixed
        if (free <= 0) return day
        val factor = ((day.kcal - fixed).toDouble() / free).coerceIn(0.75, 1.3)
        return day.copy(
            meals = day.meals.map { meal ->
                rebuilt(meal, meal.dishes.map { if (it.origin.fixed) it else scaled(it, roundQuantity(it.quantity * factor, it.unit)) })
            },
        )
    }

    // ---- Supplements ------------------------------------------------------

    /** The supplement of the food profile [dish] is, or null: «Creatina monoidrato» is the athlete's «creatina». */
    fun supplementOf(dish: Dish, profile: NutritionProfile?): Supplement? {
        val name = FoodBans.normalize(dish.name)
        return profile?.supplements?.firstOrNull { supplement ->
            val known = FoodBans.normalize(supplement.name)
            known.length >= 3 && (name == known || Regex("\\b${Regex.escape(known)}\\b").containsMatchIn(name))
        }
    }

    private val DOSE = Regex("(\\d+(?:[.,]\\d+)?)\\s*(mg|g|gr|grammi|ml|[a-zà-ù]+)?", RegexOption.IGNORE_CASE)

    /** «5 g», «500 mg», «2 capsule», «1 misurino»: the quantity and unit of a dose, or null when it has no number. */
    fun dose(text: String): Pair<Double, DishUnit>? {
        val match = DOSE.find(text.trim()) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return when (match.groupValues[2].lowercase()) {
            "g", "gr", "grammi" -> value to DishUnit.GRAM
            "mg" -> value / 1000 to DishUnit.GRAM
            "ml" -> value to DishUnit.MILLILITRE
            else -> value to DishUnit.PIECE
        }
    }

    /**
     * The supplements of the day in the dose of the food profile: the
     * athlete takes what they take, the same every day, and NutriAI does not
     * decide how much.
     */
    fun fixSupplements(day: NutritionDay, profile: NutritionProfile): NutritionDay {
        if (profile.supplements.isEmpty()) return day
        return day.copy(
            meals = day.meals.map { meal ->
                val dishes = meal.dishes.map { dish ->
                    val (quantity, unit) = supplementOf(dish, profile)?.let { dose(it.dose) } ?: return@map dish
                    if (unit == dish.unit) scaled(dish, quantity) else dish.copy(quantity = quantity, unit = unit)
                }
                if (dishes == meal.dishes) meal else rebuilt(meal, dishes)
            },
        )
    }

    /** The dishes the athlete chose, by meal: what «Rifai» must leave in place. */
    fun fixed(day: NutritionDay): List<Pair<String, List<Dish>>> =
        day.meals.mapNotNull { meal -> meal.dishes.filter { it.origin.fixed }.takeIf { it.isNotEmpty() }?.let { meal.name to it } }

    /**
     * [fresh], a day just written again, with the dishes the athlete chose in
     * [old] back in their meals (found by name, else by position).
     */
    fun keepFixed(fresh: NutritionDay, old: NutritionDay?): NutritionDay {
        old ?: return fresh
        var meals = fresh.meals
        old.meals.forEachIndexed { index, oldMeal ->
            val keep = oldMeal.dishes.filter { it.origin.fixed }
            if (keep.isEmpty()) return@forEachIndexed
            val at = meals.indexOfFirst { FoodBans.normalize(it.name) == FoodBans.normalize(oldMeal.name) }.takeIf { it >= 0 }
                ?: index.takeIf { it < meals.size }
            if (at == null) {
                meals = meals + rebuilt(oldMeal, keep)
                return@forEachIndexed
            }
            val names = keep.map { FoodBans.normalize(it.name) }.toSet()
            val others = meals[at].dishes.filter { FoodBans.normalize(it.name) !in names }
            meals = meals.toMutableList().also { it[at] = rebuilt(meals[at], keep + others) }
        }
        return fresh.copy(meals = meals)
    }

    /**
     * The main dish of every lunch and dinner of [days], the one with most
     * energy: what NutriAI should not serve again when it writes another day
     * of the same week.
     */
    fun mains(days: Collection<NutritionDay>, limit: Int = 14): List<String> =
        days.sortedBy { it.date }
            .flatMap { day -> day.meals.filter { it.dishes.size >= 2 }.mapNotNull { meal -> meal.dishes.maxByOrNull { it.kcal }?.name } }
            .distinctBy(FoodBans::normalize)
            .takeLast(limit)

    /**
     * The meals of the days next to the one NutriAI is about to write, as
     * text: what it should not serve again the day after.
     */
    fun nearby(days: Collection<NutritionDay>, date: LocalDate): String = days
        .filter { it.date == date.minusDays(1) || it.date == date.plusDays(1) }
        .sortedBy { it.date }
        .joinToString("\n") { day ->
            (if (day.date.isBefore(date)) "Il giorno prima" else "Il giorno dopo") + ": " +
                day.meals.filter { it.dishes.isNotEmpty() }.joinToString("; ") { meal -> "${meal.name}: ${describe(meal.dishes)}" }
        }

    /** Whether the meals were written for other numbers than the app's of today: the plan changed since. */
    fun outdated(day: NutritionDay, targets: DayTargets): Boolean =
        day.meals.isNotEmpty() &&
            (abs(targets.kcal - day.kcal) > 0.04 * day.kcal || abs(targets.carbsG - day.carbsG) > 0.08 * max(day.carbsG, 1))

    // ---- The food profile --------------------------------------------------

    fun verdict(hit: BanHit?): DishVerdict = when (hit?.reason) {
        null -> DishVerdict.OK
        BanReason.ALLERGY, BanReason.INTOLERANCE -> DishVerdict.BLOCKED
        BanReason.DIET, BanReason.DISLIKE, BanReason.CAFFEINE -> DishVerdict.CONFIRM
    }

    /** The ban [dish] breaks, or null; a food the athlete confirmed is theirs to eat, an allergy never. */
    fun check(dish: Dish, profile: NutritionProfile): BanHit? =
        FoodBans.check(dish.text(), profile)?.takeUnless { dish.confirmed && verdict(it) == DishVerdict.CONFIRM }

    /**
     * The dishes of [day] the profile bans now, by position: a day written
     * before the profile changed is not rewritten, it is marked.
     */
    fun flags(day: NutritionDay, profile: NutritionProfile): Map<DishKey, BanHit> = buildMap {
        day.meals.forEachIndexed { m, meal ->
            if (meal.dishes.isEmpty()) {
                FoodBans.check("${meal.name} ${meal.description}", profile)?.let { put(DishKey(m, -1), it) }
            } else {
                meal.dishes.forEachIndexed { d, dish -> check(dish, profile)?.let { put(DishKey(m, d), it) } }
            }
        }
    }

    /** Whether [dish] is, or holds, one of the athlete's favourite foods. */
    fun liked(dish: Dish, profile: NutritionProfile): Boolean {
        val text = FoodBans.normalize(dish.text())
        return profile.likes.map(FoodBans::normalize).any { it.isNotEmpty() && text.contains(it) }
    }

    // ---- Changing a dish ---------------------------------------------------

    /**
     * NutriAI's [candidates] for the place of [target], as the app offers
     * them: none the profile bans, none already there or already seen, each
     * in the quantity that gives the calories of [target].
     */
    fun alternatives(candidates: List<Dish>, target: Dish, profile: NutritionProfile, exclude: Collection<String> = emptyList()): List<Dish> {
        val seen = (exclude + target.name).map(FoodBans::normalize).toSet()
        return candidates.asSequence()
            .map(::sane)
            .filter { it.kcal > 0 && FoodBans.normalize(it.name) !in seen && FoodBans.check(it.text(), profile) == null }
            .distinctBy { FoodBans.normalize(it.name) }
            .map { matchKcal(it, target.kcal).copy(origin = DishOrigin.NUTRI_AI, replaced = null, addedWith = null, confirmed = false) }
            .take(MAX_ALTERNATIVES)
            .toList()
    }

    /** What the athlete wrote, before any model: an allergy or an intolerance in the words themselves stops there. */
    fun precheck(typed: String, profile: NutritionProfile): BanHit? =
        FoodBans.check(typed, profile)?.takeIf { verdict(it) == DishVerdict.BLOCKED }

    /**
     * The dish the athlete wrote in place of [target], from NutriAI's
     * [estimate]: checked against the profile (the athlete's words and the
     * ingredients NutriAI names), in a usual portion moved towards the
     * calories of [target] by a quarter at most, with what it leaves out
     * and, when that is a lot, the food NutriAI adds to make up for it.
     */
    fun typed(typed: String, estimate: DishEstimate, target: Dish, profile: NutritionProfile): TypedDish {
        val raw = estimate.dish?.takeIf { estimate.understood } ?: return TypedDish.Unknown
        val base = sane(raw).copy(origin = DishOrigin.ATHLETE, replaced = null, addedWith = null, confirmed = false)
        if (base.kcal <= 0) return TypedDish.Unknown
        val hit = FoodBans.check("$typed. ${base.text()}", profile)
        if (verdict(hit) == DishVerdict.BLOCKED) return TypedDish.Blocked(hit!!)
        val complement = estimate.complement?.let(::sane)?.takeIf { it.kcal > 0 && FoodBans.check(it.text(), profile) == null }
            ?.copy(origin = DishOrigin.ADDED, replaced = null, confirmed = false)
        return ready(base, matchKcal(base, target.kcal, floor = 0.75, cap = 1.25).quantity, target, complement, estimate.complementWhy, hit)
    }

    /** [ready] in another quantity, chosen with «−» and «+». */
    fun requantified(ready: TypedDish.Ready, quantity: Double, target: Dish): TypedDish.Ready =
        ready(ready.base, quantity, target, ready.complementBase, ready.complementWhy, ready.confirm)

    private fun ready(base: Dish, quantity: Double, target: Dish, complementBase: Dish?, why: String, confirm: BanHit?): TypedDish.Ready {
        val dish = scaled(base, quantity)
        val gap = gap(dish, target)
        // Worth making up for: at least 100 kcal and a fifth of the dish it replaces.
        val missing = -gap.kcal
        val complement = complementBase?.takeIf { missing >= 100 && missing >= 0.2 * target.kcal }
            ?.let { matchKcal(it, missing, floor = 0.4, cap = 1.5).copy(addedWith = dish.name) }
        return TypedDish.Ready(base, dish, gap, complementBase, complement, why, confirm)
    }

    /**
     * [day] with the dish at [key] replaced by [with], and [extra] beside
     * it. The new dish remembers the one it replaces; what NutriAI had added
     * beside the old one leaves with it. Null when the dish is not there.
     */
    fun replace(day: NutritionDay, key: DishKey, with: Dish, extra: Dish? = null): NutritionDay? {
        val meal = day.meals.getOrNull(key.meal) ?: return null
        val old = meal.dishes.getOrNull(key.dish) ?: return null
        val before = old.replaced ?: old.copy(replaced = null)
        val new = with.copy(replaced = before.copy(replaced = null))
        val dishes = buildList {
            meal.dishes.forEachIndexed { index, dish ->
                when {
                    index == key.dish -> {
                        add(new)
                        extra?.let { add(it.copy(origin = DishOrigin.ADDED, addedWith = new.name, replaced = null)) }
                    }
                    dish.origin == DishOrigin.ADDED && dish.addedWith == old.name -> Unit
                    else -> add(dish)
                }
            }
        }
        return withMeal(day, key.meal, rebuilt(meal, dishes))
    }

    /** «Rimetti»: the dish that was at [key] before the change, back; null when there is none to put back. */
    fun restore(day: NutritionDay, key: DishKey): NutritionDay? {
        val meal = day.meals.getOrNull(key.meal) ?: return null
        val current = meal.dishes.getOrNull(key.dish) ?: return null
        val before = current.replaced ?: return null
        val dishes = buildList {
            meal.dishes.forEachIndexed { index, dish ->
                when {
                    index == key.dish -> add(before)
                    dish.origin == DishOrigin.ADDED && dish.addedWith == current.name -> Unit
                    else -> add(dish)
                }
            }
        }
        return withMeal(day, key.meal, rebuilt(meal, dishes))
    }

    private fun withMeal(day: NutritionDay, index: Int, meal: MealSuggestion): NutritionDay =
        day.copy(meals = day.meals.toMutableList().also { it[index] = meal })
}
