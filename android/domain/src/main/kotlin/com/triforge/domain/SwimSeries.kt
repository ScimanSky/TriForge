package com.triforge.domain

import java.time.Instant
import kotlin.math.roundToInt

/**
 * Equal repeats swum one after the other, as a swimmer writes a set:
 * «8 × 50 m». The athlete marks equipment by series, not repeat by repeat.
 */
data class SwimSeries(val repeats: List<SwimRepeat>, val poolLengthMeters: Double? = null) {
    init {
        require(repeats.isNotEmpty()) { "a series has repeats" }
    }

    val count: Int get() = repeats.size

    /** The distance of each repeat. */
    val distanceMeters: Double get() = repeats.first().distanceMeters
    val meters: Double get() = repeats.sumOf { it.distanceMeters }
    val numbers: List<Int> get() = repeats.map { it.number }

    /** The pace of the series as a whole, s/100 m. */
    val paceSecPer100: Double get() = repeats.sumOf { it.durationSec } / meters * 100.0

    /** Strokes per pool length over the repeats that have them, or null. */
    val strokesPerLength: Double?
        get() = repeats.mapNotNull { SwimSets.strokesPerLength(it, poolLengthMeters) }.takeIf { it.isNotEmpty() }?.let(Stats::mean)

    /** The stroke the watch gave it, when it is not freestyle: "breast", "back"… */
    val otherStroke: String? get() = repeats.first().stroke?.takeIf { !repeats.first().isFreestyle }
}

/**
 * The repeats of a swim that look swum with equipment: much faster than the
 * rest of the swim and with fewer strokes per length. A guess to put in
 * front of the athlete, never a mark: only the athlete knows.
 */
data class SwimGearGuess(
    /** Freestyle repeats that look swum with equipment, by [SwimRepeat.number]. */
    val likely: Set<Int>,
    /**
     * Repeats with the pace and the strokes of freestyle with equipment that
     * the watch gave another stroke. With fins it often mistakes the stroke;
     * if it was right, there was no equipment.
     */
    val maybe: Set<Int>,
    /** What they were measured against: the pace and the strokes per length of the rest of the swim. */
    val usualPaceSecPer100: Double,
    val usualStrokesPerLength: Double,
    /** Metres of the [likely] ones. */
    val meters: Double,
    /**
     * Repeats that do not look it themselves and lie between [likely] ones:
     * equipment stays on for a stretch of the session, so an easy repeat or
     * another stroke in the middle of it was probably swum with it too.
     */
    val between: Set<Int> = emptySet(),
)

/**
 * Series of repeats and the guess about equipment, after SwimForge: there
 * the app proposes the repeats that look swum with equipment and the
 * athlete confirms them by series.
 */
object SwimSets {

    /** A rest at least this long, in seconds, starts a new series. */
    private const val LONG_REST = 90.0

    /** With equipment a repeat is at least this much faster than usual (share of the usual pace). */
    const val GEAR_PACE_SHARE = 0.90

    /** And swum with at most this share of the usual strokes per length. */
    const val GEAR_STROKES_SHARE = 0.85

    /**
     * Over one length a real sprint with a long push-off also has fewer
     * strokes: there the strokes must drop further.
     */
    const val GEAR_STROKES_SHARE_ONE_LENGTH = 0.75

    /**
     * Much faster than that, the strokes need to drop less: with fins the
     * legs do the work and the arms turn over almost as usual. Not over
     * one length, where a sprint is this fast.
     */
    const val GEAR_FAST_PACE_SHARE = 0.83
    const val GEAR_FAST_STROKES_SHARE = 0.90

    /** At most this many repeats between two likely ones are taken as between them. */
    private const val MAX_BETWEEN = 2

    /**
     * A length timed under this share of the middle length of its repeat
     * was not swum: the watch counted a turn that was not there, and the
     * repeat looks longer and faster than it was, with fewer strokes a
     * length. Just what equipment looks like.
     */
    private const val MISCOUNTED_LENGTH_SHARE = 0.7

