package com.triforge.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckGateTest {

    private val gate = UpdateCheckGate(minIntervalMs = 600_000)

    @Test
    fun `the first check starts at once, even right after boot`() {
        assertTrue(gate.tryStart(now = 0))
    }

    @Test
    fun `a second check waits for the first to finish`() {
        gate.tryStart(now = 1_000)

        // Back from the sign-in page while the SDK is still working.
        assertFalse(gate.tryStart(now = 900_000))
    }

    @Test
    fun `coming back soon after a check does not ask again`() {
        gate.tryStart(now = 1_000)
        gate.finish(now = 5_000)

        assertFalse(gate.tryStart(now = 60_000))
        assertFalse(gate.tryStart(now = 604_999))
        assertTrue(gate.tryStart(now = 605_000))
    }
}
