package com.triforge.domain

import com.triforge.domain.coach.AthleteContext
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Swims the athlete marked as swum with fins, a pull buoy or paddles. */
class SwimGearTest {

    private val today = LocalDate.of(2026, 11, 20)
    private val zone = ZoneOffset.UTC

    private fun lap(meters: Double, seconds: Double, strokes: Int? = null) =
        SwimLap(distanceMeters = meters, durationSec = seconds, stroke = "free", strokes = strokes)

    private fun rest(seconds: Double) = SwimLap(distanceMeters = 0.0, durationSec = seconds)

    private fun swim(daysAgo: Long, laps: List<SwimLap>, gear: SwimGearMarks? = null) = Activity(
        id = "swim-$daysAgo", source = ActivitySource.GARMIN, sport = Sport.SWIM, name = "",
        startTime = today.minusDays(daysAgo).atTime(13, 20).toInstant(zone),
        durationSec = laps.sumOf { it.durationSec }.toLong(),
        movingDurationSec = laps.filter { !it.isRest }.sumOf { it.durationSec }.toLong(),
        distanceMeters = laps.sumOf { it.distanceMeters }, openWater = false, laps = laps, gear = gear,
    )

    private fun block(daysAgo: Long, meters: Double, movingSec: Long, gear: SwimGearMarks? = null) = Activity(
        id = "block-$daysAgo", source = ActivitySource.GARMIN, sport = Sport.SWIM, name = "",
        startTime = today.minusDays(daysAgo).atTime(13, 20).toInstant(zone),
        durationSec = movingSec + 60, movingDurationSec = movingSec, distanceMeters = meters, openWater = false, gear = gear,
    )

    private val finsAndPaddles = setOf(SwimGear.FINS, SwimGear.PADDLES)

    /**
     * A Friday of the athlete this was made for: 2,000 m of his own at
     * 2:02 /100 m (ten 200s), then eight 100s with fins and paddles at 1:30.
     * The watch gives 1:53 /100 m over the lot.
     */
    private fun friday(daysAgo: Long = 0, marked: Boolean = true): Activity {
        val own = (1..10).flatMap { listOf(lap(200.0, 244.0, strokes = 170), rest(30.0)) }
        val with = (1..8).flatMap { listOf(lap(100.0, 90.0, strokes = 60), rest(20.0)) }
        val marks = SwimGearMarks(repeats = (11..18).associateWith { finsAndPaddles })
        return swim(daysAgo, own + with, if (marked) marks else null)
    }

    // ---- The marks -------------------------------------------------------------

    @Test
    fun `marks are stored by interval and read back`() {
        val marks = SwimGearMarks(session = setOf(SwimGear.PULL_BUOY), repeats = mapOf(3 to finsAndPaddles, 12 to setOf(SwimGear.FINS)))
        val fields = ActivityMapper.gearToFields(marks)

        assertEquals(listOf("pull_buoy"), fields["session"])
        assertEquals(mapOf("3" to listOf("fins", "paddles"), "12" to listOf("fins")), fields["repeats"])
        assertEquals(marks, ActivityMapper.gearFromFields(fields))
    }

    @Test
    fun `no stored entry is no answer and an empty one is the answer none`() {
        assertNull(ActivityMapper.gearFromFields(null))
        val none = ActivityMapper.gearFromFields(ActivityMapper.gearToFields(SwimGearMarks.NONE))
        assertEquals(SwimGearMarks.NONE, none)
        assertFalse(none!!.any)
    }

    @Test
    fun `what cannot be read in the stored marks is dropped`() {
        val marks = ActivityMapper.gearFromFields(
            mapOf("session" to listOf("snorkel", 7), "repeats" to mapOf("x" to listOf("fins"), "0" to listOf("fins"), "4" to listOf("kickboard"), "5" to listOf("paddles"))),
        )
        assertEquals(SwimGearMarks(repeats = mapOf(5 to setOf(SwimGear.PADDLES))), marks)
    }

    @Test
    fun `the whole swim and single intervals add up`() {
        val marks = SwimGearMarks(session = setOf(SwimGear.PULL_BUOY), repeats = mapOf(2 to setOf(SwimGear.PADDLES)))
        assertEquals(setOf(SwimGear.PULL_BUOY), marks.of(1))
        assertEquals(setOf(SwimGear.PULL_BUOY, SwimGear.PADDLES), marks.of(2))
    }

    // ---- The repeats -----------------------------------------------------------

