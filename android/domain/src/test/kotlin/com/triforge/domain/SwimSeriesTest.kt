package com.triforge.domain

import com.triforge.domain.coach.AthleteContext
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Series of repeats, and the guess about which of them were swum with equipment. */
class SwimSeriesTest {

    private val day = LocalDate.of(2026, 11, 20)

    private fun lap(meters: Double, seconds: Double, strokes: Int?, stroke: String = "free") =
        SwimLap(distanceMeters = meters, durationSec = seconds, stroke = stroke, strokes = strokes)

    private fun rest(seconds: Double) = SwimLap(distanceMeters = 0.0, durationSec = seconds)

    /** A freestyle repeat with the time of each of its lengths, as the watch counted them. */
    private fun counted(meters: Double, seconds: Double, strokes: Int, vararg lengths: Double) =
        SwimLap(meters, seconds, strokes = strokes, stroke = "free", lengths = lengths.map { SwimLength(25.0, it) })

    private fun swim(laps: List<SwimLap>, gear: SwimGearMarks? = null, daysAgo: Long = 0, pool: Double? = 25.0) = Activity(
        id = "swim-$daysAgo", source = ActivitySource.GARMIN, sport = Sport.SWIM, name = "",
        startTime = day.minusDays(daysAgo).atTime(13, 20).toInstant(ZoneOffset.UTC),
        durationSec = laps.sumOf { it.durationSec }.toLong(),
        movingDurationSec = laps.filter { !it.isRest }.sumOf { it.durationSec }.toLong(),
        distanceMeters = laps.sumOf { it.distanceMeters }, openWater = false, poolLengthMeters = pool, laps = laps, gear = gear,
    )

    /** [count] repeats of [meters] with [restSec] between them and [after] at the end. */
    private fun set(count: Int, meters: Double, pace: Double, perLength: Int, restSec: Double = 20.0, after: Double = 20.0, stroke: String = "free") =
        (1..count).flatMap { i ->
            listOf(
                lap(meters, pace * meters / 100, strokes = (perLength * meters / 25).toInt(), stroke = stroke),
                rest(if (i == count) after else restSec),
            )
        }

    /**
     * A Friday with equipment: a 400 and six 100s of the athlete's own
     * (1:55, 13 strokes a length), four 100s with fins (1:26, 8 strokes),
     * eight 50s with paddles and a pull buoy (1:30, 10 strokes), six 50s
     * with the pull buoy alone, four sprints over one length, a 200 to end.
     */
    private fun friday(gear: SwimGearMarks? = null) = swim(
        set(1, 400.0, 118.0, 13, after = 60.0) +
            set(6, 100.0, 115.0, 13, after = 120.0) +
            set(4, 100.0, 86.0, 8, after = 100.0) +
            set(8, 50.0, 90.0, 10, after = 100.0) +
            set(6, 50.0, 111.0, 13, after = 60.0) +
            set(4, 25.0, 88.0, 12, after = 60.0) +
            set(1, 200.0, 122.0, 14),
        gear,
    )

    // ---- Series ----------------------------------------------------------------

    @Test
    fun `equal repeats in a row make a series`() {
        val activity = friday()
        val series = SwimSets.series(SwimAnalysis.repeats(activity), activity.poolLengthMeters)

        assertEquals(listOf(1 to 400, 6 to 100, 4 to 100, 8 to 50, 6 to 50, 4 to 25, 1 to 200), series.map { it.count to it.distanceMeters.toInt() })
        val fins = series[2]
        assertEquals((8..11).toList(), fins.numbers)
        assertEquals(86.0, fins.paceSecPer100, 0.01)
        assertEquals(8.0, fins.strokesPerLength!!, 0.01)
    }

    @Test
    fun `a long rest starts a new series and so does another stroke`() {
        val activity = swim(
            set(3, 100.0, 115.0, 13, after = 150.0) + set(3, 100.0, 115.0, 13) + set(2, 100.0, 140.0, 9, stroke = "breast"),
        )
        val series = SwimSets.series(SwimAnalysis.repeats(activity), 25.0)

        assertEquals(listOf(3, 3, 2), series.map { it.count })
        assertEquals("breast", series.last().otherStroke)
        assertNull(series.first().otherStroke)
    }

