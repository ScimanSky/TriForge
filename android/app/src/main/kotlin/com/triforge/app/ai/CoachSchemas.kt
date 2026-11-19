package com.triforge.app.ai

import com.google.firebase.ai.type.Schema

/**
 * The JSON shapes asked of the model. Field names match
 * [com.triforge.domain.coach.CoachMapper], which validates what comes back.
 */
object CoachSchemas {

    /** What a coach answers when it sends the athlete to nobody. */
    private const val NO_HANDOFF = "none"

    /**
     * What a coach answers in its chat: the reply, a change to its plan when
     * it proposes one, and a note for the other coaches. Only a coach that may
     * send the athlete to someone else ([handoffs] not empty) has the
     * `handoff` field: an optional name without its property is refused by
     * the library when the schema is built.
     */
    fun chatReply(planSchema: Schema, handoffs: List<String>): Schema {
        val handoff: Map<String, Schema> =
            if (handoffs.isEmpty()) emptyMap() else mapOf("handoff" to Schema.enumeration(handoffs + NO_HANDOFF))
        return Schema.obj(
            properties = mapOf(
                "reply" to Schema.string(description = "La risposta all'atleta, in italiano"),
                "proposal" to planSchema,
                "noteForPartner" to Schema.string(description = "Nota per gli altri coach, o vuoto"),
            ) + handoff,
            optionalProperties = listOf("proposal", "noteForPartner") + handoff.keys,
        )
    }

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
