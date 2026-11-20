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
    LOSE("lose", "Calare gradualmente"),
    PERFORMANCE("performance", "Più energia per allenarmi"),
    GAIN("gain", "Aumentare la massa muscolare"),
    ;

    companion object {
        fun of(key: String?): BodyGoal? = entries.firstOrNull { it.key == key }
    }
}

/** For the energy estimate (Mifflin-St Jeor) and the intake floor. */
enum class Sex(val key: String, val label: String) {
    FEMALE("female", "Donna"),
    MALE("male", "Uomo"),
    UNSPECIFIED("unspecified", "Preferisco non dirlo"),
    ;

    companion object {
        fun of(key: String?): Sex? = entries.firstOrNull { it.key == key }
    }
}

/** The 14 allergens of the EU rules on food information (Reg. UE 1169/2011, allegato II). */
enum class Allergen(val key: String, val label: String) {
    GLUTEN("gluten", "Cereali con glutine"),
    CRUSTACEANS("crustaceans", "Crostacei"),
    EGGS("eggs", "Uova"),
    FISH("fish", "Pesce"),
    PEANUTS("peanuts", "Arachidi"),
    SOY("soy", "Soia"),
    MILK("milk", "Latte"),
    NUTS("nuts", "Frutta a guscio"),
    CELERY("celery", "Sedano"),
    MUSTARD("mustard", "Senape"),
    SESAME("sesame", "Sesamo"),
    SULPHITES("sulphites", "Solfiti"),
    LUPIN("lupin", "Lupini"),
    MOLLUSCS("molluscs", "Molluschi"),
    ;

    companion object {
        fun of(key: String?): Allergen? = entries.firstOrNull { it.key == key }
    }
}

enum class Intolerance(val key: String, val label: String) {
    LACTOSE("lactose", "Lattosio"),
    GLUTEN("gluten_celiac", "Glutine o celiachia"),
    ;

    companion object {
        fun of(key: String?): Intolerance? = entries.firstOrNull { it.key == key }
    }
}

enum class SupplementTiming(val key: String, val label: String) {
    DAILY("daily", "ogni giorno"),
    BEFORE("before", "prima dell'allenamento"),
    DURING("during", "durante l'allenamento"),
    AFTER("after", "dopo l'allenamento"),
    EVENING("evening", "la sera"),
    ;

    companion object {
        fun of(key: String?): SupplementTiming? = entries.firstOrNull { it.key == key }
    }
}

/** A food the athlete does not eat, with the forms they do eat («latte, tranne kefir»). */
data class DislikeEntry(val food: String, val exceptions: List<String> = emptyList()) {
    /** «latte (tranne kefir, cappuccino)», the way the profile and the prompts write it. */
    fun text(): String = if (exceptions.isEmpty()) food else "$food (tranne ${exceptions.joinToString(", ")})"
}

data class Supplement(val name: String, val dose: String = "", val timing: SupplementTiming = SupplementTiming.DAILY)

data class Drink(val name: String, val perDay: String = "")

/**
 * How the athlete eats, as NutriAI needs to know it. Allergies and
 * intolerances are health data, covered by the health consent. Height and
 * weight live in the athlete profile, the year of birth in the account.
 */
data class NutritionProfile(
    val sex: Sex? = null,
    val diet: Diet = Diet.OMNIVORE,
    val goal: BodyGoal = BodyGoal.MAINTAIN,
    val mealsPerDay: Int = 4,
    val allergies: Set<Allergen> = emptySet(),
    /** Allergies outside the EU list, in the athlete's words. */
    val otherAllergies: String = "",
    val intolerances: Set<Intolerance> = emptySet(),
    val otherIntolerances: String = "",
    /** Foods NutriAI proposes more often. */
    val likes: List<String> = emptyList(),
    val dislikes: List<DislikeEntry> = emptyList(),
    /** Red meat at most this many times a week (0 to 3); null without a limit. */
    val redMeatPerWeek: Int? = null,
    val supplements: List<Supplement> = emptyList(),
    val drinks: List<Drink> = emptyList(),
    /** «Niente caffè né caffeina». */
    val noCaffeine: Boolean = false,
    /** Anything else, in the athlete's words. */
    val notes: String = "",
) {
    /** The chips under NutriAI's title. */
    fun chips(): List<String> = buildList {
        add(diet.label)
        allergies.forEach { add("Allergia: ${it.label.lowercase()}") }
        intolerances.forEach { add("Senza ${it.label.lowercase()}") }
        add("$mealsPerDay pasti")
    }

    /**
     * The profile in a line, for the row that opens it: «Onnivoro · 1
     * allergia · 4 cibi esclusi · creatina 3 g».
     */
    fun summary(): String = buildList {
        fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
        add(diet.label)
        val allergic = allergies.size + parseList(otherAllergies).size
        if (allergic > 0) add(count(allergic, "allergia", "allergie"))
        val intolerant = intolerances.size + parseList(otherIntolerances).size
        if (intolerant > 0) add(count(intolerant, "intolleranza", "intolleranze"))
        if (dislikes.isNotEmpty()) add(count(dislikes.size, "cibo escluso", "cibi esclusi"))
        when (supplements.size) {
            0 -> Unit
            1 -> add(supplements.single().let { listOf(it.name.lowercase(), it.dose).filter(String::isNotBlank).joinToString(" ") })
            else -> add("${supplements.size} integratori")
        }
    }.joinToString(" · ")

    companion object {
        const val MIN_MEALS = 2
        const val MAX_MEALS = 6
        const val MAX_RED_MEAT = 3
        const val MAX_SUPPLEMENTS = 15
        const val MAX_DRINKS = 10
        const val MAX_ITEMS = 30
        const val TEXT_MAX = 200
        const val NAME_MAX = 60

        /** Splits what the athlete typed into items: commas, semicolons or new lines. */
        fun parseList(text: String): List<String> =
            text.split(',', ';', '\n').map { it.trim().take(NAME_MAX) }.filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }.take(MAX_ITEMS)

        /**
         * One entry of «Cosa non mangi» from its two fields: the food and the
         * exceptions, separated by commas. Null when the food is blank.
         */
        fun dislike(food: String, exceptions: String): DislikeEntry? {
            val name = food.replace(Regex("[;,()\\n]"), " ").replace(Regex("\\s+"), " ").trim().take(NAME_MAX)
            if (name.isEmpty()) return null
            return DislikeEntry(name, parseList(exceptions.replace(Regex("[()]"), " ")))
        }
    }
}
