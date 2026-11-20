package com.triforge.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** One swum interval of a session, with the rest that followed it. */
data class SwimRepeat(
    /** Position among the swum intervals, from 1. */
    val number: Int,
    val distanceMeters: Double,
    val durationSec: Double,
    val strokes: Int?,
    val swolf: Int?,
    val avgHr: Int?,
    val stroke: String?,
    val lengths: List<SwimLength>,
    /** Seconds at the wall before the next interval; null after the last one. */
    val restAfterSec: Double?,
    /**
     * The pace is not one the athlete swims: far faster than the rest of the
     * session, as when the watch counts a length that was not swum. Shown,
     * but kept out of zones, load, records and the threshold estimate.
     */
    val doubtful: Boolean = false,
    /**
     * Equipment the athlete marked on it. With it the pace is not that of
     * the stroke alone: shown, but kept out of zones, records, comparisons
     * and the threshold, like a doubtful repeat.
     */
    val gear: Set<SwimGear> = emptySet(),
    /**
     * Lengths the watch counted twice, already taken away: the distance
     * and the lengths here are the ones swum, not the ones it recorded.
     */
    val recounted: Int = 0,
) {
    val paceSecPer100: Double get() = durationSec / distanceMeters * 100.0

    /** The distance the watch recorded, with the lengths it counted twice. */
    val recordedMeters: Double get() = if (lengths.isEmpty()) distanceMeters else distanceMeters * (lengths.size + recounted) / lengths.size

    /** Swum with equipment. */
    val assisted: Boolean get() = gear.isNotEmpty()

    /** The pace is the athlete's own: one the watch can be trusted on, swum without equipment. */
    val own: Boolean get() = !doubtful && !assisted

    /** Metres per stroke, when the watch counted the strokes. */
    val metersPerStroke: Double? get() = strokes?.takeIf { it > 0 }?.let { distanceMeters / it }

    /** Strokes per minute, when the watch counted the strokes. */
    val strokeRate: Double? get() = strokes?.takeIf { it > 0 }?.let { it / durationSec * 60.0 }

    val isFreestyle: Boolean get() = stroke == null || stroke == "free"
}

/**
 * How the main set of a session held up: the longest series of equal
 * repeats (at least [SwimAnalysis.MIN_SET_REPEATS] of them).
 */
data class SwimSetHold(
    val repeats: Int,
    val distanceMeters: Double,
    /** Last repeat's pace minus the first's, seconds per 100 m: positive is slower. */
    val fadeSecPer100: Double,
    /** Spread of the paces, as a percentage of their mean (coefficient of variation). */
    val variationPct: Double,
    /** Metres per stroke on the first and last repeat, when strokes were counted. */
    val firstMetersPerStroke: Double?,
    val lastMetersPerStroke: Double?,
    val firstStrokeRate: Double?,
    val lastStrokeRate: Double?,
) {
    /** Change of the stroke length from first to last repeat, in percent. */
    val strokeLengthChangePct: Double?
        get() {
            val first = firstMetersPerStroke ?: return null
            val last = lastMetersPerStroke ?: return null
            return (last - first) / first * 100.0
        }

    val regularity: PacingRegularity
        get() = when {
            variationPct < 1.5 -> PacingRegularity.VERY_STEADY
            variationPct < 3.0 -> PacingRegularity.STEADY
            else -> PacingRegularity.UNEVEN
        }
}

enum class PacingRegularity { VERY_STEADY, STEADY, UNEVEN }

/** Stroke efficiency of one swim: freestyle in the pool. */
data class SwimEfficiency(
    val metersPerStroke: Double?,
    val strokeRate: Int?,
    val swolf: Int?,
)

/**
 * How much of a swim was swum with equipment, and what is left without.
 * The two parts are read apart: with fins, a pull buoy or paddles the pace
 * is faster than the athlete's own.
 */
