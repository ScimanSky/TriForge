package com.triforge.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The swim threshold checked against the swims, and the repeats the watch got wrong. */
class SwimThresholdTest {

    private val today = LocalDate.of(2026, 11, 19)
    private val zone = ZoneOffset.UTC

    private fun lap(meters: Double, seconds: Double) = SwimLap(distanceMeters = meters, durationSec = seconds, stroke = "free")
    private fun rest(seconds: Double) = SwimLap(distanceMeters = 0.0, durationSec = seconds)

    private fun swim(daysAgo: Long, laps: List<SwimLap>, hr: Int? = null) = Activity(
        id = "swim-$daysAgo", source = ActivitySource.GARMIN, sport = Sport.SWIM, name = "",
        startTime = today.minusDays(daysAgo).atTime(13, 20).toInstant(zone),
        durationSec = laps.sumOf { it.durationSec }.toLong(),
        movingDurationSec = laps.filter { !it.isRest }.sumOf { it.durationSec }.toLong(),
        distanceMeters = laps.sumOf { it.distanceMeters }, openWater = false, avgHr = hr, laps = laps,
    )

    /** 3,000 m at about 1:53 /100 m with long rests: thirty 100s. */
    private fun usualSwim(daysAgo: Long, pace: Double = 113.0, hr: Int? = 122) =
        swim(daysAgo, (1..30).flatMap { listOf(lap(100.0, pace), rest(60.0)) }, hr)

    private fun swimNoLaps(daysAgo: Long, meters: Double, movingSec: Long) = Activity(
        id = "block-$daysAgo", source = ActivitySource.GARMIN, sport = Sport.SWIM, name = "",
        startTime = today.minusDays(daysAgo).atTime(13, 20).toInstant(zone),
        durationSec = movingSec + 600, movingDurationSec = movingSec, distanceMeters = meters, openWater = false,
    )

    // ---- Repeats the watch got wrong ------------------------------------------

    @Test
    fun `a 100 in a minute among 100s in two is a miscounted length`() {
        val laps = (1..8).flatMap { listOf(lap(100.0, 113.0), rest(20.0)) } + lap(100.0, 61.0) + rest(20.0) + lap(100.0, 114.0)
        val repeats = SwimAnalysis.repeats(swim(1, laps))

        assertEquals(listOf(9), repeats.filter { it.doubtful }.map { it.number })
        // It is out of the zones, and its time counts at the usual pace, not at a sprint's.
        val meters = SwimAnalysis.zoneMeters(swim(1, laps), 113)!!
        assertEquals(900.0, meters.sum(), 1e-9)
        val honest = SwimAnalysis.lapTss(swim(1, laps.map { if (it.durationSec == 61.0) lap(54.0, 61.0) else it }), 113)!!
        assertEquals(honest, SwimAnalysis.lapTss(swim(1, laps), 113)!!, 0.6)
    }

    @Test
    fun `a sprint over one length is fast, not wrong`() {
        val laps = (1..8).flatMap { listOf(lap(100.0, 113.0), rest(20.0)) } + lap(25.0, 18.0) + rest(30.0) + lap(50.0, 40.0)
        assertTrue(SwimAnalysis.repeats(swim(1, laps)).none { it.doubtful })
        // Nobody holds 40 seconds per 100 m, however short the repeat.
        assertTrue(SwimAnalysis.repeats(swim(1, listOf(lap(50.0, 20.0)))).single().doubtful)
    }

    @Test
    fun `a miscounted length is not anyone's best effort`() {
        val efforts = swim(3, listOf(lap(400.0, 440.0), rest(120.0), lap(200.0, 208.0), rest(60.0), lap(200.0, 100.0)) + (1..6).map { lap(100.0, 115.0) })
        val estimate = SwimAnalysis.estimateCss(listOf(efforts), Instant.EPOCH)
        assertNotNull(estimate)
        assertEquals(208.0, estimate.efforts.first { it.distanceMeters == 200 }.seconds)
    }

    // ---- The threshold against the swims ---------------------------------------

    @Test
    fun `a threshold slower than every ordinary swim is called out with a proposal`() {
        val profile = AthleteProfile(cssSecPer100m = 126)
        val check = SwimThreshold.check(listOf(usualSwim(1), usualSwim(4), usualSwim(8)), profile, today, zone)

        assertNotNull(check)
        assertEquals(126, check.currentSecPer100)
        assertEquals(3, check.sessions)
        assertEquals(1.0, check.highShare, 1e-9)
        assertEquals(3, check.fasterSessions, "56 minutes of swimming faster than the threshold, three times")
        assertEquals(113, check.usualPaceSecPer100)
        assertEquals(122, check.avgHr)
        // No two distances to read a critical speed from: the usual pace, on the cautious side.
        assertEquals(CssSuggestion(107, CssSource.USUAL_PACE), check.suggestion)
    }

    @Test
    fun `a threshold that fits the swims is left alone`() {
        val swims = listOf(usualSwim(1), usualSwim(4), usualSwim(8))
        assertNull(SwimThreshold.check(swims, AthleteProfile(cssSecPer100m = 107), today, zone))
        assertNull(SwimThreshold.check(swims, AthleteProfile(cssSecPer100m = 100), today, zone), "swimming easy is not a wrong threshold")
        assertNull(SwimThreshold.check(swims, AthleteProfile(), today, zone), "no threshold, nothing to check")
    }

    @Test
    fun `one swim or old swims are not enough to doubt a threshold`() {
        val profile = AthleteProfile(cssSecPer100m = 126)
        assertNull(SwimThreshold.check(listOf(usualSwim(1)), profile, today, zone))
        assertNull(SwimThreshold.check(listOf(usualSwim(30), usualSwim(34), usualSwim(40)), profile, today, zone))
    }

    @Test
    fun `swims without laps are read as one block`() {
        val swims = listOf(swimNoLaps(2, 2500.0, 2825), swimNoLaps(6, 2000.0, 2260))
        val check = SwimThreshold.check(swims, AthleteProfile(cssSecPer100m = 126), today, zone)
        assertNotNull(check)
        assertEquals(2, check.fasterSessions)
        assertEquals(CssSource.USUAL_PACE, check.suggestion.source)
    }

    @Test
    fun `best efforts over two distances give the proposal when there are any`() {
        val test = swim(5, listOf(lap(400.0, 420.0), rest(300.0), lap(200.0, 198.0)) + (1..20).flatMap { listOf(rest(30.0), lap(100.0, 113.0)) })
        val check = SwimThreshold.check(listOf(usualSwim(1), test, usualSwim(9)), AthleteProfile(cssSecPer100m = 126), today, zone)
        assertNotNull(check)
        assertEquals(CssSource.BEST_EFFORTS, check.suggestion.source)
        // (400 − 200) / (420 − 198) m/s is 1:51 per 100 m.
        assertEquals(111, check.suggestion.paceSecPer100)
        assertFalse(check.suggestion.paceSecPer100 > 126)
    }

    @Test
    fun `the test's two times give the threshold, and times that cannot be a test give none`() {
        // 400 m in 7:00 and 200 m in 3:18: (420 − 198) / 2 is 1:51 per 100 m.
        assertEquals(111, SwimThreshold.fromTest(420, 198))
        assertNull(SwimThreshold.fromTest(198, 420), "the two fields swapped")
        assertNull(SwimThreshold.fromTest(390, 200), "a 400 faster than twice the 200")
        assertNull(SwimThreshold.fromTest(0, 0))
        assertNull(SwimThreshold.fromTest(3600, 600), "nobody's threshold")
    }
}