    @Test
    fun `what looks swum with equipment stays in a series of its own`() {
        // Six 100s without a longer rest between: the last three with fins.
        val activity = swim(set(1, 400.0, 118.0, 13) + set(6, 100.0, 115.0, 13) + set(3, 100.0, 86.0, 8))
        val repeats = SwimAnalysis.repeats(activity)
        val guess = assertNotNull(SwimSets.gearGuess(repeats, 25.0))

        val series = SwimSets.series(repeats, 25.0) { it.number in guess.likely }
        assertEquals(listOf(1, 6, 3), series.map { it.count })
        assertEquals(guess.likely, series.last().numbers.toSet())
    }

    // ---- The guess -------------------------------------------------------------

    @Test
    fun `fins and paddles are guessed and the pull buoy alone and the sprints are not`() {
        val guess = assertNotNull(SwimSets.gearGuess(friday()))

        // The four 100s with fins (8 to 11) and the eight 50s with paddles (12 to 19).
        assertEquals((8..19).toSet(), guess.likely)
        assertTrue(guess.maybe.isEmpty())
        assertEquals(800.0, guess.meters, 0.01)
        assertEquals(13.0, guess.usualStrokesPerLength, 0.01)
        assertEquals(115.0, guess.usualPaceSecPer100, 0.01)
    }

    @Test
    fun `a swim without equipment has nothing to propose`() {
        val steady = swim(set(1, 400.0, 120.0, 13) + set(8, 100.0, 115.0, 13) + set(4, 50.0, 105.0, 14) + set(4, 25.0, 90.0, 13))
        assertNull(SwimSets.gearGuess(steady))
    }

    @Test
    fun `fewer strokes alone or more speed alone are not equipment`() {
        // A drill with a long glide: nine strokes, but slower. A hard 100: faster, with more strokes.
        val activity = swim(set(6, 100.0, 115.0, 13) + set(4, 50.0, 125.0, 9) + set(2, 100.0, 100.0, 14))
        assertNull(SwimSets.gearGuess(activity))
    }

    @Test
    fun `one length must drop its strokes further than a longer repeat`() {
        // Ten strokes on thirteen is enough over 50 m, not over one length: a sprint with a long push-off does that.
        val sprints = swim(set(6, 100.0, 115.0, 13) + set(4, 25.0, 88.0, 10))
        assertNull(SwimSets.gearGuess(sprints))

        val fins = swim(set(6, 100.0, 115.0, 13) + set(4, 25.0, 80.0, 8))
        assertEquals((7..10).toSet(), SwimSets.gearGuess(fins)?.likely)
    }

    @Test
    fun `much faster with somewhat fewer strokes is equipment too but not over one length`() {
        // With fins the legs do the work: 1:29 against 1:55, and eleven strokes and a half a length against thirteen.
        val activity = swim(
            set(6, 100.0, 115.0, 13) + listOf(
                lap(100.0, 89.0, strokes = 46), rest(20.0),
                // Faster, not much faster: the strokes have to drop further.
                lap(100.0, 100.0, strokes = 46), rest(20.0),
                // One length this fast is a sprint.
                lap(25.0, 22.0, strokes = 11), rest(20.0),
            ),
        )
        assertEquals(setOf(7), SwimSets.gearGuess(activity)?.likely)
    }

    @Test
    fun `a length the watch counted twice is not equipment`() {
        // 100 m in 2:02 read as 150 m, two lengths split in two: it looked fast and short of strokes, and was neither.
        val twice = counted(150.0, 122.0, 54, 29.3, 27.5, 12.6, 19.3, 20.1, 13.2)
        val activity = swim(listOf(twice, rest(20.0)) + set(6, 100.0, 115.0, 13) + set(1, 100.0, 97.5, 10))
        val guess = assertNotNull(SwimSets.gearGuess(activity))

        assertEquals(setOf(8), guess.likely)
        assertEquals(13.0, guess.usualStrokesPerLength, 0.01)
        assertEquals(115.0, guess.usualPaceSecPer100, 0.01)
    }

    @Test
    fun `a pace too fast to be right is fins when the lengths are even and nothing without them`() {
        // 100 m in 1:18 against 1:55: the app doubts it. Four even lengths say the watch counted right.
        val even = swim(set(6, 100.0, 115.0, 13) + listOf(counted(100.0, 78.0, 34, 19.0, 19.5, 20.0, 19.5), rest(20.0)))
        assertTrue(SwimAnalysis.repeats(even).last().doubtful)
        assertEquals(setOf(7), SwimSets.gearGuess(even)?.likely)

        val unknown = swim(set(6, 100.0, 115.0, 13) + set(1, 100.0, 78.0, 8))
        assertNull(SwimSets.gearGuess(unknown))
    }

