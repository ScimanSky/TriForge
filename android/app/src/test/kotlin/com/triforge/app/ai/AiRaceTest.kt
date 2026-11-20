package com.triforge.app.ai

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** The wait for an answer of the AI: one wait for the athlete, more than one call when the first is slow. */
class AiRaceTest {

    /**
     * Short times, so the tests run in real time: a second call after 100 ms,
     * everything over in two seconds. «Quick» below means well before that
     * limit, with room for a slow machine.
     */
    private val wait = AiWait(totalMs = 2_000L, againAfterMs = 100L, calls = 3)

    private val quick = 1_200L

    private fun <T> timed(block: suspend () -> T): Pair<Result<T>, Long> = runBlocking {
        val start = System.nanoTime()
        val result = runCatching { block() }
        result to (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun `a quick answer needs one call`() {
        val calls = AtomicInteger()
        val (result, ms) = timed { firstAnswer(wait) { calls.incrementAndGet(); delay(5); "ok" } }
        assertEquals("ok", result.getOrThrow())
        assertEquals(1, calls.get())
        assertTrue(ms < quick, "took $ms ms")
    }

    @Test
    fun `a first call that hangs is covered by a second one`() {
        val calls = AtomicInteger()
        val cancelled = AtomicInteger()
        val (result, ms) = timed {
            firstAnswer(wait) {
                if (calls.incrementAndGet() == 1) {
                    try {
                        awaitCancellation()
                    } catch (e: CancellationException) {
                        cancelled.incrementAndGet()
                        throw e
                    }
                }
                delay(5)
                "second"
            }
        }
        assertEquals("second", result.getOrThrow())
        assertEquals(2, calls.get())
        // The athlete waited for the second call, not for the whole limit.
        assertTrue(ms < quick, "took $ms ms")
        // The call left hanging is not kept running.
        assertEquals(1, cancelled.get())
    }

    @Test
    fun `a slow first call still wins when it answers before the second`() {
        val calls = AtomicInteger()
        val (result, _) = timed {
            firstAnswer(wait) {
                val number = calls.incrementAndGet()
                delay(if (number == 1) 300 else 1_500)
                "call $number"
            }
        }
        assertEquals("call 1", result.getOrThrow())
        // Others were started beside it meanwhile: they are dropped when it answers.
        assertTrue(calls.get() in 2..wait.calls)
    }

    @Test
    fun `a failure that asking again may fix is asked again at once`() {
        val calls = AtomicInteger()
        val (result, ms) = timed {
            firstAnswer(wait) {
                if (calls.incrementAndGet() == 1) throw CoachException(CoachException.Reason.UNAVAILABLE)
                "ok"
            }
        }
        assertEquals("ok", result.getOrThrow())
        assertEquals(2, calls.get())
        assertTrue(ms < quick, "took $ms ms")
    }

    @Test
    fun `an unreadable answer is asked again`() {
        val calls = AtomicInteger()
        val (result, _) = timed {
            firstAnswer(wait) {
                if (calls.incrementAndGet() < 3) throw CoachException(CoachException.Reason.BAD_ANSWER)
                "third"
            }
        }
        assertEquals("third", result.getOrThrow())
        assertEquals(3, calls.get())
    }

    @Test
    fun `a failure that asking again cannot fix ends the wait`() {
        val calls = AtomicInteger()
        val (result, ms) = timed<String> {
            firstAnswer(wait) {
                calls.incrementAndGet()
                throw CoachException(CoachException.Reason.BUSY)
            }
        }
        assertEquals(CoachException.Reason.BUSY, (result.exceptionOrNull() as CoachException).reason)
        assertEquals(1, calls.get())
        assertTrue(ms < quick, "took $ms ms")
    }

    @Test
    fun `when every call fails the athlete hears the last reason, without waiting for the limit`() {
        val calls = AtomicInteger()
        val (result, ms) = timed<String> {
            firstAnswer(wait) {
                calls.incrementAndGet()
                throw CoachException(CoachException.Reason.NETWORK)
            }
        }
        assertEquals(CoachException.Reason.NETWORK, (result.exceptionOrNull() as CoachException).reason)
        assertEquals(wait.calls, calls.get())
        assertTrue(ms < quick, "took $ms ms")
    }

    @Test
    fun `calls that all hang end at the limit as a timeout`() {
        val calls = AtomicInteger()
        val (result, ms) = timed<String> {
            firstAnswer(wait) {
                calls.incrementAndGet()
                awaitCancellation()
            }
        }
        assertEquals(CoachException.Reason.TIMEOUT, (result.exceptionOrNull() as CoachException).reason)
        assertEquals(wait.calls, calls.get())
        assertTrue(ms >= wait.totalMs - 50, "took $ms ms")
    }

    @Test
    fun `a failed call does not stop one still running`() {
        val calls = AtomicInteger()
        val two = wait.copy(calls = 2)
        val (result, _) = timed {
            firstAnswer(two) {
                if (calls.incrementAndGet() == 1) {
                    delay(400)
                    "slow first"
                } else {
                    throw CoachException(CoachException.Reason.UNAVAILABLE)
                }
            }
        }
        assertEquals("slow first", result.getOrThrow())
        assertEquals(2, calls.get())
    }

    @Test
    fun `a time limit inside the call counts as a failure, not as the end of the race`() {
        val calls = AtomicInteger()
        val (result, _) = timed {
            firstAnswer(wait) {
                if (calls.incrementAndGet() == 1) kotlinx.coroutines.withTimeout(10) { awaitCancellation() }
                "ok"
            }
        }
        assertEquals("ok", result.getOrThrow())
        assertEquals(2, calls.get())
    }

    @Test
    fun `the waits of the app leave room for a second call`() {
        listOf(AiWait.DISH, AiWait.CHAT, AiWait.PLAN).forEach {
            assertTrue(it.calls >= 2)
            assertTrue(it.againAfterMs * 2 <= it.totalMs, "$it")
        }
    }
}