    @Test
    fun `a repeat carries its equipment by its place among the swum intervals`() {
        val repeats = SwimAnalysis.repeats(friday())

        assertEquals(18, repeats.size)
        assertTrue(repeats.take(10).all { it.own && it.gear.isEmpty() })
        assertTrue(repeats.drop(10).all { it.assisted && !it.own && it.gear == finsAndPaddles })
    }

    @Test
    fun `fast repeats with fins are not a miscounted length once they are marked`() {
        // Four 100s at 1:10 among 100s at 2:00: the watch would be doubted.
        val laps = (1..8).flatMap { listOf(lap(100.0, 120.0), rest(20.0)) } + (1..4).flatMap { listOf(lap(100.0, 70.0), rest(20.0)) }
        val unmarked = SwimAnalysis.repeats(swim(0, laps))
        val marked = SwimAnalysis.repeats(swim(0, laps, SwimGearMarks(repeats = (9..12).associateWith { setOf(SwimGear.FINS) })))

        assertTrue(unmarked.drop(8).all { it.doubtful })
        assertTrue(marked.none { it.doubtful })
        assertEquals(120.0, SwimAnalysis.usualPace(marked))
    }

    @Test
    fun `the usual pace of the others is not pulled by the repeats with equipment`() {
        // Half the swim with fins: the median of all would sit between the two paces.
        val laps = (1..6).flatMap { listOf(lap(100.0, 120.0), rest(20.0)) } + (1..6).flatMap { listOf(lap(100.0, 80.0), rest(20.0)) }
        val marked = swim(0, laps, SwimGearMarks(repeats = (7..12).associateWith { setOf(SwimGear.FINS) }))

        assertEquals(120.0, SwimAnalysis.usualPace(SwimAnalysis.repeats(marked)))
    }

    // ---- What is read without them ----------------------------------------------

    @Test
    fun `metres with equipment are in no zone`() {
        val zones = SwimAnalysis.zoneMeters(friday(), 126)!!
        val unmarked = SwimAnalysis.zoneMeters(friday(marked = false), 126)!!

        assertEquals(2000.0, zones.sum())
        assertEquals(2800.0, unmarked.sum())
        // Unmarked, the 800 m at 1:30 against a threshold of 2:06 are all sprint.
        assertEquals(800.0, unmarked.last())
        assertEquals(0.0, zones.last())
    }

    @Test
    fun `a swim all with equipment has no zones`() {
        val all = friday().copy(gear = SwimGearMarks(session = setOf(SwimGear.FINS)))
        assertNull(SwimAnalysis.zoneMeters(all, 126))
    }

    @Test
    fun `the load counts the time with equipment at the swimmer's own pace`() {
        val css = 126
        val marked = SwimAnalysis.lapTss(friday(), css)!!
        val unmarked = SwimAnalysis.lapTss(friday(marked = false), css)!!
        // The same swim with the eight 100s swum for as long, at 2:02 /100 m.
        val plain = (2440.0 + 8 * 90.0) / 3600.0 * Math.pow(126.0 / 122.0, 3.0) * 100.0

        assertEquals(plain, marked, 0.01)
        assertTrue(unmarked > marked * 1.15, "fins would have made the load $unmarked against $marked")
    }

    @Test
    fun `a swim all with equipment counts as an ordinary swim`() {
        val css = 126
        val laps = (1..10).flatMap { listOf(lap(200.0, 170.0), rest(20.0)) }
        val all = swim(0, laps, SwimGearMarks(session = setOf(SwimGear.FINS)))
        val expected = 1700.0 / 3600.0 * Math.pow(SessionLoadModel.typicalIntensity(Sport.SWIM), 3.0) * 100.0

        assertEquals(expected, SwimAnalysis.lapTss(all, css)!!, 0.01)

        // Without laps the session load does the same.
        val one = block(0, 2000.0, 1700, SwimGearMarks(session = setOf(SwimGear.FINS)))
        val load = SessionLoadModel.estimate(listOf(one), AthleteProfile(cssSecPer100m = css)).single()
        assertEquals(expected, load.tss, 0.01)
        assertEquals(LoadQuality.MEDIUM, load.quality)
    }

    @Test
    fun `stroke length is read without the repeats with paddles`() {
        // 200 m in 170 strokes is 1.18 m a stroke; with the paddles it would be 1.26.
        assertEquals(2000.0 / 1700.0, SwimAnalysis.metersPerStroke(friday())!!, 1e-9)
        assertEquals(2800.0 / 2180.0, SwimAnalysis.metersPerStroke(friday(marked = false))!!, 1e-9)

        val watchAverage = block(0, 2000.0, 1700, SwimGearMarks(session = setOf(SwimGear.PADDLES))).copy(strokeDistanceMeters = 1.4, swolf = 30, avgCadence = 24)
        assertNull(SwimAnalysis.metersPerStroke(watchAverage))
        assertNull(SwimAnalysis.efficiency(watchAverage))
    }

