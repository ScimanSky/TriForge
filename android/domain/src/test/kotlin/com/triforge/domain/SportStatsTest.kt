package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SportStatsTest {

    private val rome = ZoneId.of("Europe/Rome")

    // Wednesday; its week starts Monday 16 November 2026 (ISO week 47).
    private val today = LocalDate.of(2026, 11, 18)

    private val runs = listOf(
        activity("this1", Sport.RUN, "2026-11-16T07:00:00Z", durationSec = 3000, distanceMeters = 10_000.0, elevationGainMeters = 80.0, avgHr = 150, avgCadence = 174),
        activity("this2", Sport.RUN, "2026-11-18T07:00:00Z", durationSec = 1800, distanceMeters = 5_000.0, avgHr = 140),
        activity("last", Sport.RUN, "2026-11-10T07:00:00Z", durationSec = 6000, distanceMeters = 18_000.0, elevationGainMeters = 300.0),
        activity("before", Sport.RUN, "2026-11-03T07:00:00Z", durationSec = 2500, distanceMeters = 10_000.0),
        activity("older", Sport.RUN, "2026-10-27T07:00:00Z", durationSec = 2400, distanceMeters = 8_000.0),
        activity("ride", Sport.BIKE, "2026-11-17T07:00:00Z", durationSec = 7200, distanceMeters = 60_000.0),
    )

    @Test
    fun `report totals only the chosen sport and period`() {
        val report = SportStats.report(runs, Sport.RUN, weeks = 2, today = today, zone = rome, loadedWeeks = 16)

        assertEquals(3, report.sessions)
        assertEquals(33_000.0, report.distanceMeters)
        assertEquals(10_800, report.durationSec)
        assertEquals(380.0, report.elevationGainMeters)
        assertEquals(1.5, report.sessionsPerWeek)
    }

    @Test
    fun `weekly volumes are oldest first and carry the iso week number`() {
        val report = SportStats.report(runs, Sport.RUN, weeks = 4, today = today, zone = rome, loadedWeeks = 16)

        assertEquals(listOf(44, 45, 46, 47), report.weekly.map { it.weekNumber })
        assertEquals(listOf(8_000.0, 10_000.0, 18_000.0, 15_000.0), report.weekly.map { it.distanceMeters })
        assertEquals(listOf(1, 1, 1, 2), report.weekly.map { it.sessions })
    }

    @Test
    fun `a week without sessions is kept and has no pace`() {
        val report = SportStats.report(runs, Sport.SWIM, weeks = 2, today = today, zone = rome, loadedWeeks = 16)

        assertEquals(2, report.weekly.size)
        assertEquals(0, report.sessions)
        assertNull(report.weekly.last().secondsPerMeter)
    }

    @Test
    fun `volume is compared with the period before`() {
        val report = SportStats.report(runs, Sport.RUN, weeks = 2, today = today, zone = rome, loadedWeeks = 16)

        // 33 km now against 18 km in the two weeks before.
        assertEquals(18_000.0, report.previousDistanceMeters)
        assertEquals(83, report.volumeChangePercent)
    }

    @Test
    fun `no comparison when the previous period was not loaded`() {
        val report = SportStats.report(runs, Sport.RUN, weeks = 16, today = today, zone = rome, loadedWeeks = 16)

        assertNull(report.previousDistanceMeters)
        assertNull(report.volumeChangePercent)
    }

    @Test
    fun `cycling volume is compared by time, not distance`() {
        val rides = listOf(
            activity("now", Sport.BIKE, "2026-11-17T07:00:00Z", durationSec = 7200, distanceMeters = 40_000.0),
            activity("prev", Sport.BIKE, "2026-11-10T07:00:00Z", durationSec = 3600, distanceMeters = 40_000.0),
        )

        val report = SportStats.report(rides, Sport.BIKE, weeks = 1, today = today, zone = rome, loadedWeeks = 16)

        assertEquals(100, report.volumeChangePercent)
    }

    @Test
    fun `technical averages ignore sessions without the value`() {
        val report = SportStats.report(runs, Sport.RUN, weeks = 1, today = today, zone = rome, loadedWeeks = 16)

        assertEquals(145, report.avgHr)
        assertEquals(174, report.avgCadence)
        assertNull(report.avgPowerWatts)
    }

    @Test
    fun `records come from every loaded activity of the sport`() {
        val report = SportStats.report(runs, Sport.RUN, weeks = 1, today = today, zone = rome, loadedWeeks = 16)

        val byKind = report.records.associate { it.kind to it.activity.id }
        assertEquals("last", byKind[RecordKind.LONGEST_DISTANCE])
        assertEquals("last", byKind[RecordKind.LONGEST_DURATION])
        // 10 km in 2500 s is the fastest pace among runs of 5 km or more.
        assertEquals("before", byKind[RecordKind.FASTEST])
    }

    @Test
    fun `a short sprint does not take the speed record`() {
        val sprint = activity("sprint", Sport.RUN, "2026-11-17T07:00:00Z", durationSec = 180, distanceMeters = 1_000.0)

        val report = SportStats.report(runs + sprint, Sport.RUN, weeks = 1, today = today, zone = rome, loadedWeeks = 16)

        assertEquals("before", report.records.first { it.kind == RecordKind.FASTEST }.activity.id)
    }

    @Test
    fun `no records without activities`() {
        val report = SportStats.report(emptyList(), Sport.RUN, weeks = 4, today = today, zone = rome, loadedWeeks = 16)

        assertEquals(emptyList(), report.records)
    }

    @Test
    fun `activities are grouped by week, newest week first`() {
        val newestFirst = runs.sortedByDescending { it.startTime }

        val groups = newestFirst.groupedByWeek(rome)

        assertEquals(
            listOf("2026-11-16", "2026-11-09", "2026-11-02", "2026-10-26"),
            groups.map { it.first.toString() },
        )
        assertEquals(listOf("this2", "ride", "this1"), groups.first().second.map { it.id })
    }
}
