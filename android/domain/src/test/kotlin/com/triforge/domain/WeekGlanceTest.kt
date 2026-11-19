package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeekGlanceTest {

    private val rome = ZoneId.of("Europe/Rome")

    // Wednesday 18 November 2026; the week starts on Monday 16.
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun `this week is compared with the same days of the last four weeks`() {
        val runs = (1..4).flatMap { back ->
            val monday = LocalDate.of(2026, 11, 16).minusWeeks(back.toLong())
            listOf(
                // 8 km on Tuesday of each past week, 12 km on its Saturday.
                activity("t$back", Sport.RUN, "${monday.plusDays(1)}T07:00:00Z", distanceMeters = 8_000.0, durationSec = 2400),
                activity("s$back", Sport.RUN, "${monday.plusDays(5)}T07:00:00Z", distanceMeters = 12_000.0, durationSec = 3600),
            )
        } + activity("now", Sport.RUN, "2026-11-17T07:00:00Z", distanceMeters = 10_000.0, durationSec = 3000)

        val glance = WeekGlanceModel.compute(runs, today, rome)
        val run = glance.sports.first { it.sport == Sport.RUN }

        assertEquals(3, glance.elapsedDays)
        assertEquals(4, glance.referenceWeeks)
        assertEquals(10_000.0, run.done)
        // By Wednesday the athlete usually has 8 km, over a whole week 20 km.
        assertEquals(8_000.0, run.usualToDate)
        assertEquals(20_000.0, run.usualWeek)
        assertEquals(2400L, glance.usualDurationToDateSec)
        assertEquals(6000L, glance.usualDurationWeekSec)
    }

    @Test
    fun `weeks before the first activity are not a reference`() {
        val activities = listOf(
            activity("first", Sport.BIKE, "2026-11-10T07:00:00Z", durationSec = 3600),
            activity("now", Sport.BIKE, "2026-11-16T07:00:00Z", durationSec = 1800),
        )

        val glance = WeekGlanceModel.compute(activities, today, rome)
        val bike = glance.sports.first { it.sport == Sport.BIKE }

        assertEquals(1, glance.referenceWeeks)
        // Bike is measured by time.
        assertEquals(1800.0, bike.done)
        assertEquals(3600.0, bike.usualToDate)
    }

    @Test
    fun `days run monday to sunday with time per sport`() {
        val activities = listOf(
            activity("swim", Sport.SWIM, "2026-11-16T06:00:00Z", durationSec = 3000, distanceMeters = 2500.0),
            activity("run", Sport.RUN, "2026-11-16T17:00:00Z", durationSec = 2400),
        )

        val glance = WeekGlanceModel.compute(activities, today, rome)

        assertEquals(7, glance.days.size)
        assertEquals(LocalDate.of(2026, 11, 16), glance.days.first().date)
        assertEquals(5400L, glance.days.first().totalSec)
        assertEquals(0L, glance.days.last().totalSec)
        assertEquals(0, glance.referenceWeeks)
        assertNull(glance.sports.first().usualToDate)
    }
}