data class SwimGearUse(
    /** Every piece of equipment marked on the swim. */
    val gear: Set<SwimGear>,
    val meters: Double,
    val seconds: Double,
    /** The swimming without equipment that can be read; zero metres when all of it was with. */
    val ownMeters: Double,
    val ownSeconds: Double,
    /** The intervals swum with equipment, by number; empty for a swim without laps. */
    val repeats: List<Int> = emptyList(),
) {
    /** True when nothing of the swim is left to read without equipment. */
    val whole: Boolean get() = ownMeters <= 0 || ownSeconds <= 0

    val paceSecPer100: Double? get() = if (meters > 0 && seconds > 0) seconds / meters * 100.0 else null

    /** The athlete's own pace in the swim, s/100 m; null when [whole]. */
    val ownPaceSecPer100: Double? get() = if (whole) null else ownSeconds / ownMeters * 100.0
}

/** A critical swim speed worked out from the athlete's own best efforts. */
data class CssEstimate(
    val paceSecPer100: Int,
    /** The efforts behind it, longest last. */
    val efforts: List<CssEffort>,
    val confidence: CssConfidence,
)

data class CssEffort(val distanceMeters: Int, val seconds: Double, val date: Instant)

/**
 * How far a [CssEstimate] can be trusted. Never high: the app cannot know
 * that the intervals were swum flat out, only a test says so.
 */
enum class CssConfidence { MEDIUM, LOW }

/**
 * Swim metrics read from the laps of a session, chosen for a triathlete:
 * pace against the CSS, how the pace and the stroke hold in the main set,
 * stroke efficiency, pace zones as swimmers name them (A1 to C3),
 * the load lap by lap and a CSS estimated from the athlete's best efforts.
 *
 * Adapted from the swim metrics of SwimForge (pace zones on CSS, pacing
 * variation and fade, stroke length drop in repeats, CSS from best efforts),
 * reduced to freestyle.
 */
object SwimAnalysis {

    /** Equal repeats needed before a series counts as a main set. */
    const val MIN_SET_REPEATS = 3

    /**
     * The swim zones as coaches name them at the pool, slowest first: A1
     * recovery, A2 aerobic, B1 tempo, B2 threshold (up to the CSS), C1 just
     * above it, C2 VO2max, C3 sprint. Recorded swims and planned sessions
     * both use them, so a planned B1 and a swum B1 are the same pace.
     */
    val ZONE_CODES = listOf("A1", "A2", "B1", "B2", "C1", "C2", "C3")

    /**
     * Upper bounds of A1 to C2 as a fraction of CSS speed, as in SwimForge;
     * above the last is C3. B2 ends at the CSS itself, with half a percent
     * of slack so that a repeat swum on the threshold does not fall in C1
     * for a few tenths of a second.
     */
    val ZONE_SPEED_BOUNDS = doubleArrayOf(0.85, 0.90, 0.95, 1.005, 1.04, 1.10)

    /** The place of B2, the threshold zone, among the zones, counted from 1. */
    const val THRESHOLD_ZONE = 4

    /** Intensity factor cap: anything above is a lap button pressed late or early. */
    private const val MAX_IF = 1.3

    /** CSS outside these paces (s/100 m) is a data error, not a swimmer. */
    private const val MIN_CSS = 60
    private const val MAX_CSS = 240

    /** The swum intervals of a session, each with the rest that followed it. */
    fun repeats(activity: Activity): List<SwimRepeat> {
        val result = mutableListOf<SwimRepeat>()
        var pending: SwimLap? = null
        var rest = 0.0
        fun flush(restAfter: Double?) {
            val recorded = pending ?: return
            val lap = recounted(recorded)
            result += SwimRepeat(
                number = result.size + 1,
                distanceMeters = lap.distanceMeters,
                durationSec = lap.durationSec,
                strokes = lap.strokes,
                swolf = lap.swolf,
                avgHr = lap.avgHr,
                stroke = lap.stroke,
                lengths = lap.lengths,
                restAfterSec = restAfter,
                gear = activity.gear?.of(result.size + 1).orEmpty(),
                recounted = recorded.lengths.size - lap.lengths.size,
            )
        }
        for (lap in activity.laps) {
            if (lap.isRest) {
                rest += lap.durationSec
            } else {
                flush(rest)
                pending = lap
                rest = 0.0
            }
        }
        flush(null)
        return flagged(result)
    }

