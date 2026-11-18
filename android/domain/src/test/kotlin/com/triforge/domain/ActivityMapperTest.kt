package com.triforge.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityMapperTest {

    private val start = Instant.parse("2026-11-18T06:30:00Z")

    @Test
    fun `garmin document is read with firestore's number types`() {
        val fields = mapOf(
            "source" to "garmin",
            "sport" to "bike",
            "name" to "Giro del lago",
            "durationSec" to 7200L,
            "distanceMeters" to 60000L, // whole numbers come back as Long
            "elevationGainMeters" to 850.5,
            "avgHr" to 141L,
            "trainingLoad" to 180.2,
        )

        val activity = ActivityMapper.fromFields("garmin_1", fields, start)

        assertEquals(
            Activity(
                id = "garmin_1",
                source = ActivitySource.GARMIN,
                sport = Sport.BIKE,
                name = "Giro del lago",
                startTime = start,
                durationSec = 7200,
                distanceMeters = 60_000.0,
                elevationGainMeters = 850.5,
                avgHr = 141,
                trainingLoad = 180.2,
            ),
            activity,
        )
    }

    @Test
    fun `missing optional fields stay null`() {
        val activity = ActivityMapper.fromFields("hc_1", mapOf("source" to "health_connect", "sport" to "swim"), start)!!

        assertEquals(ActivitySource.HEALTH_CONNECT, activity.source)
        assertEquals(Sport.SWIM, activity.sport)
        assertEquals(0, activity.durationSec)
        assertNull(activity.avgHr)
    }

    @Test
    fun `unknown sport is kept as other`() {
        val activity = ActivityMapper.fromFields("garmin_2", mapOf("source" to "garmin", "sport" to "yoga"), start)

        assertEquals(Sport.OTHER, activity?.sport)
    }

    @Test
    fun `document without start time or known source is ignored`() {
        assertNull(ActivityMapper.fromFields("x", mapOf("source" to "garmin"), null))
        assertNull(ActivityMapper.fromFields("x", mapOf("source" to "strava"), start))
        assertNull(ActivityMapper.fromFields("x", emptyMap(), start))
    }

    @Test
    fun `sport keys round trip`() {
        Sport.entries.forEach { assertEquals(it, ActivityMapper.sportOf(ActivityMapper.sportKey(it))) }
    }
}
