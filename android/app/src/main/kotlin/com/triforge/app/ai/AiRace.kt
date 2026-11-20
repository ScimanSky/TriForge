package com.triforge.app.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long the app waits for one answer of the AI, and what it does
 * meanwhile. Firebase AI Logic itself waits three minutes.
 *
 * Most answers come in a few seconds, but now and then a request stays
 * hanging: the athlete waited the whole limit, pressed «Riprova», and the
 * same question was answered at once. So the app does that by itself: when
 * the first call is still silent after [againAfterMs] it asks again beside
 * it, and uses whichever answers first. The athlete sees one wait, never
 * longer than [totalMs].
 */
data class AiWait(
    /** After this the athlete gets the screen back, with «Riprova». */
    val totalMs: Long,
    /** A call silent for this long gets another one beside it. */
    val againAfterMs: Long,
    /** Calls made at most for one answer, the first included. */
    val calls: Int = 3,
) {
    companion object {
        /** One dish: alternatives, or the values of a dish the athlete wrote. */
        val DISH = AiWait(totalMs = 25_000L, againAfterMs = 8_000L)

        /** One answer in a chat, with the change it may propose. */
        val CHAT = AiWait(totalMs = 45_000L, againAfterMs = 15_000L)

        /** A whole week of sessions, or a day of meals: long answers, so the second call waits longer. */
        val PLAN = AiWait(totalMs = 90_000L, againAfterMs = 30_000L, calls = 2)
    }
}

private sealed interface RaceEvent<out T> {
    class Answered<T>(val value: T) : RaceEvent<T>
    class Failed(val error: CoachException) : RaceEvent<Nothing>

    /** The call started as number [call] has been silent for [AiWait.againAfterMs]. */
    class Silent(val call: Int) : RaceEvent<Nothing>
}

/**
 * The first answer [call] gives within [wait]. A call that is slow gets
 * another one beside it; one that fails in a way that asking again may fix
 * ([CoachException.Reason.transient]) is replaced at once; any other failure
 * ends the wait, since asking again would fail the same way. The calls
 * still running when the answer comes are cancelled.
 *
 * Throws the last failure when every call failed, and
 * [CoachException.Reason.TIMEOUT] when the time is up.
 */
internal suspend fun <T> firstAnswer(wait: AiWait, call: suspend () -> T): T {
    val answer = withTimeoutOrNull(wait.totalMs) { coroutineScope { RaceAnswer(race(wait, call)) } }
    return answer?.value ?: throw CoachException(CoachException.Reason.TIMEOUT)
}

/** An answer that may itself be null, told apart from the time being up. */
private class RaceAnswer<T>(val value: T)

private suspend fun <T> CoroutineScope.race(wait: AiWait, call: suspend () -> T): T {
    val events = Channel<RaceEvent<T>>(Channel.UNLIMITED)
    val jobs = mutableListOf<Job>()
    var started = 0
    var running = 0

    fun start() {
        val number = ++started
        running++
        jobs += launch {
            val event = try {
                RaceEvent.Answered(call())
            } catch (e: CancellationException) {
                // Ours when the race is over; otherwise a time limit of the call's own, which is one more failure.
                ensureActive()
                RaceEvent.Failed(CoachException(CoachException.Reason.TIMEOUT, e))
            } catch (e: CoachException) {
                RaceEvent.Failed(e)
            } catch (e: Exception) {
                RaceEvent.Failed(CoachException(CoachException.Reason.UNAVAILABLE, e))
            }
            events.send(event)
        }
        if (number < wait.calls) {
            jobs += launch {
                delay(wait.againAfterMs)
                events.send(RaceEvent.Silent(number))
            }
        }
    }

    fun stop() = jobs.forEach { it.cancel() }

    start()
    while (true) {
        when (val event = events.receive()) {
            is RaceEvent.Answered -> {
                stop()
                return event.value
            }

            is RaceEvent.Failed -> {
                running--
                if (!event.error.reason.transient) {
                    stop()
                    throw event.error
                }
                if (started < wait.calls) {
                    start()
                } else if (running == 0) {
                    stop()
                    throw event.error
                }
            }

            // Only the latest call's silence asks for one more: an older one already has its companion.
            is RaceEvent.Silent -> if (event.call == started && started < wait.calls) start()
        }
    }
}
