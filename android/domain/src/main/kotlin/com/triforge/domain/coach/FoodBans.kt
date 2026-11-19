package com.triforge.domain.coach

import java.text.Normalizer
import java.util.Locale

/** Why a food cannot be proposed to the athlete, strongest first. */
enum class BanReason(val label: String) {
    ALLERGY("allergia"),
    INTOLERANCE("intolleranza"),
    DIET("dieta"),
    DISLIKE("non lo mangi"),
    CAFFEINE("niente caffeina"),
}

/** A food that breaks the profile: what was found and why. */
data class BanHit(val reason: BanReason, val term: String, val detail: String)

/** Where a ban was found in a day of NutriAI, e.g. the meal «Pranzo». */
data class BanViolation(val where: String, val hit: BanHit) {
    fun text(): String = "$where: «${hit.term}» (${hit.detail})"
}

/**
 * The bans of the food profile, checked by the app on everything NutriAI
 * proposes (as SwimForge does): allergies and intolerances, the diet, the
 * foods the athlete does not eat with their exceptions, caffeine. The model
 * is told the same rules, but the guarantee is this check. Italian words,
 * case and accents ignored, singular and plural alike. An exception of a
 * disliked food never lifts an allergy or an intolerance.
 */
object FoodBans {

    private val ALLERGEN_TERMS: Map<Allergen, List<String>> = mapOf(
        Allergen.GLUTEN to listOf(
            "glutine", "frumento", "grano", "farro", "orzo", "segale", "kamut", "spelta", "avena", "pasta", "pane",
            "panino", "pizza", "focaccia", "piadina", "cous cous", "couscous", "bulgur", "seitan", "semola", "farina",
            "crackers", "cracker", "grissino", "fette biscottate", "biscotto", "cornetto", "brioche", "torta",
            "gnocchi", "lasagne", "tortellini", "ravioli", "muesli", "granola", "pangrattato", "taralli",
        ),
        Allergen.CRUSTACEANS to listOf(
            "crostaceo", "gambero", "gamberetto", "gamberone", "scampo", "aragosta", "astice", "granchio", "mazzancolla",
        ),
        Allergen.EGGS to listOf("uovo", "albume", "tuorlo", "frittata", "omelette", "maionese", "all uovo"),
        Allergen.FISH to listOf(
            "pesce", "salmone", "tonno", "merluzzo", "orata", "branzino", "spigola", "sgombro", "alice", "acciuga",
            "sardina", "trota", "baccala", "nasello", "sogliola", "platessa", "dentice", "cernia", "persico", "aringa",
        ),
        Allergen.PEANUTS to listOf("arachide", "nocciolina"),
        Allergen.SOY to listOf("soia", "tofu", "tempeh", "edamame", "miso"),
        Allergen.MILK to listOf(
            "latte", "latticino", "formaggio", "mozzarella", "parmigiano", "grana", "pecorino", "ricotta", "yogurt",
            "burro", "panna", "kefir", "mascarpone", "stracchino", "scamorza", "provola", "fontina", "gorgonzola",
            "caciotta", "feta", "skyr", "fiocchi di latte", "besciamella", "gelato", "cappuccino", "latte macchiato",
            "siero di latte", "whey", "caseina",
        ),
        Allergen.NUTS to listOf(
            "frutta a guscio", "frutta secca", "mandorla", "noce", "nocciola", "anacardo", "pistacchio", "pecan",
            "macadamia", "noce brasiliana", "crema di nocciole",
        ),
        Allergen.CELERY to listOf("sedano"),
        Allergen.MUSTARD to listOf("senape"),
        Allergen.SESAME to listOf("sesamo", "tahina", "tahini", "hummus"),
        Allergen.SULPHITES to listOf("solfiti", "vino", "aceto balsamico", "frutta essiccata", "albicocche secche", "uvetta"),
        Allergen.LUPIN to listOf("lupino"),
        Allergen.MOLLUSCS to listOf(
            "mollusco", "cozza", "vongola", "calamaro", "polpo", "seppia", "totano", "ostrica", "capasanta", "lumaca",
        ),
    )