    /** Lengths needed in a repeat before one of them can be told counted twice. */
    private const val MIN_LENGTHS_TO_RECOUNT = 3

    /**
     * A piece of a length counted twice lasts under this share of a whole
     * length of its repeat. Nobody swims one length in nearly half the time
     * of the others; a sprint at the end or a turn felt late takes off a
     * third at most.
     */
    private const val PIECE_SHARE = 0.55

    /** And with the piece next to it makes no more than this share of a whole one. */
    private const val JOINED_SHARE = 1.3

    /**
     * [lap] without the lengths the watch counted twice. When it feels a
     * turn in the middle of a length it records two short ones, and the
     * repeat comes out longer and faster than it was: 100 m in 2:02 become
     * 150 m at 1:21 /100 m, a pace that then enters records and threshold
     * as the athlete's best.
     *
     * A length is a piece of one when it is much shorter than the whole
     * lengths of its repeat and, joined to the shorter length beside it,
     * makes about one of them. A short length next to a long one is not:
     * there the watch only put the turn in the wrong place, and the count
     * is right. The pieces are joined, and the distance follows the lengths
     * that are left; the time and the strokes are the repeat's own.
     */
    internal fun recounted(lap: SwimLap): SwimLap {
        val lengths = lap.lengths
        if (lengths.size < MIN_LENGTHS_TO_RECOUNT || lengths.any { it.durationSec <= 0 }) return lap
        // A whole length: the middle of the longer half, so that the pieces themselves do not shorten it.
        val whole = Stats.median(lengths.map { it.durationSec }.sorted().drop(lengths.size / 2))
        fun piece(length: SwimLength) = length.durationSec < whole * PIECE_SHARE
        fun joined(one: SwimLength, other: SwimLength) = one.durationSec + other.durationSec <= whole * JOINED_SHARE
        // Most lengths short against one very long: there the watch missed a turn, and nothing here can count it back.
        if (lengths.count(::piece) * 2 > lengths.size) return lap

        val swum = mutableListOf<SwimLength>()
        var index = 0
        while (index < lengths.size) {
            val length = lengths[index]
            val before = swum.lastOrNull()
            val after = lengths.getOrNull(index + 1)
            val withAfter = after != null && piece(length) && joined(length, after) && (before == null || after.durationSec <= before.durationSec || !joined(before, length))
            val withBefore = !withAfter && before != null && piece(length) && joined(before, length)
            when {
                withAfter -> {
                    swum += join(length, after!!)
                    index += 2
                }
                withBefore -> {
                    swum[swum.lastIndex] = join(before!!, length)
                    index += 1
                }
                else -> {
                    swum += length
                    index += 1
                }
            }
        }
        if (swum.size == lengths.size) return lap
        return lap.copy(
            distanceMeters = lap.distanceMeters * swum.size / lengths.size,
            swolf = lap.strokes?.let { ((lap.durationSec + it) / swum.size).toInt() } ?: lap.swolf,
            lengths = swum,
        )
    }

    /** Two pieces of one length as that length: the longer piece says which stroke it was. */
    private fun join(one: SwimLength, other: SwimLength): SwimLength {
        val longer = if (one.durationSec >= other.durationSec) one else other
        val strokes = if (one.strokes == null && other.strokes == null) null else (one.strokes ?: 0) + (other.strokes ?: 0)
        val seconds = one.durationSec + other.durationSec
        return SwimLength(
            distanceMeters = longer.distanceMeters,
            durationSec = seconds,
            strokes = strokes,
            swolf = strokes?.let { (seconds + it).toInt() },
            stroke = longer.stroke,
        )
    }

