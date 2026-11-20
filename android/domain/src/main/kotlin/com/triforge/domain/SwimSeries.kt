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
 * rest of the swim and with far fewer strokes per length. A guess to put in
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
     * strokes than [GEAR_STROKES_SHARE] of those. Both, because speed alone
     * is a sprint and fewer strokes alone are a drill or a long glide.
     *
     * A pull buoy alone changes little of either: it is never guessed, the
     * athlete marks it.
     */
    fun gearGuess(repeats: List<SwimRepeat>, poolLengthMeters: Double?): SwimGearGuess? {
        val counted = repeats
            .filter { !it.doubtful && !it.assisted && it.distanceMeters > 0 && it.durationSec > 0 && it.stroke != "drill" }
            .mapNotNull { repeat -> strokesPerLength(repeat, poolLengthMeters)?.let { repeat to it } }
        val free = counted.filter { it.first.isFreestyle }
        // More than one length when there are enough of them: a single length is too often a sprint.
        val reference = free.filter { (lengths(it.first, poolLengthMeters) ?: 1) > 1 }.takeIf { it.size >= MIN_REFERENCE } ?: free
        if (reference.size < MIN_REFERENCE) return null
        val strokes = reference.map { it.second }.sorted()
        val usualStrokes = strokes[((strokes.size - 1) * USUAL_STROKES_QUANTILE).roundToInt()]
        val usualPace = Stats.median(reference.filter { it.second >= usualStrokes * 0.9 }.map { it.first.paceSecPer100 })

        val looks = counted.filter { (repeat, perLength) ->
            val oneLength = (lengths(repeat, poolLengthMeters) ?: 1) <= 1
            repeat.paceSecPer100 <= usualPace * GEAR_PACE_SHARE &&
                perLength <= usualStrokes * (if (oneLength) GEAR_STROKES_SHARE_ONE_LENGTH else GEAR_STROKES_SHARE)
        }.map { it.first }
        if (looks.isEmpty()) return null
        val likely = looks.filter { it.isFreestyle }
        return SwimGearGuess(
            likely = likely.map { it.number }.toSet(),
            maybe = looks.filter { !it.isFreestyle }.map { it.number }.toSet(),
            usualPaceSecPer100 = usualPace,
            usualStrokesPerLength = usualStrokes,
            meters = likely.sumOf { it.distanceMeters },
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