    /** Lengths needed in a repeat before one of them can be told miscounted. */
    private const val MIN_LENGTHS_FOR_MISCOUNT = 3

    /** Repeats with strokes needed before one can be told apart from the others. */
    private const val MIN_REFERENCE = 4

    /**
     * Where in the strokes per length of a swim the usual ones are looked
     * for: above the middle, so that a swim half of which was with
     * equipment still measures against the half without.
     */
    private const val USUAL_STROKES_QUANTILE = 0.7

    /** Strokes per pool length of [repeat]; null when the watch did not count them or the lengths are unknown. */
    fun strokesPerLength(repeat: SwimRepeat, poolLengthMeters: Double?): Double? {
        val strokes = repeat.strokes?.takeIf { it > 0 } ?: return null
        val lengths = lengths(repeat, poolLengthMeters) ?: return null
        return strokes.toDouble() / lengths
    }

    private fun lengths(repeat: SwimRepeat, poolLengthMeters: Double?): Int? =
        repeat.lengths.size.takeIf { it > 0 }
            ?: poolLengthMeters?.takeIf { it > 0 }?.let { (repeat.distanceMeters / it).roundToInt() }?.takeIf { it > 0 }

    /** What the guess reads of a repeat, over the lengths that can be believed. */
    private class Measure(val repeat: SwimRepeat, val lengths: Int, val paceSecPer100: Double, val strokesPerLength: Double)

    private fun measure(repeat: SwimRepeat, poolLengthMeters: Double?): Measure? {
        val strokes = repeat.strokes?.takeIf { it > 0 } ?: return null
        val counted = lengths(repeat, poolLengthMeters) ?: return null
        val times = repeat.lengths.map { it.durationSec }.filter { it > 0 }
        val miscounted = if (times.size < MIN_LENGTHS_FOR_MISCOUNT) 0 else Stats.median(times).let { middle -> times.count { it < middle * MISCOUNTED_LENGTH_SHARE } }
        val swum = (counted - miscounted).coerceAtLeast(1)
        val meters = repeat.distanceMeters * swum / counted
        return Measure(repeat, swum, repeat.durationSec / meters * 100.0, strokes.toDouble() / swum)
    }

    /**
     * [repeats] as series: consecutive repeats of the same distance and
     * stroke. A long rest starts a new one, and so does a change of
     * [apart], which keeps what looks swum with equipment in series of its
     * own.
     */
    fun series(
        repeats: List<SwimRepeat>,
        poolLengthMeters: Double? = null,
        apart: (SwimRepeat) -> Any? = { null },
    ): List<SwimSeries> {
        val rests = repeats.mapNotNull { it.restAfterSec?.takeIf { rest -> rest > 0 } }
        val long = if (rests.isEmpty()) LONG_REST else maxOf(LONG_REST, 2 * Stats.median(rests))
        val result = mutableListOf<MutableList<SwimRepeat>>()
        repeats.forEach { repeat ->
            val last = result.lastOrNull()?.last()
            val same = last != null &&
                last.distanceMeters.roundToInt() == repeat.distanceMeters.roundToInt() &&
                (last.stroke ?: "free") == (repeat.stroke ?: "free") &&
                (last.restAfterSec ?: 0.0) < long &&
                apart(last) == apart(repeat)
            if (same) result.last() += repeat else result += mutableListOf(repeat)
        }
        return result.map { SwimSeries(it, poolLengthMeters) }
    }

    /** The guess about a pool swim, or null when nothing in it looks swum with equipment. */
    fun gearGuess(activity: Activity): SwimGearGuess? {
        if (activity.sport != Sport.SWIM || activity.openWater == true) return null
        return gearGuess(SwimAnalysis.repeats(activity), activity.poolLengthMeters)
    }