    /** Seconds per 100 m nobody holds in a pool session: under it the watch miscounted. */
    private const val FASTEST_PACE = 45.0

    /** Repeats needed before one can be told apart from the others. */
    private const val MIN_FOR_DOUBT = 4

    /**
     * How much faster than the session's usual pace a repeat may be, as a
     * share of that pace: a sprint over one or two lengths can be far
     * quicker, 100 m and more cannot.
     */
    private fun plausibleShare(distanceMeters: Double): Double = when {
        distanceMeters >= 100 -> 0.72
        distanceMeters >= 50 -> 0.62
        else -> 0.55
    }

    /**
     * [repeats] with the ones whose pace cannot be right marked
     * [SwimRepeat.doubtful]. A repeat with equipment is never one: the
     * athlete said why it is fast, and it is left out of the usual pace the
     * others are measured against.
     */
    private fun flagged(repeats: List<SwimRepeat>): List<SwimRepeat> {
        val paces = repeats.filter { !it.assisted && it.distanceMeters > 0 && it.durationSec > 0 }.map { it.paceSecPer100 }
        if (paces.isEmpty()) return repeats
        val usual = Stats.median(paces)
        return repeats.map { repeat ->
            if (repeat.assisted || repeat.distanceMeters <= 0 || repeat.durationSec <= 0) return@map repeat
            val pace = repeat.paceSecPer100
            val outlier = paces.size >= MIN_FOR_DOUBT && pace < usual * plausibleShare(repeat.distanceMeters)
            if (pace < FASTEST_PACE || outlier) repeat.copy(doubtful = true) else repeat
        }
    }

    /** The usual pace of a session, s/100 m: the median of the repeats that are the athlete's own. Null without any. */
    fun usualPace(repeats: List<SwimRepeat>): Double? =
        repeats.filter { it.own && it.distanceMeters > 0 && it.durationSec > 0 }.map { it.paceSecPer100 }.takeIf { it.isNotEmpty() }?.let(Stats::median)

    /** The zone of a pace against the CSS, counted from 1 (A1) to 7 (C3): its place in [ZONE_CODES]. */
    fun zoneOf(paceSecPer100: Double, cssSecPer100: Int): Int {
        val relativeSpeed = cssSecPer100 / paceSecPer100
        val index = ZONE_SPEED_BOUNDS.indexOfFirst { relativeSpeed < it }
        return if (index < 0) ZONE_CODES.size else index + 1
    }

    /** The code of the zone of a pace against the CSS: «B1». */
    fun zoneCode(paceSecPer100: Double, cssSecPer100: Int): String = ZONE_CODES[zoneOf(paceSecPer100, cssSecPer100) - 1]

    /**
     * Metres swum in each zone (index 0 is A1, as in [ZONE_CODES]). Doubtful
     * repeats and those with equipment are left out: their pace is not one
     * to put in a zone. Null without laps or CSS, or with nothing left.
     */
    fun zoneMeters(activity: Activity, cssSecPer100: Int?): DoubleArray? {
        val css = cssSecPer100?.takeIf { it > 0 } ?: return null
        val repeats = repeats(activity).filter { it.own }
        if (repeats.isEmpty()) return null
        val meters = DoubleArray(ZONE_CODES.size)
        repeats.forEach { meters[zoneOf(it.paceSecPer100, css) - 1] += it.distanceMeters }
        return meters
    }

