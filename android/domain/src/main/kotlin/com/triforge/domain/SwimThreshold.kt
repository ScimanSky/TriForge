package com.triforge.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Where a proposed swim threshold comes from. */
enum class CssSource {
    /** The athlete's best efforts over two distances, as in a 400 + 200 test. */
    BEST_EFFORTS,

    /** A cautious reading of the pace the athlete usually swims at. */
    USUAL_PACE,
}

/** A swim threshold the app proposes in place of the one in the profile. */
data class CssSuggestion(val paceSecPer100: Int, val source: CssSource)

/**
 * Why the swim threshold of the profile does not fit the swims of the last
 * weeks, and what the app would put in its place.
 */
data class CssCheck(
    val currentSecPer100: Int,
    /** Pool swims looked at. */
    val sessions: Int,
    /** Metres swum in them without stopping: intervals of [SwimAnalysis.CONTINUOUS_METERS] and more. */
    val meters: Int,
    /** Share of those metres swum at threshold pace or faster (Z4 and Z5). */
    val highShare: Double,
    /** Share of those metres faster than the threshold zone (Z5 alone). */
    val topShare: Double = 0.0,
    /** Swims whose continuous intervals, half an hour and more of them, were faster than the threshold. */
    val fasterSessions: Int,
    /** The pace the athlete usually swims at without stopping, s/100 m: the median of the swims. */
    val usualPaceSecPer100: Int,
    /** Mean heart rate over those swims, when the watch gave it. */
    val avgHr: Int?,
    val suggestion: CssSuggestion,
)

/**
 * Checks the swim threshold (CSS) against what the athlete swims.
 *
 * The threshold is the pace one can hold for about half an hour flat out.
 * So a threshold is too slow when most of the metres swum without stopping
 * in ordinary sessions come out at threshold pace and above, or when half
 * an hour of them in one swim was faster than it: zones, load and form are
 * then inflated. The app says so and proposes a value, from the athlete's
 * best efforts when there are two distances to read it from, otherwise from
 * the usual pace, on the cautious side. A proper test (400 m and 200 m flat
 * out) stays the better answer.
 *
 * Only intervals of [SwimAnalysis.CONTINUOUS_METERS] and more are read.
 * Repeats of 25, 50 or 100 m with rest are swum faster than the threshold,
 * and by how much depends on the swimmer: one who holds 1:49 over 100 m may
 * have a threshold of 2:05. Their pace says nothing about it.
 *
 * Only a threshold that is too slow is looked for: swimming mostly easy,
 * well under the threshold, is what a triathlete's week looks like.
 */
object SwimThreshold {

    /** Days of swims the check looks at. */
    const val WINDOW_DAYS = 21L

    /** Days the proposed value is read from. */
    const val ESTIMATE_DAYS = 56L

    const val MIN_SESSIONS = 2
    const val MIN_METERS = 3000

    /** A swim with fewer continuous metres than this says little about the threshold. */
    const val MIN_SESSION_METERS = 800

    /** A swim without laps counts when it was swum nearly without stopping: this share of its time moving. */
    private const val CONTINUOUS_SHARE = 0.9

    /** More than this share of the metres at threshold and above is not a training week. */
    const val HIGH_SHARE = 0.5

    /** Swimming time over which nobody stays faster than their threshold. */
    const val SUSTAINED_SEC = 30 * 60

    /** A session counts as faster than the threshold from this margin on. */
    private const val FASTER_MARGIN = 0.98

    /**
     * The threshold as a share of the usual pace of the sessions: sets and
     * warm-up together run a little slower than the threshold, about 5%.
     */
    const val USUAL_TO_CSS = 0.95

    /** A proposal closer than this to the current value is not worth a warning. */
    const val MIN_GAP = 0.03

    private data class Swim(val meters: Double, val seconds: Double, val highMeters: Double, val topMeters: Double, val avgHr: Int?) {
        val pace: Double get() = seconds / meters * 100.0
    }

    /** The intervals of a swim that say something about the threshold: trusted, and swum without stopping for long enough. */
    private fun continuous(activity: Activity): List<SwimRepeat> = SwimAnalysis.repeats(activity)
        .filter { !it.doubtful && it.durationSec > 0 && it.distanceMeters >= SwimAnalysis.CONTINUOUS_METERS }

    /** A swim without laps that was one block, or close to it. */
    private fun oneBlock(activity: Activity): Boolean = activity.laps.isEmpty() && activity.distanceMeters > 0 &&
        activity.movingSec > 0 && activity.movingSec >= activity.durationSec * CONTINUOUS_SHARE