    /** Phrases that look like an allergen but are not, removed before that allergen's check. */
    private val NOT_ALLERGEN: Map<Allergen, List<Regex>> = mapOf(
        Allergen.GLUTEN to listOf(
            Regex("\\bgrano saraceno\\b"),
            Regex("\\b(farina|pasta|pane|crackers?|gallette?|fiocchi) (di |d )?(riso|mais|ceci|lenticchie|legumi|piselli|grano saraceno|mandorle|cocco|castagne|quinoa|amaranto|teff|patate)\\b"),
        ),
        Allergen.MILK to listOf(
            Regex("\\b(latte|yogurt|panna|gelato|burro|formaggio) (di |d )?(mandorl[ae]|soia|avena|riso|cocco|nocciol[ae]|anacardi|arachidi|cacao|vegetale|vegano)\\b"),
            Regex("\\b(latte|yogurt|panna|formaggio) vegetal[ei]\\b"),
        ),
        Allergen.NUTS to listOf(Regex("\\bnoce (moscata|di cocco)\\b"), Regex("\\bnoci di cocco\\b")),
        Allergen.FISH to listOf(Regex("\\bpesce (vegetale|vegano)\\b")),
    )

    /** A food said to be free of lactose or gluten: the intolerance (and the gluten allergy) lets it pass. */
    private val LACTOSE_FREE = Regex("\\b((senza|zero) lattosio|delattosat\\w*|lactose free)\\b")
    private val GLUTEN_FREE = Regex("\\b((senza|zero) glutine|gluten free)\\b")

    /** Aged cheeses are naturally free of lactose. */
    private val AGED_CHEESE = listOf(Regex("\\b(parmigiano|grana)( reggiano| padano)?\\b"))

    /** «senza burro»: what follows «senza» is not in the food. */
    private val WITHOUT = listOf(Regex("\\b(senza|zero) \\w+"))

    private val MEAT = listOf(
        "carne", "pollo", "tacchino", "manzo", "maiale", "vitello", "agnello", "coniglio", "cavallo", "anatra",
        "prosciutto", "bresaola", "speck", "salame", "salsiccia", "mortadella", "pancetta", "guanciale", "wurstel",
        "hamburger", "bistecca", "cotoletta", "polpetta", "ragu", "fesa", "arrosto", "spezzatino",
    )
    private val RED_MEAT = listOf(
        "carne rossa", "manzo", "vitello", "maiale", "agnello", "cavallo", "bistecca", "hamburger", "salsiccia",
        "prosciutto", "bresaola", "speck", "salame", "mortadella", "pancetta", "guanciale", "ragu", "spezzatino",
    )
    private val NOT_MEAT = listOf(
        Regex("\\b(ragu|polpett[ae]|hamburger|burger|cotolett[ae]|bistecc[ah]e?|salsicc[ae]|spezzatino|carne) (vegetal[ei]|vegan[oaie]?|di (soia|ceci|lenticchie|legumi|seitan|tofu|verdure|fagioli|piselli|melanzane|zucchine|cavolfiore|quinoa|tonno|pesce|salmone))\\b"),
        Regex("\\bcarne (di )?(soia|vegetale)\\b"),
    )
    private val ANIMAL = listOf("miele")

    private val CAFFEINE = listOf(
        "caffe", "caffeina", "espresso", "cappuccino", "latte macchiato", "te nero", "te verde", "matcha", "cola",
        "energy drink", "guarana", "mate",
    )
    private val NOT_CAFFEINE = listOf(
        Regex("\\b(caffe )?d orzo\\b"), Regex("\\borzo solubile\\b"), Regex("\\bdecaffeinat\\w*"), Regex("\\bdeca\\b"),
        Regex("\\b(senza|zero) caffeina\\b"), Regex("\\bte (deteinato|senza teina)\\b"),
    )

