package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecoveryBalanceTest {

    private val rome = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 11, 18)
    private val profile = AthleteProfile(ftpWatts = 250, thresholdPaceSecPerKm = 270, cssSecPer100m = 95)

    /** One hour easy run every day for [days] days, plus a weekly long ride. */
    private fun training(days: Int, tssScale: (Int) -> Double = { 1.0 }): List<Activity> =
        (0 until days).flatMap { back ->
            val date = today.minusDays(back.toLong())
            val scale = tssScale(back)
            buildList {
                add(
                    activity(
                        "run$back", Sport.RUN, "${date}T07:00:00Z",
                        durationSec = (3600 * scale).toLong(), distanceMeters = 12_000.0 * scale, avgHr = 140,
                    ),
                )
                if (back % 7 == 0) {
                    add(
                        Activity(
                            id = "bike$back", source = ActivitySource.GARMIN, sport = Sport.BIKE, name = "",
                            startTime = java.time.Instant.parse("${date}T15:00:00Z"),
                            durationSec = (7200 * scale).toLong(), distanceMeters = 60_000.0, normPowerWatts = 180,
                        ),
                    )
                }
            }
        }

    /**
     * Wellness with small day-to-day noise. [shift] moves a day away from
     * normal: HRV down, resting HR up, sleep shorter, by that many units.
     */
    private fun wellness(days: Int, seed: Int = 7, shift: (Int) -> Double = { 0.0 }): List<DailyWellness> {
        val random = Random(seed)
        return (0 until days).map { back ->
            val s = shift(back)
            DailyWellness(
                date = today.minusDays(back.toLong()),
                hrvRmssd = 60.0 * (1 + random.nextDouble(-0.06, 0.06)) * (1 - 0.12 * s),
                restingHr = (46 + random.nextInt(-1, 2) + 3 * s).toInt(),
                sleepSec = ((7.6 + random.nextDouble(-0.3, 0.3) - 1.0 * s) * 3600).toLong(),
            )
        }
    }

    private fun checkIns(days: Int, total: (Int) -> Int = { 10 }, sick: (Int) -> Boolean = { false }) =
        (0 until days).map { back ->
            val t = total(back)
            val item = (t / 5).coerceIn(1, 7)
            val last = (t - 4 * item).coerceIn(1, 7)
            CheckIn(today.minusDays(back.toLong()), item, item, item, item, last, sick(back))
        }

    private fun report(
        activities: List<Activity>,
        wellness: List<DailyWellness>,
        checkIns: List<CheckIn> = emptyList(),
    ) = RecoveryBalance.compute(activities, wellness, checkIns, profile, today, rome)

    @Test
    fun `deviation is zero inside the smallest worthwhile change and saturates at two and a half spreads`() {
        assertEquals(0.0, RecoveryBalance.deviation(0.4))
        assertEquals(0.5, RecoveryBalance.deviation(1.5), 1e-9)
        assertEquals(1.0, RecoveryBalance.deviation(4.0))
    }

    @Test
    fun `no data gives no score`() {
        val today = report(emptyList(), emptyList()).today

        assertEquals(RecoveryPhase.NO_DATA, today.phase)
        assertNull(today.score)
        assertEquals(0, today.level)
    }

    @Test
    fun `fewer than two weeks of recovery data is still learning`() {
        val result = report(training(60), wellness(10)).today

        assertEquals(RecoveryPhase.LEARNING, result.phase)
        assertNull(result.score)
        assertTrue(RecoveryComponent.LOAD in result.deviations)
    }

    @Test
    fun `steady training and normal recovery is green with high confidence`() {
        val result = report(training(120), wellness(90), checkIns(90)).today

        assertEquals(RecoveryPhase.FULL, result.phase)
        assertTrue(result.score!! < 30, "score ${result.score}")
        assertEquals(RecoveryLight.GREEN, result.light)
        assertEquals(0, result.level)
        assertEquals(ConfidenceLevel.HIGH, result.confidenceLevel)
        assertEquals(emptySet(), result.missing)
    }

    @Test
    fun `contributions add up to the score`() {
        val result = report(training(120), wellness(90) { if (it < 7) 1.5 else 0.0 }, checkIns(90)).today

        assertEquals(result.score!!.toDouble(), result.contributions.values.sum(), 0.5)
    }

    @Test
    fun `recovery off for a week without extra load reads as unexplained and raises a notice`() {
        val bad = { back: Int -> if (back < 9) 2.5 else 0.0 }
        val result = report(
            training(120),
            wellness(90, shift = bad),
            checkIns(90, total = { if (it < 9) 28 else 10 }),
        )

        val today = result.today
        assertEquals(LoadResponsePattern.UNEXPLAINED, today.pattern)
        assertTrue(today.score!! >= 55, "score ${today.score}")
        assertTrue(today.level >= 2, "level ${today.level}")
        assertTrue(today.deviations.getValue(RecoveryComponent.HRV) > 0.5)
        assertTrue(today.deviations.getValue(RecoveryComponent.RESTING_HR) > 0.5)
    }

    @Test
    fun `a single bad night does not raise a notice`() {
        val result = report(training(120), wellness(90) { if (it == 0) 3.0 else 0.0 }, checkIns(90)).today

        assertEquals(0, result.level)
    }

    @Test
    fun `a big load block with normal recovery reads as absorbing`() {
        // Last 10 days at about double the usual volume.
        val result = report(training(120) { if (it < 10) 2.2 else 1.0 }, wellness(90), checkIns(90)).today

        assertEquals(LoadResponsePattern.ABSORBING, result.pattern)
        assertTrue(result.deviations.getValue(RecoveryComponent.LOAD) >= 0.4)
        assertEquals(0, result.level)
    }

    @Test
    fun `feeling ill pauses the index`() {
        val result = report(
            training(120),
            wellness(90) { if (it < 9) 2.5 else 0.0 },
            checkIns(90, total = { if (it < 9) 28 else 10 }, sick = { it == 1 }),
        ).today

        assertEquals(LoadResponsePattern.ILLNESS, result.pattern)
        assertEquals(0, result.level)
    }

    @Test
    fun `without check-ins the weights are rescaled and confidence drops`() {
        val result = report(training(120), wellness(90)).today

        assertTrue(RecoveryComponent.CHECK_IN in result.missing)
        assertTrue(result.score != null)
        assertTrue(result.confidence < 0.80)
    }

    @Test
    fun `the level steps down only after three days back to normal`() {
        // Bad from day 16 to day 3 back; the last 3 days are normal again.
        val bad = { back: Int -> if (back in 3..16) 2.5 else 0.0 }
        val checks = checkIns(90, total = { if (it in 3..16) 28 else 10 })
        val history = report(training(120), wellness(90, shift = bad), checks).history

        val peak = history.first { it.date == today.minusDays(3) }.level
        assertTrue(peak >= 2, "peak $peak")
        val afterTwo = history.first { it.date == today.minusDays(1) }
        assertEquals(peak, afterTwo.level)
    }

    @Test
    fun `history covers the requested days and ends today`() {
        val result = report(training(120), wellness(90), checkIns(90))

        assertEquals(42, result.history.size)
        assertEquals(today, result.history.last().date)
        assertEquals(42, result.hrvBand.size)
        assertTrue(result.hrvBand.last().low!! < result.hrvBand.last().high!!)
    }

    @Test
    fun `a sudden jump in running load is flagged separately`() {
        val result = report(training(120) { if (it < 7) 2.0 else 1.0 }, wellness(90), checkIns(90))

        assertTrue(result.runLoadJump)
        assertTrue(!report(training(120), wellness(90)).runLoadJump)
    }
}
