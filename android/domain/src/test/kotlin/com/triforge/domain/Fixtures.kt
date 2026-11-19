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
    trainingLoad: Double? = null,
    avgHr: Int? = null,
    avgCadence: Int? = null,
    movingDurationSec: Long? = null,
) = Activity(
    id = id,
    source = source,
    sport = sport,
    name = id,
    startTime = Instant.parse(start),
    durationSec = durationSec,
    distanceMeters = distanceMeters,
    elevationGainMeters = elevationGainMeters,
    trainingLoad = trainingLoad,
    avgHr = avgHr,
    avgCadence = avgCadence,
    movingDurationSec = movingDurationSec,
)