    /** Lowercase, no accents, apostrophes and punctuation as spaces, single spaces. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ITALIAN)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /** The singular and plural forms of an Italian word, as far as the endings go. */
    private fun variants(word: String): Set<String> {
        if (word.length < 4) return setOf(word)
        val stem = word.dropLast(1)
        return when (word.last()) {
            'o' -> if (word == "uovo") setOf(word, "uova") else setOf(word, stem + "i")
            'a' -> setOf(word, stem + "e", if (word.endsWith("ca") || word.endsWith("ga")) stem + "he" else word)
            'e' -> setOf(word, stem + "i")
            'i' -> setOf(word, stem + "o", stem + "e")
            else -> setOf(word)
        }
    }

    /** Whether [term] appears in [text] (both normalized) as whole words. */
    private fun contains(text: String, term: String): Boolean {
        val words = text.split(' ').filter { it.isNotEmpty() }
        val parts = normalize(term).split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty() || words.size < parts.size) return false
        val forms = parts.map(::variants)
        return (0..words.size - parts.size).any { start -> forms.indices.all { words[start + it] in forms[it] } }
    }

    private val LINKS = setOf("di", "d", "al", "allo", "alla", "con", "e")

    /**
     * [text] without every mention of [exception], taking along a [food] the
     * exception is made with («kefir di latte», «cappuccino con latte scremato»).
     */
    private fun without(text: String, exception: String, food: String): String {
        val words = text.split(' ').filter { it.isNotEmpty() }
        val parts = normalize(exception).split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty() || words.size < parts.size) return text
        val forms = parts.map(::variants)
        val foodForms = normalize(food).split(' ').filter { it.isNotEmpty() }.map(::variants)
        val drop = BooleanArray(words.size)
        var start = 0
        while (start <= words.size - parts.size) {
            if (forms.indices.all { words[start + it] in forms[it] }) {
                var end = start + parts.size
                val after = if (end < words.size && words[end] in LINKS) end + 1 else end
                if (after > end && foodForms.isNotEmpty() && after + foodForms.size <= words.size &&
                    foodForms.indices.all { words[after + it] in foodForms[it] }
                ) {
                    end = after + foodForms.size
                }
                for (i in start until end) drop[i] = true
                start = end
            } else {
                start++
            }
        }
        return words.filterIndexed { i, _ -> !drop[i] }.joinToString(" ")
    }

    private fun firstTerm(text: String, terms: List<String>): String? = terms.firstOrNull { contains(text, it) }

    private fun strip(text: String, patterns: List<Regex>): String =
        patterns.fold(text) { acc, regex -> regex.replace(acc, " ") }.replace(Regex("\\s+"), " ").trim()

    /** The athlete's own words («kiwi; fragole») as terms. */
    private fun own(text: String): List<String> = NutritionProfile.parseList(text).map(::normalize).filter { it.isNotEmpty() }

    /** The first ban [food] breaks, or null when NutriAI may propose it. */
    fun check(food: String, profile: NutritionProfile): BanHit? {
        val full = normalize(food)
        if (full.isEmpty()) return null
        val text = strip(full, WITHOUT)
        val glutenFree = GLUTEN_FREE.containsMatchIn(full)
        for (allergen in Allergen.entries.filter { it in profile.allergies }) {
            if (allergen == Allergen.GLUTEN && glutenFree) continue
            val term = firstTerm(strip(text, NOT_ALLERGEN[allergen].orEmpty()), ALLERGEN_TERMS.getValue(allergen))
            if (term != null) return BanHit(BanReason.ALLERGY, term, "allergia: ${allergen.label.lowercase()}")
        }
        own(profile.otherAllergies).firstOrNull { contains(text, it) }?.let { return BanHit(BanReason.ALLERGY, it, "allergia") }
        if (Intolerance.LACTOSE in profile.intolerances && !LACTOSE_FREE.containsMatchIn(full)) {
            val milk = strip(strip(text, NOT_ALLERGEN.getValue(Allergen.MILK)), AGED_CHEESE)
            firstTerm(milk, ALLERGEN_TERMS.getValue(Allergen.MILK))?.let { return BanHit(BanReason.INTOLERANCE, it, "intolleranza al lattosio") }
        }
        if (Intolerance.GLUTEN in profile.intolerances && !glutenFree) {
            val gluten = strip(text, NOT_ALLERGEN.getValue(Allergen.GLUTEN))
            firstTerm(gluten, ALLERGEN_TERMS.getValue(Allergen.GLUTEN))?.let { return BanHit(BanReason.INTOLERANCE, it, "glutine o celiachia") }
        }
        own(profile.otherIntolerances).firstOrNull { contains(text, it) }?.let { return BanHit(BanReason.INTOLERANCE, it, "intolleranza") }
        dietHit(text, profile)?.let { return it }
        for (entry in profile.dislikes) {
            val disliked = normalize(entry.food)
            if (disliked.isEmpty() || !contains(text, disliked)) continue
            // A favourite food counts as an exception: «latte» banned and «kefir» liked lets «kefir di latte» pass.
            // An exception lifts the ban only for itself («kefir», «kefir di latte»): the same
            // food elsewhere in the meal («porridge nel latte con kefir») is still a ban.
            if (!contains((entry.exceptions + profile.likes).fold(text) { acc, exception -> without(acc, exception, disliked) }, disliked)) continue
            return BanHit(BanReason.DISLIKE, entry.food, "non lo mangi")
        }
        if (profile.noCaffeine) {
            firstTerm(strip(full, NOT_CAFFEINE).let { strip(it, WITHOUT) }, CAFFEINE)?.let { return BanHit(BanReason.CAFFEINE, it, "niente caffè né caffeina") }
        }
        return null
    }

    private fun dietHit(text: String, profile: NutritionProfile): BanHit? {
        val meatless = strip(text, NOT_MEAT)
        val seafood = ALLERGEN_TERMS.getValue(Allergen.FISH) + ALLERGEN_TERMS.getValue(Allergen.CRUSTACEANS) +
            ALLERGEN_TERMS.getValue(Allergen.MOLLUSCS)
        val terms = when (profile.diet) {
            Diet.OMNIVORE -> emptyList()
            Diet.PESCATARIAN -> MEAT
            Diet.VEGETARIAN -> MEAT + seafood
            Diet.VEGAN -> MEAT + seafood + ALLERGEN_TERMS.getValue(Allergen.EGGS) + ANIMAL
        }
        firstTerm(meatless, terms)?.let { return BanHit(BanReason.DIET, it, "dieta ${profile.diet.label.lowercase()}") }
        if (profile.diet == Diet.VEGAN) {
            val milk = strip(text, NOT_ALLERGEN.getValue(Allergen.MILK))
            firstTerm(milk, ALLERGEN_TERMS.getValue(Allergen.MILK))?.let { return BanHit(BanReason.DIET, it, "dieta vegana") }
        }
        if (profile.redMeatPerWeek == 0) {
            firstTerm(meatless, RED_MEAT)?.let { return BanHit(BanReason.DIET, it, "niente carne rossa") }
        }
        return null
    }

    /**
     * Everything in [day] that breaks the profile: dishes (a meal stored
     * without dishes as a whole) and fuelling.
     */
    fun violations(day: NutritionDay, profile: NutritionProfile): List<BanViolation> = buildList {
        day.meals.forEach { meal ->
            if (meal.dishes.isEmpty()) {
                check("${meal.name} ${meal.description}", profile)?.let { add(BanViolation(meal.name, it)) }
            } else {
                meal.dishes.forEach { dish -> NutriMeals.check(dish, profile)?.let { add(BanViolation("${meal.name}, ${dish.name}", it)) } }
            }
        }
        day.fueling.forEach { step -> check(step.text, profile)?.let { add(BanViolation(step.time, it)) } }
    }

    /**
     * [day] without the dishes and fuelling steps that break the profile; a
     * meal left with no dish goes too.
     */
    fun clean(day: NutritionDay, profile: NutritionProfile): NutritionDay = day.copy(
        meals = day.meals.mapNotNull { meal ->
            if (meal.dishes.isEmpty()) {
                meal.takeIf { check("${it.name} ${it.description}", profile) == null }
            } else {
                meal.dishes.filter { NutriMeals.check(it, profile) == null }.takeIf { it.isNotEmpty() }?.let { NutriMeals.rebuilt(meal, it) }
            }
        },
        fueling = day.fueling.filter { check(it.text, profile) == null },
    )
}
