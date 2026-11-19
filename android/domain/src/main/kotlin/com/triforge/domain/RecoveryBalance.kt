package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The parts of the load-recovery balance, with their nominal weights.
 * [response] parts describe how the body reacts; load is the context.
 * See docs/bilancio-carico-recupero.md for the reasoning behind each number.
 */
enum class RecoveryComponent(val weight: Double, val response: Boolean) {
    CHECK_IN(0.25, true),
    HRV(0.25, true),
    RESTING_HR(0.15, true),
    SLEEP(0.15, true),
    LOAD(0.15, false),
    PERFORMANCE(0.05, true),
}

enum class RecoveryPhase {
    /** No activity and no recovery data at all. */
    NO_DATA,

    /** Fewer than 14 days of a recovery marker: only load is shown. */
    LEARNING,

    /** 14 to 27 days: a provisional value, at most a level 1 notice. */
    PROVISIONAL,

    /** 28 days or more: the full index. */
    FULL,
}

/** How load and response combine. Letters follow the study, section 9.6. */
enum class LoadResponsePattern {
    /** Nothing notable. */
    BASELINE,

    /** A: load is high and the body is coping. */
    ABSORBING,

    /** B: fatigue that the recent load explains. */
    LOAD_FATIGUE,

    /** C: fatigue signs that the load does not explain. */
    UNEXPLAINED,

    /** D: still off after a week or more of reduced load. */
    PERSISTING_AFTER_TAPER,

    /** E: unusually high HRV or low resting HR with other signs of fatigue. */
    ATYPICAL_HIGH_HRV,

    /** The athlete reported feeling ill: the overload logic is paused. */
    ILLNESS,
}

enum class RecoveryLight { GREEN, YELLOW, ORANGE, RED }

enum class ConfidenceLevel { LOW, MEDIUM, HIGH }

/** Load on one day, in TSS-equivalents. */
data class LoadState(
    val date: LocalDate,
    val dailyTss: Double,
    /** Acute load: 7-day exponential average. */
    val atl: Double,
    /** Chronic load: 42-day exponential average. */
    val ctl: Double,
    /** (CTL - ATL) / max(CTL, 20), from yesterday's values. Negative is fatigue. */
    val formPct: Double,
    /** CTL change over the last 7 days. */
    val ramp: Double,
    /** Foster monotony over the last 7 days, capped at 4. */
    val monotony: Double,
)

/** One day of a recovery marker against the athlete's normal range. */
data class MarkerBand(
    val date: LocalDate,
    val value: Double?,
    val mean7: Double?,
    val low: Double?,
    val center: Double?,
    val high: Double?,
)

