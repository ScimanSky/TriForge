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
    fun `pool swim keeps the swimming time and reads the pool in metres`() {
        // As synced from Garmin on 18 Nov 2026: 121 lengths of 25 m, with the
        // pool length still in centimetres.
        val fields = mapOf(
            "source" to "garmin",
            "sport" to "swim",
            "durationSec" to 5236L,
            "movingDurationSec" to 3429L,
            "distanceMeters" to 3025L,
            "poolLengthMeters" to 2500L,
            "lengths" to 121L,
        )

        val activity = ActivityMapper.fromFields("garmin_3", fields, start)!!

        assertEquals(3429, activity.movingDurationSec)
        assertEquals(25.0, activity.poolLengthMeters)
        assertEquals("1:53", Formatters.paceValue(Sport.SWIM, activity.distanceMeters, activity.paceSec))
        assertEquals(1807, activity.restSec)
    }

    @Test
    fun `pool length already in metres is left alone`() {
        assertEquals(25.0, ActivityMapper.poolLength(25.0))
        assertEquals(50.0, ActivityMapper.poolLength(50.0))
        assertEquals(22.86, ActivityMapper.poolLength(2286.0)) // 25 yd
        assertNull(ActivityMapper.poolLength(0.0))
        assertNull(ActivityMapper.poolLength(null))
    }

    @Test
    fun `pace time falls back to the duration`() {
        val swim = activity("s", Sport.SWIM, "2026-11-18T06:30:00Z", durationSec = 3600)
        val run = activity("r", Sport.RUN, "2026-11-18T06:30:00Z", durationSec = 3600, movingDurationSec = 3300)
        val odd = activity("o", Sport.SWIM, "2026-11-18T06:30:00Z", durationSec = 3600, movingDurationSec = 3700)

        // Health Connect gives no time in motion.
        assertEquals(3600, swim.paceSec)
        assertNull(swim.restSec)
        // A run keeps the timer, as the watch does.
        assertEquals(3600, run.paceSec)
        assertEquals(3300, run.movingSec)
        assertEquals(300, run.restSec)
        // Time in motion can never exceed the session.
        assertEquals(3600, odd.paceSec)
        assertEquals(0, odd.restSec)
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
