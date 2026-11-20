package com.triforge.app.ai

import com.triforge.domain.coach.ChatMessage
import com.triforge.domain.coach.CoachReply
import com.triforge.domain.coach.CoachRole

/**
 * One AI coach, as the app sees it. The screens only talk to this interface,
 * so the agents can later move to a server (Cloud Functions or Cloud Run)
 * without touching the rest of the app.
 *
 * Each agent is separate: its own instructions, its own model session, its
 * own plan type [P]. What it knows of the other agent arrives inside the
 * request text, from the shared board.
 */
interface CoachAgent<P> {
    val role: CoachRole

    /** Writes a new plan from [request], built by [com.triforge.domain.coach.CoachPrompts]. */
    suspend fun plan(request: String): P

    /** Answers the athlete. [history] is the conversation so far, oldest first; [turn] the new message with context. */
    suspend fun reply(history: List<ChatMessage>, turn: String): CoachReply<P>
}

/** Why a coach could not answer, in words the athlete can act on. */
class CoachException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** The phone has no connection. */
        NETWORK,

        /** The model took longer than the app waits for it. */
        TIMEOUT,

        /** The AI service answered with an error: nothing the athlete's connection can fix. */
        UNAVAILABLE,

        /** Too many requests in a short time. */
        BUSY,

        /** Firebase AI Logic is not set up on the project. */
        NOT_ENABLED,

        /** The answer could not be used, e.g. a plan without sessions. */
        BAD_ANSWER,

        /** NutriAI cannot work out the day without the athlete's weight. */
        NEEDS_WEIGHT,

        /** The model declined to answer, e.g. for its safety filters. */
        BLOCKED,
        ;

        /** Whether asking again, unchanged, may well work. */
        val transient: Boolean get() = this == TIMEOUT || this == UNAVAILABLE || this == NETWORK || this == BAD_ANSWER
    }
}

/**
 * How long the app waits for one answer before giving the athlete the
 * screen back with «Riprova». Firebase AI Logic itself waits three minutes.
 */
object AiWait {
    /** One dish: alternatives, or the values of a dish the athlete wrote. */
    const val DISH_MS = 20_000L

    /** One answer in a chat, with the change it may propose. */
    const val CHAT_MS = 45_000L

    /** A whole week of sessions, or a day of meals. */
    const val PLAN_MS = 90_000L
}