    @Test
    fun `the main set is looked for among the repeats without equipment`() {
        val hold = SwimAnalysis.setHold(friday())!!
        assertEquals(10, hold.repeats)
        assertEquals(200.0, hold.distanceMeters)

        // All marked: there is no set left to judge.
        assertNull(SwimAnalysis.setHold(friday().copy(gear = SwimGearMarks(session = setOf(SwimGear.FINS)))))
    }

    // ---- The threshold -----------------------------------------------------------

    /** A 400 and a 200 flat out with fins, far faster than anything the athlete swims alone. */
    private fun finsTest(daysAgo: Long, marked: Boolean): Activity {
        val laps = listOf(lap(400.0, 368.0), rest(300.0), lap(200.0, 176.0), rest(60.0)) + (1..6).flatMap { listOf(lap(200.0, 244.0), rest(30.0)) }
        return swim(daysAgo, laps, if (marked) SwimGearMarks(repeats = mapOf(1 to setOf(SwimGear.FINS), 2 to setOf(SwimGear.FINS))) else null)
    }

    @Test
    fun `efforts with fins do not make a threshold`() {
        val since = today.minusDays(56).atStartOfDay().toInstant(zone)
        val own = swim(3, listOf(lap(400.0, 487.0), rest(300.0), lap(200.0, 235.0)))

        val unmarked = SwimAnalysis.estimateCss(listOf(own, finsTest(1, marked = false)), since)
        val marked = SwimAnalysis.estimateCss(listOf(own, finsTest(1, marked = true)), since)

        assertEquals(96, unmarked!!.paceSecPer100)
        assertEquals(126, marked!!.paceSecPer100)
        assertEquals(CssConfidence.MEDIUM, marked.confidence, "the 400 with fins must not contradict the estimate either")
    }

    @Test
    fun `fast continuous swimming with equipment does not make the threshold look slow`() {
        val profile = AthleteProfile(cssSecPer100m = 126)
        fun pull(daysAgo: Long, marked: Boolean) = swim(
            daysAgo,
            (1..15).flatMap { listOf(lap(200.0, 216.0), rest(30.0)) },
            if (marked) SwimGearMarks(session = setOf(SwimGear.PULL_BUOY, SwimGear.PADDLES)) else null,
        )

        // Three weeks of 200s at 1:48 with pull buoy and paddles, against a threshold of 2:06.
        assertNotNull(SwimThreshold.check(listOf(pull(1, false), pull(4, false), pull(8, false)), profile, today, zone))
        assertNull(SwimThreshold.check(listOf(pull(1, true), pull(4, true), pull(8, true)), profile, today, zone))
    }

    @Test
    fun `a swim without laps marked with equipment is not read as one block`() {
        val profile = AthleteProfile(cssSecPer100m = 126)
        fun fast(daysAgo: Long, marked: Boolean) =
            block(daysAgo, 2000.0, 2000, if (marked) SwimGearMarks(session = setOf(SwimGear.FINS)) else null)

        assertNotNull(SwimThreshold.check(listOf(fast(1, false), fast(4, false)), profile, today, zone))
        assertNull(SwimThreshold.check(listOf(fast(1, true), fast(4, true)), profile, today, zone))
        assertNull(SwimThreshold.suggest(listOf(fast(1, true), fast(4, true)), today, zone))
    }

    // ---- The swim as a whole -------------------------------------------------------

    @Test
    fun `the swim is split in what was with equipment and what was the athlete's own`() {
        val use = SwimAnalysis.gearUse(friday())!!

        assertEquals(finsAndPaddles, use.gear)
        assertEquals(800.0, use.meters)
        assertEquals(90.0, use.paceSecPer100!!, 1e-9)
        assertEquals(2000.0, use.ownMeters)
        assertEquals(122.0, use.ownPaceSecPer100!!, 1e-9)
        assertEquals((11..18).toList(), use.repeats)
        assertFalse(use.whole)
        assertEquals(PaceBase(2000.0, 2440.0), SwimAnalysis.ownPace(friday()))
    }

