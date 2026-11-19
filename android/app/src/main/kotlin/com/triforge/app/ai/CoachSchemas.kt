package com.triforge.app.ai

import com.google.firebase.ai.type.Schema

/**
 * The JSON shapes asked of the model. Field names match
 * [com.triforge.domain.coach.CoachMapper], which validates what comes back.
 */
object CoachSchemas {

    private val session = Schema.obj(
        properties = mapOf(
            "sport" to Schema.enumeration(listOf("swim", "bike", "run", "other", "rest")),
            "title" to Schema.string(description = "Titolo breve, es. \"Soglia 4 × 8′\""),
            "durationMin" to Schema.integer(description = "Durata in minuti"),
            "intensity" to Schema.enumeration(listOf("rest", "easy", "endurance", "tempo", "threshold", "vo2max", "race")),
            "details" to Schema.string(description = "Come farla: serie, bersagli in watt, passo o zone"),
        ),
        optionalProperties = listOf("details"),
    )

    val weekPlan: Schema = Schema.obj(
        properties = mapOf(
            "weekStart" to Schema.string(description = "Lunedì della settimana, AAAA-MM-GG"),
            "phase" to Schema.string(description = "Fase di allenamento, es. \"Costruzione · sett. 2 di 3\""),
            "days" to Schema.array(
                Schema.obj(
                    properties = mapOf(
                        "date" to Schema.string(description = "AAAA-MM-GG"),
                        "sessions" to Schema.array(session),
                    ),
                ),
            ),
            "rationale" to Schema.string(description = "Perché la settimana è così, 2-4 frasi"),
            "noteForNutriAi" to Schema.string(description = "Cosa deve sapere NutriAI"),
        ),
    )

    val nutritionDay: Schema = Schema.obj(
        properties = mapOf(
            "date" to Schema.string(description = "AAAA-MM-GG"),
            "dayType" to Schema.enumeration(listOf("rest", "light", "moderate", "heavy", "race")),
            "headline" to Schema.string(description = "Frase breve sul giorno"),
            "kcal" to Schema.integer(),
            "carbsG" to Schema.integer(),
            "proteinG" to Schema.integer(),
            "fatG" to Schema.integer(),
            "fueling" to Schema.array(
                Schema.obj(
                    properties = mapOf(
                        "time" to Schema.string(description = "Orario o \"Prima\", \"Durante\", \"Dopo\""),
                        "text" to Schema.string(),
                    ),
                ),
            ),
            "meals" to Schema.array(
                Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(description = "Es. Colazione, Pranzo, Spuntino pre-bici"),
                        "description" to Schema.string(),
                        "kcal" to Schema.integer(),
                    ),
                ),
            ),
            "rationale" to Schema.string(),
            "noteForCoach" to Schema.string(description = "Cosa deve sapere il Coach, o vuoto"),
        ),
    )
}