    /**
     * The usual strokes per length of the swim are taken above the middle
     * of its freestyle repeats, and the usual pace is that of the repeats
     * swum with those strokes. A repeat is then a likely one when it is
     * both faster than [GEAR_PACE_SHARE] of that pace and swum with fewer
     * strokes than [GEAR_STROKES_SHARE] of those, or much faster
     * ([GEAR_FAST_PACE_SHARE]) with somewhat fewer
     * ([GEAR_FAST_STROKES_SHARE]). Both, because speed alone is a sprint
     * and fewer strokes alone are a drill or a long glide.
     *
     * The shares come from a real swim the athlete had marked by hand: with
     * fins the 100s went from 1:50 to 1:28 while the strokes went from 13 a
     * length to 9 on some and 11 on others, and two repeats had a length
     * the watch counted twice.
     *
     * A pull buoy alone changes little of either: it is never guessed, the
     * athlete marks it.
     */
    fun gearGuess(repeats: List<SwimRepeat>, poolLengthMeters: Double?): SwimGearGuess? {
        val counted = repeats
            // A pace that cannot be right is read all the same when the repeat has the times of its lengths:
            // they tell a length counted twice, which is taken away, from fins, which are just that fast.
            .filter { !it.doubtful || it.lengths.size >= MIN_LENGTHS_FOR_MISCOUNT }
            .filter { !it.assisted && it.distanceMeters > 0 && it.durationSec > 0 && it.stroke != "drill" }
            .mapNotNull { measure(it, poolLengthMeters) }
        val free = counted.filter { it.repeat.isFreestyle }
        // More than one length when there are enough of them: a single length is too often a sprint.
        val reference = free.filter { it.lengths > 1 }.takeIf { it.size >= MIN_REFERENCE } ?: free
        if (reference.size < MIN_REFERENCE) return null
        val strokes = reference.map { it.strokesPerLength }.sorted()
        val usualStrokes = strokes[((strokes.size - 1) * USUAL_STROKES_QUANTILE).roundToInt()]
        val usualPace = Stats.median(reference.filter { it.strokesPerLength >= usualStrokes * 0.9 }.map { it.paceSecPer100 })

        val looks = counted.filter {
            val oneLength = it.lengths <= 1
            val pace = it.paceSecPer100 / usualPace
            val perLength = it.strokesPerLength / usualStrokes
            pace <= GEAR_PACE_SHARE && perLength <= (if (oneLength) GEAR_STROKES_SHARE_ONE_LENGTH else GEAR_STROKES_SHARE) ||
                !oneLength && pace <= GEAR_FAST_PACE_SHARE && perLength <= GEAR_FAST_STROKES_SHARE
        }.map { it.repeat }
        if (looks.isEmpty()) return null
        val likely = looks.filter { it.isFreestyle }
        val taken = looks.map { it.number }.toSet()
        val open = repeats.filter { !it.assisted && it.distanceMeters > 0 && it.durationSec > 0 }.map { it.number }.toSet()
        val between = likely.map { it.number }.sorted().zipWithNext()
            .filter { (from, to) -> to - from - 1 in 1..MAX_BETWEEN }
            .flatMap { (from, to) -> (from + 1 until to) }
            .filter { it in open && it !in taken }
        return SwimGearGuess(
            likely = likely.map { it.number }.toSet(),
            maybe = looks.filter { !it.isFreestyle }.map { it.number }.toSet(),
            usualPaceSecPer100 = usualPace,
            usualStrokesPerLength = usualStrokes,
            meters = likely.sumOf { it.distanceMeters },
            between = between.toSet(),
        )
    }

    /**
     * The equipment the athlete used last: what was marked on most repeats
     * of the latest swim with equipment before [before]. It is what a new
     * swim most likely was swum with, so the page starts from it.
     */
    fun lastGear(activities: List<Activity>, before: Instant): Set<SwimGear> {
        val marks = activities
            .filter { it.sport == Sport.SWIM && it.startTime < before && it.gear?.any == true }
            .maxByOrNull { it.startTime }?.gear ?: return emptySet()
        return marks.session.takeIf { it.isNotEmpty() }
            ?: marks.repeats.values.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: emptySet()
    }
}
