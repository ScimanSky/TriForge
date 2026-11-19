package com.triforge.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FitnessTrendTest {

    private fun point(day: Int, fitness: Double, fatigue: Double, load: Double = 0.0, formPct: Double = 0.0) =
        FitnessPoint(LocalDate.of(2026, 11, 1).plusDays(day.toLong()), fitness, fatigue, load, formPct)

    @Test
    fun `form zones`() {
        assertEquals(FormZone.PRODUCTIVE, FormZone.of(-0.25))
        assertEquals(FormZone.OVERLOAD, FormZone.of(-0.33))
        assertEquals(FormZone.NEUTRAL, FormZone.of(0.0))
        assertEquals(FormZone.NEUTRAL, FormZone.of(-0.10))
        assertEquals(FormZone.FRESH, FormZone.of(0.17))
        assertEquals(FormZone.DETRAINING, FormZone.of(0.33))
    }

    @Test
    fun `trend uses the recovery balance load, one model for the whole screen`() {
        val rome = java.time.ZoneId.of("Europe/Rome")
        val today = LocalDate.of(2026, 11, 18)
        // Daily one-hour runs with no Garmin load: the old chart counted them as zero.
        val runs = (0 until 60).map { back ->
            activity("r$back", Sport.RUN, "${today.minusDays(back.toLong())}T07:00:00Z", durationSec = 3600)
        }
        val report = RecoveryBalance.compute(runs, emptyList(), emptyList(), AthleteProfile(), today, rome)

        val trend = FitnessTrend.from(report.history)

        assertEquals(42, trend.points.size)
        // An hour at the typical run intensity 0.8 is 64 TSS a day.
        assertEquals(64.0, trend.latest!!.dailyLoad, 1e-6)
        assertEquals(report.today.load!!.formPct, trend.latest!!.formPct, 1e-12)
        assert(trend.latest!!.fitness > 40)
    }

    @Test
    fun `fitness change and recent load`() {
        val trend = FitnessTrend((0..10).map { point(it, 50.0 + it, 40.0, load = 10.0) })

        assertEquals(7.0, trend.fitnessChange(7)!!, 1e-9)
        assertNull(trend.fitnessChange(11))
        assertEquals(70.0, trend.loadOfLast(7), 1e-9)
    }
}
