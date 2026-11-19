package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileSummaryTest {

    private val today = LocalDate.of(2026, 11, 19) // a Thursday

    @Test
    fun `totals cover the window and split by sport`() {
        val activities = listOf(
            activity("s", Sport.SWIM, "2026-11-17T06:00:00Z", durationSec = 1800, distanceMeters = 2000.0),
            activity("b", Sport.BIKE, "2026-11-10T06:00:00Z", durationSec = 7200, distanceMeters = 60_000.0),
            activity("r", Sport.RUN, "2026-11-10T18:00:00Z", durationSec = 3600, distanceMeters = 12_000.0),
            // Before the four-week window: left out.
            activity("old", Sport.RUN, "2026-10-01T06:00:00Z"),
        )

        val summary = ProfileStats.summary(activities, weeks = 4, today = today, zone = ZoneOffset.UTC)

        assertEquals(3, summary.sessions)
        assertEquals(12_600, summary.durationSec)
        assertEquals(2, summary.activeWeeks)
        assertEquals(listOf(Sport.SWIM, Sport.BIKE, Sport.RUN), summary.bySport.map { it.sport })
        assertEquals(7200f / 12_600, summary.timeShare(Sport.BIKE))
    }

    @Test
    fun `no activities means zero shares, not a division by zero`() {
        val summary = ProfileStats.summary(emptyList(), weeks = 4, today = today, zone = ZoneOffset.UTC)

        assertEquals(0f, summary.timeShare(Sport.SWIM))
    }

    @Test
    fun `days to race count down and stop after the race`() {
        assertEquals(10, ProfileStats.daysToRace(today.plusDays(10), today))
        assertEquals(0, ProfileStats.daysToRace(today, today))
        assertNull(ProfileStats.daysToRace(today.minusDays(1), today))
        assertNull(ProfileStats.daysToRace(null, today))
    }
}
