package com.triforge.domain

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionLoadTest {

    private fun session(
        sport: Sport,
        durationSec: Long = 3600,
        distanceMeters: Double = 0.0,
        normPower: Int? = null,
        trainingLoad: Double? = null,
        id: String = "s",
    ) = Activity(
        id = id, source = ActivitySource.GARMIN, sport = sport, name = "",
        startTime = Instant.parse("2026-11-17T07:00:00Z"), durationSec = durationSec,
        distanceMeters = distanceMeters, normPowerWatts = normPower, trainingLoad = trainingLoad,
    )

    @Test
    fun `an hour at FTP is 100`() {
        val load = SessionLoadModel.estimate(listOf(session(Sport.BIKE, normPower = 250)), AthleteProfile(ftpWatts = 250)).single()

        assertEquals(100.0, load.tss, 1e-6)
        assertEquals(LoadMethod.POWER, load.method)
        assertEquals(LoadQuality.HIGH, load.quality)
    }

    @Test
    fun `run load grows with the square of relative speed`() {
        // Threshold 4:00/km; an hour at 5:00/km is IF 0.8.
        val load = SessionLoadModel.estimate(
            listOf(session(Sport.RUN, distanceMeters = 12_000.0)),
            AthleteProfile(thresholdPaceSecPerKm = 240),
        ).single()

        assertEquals(64.0, load.tss, 1e-6)
        assertEquals(LoadMethod.PACE, load.method)
    }

    @Test
    fun `swim load uses the cube of relative speed`() {
        // CSS 1:30/100 m; an hour at 2:00/100 m (3000 m) is IF 0.75.
        val load = SessionLoadModel.estimate(
            listOf(session(Sport.SWIM, distanceMeters = 3000.0)),
            AthleteProfile(cssSecPer100m = 90),
        ).single()

        assertEquals(42.1875, load.tss, 1e-6)
        assertEquals(LoadMethod.SWIM_PACE, load.method)
    }

    @Test
    fun `Garmin load is rescaled on the athlete's own sessions`() {
        val profile = AthleteProfile(ftpWatts = 250)
        // Three rides where TSS is half of Garmin's load, then a run with only Garmin load.
        val rides = (1..3).map { session(Sport.BIKE, normPower = 250, trainingLoad = 200.0, id = "r$it") }
        val run = session(Sport.RUN, trainingLoad = 120.0, id = "run")

        val load = SessionLoadModel.estimate(rides + run, profile).last()

        assertEquals(60.0, load.tss, 1e-6)
        assertEquals(LoadMethod.GARMIN_LOAD, load.method)
        assertEquals(LoadQuality.MEDIUM, load.quality)
    }

    @Test
    fun `without anything else duration gives a low quality estimate`() {
        val load = SessionLoadModel.estimate(listOf(session(Sport.RUN, durationSec = 1800)), AthleteProfile()).single()

        assertEquals(32.0, load.tss, 1e-6)
        assertEquals(LoadQuality.LOW, load.quality)
    }
}

class WellnessMapperTest {

    @Test
    fun `Garmin wins field by field and Health Connect fills the gaps`() {
        val day = LocalDate.of(2026, 11, 18)
        val merged = WellnessMapper.merge(
            listOf(
                ActivitySource.HEALTH_CONNECT to DailyWellness(day, hrvRmssd = 70.0, restingHr = 50, sleepSec = 25_000),
                ActivitySource.GARMIN to DailyWellness(day, hrvRmssd = 62.0, sleepSec = null),
            ),
        ).single()

        assertEquals(62.0, merged.hrvRmssd)
        assertEquals(50, merged.restingHr)
        assertEquals(25_000L, merged.sleepSec)
    }

    @Test
    fun `reads a stored wellness document`() {
        val (source, wellness) = WellnessMapper.fromFields(
            mapOf("source" to "garmin", "date" to "2026-11-18", "hrvRmssd" to 61.5, "restingHr" to 47L, "sleepSec" to 27000L),
        )!!

        assertEquals(ActivitySource.GARMIN, source)
        assertEquals(47, wellness.restingHr)
        assertNull(WellnessMapper.fromFields(mapOf("source" to "garmin", "date" to "bad")))
    }

    @Test
    fun `a check-in survives a round trip and rejects out of range items`() {
        val checkIn = CheckIn(LocalDate.of(2026, 11, 18), 2, 3, 4, 5, 6, sick = true)

        assertEquals(checkIn, CheckInMapper.fromFields(CheckInMapper.toFields(checkIn)))
        assertEquals(20, checkIn.total)
        assertNull(CheckInMapper.fromFields(CheckInMapper.toFields(checkIn) + ("fatigue" to 9)))
    }
}
