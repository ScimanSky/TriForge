package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrainingStatsTest {

    private val rome = ZoneId.of("Europe/Rome")

    // Wednesday 18 November 2026; its week runs Monday 16 to Sunday 22.
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun `week starts on monday`() {
        assertEquals(LocalDate.of(2026, 11, 16), TrainingStats.weekStart(today))
        assertEquals(LocalDate.of(2026, 11, 16), TrainingStats.weekStart(LocalDate.of(2026, 11, 16)))
        assertEquals(LocalDate.of(2026, 11, 16), TrainingStats.weekStart(LocalDate.of(2026, 11, 22)))
    }

    @Test
    fun `week summary totals each sport separately`() {
        val activities = listOf(
            activity("swim1", Sport.SWIM, "2026-11-16T06:00:00Z", durationSec = 2400, distanceMeters = 2000.0),
            activity("swim2", Sport.SWIM, "2026-11-18T06:00:00Z", durationSec = 1800, distanceMeters = 1500.0),
            activity("ride", Sport.BIKE, "2026-11-17T15:00:00Z", durationSec = 7200, distanceMeters = 60_000.0, elevationGainMeters = 850.0),
            activity("run", Sport.RUN, "2026-11-18T17:00:00Z", durationSec = 3000, distanceMeters = 10_000.0),
        )

        val week = TrainingStats.weekSummary(activities, today, rome)

        assertEquals(LocalDate.of(2026, 11, 16), week.weekStart)
        assertEquals(SportTotals(Sport.SWIM, 2, 3500.0, 4200, 0.0), week.of(Sport.SWIM))
        assertEquals(SportTotals(Sport.BIKE, 1, 60_000.0, 7200, 850.0), week.of(Sport.BIKE))
        assertEquals(4, week.sessions)
        assertEquals(14_400, week.durationSec)
    }

    @Test
    fun `a sport with no sessions reports zeros`() {
        val week = TrainingStats.weekSummary(emptyList(), today, rome)

        assertEquals(SportTotals(Sport.RUN), week.of(Sport.RUN))
        assertEquals(0, week.sessions)
    }

    @Test
    fun `week boundaries follow the athlete's time zone, not UTC`() {
        // 23:30 UTC on Sunday 15 November is already 00:30 on Monday 16 in Rome.
        val lateSunday = activity("late", Sport.RUN, "2026-11-15T23:30:00Z")
        // 23:30 UTC on Sunday 22 November is Monday 23 in Rome: next week.
        val nextWeek = activity("next", Sport.RUN, "2026-11-22T23:30:00Z")

        val utc = ZoneId.of("UTC")

        assertEquals(1, TrainingStats.weekSummary(listOf(lateSunday), today, rome).sessions)
        assertEquals(0, TrainingStats.weekSummary(listOf(nextWeek), today, rome).sessions)
        assertEquals(0, TrainingStats.weekSummary(listOf(lateSunday), today, utc).sessions)
        assertEquals(1, TrainingStats.weekSummary(listOf(nextWeek), today, utc).sessions)
    }

    @Test
    fun `trend lists weeks oldest first and keeps empty weeks`() {
        val activities = listOf(
            activity("old", Sport.RUN, "2026-11-03T08:00:00Z"),
            activity("now", Sport.BIKE, "2026-11-18T08:00:00Z"),
        )

        val trend = TrainingStats.weeklyTrend(activities, weeks = 4, today = today, zone = rome)

        assertEquals(
            listOf("2026-10-26", "2026-11-02", "2026-11-09", "2026-11-16"),
            trend.map { it.weekStart.toString() },
        )
        assertEquals(listOf(0, 1, 0, 1), trend.map { it.sessions })
    }

    @Test
    fun `percent change compares against the previous value`() {
        assertEquals(25, TrainingStats.percentChange(current = 5.0, previous = 4.0))
        assertEquals(-50, TrainingStats.percentChange(current = 2.0, previous = 4.0))
        assertEquals(-100, TrainingStats.percentChange(current = 0.0, previous = 4.0))
    }

    @Test
    fun `percent change is undefined without a previous value`() {
        assertNull(TrainingStats.percentChange(current = 5.0, previous = 0.0))
    }
}
