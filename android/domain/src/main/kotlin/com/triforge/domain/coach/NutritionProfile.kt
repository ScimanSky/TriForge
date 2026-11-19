package com.triforge.domain.coach

enum class Diet(val key: String, val label: String) {
    OMNIVORE("omnivore", "Onnivoro"),
    PESCATARIAN("pescatarian", "Pescetariano"),
    VEGETARIAN("vegetarian", "Vegetariano"),
    VEGAN("vegan", "Vegano"),
    ;

    companion object {
        fun of(key: String?): Diet? = entries.firstOrNull { it.key == key }
    }
}

enum class BodyGoal(val key: String, val label: String) {
    MAINTAIN("maintain", "Mantenere il peso"),
    LOSE("lose", "Perdere un po' di peso"),
    GAIN("gain", "Aumentare la massa"),
    ;

    companion object {
        fun of(key: String?): BodyGoal? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How the athlete eats, as NutriAI needs to know it. Intolerances and
 * allergies are health data, covered by the health consent.
 */
data class NutritionProfile(
    val diet: Diet = Diet.OMNIVORE,
    /** Free words, e.g. "lattosio", "frutta a guscio". */
    val avoid: List<String> = emptyList(),
    val mealsPerDay: Int = 4,
    val goal: BodyGoal = BodyGoal.MAINTAIN,
    /** Anything else, in the athlete's words. */
    val notes: String = "",
) {
    /** The chips under NutriAI's title. */
    fun chips(): List<String> = buildList {
        add(diet.label)
        avoid.forEach { add("Senza $it") }
        add("$mealsPerDay pasti")
    }

    companion object {
        const val MIN_MEALS = 2
        const val MAX_MEALS = 6

        /** Splits what the athlete typed into items: commas, semicolons or new lines. */
        fun parseAvoid(text: String): List<String> =
            text.split(',', ';', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
    }
}
