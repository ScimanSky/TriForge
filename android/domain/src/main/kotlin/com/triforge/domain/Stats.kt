package com.triforge.domain

import kotlin.math.abs
import kotlin.math.sqrt

/** Small robust statistics used by the recovery model. */
internal object Stats {

    fun median(values: List<Double>): Double {
        require(values.isNotEmpty()) { "median of nothing" }
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** Median absolute deviation, scaled so it estimates the standard deviation. */
    fun scaledMad(values: List<Double>): Double {
        val center = median(values)
        return 1.4826 * median(values.map { abs(it - center) })
    }

    fun mean(values: List<Double>): Double = values.sum() / values.size

    /** Sample standard deviation; zero for fewer than two values. */
    fun sd(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val m = mean(values)
        return sqrt(values.sumOf { (it - m) * (it - m) } / (values.size - 1))
    }

    /** Least-squares slope of y against x. Null when x does not vary. */
    fun slope(points: List<Pair<Double, Double>>): Double? {
        if (points.size < 2) return null
        val mx = points.sumOf { it.first } / points.size
        val my = points.sumOf { it.second } / points.size
        val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
        if (sxx == 0.0) return null
        return points.sumOf { (it.first - mx) * (it.second - my) } / sxx
    }

    fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)
}
