package com.triforge.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FitnessModelTest {

    private val rome = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 11, 18)

    private fun dailyRuns(days: Int, load: Double) = (0 until days).map { back ->
        val date = today.minusDays(back.toLong())
        activity("run$back", Sport.RUN, "${date}T08:00:00Z", trainingLoad = load)
    }

    @Test
    fun `no training load means nothing to draw`() {
        val withoutLoad = listOf(activity("a", Sport.RUN, "2026-11-17T08:00:00Z"))

        assertEquals(emptyList(), FitnessModel.series(withoutLoad, today, rome))
    }

    @Test
    fun `series covers the requested days and ends today`() {
        val series = FitnessModel.series(dailyRuns(3, 100.0), today, rome)

        assertEquals(42, series.size)
        assertEquals(today.minusDays(41), series.first().date)
        assertEquals(today, series.last().date)
    }

    @Test
    fun `a steady load brings fitness and fatigue towards that load`() {
        val series = FitnessModel.series(dailyRuns(300, 100.0), today, rome)

        assertEquals(100.0, series.last().fitness, 0.5)
        assertEquals(100.0, series.last().fatigue, 0.5)
        assertEquals(0.0, series.last().form, 0.5)
    }

    @Test
    fun `fatigue reacts faster than fitness`() {
        // One week of training after nothing: fatigue has climbed further.
        val last = FitnessModel.series(dailyRuns(7, 100.0), today, rome).last()

        assertTrue(last.fatigue > last.fitness)
        assertTrue(last.form < 0)
        assertEquals(100 * (1 - Math.pow(41.0 / 42, 7.0)), last.fitness, 0.001)
        assertEquals(100 * (1 - Math.pow(6.0 / 7, 7.0)), last.fatigue, 0.001)
    }

    @Test
    fun `rest lets fatigue fall below fitness`() {
        // Six weeks of training, then ten days off.
        val block = (10 until 52).map { back ->
            activity("r$back", Sport.RUN, "${today.minusDays(back.toLong())}T08:00:00Z", trainingLoad = 100.0)
        }

        val last = FitnessModel.series(block, today, rome).last()

        assertTrue(last.form > 0)
    }

    @Test
    fun `sessions on the same day add up`() {
        val double = listOf(
            activity("swim", Sport.SWIM, "${today}T06:00:00Z", trainingLoad = 40.0),
            activity("run", Sport.RUN, "${today}T17:00:00Z", trainingLoad = 60.0),
        )
        val single = listOf(activity("run", Sport.RUN, "${today}T17:00:00Z", trainingLoad = 100.0))

        assertEquals(
            FitnessModel.series(single, today, rome).last(),
            FitnessModel.series(double, today, rome).last(),
        )
    }

    @Test
    fun `load before the shown window still counts`() {
        val old = listOf(activity("old", Sport.RUN, "${today.minusDays(60)}T08:00:00Z", trainingLoad = 420.0))

        val series = FitnessModel.series(old, today, rome)

        assertEquals(42, series.size)
        assertTrue(series.first().fitness > 0)
    }
}