    @Test
    fun `nothing marked, the answer none and marks that fit no interval leave the swim as it is`() {
        val plain = friday(marked = false)
        val whole = PaceBase(plain.distanceMeters, plain.paceSec.toDouble())

        listOf(null, SwimGearMarks.NONE, SwimGearMarks(repeats = mapOf(40 to setOf(SwimGear.FINS)))).forEach { marks ->
            val swim = plain.copy(gear = marks)
            assertNull(SwimAnalysis.gearUse(swim), "$marks")
            assertEquals(whole, SwimAnalysis.ownPace(swim), "$marks")
        }
    }

    @Test
    fun `a swim all with equipment has no pace of its own`() {
        val all = block(0, 2000.0, 1700, SwimGearMarks(session = setOf(SwimGear.FINS)))
        val use = SwimAnalysis.gearUse(all)!!

        assertTrue(use.whole)
        assertEquals(85.0, use.paceSecPer100!!, 1e-9)
        assertNull(use.ownPaceSecPer100)
        assertNull(SwimAnalysis.ownPace(all))
    }

    @Test
    fun `rides and runs keep the pace of the session`() {
        val run = activity("run", Sport.RUN, "2026-11-18T07:00:00Z", durationSec = 3000, distanceMeters = 10_000.0)
        assertEquals(PaceBase(10_000.0, 3000.0), SwimAnalysis.ownPace(run))
    }

    // ---- Records and trends ----------------------------------------------------------

    @Test
    fun `a swim with equipment sets no speed record and does not move the pace of the weeks`() {
        val plain = swim(2, (1..10).flatMap { listOf(lap(200.0, 244.0), rest(30.0)) })
        val all = swim(1, (1..10).flatMap { listOf(lap(200.0, 170.0), rest(30.0)) }, SwimGearMarks(session = setOf(SwimGear.FINS)))
        val report = SportStats.report(listOf(plain, all, friday()), Sport.SWIM, weeks = 1, today = today, zone = zone, loadedWeeks = 16)

        assertEquals("swim-2", report.records.single { it.kind == RecordKind.FASTEST }.activity.id)
        // Volume: everything swum. Pace: the 4,000 m without equipment, all at 2:02 /100 m.
        assertEquals(6800.0, report.distanceMeters)
        assertEquals(4000.0, report.paceMeters)
        assertEquals(1.22, report.paceSec / report.paceMeters, 1e-9)
        assertEquals(1.22, report.weekly.single().secondsPerMeter!!, 1e-9)
    }

    @Test
    fun `without equipment the pace of a week is over all its metres`() {
        val plain = swim(2, (1..10).flatMap { listOf(lap(200.0, 244.0), rest(30.0)) })
        val week = SportStats.report(listOf(plain), Sport.SWIM, weeks = 1, today = today, zone = zone, loadedWeeks = 16).weekly.single()

        assertEquals(week.distanceMeters, week.paceMeters)
        assertEquals(1.22, week.secondsPerMeter!!, 1e-9)
    }

    // ---- The coaches and the question ---------------------------------------------------

    @Test
    fun `the coaches read which part was with equipment and which pace is the athlete's`() {
        assertEquals(
            "di cui 800 m con pinne e palette a 1:30/100 m; senza attrezzatura 2:02/100 m, ed è questo il suo passo",
            AthleteContext.gear(SwimAnalysis.gearUse(friday())!!),
        )
        val all = block(0, 2000.0, 1700, SwimGearMarks(session = setOf(SwimGear.FINS, SwimGear.PULL_BUOY, SwimGear.PADDLES)))
        assertEquals(
            "tutta con pinne, pull buoy e palette: quel passo non è il suo, non confrontarlo con la soglia né con le altre nuotate",
            AthleteContext.gear(SwimAnalysis.gearUse(all)!!),
        )
    }

    @Test
    fun `the app asks about the latest pool swim until it has an answer`() {
        val old = friday(daysAgo = 5, marked = false)
        val yesterday = friday(daysAgo = 1, marked = false)

        assertEquals("swim-1", SwimAnalysis.gearToAsk(listOf(old, yesterday), today, zone)?.id)
        assertNull(SwimAnalysis.gearToAsk(listOf(old), today, zone), "too long ago to remember")
        assertNull(SwimAnalysis.gearToAsk(listOf(yesterday.copy(gear = SwimGearMarks.NONE)), today, zone), "answered: none")
        assertNull(SwimAnalysis.gearToAsk(listOf(yesterday.copy(openWater = true)), today, zone), "the pool only")
        // An older swim is not asked about once the latest one is answered.
        val twoDaysAgo = friday(daysAgo = 2, marked = false)
        assertNull(SwimAnalysis.gearToAsk(listOf(twoDaysAgo, friday(daysAgo = 0)), today, zone))
    }
}
