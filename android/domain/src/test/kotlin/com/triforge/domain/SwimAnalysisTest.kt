package com.triforge.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SwimAnalysisTest {

    private fun lap(meters: Double, seconds: Double, strokes: Int? = null, stroke: String? = "free") =
        SwimLap(distanceMeters = meters, durationSec = seconds, strokes = strokes, stroke = stroke)

    private fun rest(seconds: Double) = SwimLap(distanceMeters = 0.0, durationSec = seconds)

    private fun swim(
        laps: List<SwimLap>,
        start: String = "2026-11-12T06:10:00Z",
        openWater: Boolean = false,
        strokeDistance: Double? = null,
        cadence: Int? = null,
        swolf: Int? = null,
    ) = Activity(
        id = start, source = ActivitySource.GARMIN, sport = Sport.SWIM, name = "",
        startTime = Instant.parse(start), durationSec = laps.sumOf { it.durationSec }.toLong(),
        distanceMeters = laps.sumOf { it.distanceMeters }, openWater = openWater,
        strokeDistanceMeters = strokeDistance, avgCadence = cadence, swolf = swolf, laps = laps,
    )

    /** Warm-up 400, 5 x 200 around a 1:48 CSS, 4 x 100 pull, cool-down 200. */
    private val session = swim(
        listOf(
            lap(400.0, 480.0), rest(30.0),
            lap(200.0, 214.0, strokes = 115), rest(30.0),
            lap(200.0, 212.0, strokes = 116), rest(30.0),
            lap(200.0, 216.0, strokes = 117), rest(30.0),
            lap(200.0, 216.0, strokes = 118), rest(30.0),
            lap(200.0, 218.0, strokes = 120), rest(90.0),
            lap(100.0, 112.0), rest(20.0),
            lap(100.0, 112.0), rest(20.0),
            lap(100.0, 112.0), rest(20.0),
            lap(100.0, 113.0), rest(60.0),
            lap(200.0, 270.0, stroke = "back"),
        ),
    )

    @Test
    fun `repeats carry the rest that follows them`() {
        val repeats = SwimAnalysis.repeats(session)

        assertEquals(11, repeats.size)
        assertEquals(30.0, repeats[0].restAfterSec)
        assertEquals(90.0, repeats[5].restAfterSec)
        assertNull(repeats.last().restAfterSec)
        assertEquals(120.0, repeats[0].paceSecPer100, 1e-9)
    }

    @Test
    fun `zones follow the CSS speed`() {
        // B2 is the threshold zone and ends at the CSS itself.
        assertEquals("B2", SwimAnalysis.zoneCode(108.0, 108))
        assertEquals("B2", SwimAnalysis.zoneCode(110.0, 108))
        assertEquals(SwimAnalysis.THRESHOLD_ZONE, SwimAnalysis.zoneOf(110.0, 108))
        assertEquals("C1", SwimAnalysis.zoneCode(104.0, 108))
        assertEquals("C2", SwimAnalysis.zoneCode(100.0, 108))
        assertEquals("C3", SwimAnalysis.zoneCode(95.0, 108))
        assertEquals("B1", SwimAnalysis.zoneCode(115.0, 108))
        assertEquals("A2", SwimAnalysis.zoneCode(124.0, 108))
        assertEquals("A1", SwimAnalysis.zoneCode(130.0, 108))
        assertEquals(1, SwimAnalysis.zoneOf(130.0, 108))
        assertEquals(7, SwimAnalysis.zoneOf(95.0, 108))
    }

    @Test
    fun `metres per zone add up to the swum distance`() {
        val meters = assertNotNull(SwimAnalysis.zoneMeters(session, 108))

        // A1 to C3: the backstroke in A1, the warm-up in B1, 200s and 100s around the threshold in B2,
        // and the two fastest 200s, a second or two under it, in C1.
        assertContentEquals(doubleArrayOf(200.0, 0.0, 400.0, 1000.0, 400.0, 0.0, 0.0), meters)
        assertNull(SwimAnalysis.zoneMeters(session, null))
        assertNull(SwimAnalysis.zoneMeters(swim(emptyList()), 108))
    }

    @Test
    fun `lap by lap load counts only the swimming`() {
        val oneLap = swim(listOf(lap(3600.0 / 108.0 * 100.0, 3600.0), rest(600.0)))

        assertEquals(100.0, assertNotNull(SwimAnalysis.lapTss(oneLap, 108)), 1e-6)
        assertNull(SwimAnalysis.lapTss(oneLap, null))
    }

    @Test
    fun `session load uses the laps when they are there`() {
        val load = SessionLoadModel.estimate(listOf(session), AthleteProfile(cssSecPer100m = 108)).single()

        assertEquals(LoadMethod.SWIM_PACE, load.method)
        assertEquals(LoadQuality.HIGH, load.quality)
        assertEquals(SwimAnalysis.lapTss(session, 108)!!, load.tss, 1e-9)
    }

    @Test
    fun `main set is the longest series of equal repeats`() {
        val hold = assertNotNull(SwimAnalysis.setHold(session))

        assertEquals(5, hold.repeats)
        assertEquals(200.0, hold.distanceMeters)
        assertEquals(2.0, hold.fadeSecPer100, 1e-9)
        assertEquals(PacingRegularity.VERY_STEADY, hold.regularity)
        assertEquals(200.0 / 115, assertNotNull(hold.firstMetersPerStroke), 1e-9)
        assertEquals(200.0 / 120, assertNotNull(hold.lastMetersPerStroke), 1e-9)
        assertEquals((115.0 / 120 - 1) * 100, assertNotNull(hold.strokeLengthChangePct), 1e-9)
    }

    @Test
    fun `no main set without three equal repeats`() {
        assertNull(SwimAnalysis.setHold(swim(listOf(lap(400.0, 480.0), lap(200.0, 214.0), lap(200.0, 216.0)))))
    }

    @Test
    fun `stroke length comes from freestyle laps, else from Garmin`() {
        val fromLaps = assertNotNull(SwimAnalysis.metersPerStroke(session))
        assertEquals(1000.0 / (115 + 116 + 117 + 118 + 120), fromLaps, 1e-9)

        val noStrokes = swim(listOf(lap(400.0, 480.0)), strokeDistance = 1.65)
        assertEquals(1.65, SwimAnalysis.metersPerStroke(noStrokes))
    }

    @Test
    fun `open water has no pool efficiency`() {
        assertNull(SwimAnalysis.efficiency(swim(listOf(lap(1000.0, 1200.0)), openWater = true, strokeDistance = 1.5)))
        val pool = assertNotNull(SwimAnalysis.efficiency(swim(listOf(lap(400.0, 480.0)), cadence = 32, swolf = 37)))
        assertEquals(32, pool.strokeRate)
        assertEquals(37, pool.swolf)
    }

    @Test
    fun `usual efficiency averages earlier pool swims only`() {
        val earlier = listOf(
            swim(listOf(lap(400.0, 480.0)), start = "2026-11-01T06:00:00Z", strokeDistance = 1.60, swolf = 38),
            swim(listOf(lap(400.0, 480.0)), start = "2026-11-05T06:00:00Z", strokeDistance = 1.70, swolf = 36),
            swim(listOf(lap(1000.0, 1200.0)), start = "2026-11-06T06:00:00Z", openWater = true, strokeDistance = 1.2),
            swim(listOf(lap(400.0, 480.0)), start = "2026-11-20T06:00:00Z", strokeDistance = 2.0),
        )

        val usual = assertNotNull(SwimAnalysis.usualEfficiency(earlier, Instant.parse("2026-11-12T06:10:00Z")))

        assertEquals(1.65, assertNotNull(usual.metersPerStroke), 1e-9)
        assertEquals(37, usual.swolf)
    }

    @Test
    fun `CSS from a 200 and a 400`() {
        val swims = listOf(
            swim(listOf(lap(200.0, 198.0)), start = "2026-11-12T06:00:00Z"),
            swim(listOf(lap(400.0, 410.0)), start = "2026-10-29T06:00:00Z"),
        )

        val css = assertNotNull(SwimAnalysis.estimateCss(swims, Instant.parse("2026-08-20T00:00:00Z")))

        assertEquals(106, css.paceSecPer100)
        assertEquals(CssConfidence.MEDIUM, css.confidence)
        assertEquals(listOf(200, 400), css.efforts.map { it.distanceMeters })
    }

    @Test
    fun `CSS keeps the best effort per distance and drops easy ones`() {
        val swims = listOf(
            swim(listOf(lap(200.0, 198.0), lap(200.0, 205.0), lap(400.0, 410.0), lap(800.0, 860.0))),
            // A 300 slower per 100 m than the 400: not swum hard.
            swim(listOf(lap(300.0, 330.0)), start = "2026-11-01T06:00:00Z"),
            // Too old.
            swim(listOf(lap(400.0, 380.0)), start = "2026-01-01T06:00:00Z"),
        )

        val css = assertNotNull(SwimAnalysis.estimateCss(swims, Instant.parse("2026-08-20T00:00:00Z")))

        assertEquals(listOf(200, 400, 800), css.efforts.map { it.distanceMeters })
        assertEquals(198.0, css.efforts.first().seconds)
        // Three distances on a line, and still an estimate: only a test is sure.
        assertEquals(CssConfidence.MEDIUM, css.confidence)
    }

    /** Twenty 100s at [pace] seconds each, with rests: a session of short repeats. */
    private fun hundreds(start: String, pace: Double) =
        swim((1..20).flatMap { listOf(lap(100.0, pace), rest(20.0)) }, start = start)

    @Test
    fun `the pace of the 100s does not speak against a slower threshold`() {
        val since = Instant.parse("2026-08-20T00:00:00Z")
        // 1:49 over 100 m, up to 1:55 as the repeats go on, and much slower beyond
        // 100 m: 200, 300 and 400 m at 1:57 to 2:02. The threshold is about 2:06,
        // slower than every 100, and that is how this swimmer is made.
        val swims = listOf(
            hundreds("2026-11-03T06:00:00Z", 109.0),
            hundreds("2026-11-08T06:00:00Z", 112.0),
            hundreds("2026-11-12T06:00:00Z", 115.0),
            swim(
                (1..8).flatMap { listOf(lap(100.0, 110.0), rest(20.0)) } +
                    listOf(lap(200.0, 235.0), rest(30.0), lap(300.0, 362.0), rest(30.0), lap(400.0, 487.0)),
                start = "2026-11-18T06:00:00Z",
            ),
        )

        val css = assertNotNull(SwimAnalysis.estimateCss(swims, since))

        assertEquals(126, css.paceSecPer100)
        assertEquals(listOf(200, 300, 400), css.efforts.map { it.distanceMeters })
        assertEquals(CssConfidence.MEDIUM, css.confidence)
    }

    @Test
    fun `a longer swim that beats the model makes the estimate of low confidence`() {
        val since = Instant.parse("2026-08-20T00:00:00Z")
        val set = swim(listOf(lap(200.0, 235.0), rest(30.0), lap(400.0, 487.0)), start = "2026-11-18T06:00:00Z")
        // 600 m without stopping at 1:57: nobody swims that far faster than their 400.
        val long = swim(listOf(lap(600.0, 700.0)), start = "2026-11-10T06:00:00Z")
        // 250 m is not one of the test's distances, and it is slower than the model: no contradiction.
        val easy = swim(listOf(lap(250.0, 330.0)), start = "2026-11-11T06:00:00Z")

        assertEquals(CssConfidence.MEDIUM, assertNotNull(SwimAnalysis.estimateCss(listOf(set, easy), since)).confidence)
        val doubted = assertNotNull(SwimAnalysis.estimateCss(listOf(set, easy, long), since))
        assertEquals(126, doubted.paceSecPer100)
        assertEquals(CssConfidence.LOW, doubted.confidence)
    }

    @Test
    fun `no CSS without a 400 or longer`() {
        val swims = listOf(swim(listOf(lap(200.0, 198.0), lap(300.0, 305.0))))

        assertNull(SwimAnalysis.estimateCss(swims, Instant.parse("2026-08-20T00:00:00Z")))
    }

    @Test
    fun `metres per stroke week by week`() {
        val swims = listOf(
            swim(listOf(lap(400.0, 480.0)), start = "2026-11-10T06:00:00Z", strokeDistance = 1.6),
            swim(listOf(lap(1200.0, 1440.0)), start = "2026-11-12T06:00:00Z", strokeDistance = 1.8),
        )

        val weeks = SwimAnalysis.weeklyMetersPerStroke(swims, LocalDate.of(2026, 11, 19), ZoneOffset.UTC, weeks = 2)

        assertEquals(listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16)), weeks.map { it.first })
        assertEquals(1.75, assertNotNull(weeks[0].second), 1e-9)
        assertNull(weeks[1].second)
    }

    @Test
    fun `laps are read from the document`() {
        val laps = ActivityMapper.laps(
            listOf(
                mapOf(
                    "distanceMeters" to 100L, "durationSec" to 110.5, "strokes" to 60L, "stroke" to "free",
                    "lengths" to listOf(mapOf("distanceMeters" to 25.0, "durationSec" to 27.0, "strokes" to 15L), "junk"),
                ),
                mapOf("distanceMeters" to 0.0, "durationSec" to 30L),
                mapOf("distanceMeters" to 100.0),
                "junk",
            ),
        )

        assertEquals(2, laps.size)
        assertEquals(110.5, laps[0].durationSec)
        assertEquals(1, laps[0].lengths.size)
        assertEquals(15, laps[0].lengths[0].strokes)
        assertEquals(true, laps[1].isRest)
    }
}