    @Test
    fun `what lies between repeats with equipment is proposed apart`() {
        val activity = swim(
            set(6, 100.0, 115.0, 13) +
                // 7 and 8 with fins, 9 breaststroke at an easy pace, 10 with fins.
                set(2, 100.0, 86.0, 8) + set(1, 100.0, 114.0, 11, stroke = "breast") + set(1, 100.0, 86.0, 8) +
                // 11 and 12 easy, 13 with fins.
                set(1, 100.0, 114.0, 13) + set(1, 200.0, 106.0, 13) + set(1, 100.0, 86.0, 8) +
                // Three in a row are the athlete's own, whatever comes after.
                set(3, 100.0, 115.0, 13) + set(1, 100.0, 86.0, 8),
        )
        val guess = assertNotNull(SwimSets.gearGuess(activity))

        assertEquals(setOf(7, 8, 10, 13, 17), guess.likely)
        assertEquals(setOf(9, 11, 12), guess.between)
        assertTrue(guess.maybe.isEmpty())
    }

    @Test
    fun `a fast repeat the watch calls breaststroke is only a maybe`() {
        val activity = swim(set(6, 100.0, 115.0, 13) + set(1, 50.0, 98.0, 5, stroke = "breast") + set(2, 50.0, 150.0, 9, stroke = "breast"))
        val guess = assertNotNull(SwimSets.gearGuess(activity))

        assertTrue(guess.likely.isEmpty())
        assertEquals(setOf(7), guess.maybe)
    }

    @Test
    fun `half a swim with equipment is still measured against the half without`() {
        val activity = swim(set(6, 100.0, 115.0, 13) + set(6, 100.0, 86.0, 8))
        assertEquals((7..12).toSet(), SwimSets.gearGuess(activity)?.likely)
    }

    @Test
    fun `without stroke counts or with too few repeats there is no guess`() {
        val uncounted = swim((1..8).flatMap { listOf(SwimLap(100.0, if (it > 4) 86.0 else 115.0, stroke = "free"), rest(20.0)) })
        assertNull(SwimSets.gearGuess(uncounted))
        assertNull(SwimSets.gearGuess(swim(set(2, 100.0, 115.0, 13) + set(1, 100.0, 86.0, 8))))
    }

    @Test
    fun `the lengths of the pool are read from the laps when the swim does not say`() {
        val lengths = List(4) { SwimLength(distanceMeters = 25.0, durationSec = 28.0) }
        val repeat = SwimAnalysis.repeats(swim(listOf(SwimLap(100.0, 112.0, strokes = 52, stroke = "free", lengths = lengths)), pool = null)).single()

        assertEquals(13.0, SwimSets.strokesPerLength(repeat, null)!!, 0.01)
        assertNull(SwimSets.strokesPerLength(repeat.copy(lengths = emptyList()), null))
        assertEquals(13.0, SwimSets.strokesPerLength(repeat.copy(lengths = emptyList()), 25.0)!!, 0.01)
    }

    // ---- What the page starts from, and what the coaches read -----------------------

    @Test
    fun `the page starts from the equipment used last`() {
        val paddles = setOf(SwimGear.PADDLES, SwimGear.PULL_BUOY)
        val before = friday().startTime
        val history = listOf(
            friday(SwimGearMarks(repeats = (8..11).associateWith { setOf(SwimGear.FINS) } + (12..19).associateWith { paddles })).copy(
                id = "last-friday",
                startTime = before.minusSeconds(7 * 86_400),
            ),
            swim(set(4, 100.0, 115.0, 13), SwimGearMarks(session = setOf(SwimGear.FINS)), daysAgo = 14),
            swim(set(4, 100.0, 115.0, 13), SwimGearMarks.NONE, daysAgo = 2),
        )

        assertEquals(paddles, SwimSets.lastGear(history, before))
        assertEquals(emptySet(), SwimSets.lastGear(history.takeLast(1), before))
        assertEquals(emptySet(), SwimSets.lastGear(history, before.minusSeconds(30 * 86_400)))
    }

    @Test
    fun `the coaches read the guess only while the athlete has not answered`() {
        val line = AthleteContext.gearGuess(assertNotNull(SwimSets.gearGuess(friday())))
        assertTrue("12 ripetute (800 m)" in line, line)
        assertTrue("l'atleta non l'ha ancora detto" in line, line)
    }
}
