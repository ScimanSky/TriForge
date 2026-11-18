package com.triforge.domain

import java.time.Instant

enum class Sport { SWIM, BIKE, RUN, OTHER }

enum class ActivitySource { GARMIN, HEALTH_CONNECT }

/** One training session, whatever source it came from. */
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
    val calories: Int? = null,
    val trainingLoad: Double? = null,
)
