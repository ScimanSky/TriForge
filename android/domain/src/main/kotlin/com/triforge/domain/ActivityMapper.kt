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
        val type = fields["type"] as? String ?: ""
        val sport = sportOf(fields["sport"], type)
        return Activity(
            id = id,
            source = source,
            sport = sport,
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
            strokeDistanceMeters = number(fields["strokeDistanceMeters"])?.toDouble()?.takeIf { it > 0 },
            laps = laps(fields["laps"]),
            type = type,
            kind = if (sport == Sport.OTHER) OtherKind.of(fields["kind"]) ?: ActivityTypes.otherKind(type) else OtherKind.GENERIC,
            sets = number(fields["sets"])?.toInt()?.takeIf { it > 0 },
            reps = number(fields["reps"])?.toInt()?.takeIf { it > 0 },
            exercises = exercises(fields["exercises"]),
        )
    }

    /** The `exercises` list of a gym document; malformed entries are skipped. */
    fun exercises(value: Any?): List<ExerciseSummary> = (value as? List<*>).orEmpty().mapNotNull { item ->
        val exercise = item as? Map<*, *> ?: return@mapNotNull null
        val category = (exercise["category"] as? String)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val sets = number(exercise["sets"])?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
        ExerciseSummary(
            category = category,
            sets = sets,
            reps = number(exercise["reps"])?.toInt()?.takeIf { it > 0 },
            volumeKg = number(exercise["volumeKg"])?.toDouble()?.takeIf { it > 0 },
        )
    }

    /** The `laps` list of a swim document; malformed entries are skipped. */
    fun laps(value: Any?): List<SwimLap> = (value as? List<*>).orEmpty().mapNotNull { item ->
        val lap = item as? Map<*, *> ?: return@mapNotNull null
        val duration = number(lap["durationSec"])?.toDouble()?.takeIf { it > 0 } ?: return@mapNotNull null
        SwimLap(
            distanceMeters = number(lap["distanceMeters"])?.toDouble()?.coerceAtLeast(0.0) ?: 0.0,
            durationSec = duration,
            strokes = number(lap["strokes"])?.toInt()?.takeIf { it > 0 },
            swolf = number(lap["swolf"])?.toInt()?.takeIf { it > 0 },
            avgHr = number(lap["avgHr"])?.toInt()?.takeIf { it > 0 },
            stroke = lap["stroke"] as? String,
            lengths = (lap["lengths"] as? List<*>).orEmpty().mapNotNull { entry ->
                val length = entry as? Map<*, *> ?: return@mapNotNull null
                val seconds = number(length["durationSec"])?.toDouble()?.takeIf { it > 0 } ?: return@mapNotNull null
                SwimLength(
                    distanceMeters = number(length["distanceMeters"])?.toDouble()?.coerceAtLeast(0.0) ?: 0.0,
                    durationSec = seconds,
                    strokes = number(length["strokes"])?.toInt()?.takeIf { it > 0 },
                    swolf = number(length["swolf"])?.toInt()?.takeIf { it > 0 },
                    stroke = length["stroke"] as? String,
                )
            },
        )
    }

    /**
     * The sport of a document. Gym sessions synced before the service knew
     * them were stored as "other" with their type: the type still tells.
     */
    fun sportOf(value: Any?, type: String? = null): Sport = when (value) {
        "swim" -> Sport.SWIM
        "bike" -> Sport.BIKE
        "run" -> Sport.RUN
        "strength" -> Sport.STRENGTH
        else -> if (ActivityTypes.isStrength(type)) Sport.STRENGTH else Sport.OTHER
    }

    fun sportKey(sport: Sport): String = when (sport) {
        Sport.SWIM -> "swim"
        Sport.BIKE -> "bike"
        Sport.RUN -> "run"
        Sport.STRENGTH -> "strength"
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
