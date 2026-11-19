package com.triforge.domain

import java.time.Instant

/**
 * Reads an activity from the fields of its Firestore document.
 *
 * The same document shape is written by the Garmin service and by the Health
 * Connect import, so this is the single place that interprets it.
 */
object ActivityMapper {

    /** Returns null when the document cannot be placed on a timeline. */
    fun fromFields(id: String, fields: Map<String, Any?>, startTime: Instant?): Activity? {
        if (startTime == null) return null
        val source = when (fields["source"]) {
            "garmin" -> ActivitySource.GARMIN
            "health_connect" -> ActivitySource.HEALTH_CONNECT
            else -> return null
        }
        return Activity(
            id = id,
            source = source,
            sport = sportOf(fields["sport"]),
            name = fields["name"] as? String ?: "",
            startTime = startTime,
            durationSec = number(fields["durationSec"])?.toLong()?.coerceAtLeast(0) ?: 0,
            distanceMeters = number(fields["distanceMeters"])?.toDouble()?.coerceAtLeast(0.0) ?: 0.0,
            movingDurationSec = number(fields["movingDurationSec"])?.toLong()?.takeIf { it > 0 },
            elevationGainMeters = number(fields["elevationGainMeters"])?.toDouble(),
            avgHr = number(fields["avgHr"])?.toInt(),
            maxHr = number(fields["maxHr"])?.toInt(),
            calories = number(fields["calories"])?.toInt(),
            trainingLoad = number(fields["trainingLoad"])?.toDouble(),
            avgPowerWatts = number(fields["avgPowerWatts"])?.toInt(),
            normPowerWatts = number(fields["normPowerWatts"])?.toInt(),
            avgCadence = number(fields["avgCadence"])?.toInt(),
            aerobicTe = number(fields["aerobicTe"])?.toDouble(),
            anaerobicTe = number(fields["anaerobicTe"])?.toDouble(),
            vo2max = number(fields["vo2max"])?.toDouble(),
            openWater = fields["openWater"] as? Boolean,
            poolLengthMeters = poolLength(number(fields["poolLengthMeters"])?.toDouble()),
            swolf = number(fields["swolf"])?.toInt(),
            strokes = number(fields["strokes"])?.toInt(),
            lengths = number(fields["lengths"])?.toInt(),
        )
    }

    fun sportOf(value: Any?): Sport = when (value) {
        "swim" -> Sport.SWIM
        "bike" -> Sport.BIKE
        "run" -> Sport.RUN
        else -> Sport.OTHER
    }

    fun sportKey(sport: Sport): String = when (sport) {
        Sport.SWIM -> "swim"
        Sport.BIKE -> "bike"
        Sport.RUN -> "run"
        Sport.OTHER -> "other"
    }

    /**
     * Garmin reports the pool length in centimetres, and documents synced
     * before the service converted it still hold that number. No pool is
     * [LONGEST_POOL_METERS] long, so anything above it is centimetres.
     */
    fun poolLength(stored: Double?): Double? = when {
        stored == null || stored <= 0 -> null
        stored > LONGEST_POOL_METERS -> stored / 100.0
        else -> stored
    }

    private const val LONGEST_POOL_METERS = 200.0

    // Firestore hands numbers back as Long or Double depending on the value.
    private fun number(value: Any?): Number? = value as? Number
}