    /**
     * Swim training stress lap by lap: hours x IF^3 x 100 for each interval,
     * IF being its speed over the CSS speed. Summing laps keeps a hard set
     * hard, where one average over warm-up, set and drills flattens it. A
     * doubtful repeat counts for its time at the session's usual pace: the
     * time was swum, the pace the watch gave it was not. So does a repeat
     * with equipment, whose speed says more of the fins than of the effort;
     * when the whole swim was with equipment it counts as an ordinary swim
     * ([SessionLoadModel.typicalIntensity]).
     */
    fun lapTss(activity: Activity, cssSecPer100: Int?): Double? {
        val css = cssSecPer100?.takeIf { it > 0 } ?: return null
        val repeats = repeats(activity)
        if (repeats.isEmpty()) return null
        val usual = usualPace(repeats)
        return repeats.sumOf {
            val intensity = when {
                it.own -> css / it.paceSecPer100
                usual != null -> css / usual
                it.assisted -> SessionLoadModel.typicalIntensity(Sport.SWIM)
                else -> return@sumOf 0.0
            }.coerceAtMost(MAX_IF)
            it.durationSec / 3600.0 * intensity.pow(3) * 100.0
        }
    }

    /** The main set and how it held up, or null when there is none. */
    fun setHold(activity: Activity): SwimSetHold? {
        val repeats = repeats(activity).filter { it.isFreestyle && it.own }
        val set = repeats
            .groupBy { it.distanceMeters.toInt() }
            .filter { (distance, group) -> distance >= 50 && group.size >= MIN_SET_REPEATS }
            .values
            .maxWithOrNull(compareBy<List<SwimRepeat>> { group -> group.sumOf { it.distanceMeters } }
                .thenByDescending { group -> group.sumOf { it.durationSec } })
            ?: return null
        val paces = set.map { it.paceSecPer100 }
        val first = set.first()
        val last = set.last()
        return SwimSetHold(
            repeats = set.size,
            distanceMeters = first.distanceMeters,
            fadeSecPer100 = last.paceSecPer100 - first.paceSecPer100,
            variationPct = Stats.sd(paces) / Stats.mean(paces) * 100.0,
            firstMetersPerStroke = first.metersPerStroke,
            lastMetersPerStroke = last.metersPerStroke,
            firstStrokeRate = first.strokeRate,
            lastStrokeRate = last.strokeRate,
        )
    }

    /**
     * Stroke efficiency of a pool swim: metres per stroke over the freestyle
     * intervals with strokes (Garmin's session average without them), stroke
     * rate and SWOLF. Null for open water, where the stroke is shorter and
     * would spoil the comparison.
     */
    fun efficiency(activity: Activity): SwimEfficiency? {
        if (activity.sport != Sport.SWIM || activity.openWater == true) return null
        val dps = metersPerStroke(activity)
        // Stroke rate and SWOLF are the watch's averages over the whole swim: with equipment in it they are not the stroke's.
        val whole = activity.gear?.any != true
        val result = SwimEfficiency(
            dps,
            activity.avgCadence?.takeIf { whole && it > 0 },
            activity.swolf?.takeIf { whole && it > 0 },
        )
        return if (result.metersPerStroke == null && result.strokeRate == null && result.swolf == null) null else result
    }

    /** Metres per stroke of a pool swim, freestyle only when laps say so, and never with equipment. */
    fun metersPerStroke(activity: Activity): Double? {
        val counted = repeats(activity).filter { it.isFreestyle && it.own && it.strokes != null }
        val strokes = counted.sumOf { it.strokes ?: 0 }
        if (strokes > 0) return counted.sumOf { it.distanceMeters } / strokes
        // Garmin's average is over the whole swim, paddles and fins included.
        return activity.strokeDistanceMeters.takeIf { activity.gear?.any != true }
    }