data class RecoveryDay(
    val date: LocalDate,
    val phase: RecoveryPhase,
    /** 0-100, higher means more signs of accumulated fatigue. Null while learning. */
    val score: Int?,
    /** Mean of the last three scores, which drives the colour. */
    val smoothedScore: Int?,
    val light: RecoveryLight?,
    /** 0 none, 1 attention, 2 accumulated fatigue, 3 persistent signs. */
    val level: Int,
    val pattern: LoadResponsePattern,
    /** 0-1: how much of the full picture the score is based on. */
    val confidence: Double,
    /** Each available part, 0 (normal) to 1 (far from normal). */
    val deviations: Map<RecoveryComponent, Double>,
    /** Points each part adds to [score]; they sum to it. */
    val contributions: Map<RecoveryComponent, Double>,
    val load: LoadState?,
    /** Parts with no usable data, so the UI can say what is missing. */
    val missing: Set<RecoveryComponent>,
) {
    val confidenceLevel: ConfidenceLevel
        get() = when {
            confidence >= RecoveryBalance.HIGH_CONFIDENCE -> ConfidenceLevel.HIGH
            confidence >= RecoveryBalance.MEDIUM_CONFIDENCE -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
}

data class RecoveryReport(
    val today: RecoveryDay,
    /** Oldest first, ending with [today]. */
    val history: List<RecoveryDay>,
    /** HRV in ms with its normal band, for the chart. */
    val hrvBand: List<MarkerBand>,
    /** Resting heart rate in bpm with its normal band. */
    val restingHrBand: List<MarkerBand>,
    /** Running load this week is well above the last four: a separate note. */
    val runLoadJump: Boolean,
    val latestWellness: DailyWellness?,
)

/**
 * Load-recovery balance ("Bilancio Carico-Recupero"): a daily 0-100 index of
 * accumulated fatigue signs, built from training load and the body's
 * response, each compared with the athlete's own normal.
 *
 * It does not diagnose overtraining, which no app can do. It flags when
 * several signals stay away from normal for days, and says whether the
 * recent load explains it.
 */
object RecoveryBalance {

    const val HIGH_CONFIDENCE = 0.70
    const val MEDIUM_CONFIDENCE = 0.45

    /** Baseline window: days t-60 to t-8, so the last week does not chase itself. */
    private const val BASELINE_FROM = 60L
    private const val BASELINE_TO = 8L
    private const val MIN_BASELINE_VALUES = 14
    private const val FULL_BASELINE_VALUES = 28
    private const val MIN_WEEK_VALUES = 4

    /** Floors for the baseline spread, so tiny spreads do not blow up z-scores. */
    private const val SIGMA_LN_HRV = 0.05
    private const val SIGMA_RHR = 1.5
    private const val SIGMA_SLEEP_H = 20.0 / 60
    private const val SIGMA_CHECK_IN = 1.0

    /** A part counts as "off" from this deviation upwards. */
    private const val DOMAIN_OFF = 0.4

    /**
     * 0 inside the smallest worthwhile change (half a spread), 1 from
     * 2.5 spreads away. [z] is oriented so positive is worse.
     */
    fun deviation(z: Double): Double = Stats.clamp01((z - 0.5) / 2.0)

    fun compute(
        activities: List<Activity>,
        wellness: List<DailyWellness>,
        checkIns: List<CheckIn>,
        profile: AthleteProfile,
        today: LocalDate,
        zone: ZoneId,
        historyDays: Int = 42,
    ): RecoveryReport {
        require(historyDays > 0) { "historyDays must be positive" }
        val inputs = Inputs(activities, wellness, checkIns, profile, today, zone)
        // Enough earlier days for the persistence rules to have a history.
        val warmUp = 21
        val first = today.minusDays((historyDays + warmUp - 1).toLong())
        val days = mutableListOf<RecoveryDay>()
        val raw = mutableListOf<RawDay>()
        var level = 0
        var daysBelow = 0
        var day = first
        while (!day.isAfter(today)) {
            val current = inputs.rawDay(day)
            raw += current
            val target = rawLevel(raw, inputs)
            when {
                current.score == null || current.pattern == LoadResponsePattern.ILLNESS -> {
                    level = 0
                    daysBelow = 0
                }

                target >= level -> {
                    level = target
                    daysBelow = 0
                }

                // Hysteresis: step down only after three days below.
                ++daysBelow >= 3 -> {
                    level = target
                    daysBelow = 0
                }
            }
            raw[raw.lastIndex] = current.copy(level = level)
            days += finish(raw)
            day = day.plusDays(1)
        }
        val shown = days.takeLast(historyDays)
        return RecoveryReport(
            today = shown.last(),
            history = shown,
            hrvBand = inputs.hrvBand(today, historyDays),
            restingHrBand = inputs.restingHrBand(today, historyDays),
            runLoadJump = inputs.runLoadJump(today),
            latestWellness = wellness.filter { !it.date.isAfter(today) }.maxByOrNull { it.date },
        )
    }

    // -- level rules ------------------------------------------------------

    private fun rawLevel(raw: List<RawDay>, inputs: Inputs): Int {
        val today = raw.last()
        if (today.score == null) return 0
        if (today.pattern == LoadResponsePattern.ILLNESS) return 0
        val last5 = raw.takeLast(5)
        val last7 = raw.takeLast(7)

        val level1 = last5.count { (it.score ?: 0) >= 30 } >= 3 && today.offDomains >= 1
        val highDays = last7.filter { (it.score ?: 0) >= 55 }
        val level2 = (
            highDays.size >= 5 &&
                highDays.count { it.offDomains >= 2 } >= 4 &&
                today.confidence >= MEDIUM_CONFIDENCE
            ) ||
            (last7.size == 7 && last7.all { it.pattern == LoadResponsePattern.UNEXPLAINED })
        val level2Run = if (level2) 1 + raw.dropLast(1).takeLastWhile { it.level >= 2 }.size else 0
        val level3 = (level2 && level2Run >= 14) ||
            today.pattern == LoadResponsePattern.PERSISTING_AFTER_TAPER ||
            inputs.worseningTrend(today.date, today.deviations[RecoveryComponent.PERFORMANCE])

        var target = when {
            level3 -> 3
            level2 -> 2
            level1 -> 1
            else -> 0
        }
        if (today.confidence < MEDIUM_CONFIDENCE || today.phase == RecoveryPhase.PROVISIONAL) {
            target = minOf(target, 1)
        }
        return target
    }

    private fun finish(raw: List<RawDay>): RecoveryDay {
        val today = raw.last()
        val recent = raw.takeLast(3).mapNotNull { it.score }
        val smoothed = if (today.score == null || recent.isEmpty()) null else recent.average().roundToInt()
        val light = smoothed?.let {
            when {
                it >= 75 && today.confidence >= MEDIUM_CONFIDENCE && today.level >= 2 -> RecoveryLight.RED
                it >= 55 -> RecoveryLight.ORANGE
                it >= 30 -> RecoveryLight.YELLOW
                else -> RecoveryLight.GREEN
            }
        }
        return RecoveryDay(
            date = today.date,
            phase = today.phase,
            score = today.score,
            smoothedScore = smoothed,
            light = light,
            level = today.level,
            pattern = today.pattern,
            confidence = today.confidence,
            deviations = today.deviations,
            contributions = today.contributions,
            load = today.load,
            missing = RecoveryComponent.entries.toSet() - today.deviations.keys,
        )
    }

    private data class RawDay(
        val date: LocalDate,
        val phase: RecoveryPhase,
        val score: Int?,
        val confidence: Double,
        val deviations: Map<RecoveryComponent, Double>,
        val contributions: Map<RecoveryComponent, Double>,
        val response: Double?,
        val offDomains: Int,
        val pattern: LoadResponsePattern,
        val load: LoadState?,
        val level: Int = 0,
    )

    private data class Baseline(val center: Double, val spread: Double, val count: Int)

    // -- per-day computation ------------------------------------------------

    private class Inputs(
        activities: List<Activity>,
        wellness: List<DailyWellness>,
        checkIns: List<CheckIn>,
        profile: AthleteProfile,
        private val today: LocalDate,
        private val zone: ZoneId,
    ) {
        private val sessions = SessionLoadModel.estimate(activities, profile)
            .filter { !date(it.activity).isAfter(today) }
        private val sessionsByDay = sessions.groupBy { date(it.activity) }
        private val checkInByDay = checkIns.associateBy { it.date }

        /** Days around a reported illness stay out of every baseline. */
        private val excluded: Set<LocalDate> = checkIns.filter { it.sick }
            .flatMap { listOf(it.date, it.date.plusDays(1)) }.toSet()

        val hrv: Map<LocalDate, Double> = wellness.mapNotNull { w -> w.hrvRmssd?.let { w.date to ln(it) } }.toMap()
        val restingHr: Map<LocalDate, Double> = wellness.mapNotNull { w -> w.restingHr?.let { w.date to it.toDouble() } }.toMap()
        val sleepHours: Map<LocalDate, Double> = wellness.mapNotNull { w -> w.sleepSec?.let { w.date to it / 3600.0 } }.toMap()
        val checkInTotal: Map<LocalDate, Double> = checkIns.associate { it.date to it.total.toDouble() }

        // Load series, from the first session (or 120 days back) to today.
        private val loadStart: LocalDate = minOf(
            sessionsByDay.keys.minOrNull() ?: today,
            today.minusDays(120),
        )
        private val dailyTss = mutableListOf<Double>()
        private val atl = mutableListOf<Double>()
        private val ctl = mutableListOf<Double>()

        init {
            var a = 0.0
            var c = 0.0
            var day = loadStart
            while (!day.isAfter(today)) {
                val tss = sessionsByDay[day].orEmpty().sumOf { it.tss }
                a += (tss - a) / 7
                c += (tss - c) / 42
                dailyTss += tss
                atl += a
                ctl += c
                day = day.plusDays(1)
            }
        }

        private fun date(activity: Activity): LocalDate = activity.startTime.atZone(zone).toLocalDate()

        private fun index(day: LocalDate): Int = ChronoUnit.DAYS.between(loadStart, day).toInt()

        fun loadState(day: LocalDate): LoadState? {
            val i = index(day)
            if (i !in dailyTss.indices) return null
            val prevAtl = if (i > 0) atl[i - 1] else 0.0
            val prevCtl = if (i > 0) ctl[i - 1] else 0.0
            val week = (max(0, i - 6)..i).map { dailyTss[it] }
            val monotony = (Stats.mean(week) / max(Stats.sd(week), 1.0)).coerceAtMost(4.0)
            return LoadState(
                date = day,
                dailyTss = dailyTss[i],
                atl = atl[i],
                ctl = ctl[i],
                formPct = (prevCtl - prevAtl) / max(prevCtl, 20.0),
                ramp = ctl[i] - (if (i >= 7) ctl[i - 7] else 0.0),
                monotony = monotony,
            )
        }

        private fun loadPressure(state: LoadState): Double {
            val form = Stats.clamp01((-state.formPct - 0.10) / 0.30)
            val ramp = Stats.clamp01((state.ramp - 7) / 7)
            val monotony = Stats.clamp01((state.monotony - 1.5) / 1.0)
            return Stats.clamp01(0.6 * form + 0.25 * ramp + 0.15 * monotony)
        }

        fun runLoadJump(day: LocalDate): Boolean {
            fun runTss(from: Long, to: Long) = (from..to).sumOf { back ->
                sessionsByDay[day.minusDays(back)].orEmpty().filter { it.activity.sport == Sport.RUN }.sumOf { it.tss }
            }
            val chronicWeek = runTss(7, 34) / 4
            if (chronicWeek < 30) return false
            return runTss(0, 6) / chronicWeek > 1.5
        }

        // -- markers ------------------------------------------------------

        private fun baseline(series: Map<LocalDate, Double>, day: LocalDate, floor: Double): Baseline? {
            val values = (BASELINE_TO..BASELINE_FROM).mapNotNull { back ->
                val d = day.minusDays(back)
                if (d in excluded) null else series[d]
            }
            if (values.size < MIN_BASELINE_VALUES) return null
            return Baseline(Stats.median(values), max(Stats.scaledMad(values), floor), values.size)
        }

        private fun week(series: Map<LocalDate, Double>, day: LocalDate): List<Double> =
            (0L..6L).mapNotNull { series[day.minusDays(it)] }

        private fun mean7(series: Map<LocalDate, Double>, day: LocalDate): Double? =
            week(series, day).takeIf { it.size >= MIN_WEEK_VALUES }?.let(Stats::mean)

        /** z of the 7-day mean against the baseline, oriented so positive is worse. */
        private fun worseZ(series: Map<LocalDate, Double>, day: LocalDate, floor: Double, higherIsWorse: Boolean): Double? {
            val base = baseline(series, day, floor) ?: return null
            val mean = mean7(series, day) ?: return null
            val z = (mean - base.center) / base.spread
            return if (higherIsWorse) z else -z
        }

        private fun hrvDeviation(day: LocalDate): Double? {
            val z = worseZ(hrv, day, SIGMA_LN_HRV, higherIsWorse = false) ?: return null
            val low = deviation(z)
            val cv = cv7(day) ?: return low
            val usual = (BASELINE_TO..BASELINE_FROM).mapNotNull { cv7(day.minusDays(it)) }
            if (usual.size < MIN_BASELINE_VALUES) return low
            val cvPart = Stats.clamp01((cv / Stats.median(usual) - 1.25) / 1.0)
            return max(low, 0.5 * low + 0.5 * cvPart)
        }

        private fun cv7(day: LocalDate): Double? {
            val values = week(hrv, day)
            if (values.size < MIN_WEEK_VALUES) return null
            val mean = Stats.mean(values)
            return if (mean <= 0) null else Stats.sd(values) / mean
        }

        private fun sleepDeviation(day: LocalDate): Double? {
            val z = worseZ(sleepHours, day, SIGMA_SLEEP_H, higherIsWorse = false) ?: return null
            val history = (1L..BASELINE_FROM).mapNotNull { sleepHours[day.minusDays(it)] }
            val target = max(if (history.isEmpty()) 0.0 else Stats.median(history), 7.0)
            val debt = (0L..6L).sumOf { back -> sleepHours[day.minusDays(back)]?.let { max(0.0, target - it) } ?: 0.0 }
            // The study also weighs bedtime regularity (0.15); it needs sleep
            // start and end times, not stored yet, so the rest is rescaled.
            return Stats.clamp01((0.5 * deviation(z) + 0.35 * Stats.clamp01((debt - 3) / 5)) / 0.85)
        }

        private fun checkInDeviation(day: LocalDate): Double? {
            val z = worseZ(checkInTotal, day, SIGMA_CHECK_IN, higherIsWorse = true) ?: return null
            val mean = mean7(checkInTotal, day) ?: return null
            return Stats.clamp01(0.7 * deviation(z) + 0.3 * Stats.clamp01((mean - 20) / 10))
        }

        /** Efficiency (output per heartbeat) on steady sessions, recent against usual. */
        private fun performanceDeviation(day: LocalDate): Double? {
            val bySport = sessions.mapNotNull { s ->
                val a = s.activity
                val hr = a.avgHr ?: return@mapNotNull null
                if (hr <= 0 || a.durationSec < 40 * 60) return@mapNotNull null
                if ((a.aerobicTe ?: 0.0) > 3.5 || (a.anaerobicTe ?: 0.0) >= 2.0) return@mapNotNull null
                val output = when (a.sport) {
                    Sport.BIKE -> (a.normPowerWatts ?: a.avgPowerWatts)?.toDouble()
                    Sport.RUN -> if (a.distanceMeters > 0) a.distanceMeters / a.durationSec else null
                    else -> null
                } ?: return@mapNotNull null
                Triple(a.sport, date(a), output / hr)
            }.groupBy { it.first }

            return bySport.values.mapNotNull { list ->
                val age = { d: LocalDate -> ChronoUnit.DAYS.between(d, day) }
                val recent = list.filter { age(it.second) in 0..13 }.map { it.third }
                val usual = list.filter { age(it.second) in 14..59 }.map { it.third }
                if (recent.size < 2 || usual.size < 3) return@mapNotNull null
                val change = (Stats.median(recent) - Stats.median(usual)) / Stats.median(usual)
                Stats.clamp01((-change - 0.03) / 0.07)
            }.maxOrNull()
        }

        private fun quality(series: Map<LocalDate, Double>, day: LocalDate): Double =
            (week(series, day).size / 5.0).coerceAtMost(1.0)

        private fun loadQuality(day: LocalDate): Double {
            val recent = (0L..27L).flatMap { sessionsByDay[day.minusDays(it)].orEmpty() }
            val total = recent.sumOf { it.tss }
            if (total <= 0) return 0.5
            return recent.filter { it.quality != LoadQuality.LOW }.sumOf { it.tss } / total
        }

        private fun daysOff(series: Map<LocalDate, Double>, floor: Double, day: LocalDate, high: Boolean): Boolean {
            val base = baseline(series, day, floor) ?: return false
            val count = (0L..6L).count { back ->
                val v = series[day.minusDays(back)] ?: return@count false
                val z = (v - base.center) / base.spread
                if (high) z > 1.5 else z < -1.5
            }
            return count >= 5
        }

        private val responses = mutableMapOf<LocalDate, Double?>()

        fun rawDay(day: LocalDate): RawDay {
            val load = loadState(day)
            val hasRecentLoad = (0L..41L).any { sessionsByDay.containsKey(day.minusDays(it)) }

            val deviations = buildMap {
                if (load != null && hasRecentLoad) put(RecoveryComponent.LOAD, loadPressure(load))
                hrvDeviation(day)?.let { put(RecoveryComponent.HRV, it) }
                worseZ(restingHr, day, SIGMA_RHR, higherIsWorse = true)?.let { put(RecoveryComponent.RESTING_HR, deviation(it)) }
                sleepDeviation(day)?.let { put(RecoveryComponent.SLEEP, it) }
                checkInDeviation(day)?.let { put(RecoveryComponent.CHECK_IN, it) }
                performanceDeviation(day)?.let { put(RecoveryComponent.PERFORMANCE, it) }
            }

            val baselineCount = listOf(
                baseline(hrv, day, SIGMA_LN_HRV),
                baseline(restingHr, day, SIGMA_RHR),
                baseline(checkInTotal, day, SIGMA_CHECK_IN),
            ).maxOfOrNull { it?.count ?: 0 } ?: 0
            val anyData = hasRecentLoad || hrv.isNotEmpty() || restingHr.isNotEmpty() || checkInTotal.isNotEmpty()
            val phase = when {
                !anyData -> RecoveryPhase.NO_DATA
                baselineCount >= FULL_BASELINE_VALUES -> RecoveryPhase.FULL
                baselineCount >= MIN_BASELINE_VALUES -> RecoveryPhase.PROVISIONAL
                else -> RecoveryPhase.LEARNING
            }

            val hasResponseAnchor = deviations.keys.any {
                it == RecoveryComponent.HRV || it == RecoveryComponent.RESTING_HR || it == RecoveryComponent.CHECK_IN
            }
            val scored = phase >= RecoveryPhase.PROVISIONAL &&
                RecoveryComponent.LOAD in deviations && hasResponseAnchor
            if (!scored) {
                responses[day] = null
                val illness = checkInByDay[day]?.sick == true || checkInByDay[day.minusDays(1)]?.sick == true
                return RawDay(
                    day, phase, null, 0.0, deviations, emptyMap(), null, 0,
                    if (illness) LoadResponsePattern.ILLNESS else LoadResponsePattern.BASELINE, load,
                )
            }

            val weightSum = deviations.keys.sumOf { it.weight }
            val score = (100 * deviations.entries.sumOf { (c, d) -> c.weight * d } / weightSum).roundToInt()
            val contributions = deviations.mapValues { (c, d) -> 100 * c.weight * d / weightSum }

            var confidence = deviations.keys.sumOf { c ->
                c.weight * when (c) {
                    RecoveryComponent.LOAD -> loadQuality(day)
                    RecoveryComponent.HRV -> quality(hrv, day)
                    RecoveryComponent.RESTING_HR -> quality(restingHr, day)
                    RecoveryComponent.SLEEP -> quality(sleepHours, day)
                    RecoveryComponent.CHECK_IN -> quality(checkInTotal, day)
                    RecoveryComponent.PERFORMANCE -> 1.0
                }
            }
            if (phase == RecoveryPhase.PROVISIONAL) confidence = minOf(confidence, HIGH_CONFIDENCE - 0.01)

            val responseParts = deviations.filterKeys { it.response }
            val response = if (responseParts.isEmpty()) {
                null
            } else {
                responseParts.entries.sumOf { (c, d) -> c.weight * d } / responseParts.keys.sumOf { it.weight }
            }
            responses[day] = response
            val offDomains = responseParts.values.count { it >= DOMAIN_OFF }

            val pattern = pattern(day, deviations, response, load)
            return RawDay(day, phase, score, confidence, deviations, contributions, response, offDomains, pattern, load)
        }

        private fun pattern(
            day: LocalDate,
            deviations: Map<RecoveryComponent, Double>,
            response: Double?,
            load: LoadState?,
        ): LoadResponsePattern {
            if (checkInByDay[day]?.sick == true || checkInByDay[day.minusDays(1)]?.sick == true) {
                return LoadResponsePattern.ILLNESS
            }
            val pressure = deviations[RecoveryComponent.LOAD] ?: 0.0
            val altered = (response ?: 0.0) >= 0.3

            // D: acute load down 20% or more on a week ago, response still off for 3 days.
            if (load != null) {
                val earlier = (7L..14L).mapNotNull { loadState(day.minusDays(it))?.atl }.maxOrNull() ?: 0.0
                val tapered = earlier > 0 && load.atl <= 0.8 * earlier
                val stillOff = (0L..2L).all { (responses[day.minusDays(it)] ?: 0.0) >= 0.3 }
                if (tapered && stillOff) return LoadResponsePattern.PERSISTING_AFTER_TAPER
            }

            // E: high HRV or low resting HR alongside other fatigue signs.
            val unusualRest = daysOff(hrv, SIGMA_LN_HRV, day, high = true) ||
                daysOff(restingHr, SIGMA_RHR, day, high = false)
            val otherSigns = (deviations[RecoveryComponent.CHECK_IN] ?: 0.0) >= DOMAIN_OFF ||
                (deviations[RecoveryComponent.PERFORMANCE] ?: 0.0) > 0.3 || pressure >= 0.4
            if (unusualRest && otherSigns) return LoadResponsePattern.ATYPICAL_HIGH_HRV

            return when {
                pressure >= 0.4 && altered -> LoadResponsePattern.LOAD_FATIGUE
                pressure >= 0.4 -> LoadResponsePattern.ABSORBING
                altered -> LoadResponsePattern.UNEXPLAINED
                else -> LoadResponsePattern.BASELINE
            }
        }

        /**
         * Level 3 by trend: over 14 days HRV keeps falling faster than one
         * smallest worthwhile change a week, the check-in keeps getting
         * worse, and efficiency is down.
         */
        fun worseningTrend(day: LocalDate, performance: Double?): Boolean {
            if ((performance ?: 0.0) <= 0.3) return false
            val base = baseline(hrv, day, SIGMA_LN_HRV) ?: return false
            val hrvPoints = (0L..13L).mapNotNull { back ->
                mean7(hrv, day.minusDays(back))?.let { -back.toDouble() to it }
            }
            val checkPoints = (0L..13L).mapNotNull { back ->
                checkInTotal[day.minusDays(back)]?.let { -back.toDouble() to it }
            }
            if (hrvPoints.size < 10 || checkPoints.size < 7) return false
            val hrvSlope = Stats.slope(hrvPoints) ?: return false
            val checkSlope = Stats.slope(checkPoints) ?: return false
            return hrvSlope < -(0.5 * base.spread) / 7 && checkSlope > 0
        }

        // -- chart bands ------------------------------------------------------

        fun hrvBand(today: LocalDate, days: Int): List<MarkerBand> = band(today, days, hrv, SIGMA_LN_HRV, ::exp)

        fun restingHrBand(today: LocalDate, days: Int): List<MarkerBand> = band(today, days, restingHr, SIGMA_RHR) { it }

        private fun band(
            today: LocalDate,
            days: Int,
            series: Map<LocalDate, Double>,
            floor: Double,
            display: (Double) -> Double,
        ): List<MarkerBand> {
            if (series.isEmpty()) return emptyList()
            return (days - 1 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                val base = baseline(series, day, floor)
                MarkerBand(
                    date = day,
                    value = series[day]?.let(display),
                    mean7 = mean7(series, day)?.let(display),
                    low = base?.let { display(it.center - 0.5 * it.spread) },
                    center = base?.let { display(it.center) },
                    high = base?.let { display(it.center + 0.5 * it.spread) },
                )
            }
        }
    }
}
