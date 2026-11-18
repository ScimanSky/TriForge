package com.triforge.app

import com.triforge.app.ui.TrainingViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TrainingWindowTest {

    @Test
    fun `window starts on the monday sixteen weeks back, at local midnight`() {
        val rome = ZoneId.of("Europe/Rome")

        // Wednesday 18 November 2026: this week starts Monday 16, and fifteen
        // weeks earlier is Monday 3 August, on summer time (UTC+2).
        val start = TrainingViewModel.windowStart(LocalDate.of(2026, 11, 18), rome)

        assertEquals("2026-08-02T22:00:00Z", start.toString())
    }
}