    /**
     * One pool swim read against [css]: from its continuous intervals when it
     * has laps, as one block when it has none and was swum without stopping.
     * Null when it has neither.
     */
    private fun read(activity: Activity, css: Int): Swim? {
        val repeats = continuous(activity)
        if (repeats.isNotEmpty()) {
            val meters = repeats.sumOf { it.distanceMeters }
            val high = repeats.filter { SwimAnalysis.zoneOf(it.paceSecPer100, css) >= 4 }.sumOf { it.distanceMeters }
            val top = repeats.filter { SwimAnalysis.zoneOf(it.paceSecPer100, css) >= 5 }.sumOf { it.distanceMeters }
            return Swim(meters, repeats.sumOf { it.durationSec }, high, top, activity.avgHr)
        }
        if (!oneBlock(activity)) return null
        val pace = activity.movingSec / activity.distanceMeters * 100.0
        val high = if (SwimAnalysis.zoneOf(pace, css) >= 4) activity.distanceMeters else 0.0
        val top = if (SwimAnalysis.zoneOf(pace, css) >= 5) activity.distanceMeters else 0.0
        return Swim(activity.distanceMeters, activity.movingSec.toDouble(), high, top, activity.avgHr)
    }

    private fun poolSwims(activities: List<Activity>, from: LocalDate, today: LocalDate, zone: ZoneId): List<Activity> = activities.filter {
        val day = it.startTime.atZone(zone).toLocalDate()
        it.sport == Sport.SWIM && it.openWater != true && !day.isBefore(from) && !day.isAfter(today)
    }

    /**
     * The check for [profile]'s swim threshold on [today], or null when
     * there is no threshold, too little swimming to judge, or nothing wrong.
     */
    fun check(activities: List<Activity>, profile: AthleteProfile, today: LocalDate, zone: ZoneId): CssCheck? {
        val css = profile.cssSecPer100m?.takeIf { it > 0 } ?: return null
        val swims = poolSwims(activities, today.minusDays(WINDOW_DAYS), today, zone)
            .mapNotNull { read(it, css) }
            .filter { it.meters >= MIN_SESSION_METERS }
        val meters = swims.sumOf { it.meters }
        if (swims.size < MIN_SESSIONS || meters < MIN_METERS) return null

        val highShare = swims.sumOf { it.highMeters } / meters
        val faster = swims.count { it.seconds >= SUSTAINED_SEC && it.pace < css * FASTER_MARGIN }
        if (highShare <= HIGH_SHARE && faster == 0) return null

        val usual = Stats.median(swims.map { it.pace })
        val suggestion = suggest(activities, today, zone) ?: return null
        // Only a threshold that is too slow: the proposal must be clearly faster.
        if (suggestion.paceSecPer100 > css * (1 - MIN_GAP)) return null
        val beats = swims.mapNotNull { it.avgHr }
        return CssCheck(
            currentSecPer100 = css,
            sessions = swims.size,
            meters = meters.roundToInt(),
            highShare = highShare,
            topShare = swims.sumOf { it.topMeters } / meters,
            fasterSessions = faster,
            usualPaceSecPer100 = usual.roundToInt(),
            avgHr = beats.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            suggestion = suggestion,
        )
    }

    /**
     * The threshold from a test: 400 m and 200 m flat out, in seconds. It is
     * the pace of the 200 m between the two, per 100 m. Null when the two
     * times cannot be those of a test (the 400 not slower than the 200, or
     * a pace nobody swims).
     */
    fun fromTest(seconds400: Int, seconds200: Int): Int? {
        if (seconds200 <= 0 || seconds400 <= seconds200) return null
        // The 400 is swum at the pace of the 200 or slower, never at half its speed.
        if (seconds400 < seconds200 * 2 || seconds400 > seconds200 * 4) return null
        return ((seconds400 - seconds200) / 2.0).roundToInt().takeIf { it in TEST_RANGE }
    }

    /** Paces per 100 m a threshold can have, from a champion to a beginner. */
    private val TEST_RANGE = 55..240

    /**
     * The threshold the app would propose: from the best efforts of the last
     * weeks when two distances allow it and nothing contradicts them,
     * otherwise from the usual pace of the continuous swimming of the
     * sessions. Null with fewer than [MIN_SESSIONS] swims to read.
     */
    fun suggest(activities: List<Activity>, today: LocalDate, zone: ZoneId): CssSuggestion? {
        val from = today.minusDays(ESTIMATE_DAYS)
        val since: Instant = from.atStartOfDay(zone).toInstant()
        SwimAnalysis.estimateCss(activities, since)?.takeIf { it.confidence != CssConfidence.LOW }
            ?.let { return CssSuggestion(it.paceSecPer100, CssSource.BEST_EFFORTS) }
        val paces = poolSwims(activities, from, today, zone).mapNotNull { activity ->
            val repeats = continuous(activity)
            val (meters, seconds) = when {
                repeats.isNotEmpty() -> repeats.sumOf { it.distanceMeters } to repeats.sumOf { it.durationSec }
                oneBlock(activity) -> activity.distanceMeters to activity.movingSec.toDouble()
                else -> return@mapNotNull null
            }
            if (meters < MIN_SESSION_METERS || seconds <= 0) null else seconds / meters * 100.0
        }
        if (paces.size < MIN_SESSIONS) return null
        return CssSuggestion((Stats.median(paces) * USUAL_TO_CSS).roundToInt(), CssSource.USUAL_PACE)
    }
}
