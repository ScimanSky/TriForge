package com.triforge.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DeduplicationTest {

    @Test
    fun `health connect copy of a garmin workout is dropped`() {
        val garmin = activity("garmin_1", Sport.RUN, "2026-11-18T06:30:00Z")
        val copy = activity("hc_1", Sport.RUN, "2026-11-18T06:31:10Z", source = ActivitySource.HEALTH_CONNECT)

        assertEquals(listOf(garmin), listOf(copy, garmin).withoutDuplicates())
    }

    @Test
    fun `health connect session with no garmin match is kept`() {
        val garmin = activity("garmin_1", Sport.RUN, "2026-11-18T06:30:00Z")
        val later = activity("hc_1", Sport.RUN, "2026-11-18T18:00:00Z", source = ActivitySource.HEALTH_CONNECT)

        assertEquals(listOf(garmin, later), listOf(garmin, later).withoutDuplicates())
    }

    @Test
    fun `same start time but a different sport is not a duplicate`() {
        val garmin = activity("garmin_1", Sport.BIKE, "2026-11-18T06:30:00Z")
        val swim = activity("hc_1", Sport.SWIM, "2026-11-18T06:30:00Z", source = ActivitySource.HEALTH_CONNECT)

        assertEquals(2, listOf(garmin, swim).withoutDuplicates().size)
    }

    @Test
    fun `health connect sessions are kept when garmin is not connected`() {
        val a = activity("hc_1", Sport.RUN, "2026-11-18T06:30:00Z", source = ActivitySource.HEALTH_CONNECT)
        val b = activity("hc_2", Sport.RUN, "2026-11-18T06:31:00Z", source = ActivitySource.HEALTH_CONNECT)

        assertEquals(listOf(a, b), listOf(a, b).withoutDuplicates())
    }

    @Test
    fun `two garmin activities are never merged`() {
        val a = activity("garmin_1", Sport.RUN, "2026-11-18T06:30:00Z")
        val b = activity("garmin_2", Sport.RUN, "2026-11-18T06:32:00Z")

        assertEquals(listOf(a, b), listOf(a, b).withoutDuplicates())
    }
}
