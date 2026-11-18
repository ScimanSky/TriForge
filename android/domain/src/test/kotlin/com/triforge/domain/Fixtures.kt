package com.triforge.domain

import java.time.Instant

fun activity(
    id: String,
    sport: Sport,
    start: String,
    source: ActivitySource = ActivitySource.GARMIN,
    durationSec: Long = 3600,
    distanceMeters: Double = 10_000.0,
    elevationGainMeters: Double? = null,
) = Activity(
    id = id,
    source = source,
    sport = sport,
    name = id,
    startTime = Instant.parse(start),
    durationSec = durationSec,
    distanceMeters = distanceMeters,
    elevationGainMeters = elevationGainMeters,
)
