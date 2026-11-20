package com.triforge.app.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.triforge.domain.coach.ChatMessage
import com.triforge.domain.coach.CoachPrompts
import com.triforge.domain.coach.CoachReply
import com.triforge.domain.coach.CoachRole
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A coach running on Gemini through Firebase AI Logic, called from the phone.
 *
 * The Vertex AI backend in a European region keeps the requests in the EU.
 * Answers are JSON that follows [planSchema]; [parse] validates them with the
 * domain mapper, so a broken answer never reaches the screen.
 */
class GeminiCoachAgent<P : Any>(
    override val role: CoachRole,
    private val modelName: String,
    location: String,
    planSchema: Schema,
    /** The agent's standing instructions; the sport coaches bring their own. */
    private val system: String = CoachPrompts.system(role),
    /** Where this coach may send a question that is not its own; empty when it never does. */
    private val handoffs: List<String> = emptyList(),
    private val parse: (Map<String, Any?>) -> P?,
) : CoachAgent<P> {

    private val ai = Firebase.ai(backend = GenerativeBackend.vertexAI(location = location))

    private fun model(schema: Schema, temperature: Float): GenerativeModel = ai.generativeModel(
        modelName = modelName,
        generationConfig = generationConfig {
            this.temperature = temperature
            responseMimeType = "application/json"
            responseSchema = schema
        },
        systemInstruction = content { text(system) },
    )

    private val planModel = model(planSchema, PLAN_TEMPERATURE)

    private val chatModel = model(CoachSchemas.chatReply(planSchema, handoffs), CHAT_TEMPERATURE)

    override suspend fun plan(request: String): P {
        val fields = geminiJson(AiWait.PLAN) { planModel.generateContent(request).text }
        return parse(fields) ?: throw CoachException(CoachException.Reason.BAD_ANSWER)
    }

    override suspend fun reply(history: List<ChatMessage>, turn: String): CoachReply<P> {
        val contents: List<Content> = history.takeLast(MAX_HISTORY).map { message ->
            content(role = if (message.fromAthlete) "user" else "model") { text(message.text) }
        } + content(role = "user") { text(turn) }
        val fields = geminiJson(AiWait.CHAT) { chatModel.generateContent(contents.first(), *contents.drop(1).toTypedArray()).text }
        val text = (fields["reply"] as? String)?.trim().orEmpty()
        if (text.isEmpty()) throw CoachException(CoachException.Reason.BAD_ANSWER)
        @Suppress("UNCHECKED_CAST")
        val proposal = (fields["proposal"] as? Map<String, Any?>)?.let(parse)
        val handoff = (fields["handoff"] as? String)?.trim()?.takeIf { it in handoffs }.orEmpty()
        return CoachReply(text, proposal, (fields["noteForPartner"] as? String)?.trim().orEmpty(), handoff)
    }

    private companion object {
        const val PLAN_TEMPERATURE = 0.4f
        const val CHAT_TEMPERATURE = 0.6f

        /** Earlier messages sent with each chat turn: enough to follow the thread, not the whole history. */
        const val MAX_HISTORY = 20
    }
}

/**
 * One answer of Gemini in JSON: its errors become a [CoachException] the
 * screens can explain, its text a map of fields.
 *
 * The answer is given [wait] and no more: a request that hangs must give
 * the screen back, not keep a spinner for minutes. Within that time a call
 * that is slow, failed on the way or came back unreadable is made again
 * before the athlete hears about it: see [firstAnswer].
 */
internal suspend fun geminiJson(wait: AiWait, block: suspend () -> String?): Map<String, Any?> =
    firstAnswer(wait) { geminiJsonOnce(block) }

private suspend fun geminiJsonOnce(block: suspend () -> String?): Map<String, Any?> {
    val text = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ServiceDisabledException) {
        throw CoachException(CoachException.Reason.NOT_ENABLED, e)
    } catch (e: QuotaExceededException) {
        throw CoachException(CoachException.Reason.BUSY, e)
    } catch (e: PromptBlockedException) {
        throw CoachException(CoachException.Reason.BLOCKED, e)
    } catch (e: ResponseStoppedException) {
        throw CoachException(CoachException.Reason.BLOCKED, e)
    } catch (e: Exception) {
        throw CoachException(reasonOf(e), e)
    } ?: throw CoachException(CoachException.Reason.BAD_ANSWER)
    return try {
        JSONObject(text).toMap()
    } catch (e: JSONException) {
        throw CoachException(CoachException.Reason.BAD_ANSWER, e)
    }
}

/**
 * Why a call failed, from the error and what caused it. Only a failure of
 * the connection itself is called one: a slow or broken service is not the
 * athlete's network, and saying so sends them to check the wrong thing.
 */
private fun reasonOf(error: Exception): CoachException.Reason {
    val chain = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
    val text = chain.joinToString(" ") { it.message.orEmpty() }
    return when {
        text.contains("not been used", true) || text.contains("disabled", true) -> CoachException.Reason.NOT_ENABLED
        chain.any { it::class.java.simpleName.contains("Timeout", true) } -> CoachException.Reason.TIMEOUT
        chain.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException } -> CoachException.Reason.NETWORK
        chain.any { it is IOException } -> CoachException.Reason.NETWORK
        else -> CoachException.Reason.UNAVAILABLE
    }
}

internal fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> opt(key).plain() }

private fun Any?.plain(): Any? = when (this) {
    null, JSONObject.NULL -> null
    is JSONObject -> toMap()
    is JSONArray -> (0 until length()).map { opt(it).plain() }
    else -> this
}
