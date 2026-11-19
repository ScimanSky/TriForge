package com.triforge.app.ai

import com.google.firebase.ai.type.Schema

/**
 * The JSON shapes asked of the model. Field names match
 * [com.triforge.domain.coach.CoachMapper], which validates what comes back.
 */
object CoachSchemas {

    /**
     * What one sport coach writes: its own sessions as structured workouts.
     * No sport, duration or targets: the app knows the first and computes the
     * others from the steps and the athlete's zones.
     */
    fun sportWeek(zones: List<String>): Schema = Schema.obj(
        properties = mapOf(
            "days" to Schema.array(
                Schema.obj(
                    properties = mapOf(
                        "date" to Schema.string(description = "AAAA-MM-GG"),
                        "sessions" to Schema.array(
                            Schema.obj(
                                properties = mapOf(
                                    "title" to Schema.string(description = "Titolo breve, es. \"6 × 800 m a soglia\""),
                                    "focus" to Schema.string(description = "Una o due parole, es. \"Soglia\", \"Tecnica\", \"Lungo\""),
                                    "why" to Schema.string(description = "Perché questa seduta, 1-2 frasi"),
                                    "steps" to Schema.array(
                                        Schema.obj(
                                            properties = mapOf(
                                                "block" to Schema.enumeration(listOf("warmup", "main", "cooldown")),
                                                "reps" to Schema.integer(description = "Ripetute, 1 per un passo unico"),
                                                "distanceM" to Schema.integer(description = "Metri di una ripetuta, se a distanza"),
                                                "durationSec" to Schema.integer(description = "Secondi di una ripetuta, se a tempo"),
                                                "zone" to Schema.enumeration(zones),
                                                "recoverySec" to Schema.integer(description = "Secondi di recupero dopo ogni ripetuta"),
                                                "text" to Schema.string(description = "Cosa fare, es. \"corsa facile\", \"stile libero\""),
                                            ),
                                            optionalProperties = listOf("distanceM", "durationSec", "recoverySec", "text"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            "noteForCoaches" to Schema.string(description = "Cosa hai scelto e cosa chiedi agli altri due coach"),
            "noteForNutriAi" to Schema.string(description = "Cosa deve sapere NutriAI, o vuoto"),
            "rationale" to Schema.string(description = "Perché la tua settimana è così, 1-2 frasi"),
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
