package com.triforge.app.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.CoachPrompts
import com.triforge.domain.coach.CoachRole
import com.triforge.domain.coach.Dish
import com.triforge.domain.coach.DishEstimate

/**
 * NutriAI when the athlete changes one dish of a meal: it proposes what can
 * take its place, and reads a dish the athlete wrote. It only proposes: the
 * app filters, scales and checks everything against the food profile
 * ([com.triforge.domain.coach.NutriMeals]).
 */
interface DishAdvisor {
    /** Dishes for the place of another, from a request built by [CoachPrompts.dishAlternativesRequest]. */
    suspend fun alternatives(request: String): List<Dish>

    /** What a dish the athlete wrote is worth, from a request built by [CoachPrompts.dishEstimateRequest]. */
    suspend fun estimate(request: String): DishEstimate
}

/** [DishAdvisor] on Gemini through Firebase AI Logic, with NutriAI's own instructions. */
class GeminiDishAdvisor(private val modelName: String, location: String) : DishAdvisor {

    private val ai = Firebase.ai(backend = GenerativeBackend.vertexAI(location = location))

    private fun model(schema: Schema, temperature: Float): GenerativeModel = ai.generativeModel(
        modelName = modelName,
        generationConfig = generationConfig {
            this.temperature = temperature
            responseMimeType = "application/json"
            responseSchema = schema
        },
        systemInstruction = content { text(CoachPrompts.system(CoachRole.NUTRI_AI)) },
    )

    // Variety among the alternatives, steadiness in the numbers of a dish.
    private val alternativesModel by lazy { model(CoachSchemas.dishAlternatives, 0.7f) }
    private val estimateModel by lazy { model(CoachSchemas.dishEstimate, 0.2f) }

    override suspend fun alternatives(request: String): List<Dish> =
        CoachMapper.dishAlternativesFromFields(geminiJson { alternativesModel.generateContent(request).text })

    override suspend fun estimate(request: String): DishEstimate =
        CoachMapper.dishEstimateFromFields(geminiJson { estimateModel.generateContent(request).text })
}