    /**
     * What of a swim was swum with equipment, as the athlete marked it. Null
     * when nothing was marked, or the marks fit no interval of the swim.
     */
    fun gearUse(activity: Activity): SwimGearUse? {
        val marks = activity.gear?.takeIf { it.any } ?: return null
        val repeats = repeats(activity).filter { it.distanceMeters > 0 && it.durationSec > 0 }
        if (repeats.isEmpty()) {
            // Without laps only the whole swim can be marked.
            if (marks.session.isEmpty() || activity.distanceMeters <= 0) return null
            return SwimGearUse(marks.session, activity.distanceMeters, activity.movingSec.toDouble(), 0.0, 0.0)
        }
        val with = repeats.filter { it.assisted }
        if (with.isEmpty()) return null
        val own = repeats.filter { it.own }
        return SwimGearUse(
            gear = with.flatMap { it.gear }.toSet(),
            meters = with.sumOf { it.distanceMeters },
            seconds = with.sumOf { it.durationSec },
            ownMeters = own.sumOf { it.distanceMeters },
            ownSeconds = own.sumOf { it.durationSec },
            repeats = with.map { it.number },
        )
    }

    /** Days after a pool swim in which the app still asks what it was swum with. */
    const val GEAR_ASK_DAYS = 2L

    /**
     * The pool swim to ask the athlete about: the latest of the last
     * [GEAR_ASK_DAYS] days, when nobody has said yet whether it was swum
     * with equipment. Null when the latest one has its answer.
     */
    fun gearToAsk(activities: List<Activity>, today: LocalDate, zone: ZoneId): Activity? {
        val from = today.minusDays(GEAR_ASK_DAYS)
        return activities
            .filter { it.sport == Sport.SWIM && it.openWater != true && it.distanceMeters > 0 }
            .filter { val day = it.startTime.atZone(zone).toLocalDate(); !day.isBefore(from) && !day.isAfter(today) }
            .maxByOrNull { it.startTime }
            ?.takeIf { it.gear == null }
    }

    /**
     * The metres and seconds the athlete's own pace is read on: the session
     * itself, or for a swim with equipment what was swum without. Null when
     * there is no pace to read, as when the whole swim was with equipment.
     * Records, trends and comparisons between sessions go by this.
     */
    fun ownPace(activity: Activity): PaceBase? {
        val use = if (activity.sport == Sport.SWIM) gearUse(activity) else null
        return when {
            use == null -> PaceBase(activity.distanceMeters, activity.paceSec.toDouble()).takeIf { it.meters > 0 && it.seconds > 0 }
            use.whole -> null
            else -> PaceBase(use.ownMeters, use.ownSeconds)
        }
    }

