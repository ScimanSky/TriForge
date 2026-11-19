package com.triforge.domain.coach

/**
 * The check for signs of an eating disorder, as SwimForge does it: run on the
 * athlete's message before any model. When it finds one the model is not
 * called and the answer is fixed and kind: the family doctor, the national
 * helpline, the map of the services. A single cautious match is enough: a
 * false alarm costs a kind message, a miss costs much more. Training on an
 * empty stomach («a digiuno») is not a sign by itself.
 */
object NutriSafety {

    /** Numero Verde SOS Disturbi Alimentari: free and anonymous, set up by the Istituto Superiore di Sanità. */
    const val HELPLINE = "800 180 969"
    const val SITE = "disturbialimentarionline.it"

    val REPLY: String = listOf(
        "Grazie per avermelo scritto: quello che racconti è importante e merita più di un consiglio sull'alimentazione.",
        "",
        "Su questo non posso aiutarti io. Ti consiglio di parlarne con il tuo medico di base: può ascoltarti e indirizzarti da chi se ne occupa. " +
            "Puoi anche chiamare il Numero Verde SOS Disturbi Alimentari, $HELPLINE: è gratuito e anonimo, attivato dall'Istituto Superiore di Sanità. " +
            "Su $SITE trovi la mappa dei servizi vicino a te.",
        "",
        "Chiedere aiuto è il primo passo, e si può stare meglio.",
    ).joinToString("\n")

    private const val DAYS = "(\\d+|due|tre|quattro|cinque|sei|sette|qualche|diversi|molti|parecchi) giorn"

    private val SIGNS = listOf(
        Regex("\\bdigiun\\w* (da|per) $DAYS"),
        Regex("\\bnon (mangio|ho mangiato|mangero) (niente |nulla )?(da|per) $DAYS"),
        Regex("\\bnon mangio (quasi )?(niente|nulla) (da|per)"),
        Regex("\\bsalt\\w* (i |dei |tutti i )?pasti per (dimagrire|perdere peso|calare)"),
        Regex("\\b(mi faccio|farmi|mi provoco|provocarmi) (il )?vomit"),
        Regex("\\bvomit\\w* dopo (aver mangiato|mangiato|i pasti|il pasto|ogni pasto|cena|pranzo)"),
        Regex("\\blassativ"),
        Regex("\\babbuffat\\w*.{0,40}\\b(colpa|vergogna|schifo)"),
        Regex("\\b(colpa|vergogna|schifo).{0,40}\\babbuffat"),
        Regex("\\b(mi sento|sentirmi) (in colpa|uno schifo) (quando|ogni volta che|se) mangio"),
        Regex("\\bpro ?ana\\b"),
        Regex("\\b(perso|perdere|calato|calare) \\d+ (kg|chili|kili) in (una|1|due|2) settiman"),
    )

    /** Calories a day said in the message, when far below any safe intake. */
    private val LOW_CALORIES = Regex("\\b(mangio|mangiare|assumo|assumere|sto a|stare a|dieta da|resto sotto|stare sotto) (solo |circa |meno di )?(\\d{3})( kcal| calorie)")

    /** Whether [message] shows a sign: then answer with [REPLY] and nothing else. */
    fun flags(message: String): Boolean {
        val text = FoodBans.normalize(message)
        if (SIGNS.any { it.containsMatchIn(text) }) return true
        return LOW_CALORIES.findAll(text).any { (it.groupValues[3].toIntOrNull() ?: 9999) < 1000 }
    }
}
