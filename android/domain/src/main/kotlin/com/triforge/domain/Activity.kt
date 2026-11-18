package com.triforge.domain

import java.time.Instant

enum class Sport { SWIM, BIKE, RUN, OTHER }

enum class ActivitySource { GARMIN, HEALTH_CONNECT }

/**
 * One training session, whatever source it came from.
 *
 * Everything after [distanceMeters] is optional: Garmin fills most of it,
 * Health Connect only a few fields.
 */
data class Activity(
    val id: String,
    val source: ActivitySource,
    val sport: Sport,
    val name: String,
    val startTime: Instant,
    val durationSec: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val calories: Int? = null,
    val trainingLoad: Double? = null,
    val avgPowerWatts: Int? = null,
    val normPowerWatts: Int? = null,
    val avgCadence: Int? = null,
    val aerobicTe: Double? = null,
    val anaerobicTe: Double? = null,
    val vo2max: Double? = null,
    val openWater: Boolean? = null,
    val poolLengthMeters: Double? = null,
    val swolf: Int? = null,
    val strokes: Int? = null,
    val lengths: Int? = null,
)