    /**
     * The athlete's usual efficiency: averages over the pool swims of the
     * [weeks] before [before] (the swim being looked at starts at [before], so it is left out). Null fields have no data.
     */
    fun usualEfficiency(history: List<Activity>, before: Instant, weeks: Long = 8): SwimEfficiency? {
        val from = before.minus(weeks * 7, ChronoUnit.DAYS)
        val pool = history.filter {
            it.sport == Sport.SWIM && it.openWater != true && it.startTime < before && it.startTime >= from
        }
        val readings = pool.mapNotNull { efficiency(it) }
        if (readings.isEmpty()) return null
        fun <T : Number> avg(values: List<T>): Double? =
            values.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.toDouble() } / list.size }
        return SwimEfficiency(
            metersPerStroke = avg(readings.mapNotNull { it.metersPerStroke }),
            strokeRate = avg(readings.mapNotNull { it.strokeRate })?.let { Math.round(it).toInt() },
            swolf = avg(readings.mapNotNull { it.swolf })?.let { Math.round(it).toInt() },
        )
    }

    /** Metres per stroke week by week (Monday first), oldest first; null for weeks without data. */
    fun weeklyMetersPerStroke(
        activities: List<Activity>,
        today: LocalDate,
        zone: ZoneId,
        weeks: Int = 8,
    ): List<Pair<LocalDate, Double?>> {
        val first = TrainingStats.weekStart(today).minusWeeks(weeks - 1L)
        val byWeek = activities
            .filter { it.sport == Sport.SWIM && it.openWater != true }
            .groupBy { TrainingStats.weekStart(it.startTime.atZone(zone).toLocalDate()) }
        return (0 until weeks).map { i ->
            val week = first.plusWeeks(i.toLong())
            val values = byWeek[week].orEmpty().mapNotNull { a -> metersPerStroke(a)?.let { it to a.distanceMeters } }
            val meters = values.sumOf { it.second }
            week to if (values.isEmpty() || meters <= 0) null else values.sumOf { it.first * it.second } / meters
        }
    }

    /** Distances (m) whose best efforts make the CSS, as in a 200 + 400 test and longer. */
    private val CSS_DISTANCES = listOf(200, 300, 400, 500, 800, 1000, 1500)

    /**
     * From this length on an interval says something about the threshold.
     * Shorter repeats with rest are swum faster than it, by a little or by a
     * lot depending on the swimmer: a pace over 100 m is not a threshold.
     */
    const val CONTINUOUS_METERS = 200.0

    /** How much farther than the model allows a swim may go before it contradicts the estimate. */
    private const val MODEL_SLACK = 1.03

    /**
     * Critical swim speed from the best freestyle intervals of the pool swims
     * since [since]: for each distance the fastest interval, then the slope of
     * distance over time (distance = CS x time + D'). An effort slower per
     * 100 m than a longer one was not swum hard and is left out. Needs two
     * distances, one of them at least 400 m.
     *
     * The estimate is right when the intervals were swum flat out, which the
     * app cannot know: it is never more than of medium confidence, a test
     * says more. It is of low confidence when another continuous swim of
     * [CONTINUOUS_METERS] and more went farther in its time than the model
     * allows: then the efforts were not the athlete's best and the estimate
     * is too slow. Short repeats are not compared with it: they are faster
     * than the threshold for everybody. Intervals swum with equipment are
     * read nowhere here.
     */
    fun estimateCss(activities: List<Activity>, since: Instant): CssEstimate? {
        val continuous = activities
            .filter { it.sport == Sport.SWIM && it.openWater != true && it.startTime >= since }
            .flatMap { activity -> repeats(activity).filter { it.isFreestyle && it.own }.map { activity to it } }
        val efforts = continuous
            .mapNotNull { (activity, repeat) ->
                val distance = CSS_DISTANCES.firstOrNull { kotlin.math.abs(repeat.distanceMeters - it) <= it * 0.02 }
                distance?.let { CssEffort(it, repeat.durationSec, activity.startTime) }
            }
            .groupBy { it.distanceMeters }
            .map { (_, group) -> group.minBy { it.seconds } }
            .sortedBy { it.distanceMeters }
        // Keep an effort only if no longer one was faster per 100 m.
        val hard = efforts.filterIndexed { i, effort ->
            efforts.drop(i + 1).none { it.seconds / it.distanceMeters < effort.seconds / effort.distanceMeters }
        }
        if (hard.size < 2 || hard.last().distanceMeters < 400) return null
        val points = hard.map { it.seconds to it.distanceMeters.toDouble() }
        val speed = Stats.slope(points) ?: return null
        if (speed <= 0) return null
        val pace = Math.round(100.0 / speed).toInt()
        if (pace !in MIN_CSS..MAX_CSS) return null
        // D' (metres of anaerobic reserve) must be positive for the model to hold.
        val meanT = points.sumOf { it.first } / points.size
        val meanD = points.sumOf { it.second } / points.size
        val reserve = meanD - speed * meanT
        if (reserve <= 0) return null
        val beaten = continuous.any { (_, repeat) ->
            repeat.distanceMeters >= CONTINUOUS_METERS && repeat.distanceMeters > (speed * repeat.durationSec + reserve) * MODEL_SLACK
        }
        return CssEstimate(pace, hard, if (beaten) CssConfidence.LOW else CssConfidence.MEDIUM)
    }
}
