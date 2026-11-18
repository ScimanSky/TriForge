package com.triforge.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.triforge.domain.ActivityMapper
import com.triforge.domain.Sport
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlin.math.roundToInt

enum class HealthConnectAvailability { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

/**
 * Reads workouts from Health Connect, the official on-device store that
 * Garmin Connect and other apps write to.
 *
 * It is the second data source: it keeps the app useful if the unofficial
 * Garmin login stops working, and covers athletes on other watches.
 */
class HealthConnectRepository(
    private val context: Context,
    private val activities: ActivityRepository,
) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NEEDS_UPDATE
        else -> HealthConnectAvailability.UNAVAILABLE
    }

    suspend fun hasPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    /**
     * Copies swim, bike and run sessions since [since] into the athlete's
     * activities. Returns how many were written. Safe to repeat: each session
     * keeps the same document id.
     */
    suspend fun importWorkouts(uid: String, since: Instant): Int {
        val documents = mutableMapOf<String, Map<String, Any?>>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(since),
                    pageToken = pageToken,
                ),
            )
            for (session in response.records) {
                val sport = sportFor(session.exerciseType) ?: continue
                documents["hc_${session.metadata.id}"] = document(session, sport)
            }
            pageToken = response.pageToken
        } while (pageToken != null)

        activities.upsertHealthConnect(uid, documents)
        return documents.size
    }

    private suspend fun document(session: ExerciseSessionRecord, sport: Sport): Map<String, Any?> {
        // Distance, heart rate and calories are separate records. Restricting
        // the aggregate to the app that wrote the session avoids counting the
        // same workout twice when two apps recorded it.
        val totals = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    DistanceRecord.DISTANCE_TOTAL,
                    HeartRateRecord.BPM_AVG,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                dataOriginFilter = setOf(session.metadata.dataOrigin),
            ),
        )
        return mapOf(
            "source" to "health_connect",
            "externalId" to session.metadata.id,
            "sport" to ActivityMapper.sportKey(sport),
            "type" to "health_connect_${session.exerciseType}",
            "name" to session.title.orEmpty(),
            "startTime" to Timestamp(Date.from(session.startTime)),
            "durationSec" to Duration.between(session.startTime, session.endTime).seconds,
            "distanceMeters" to (totals[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0),
            "avgHr" to totals[HeartRateRecord.BPM_AVG]?.toInt(),
            "calories" to totals[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.roundToInt(),
            "dataOrigin" to session.metadata.dataOrigin.packageName,
            "syncedAt" to FieldValue.serverTimestamp(),
        )
    }

    companion object {
        /** Null for anything that is not one of the three disciplines. */
        fun sportFor(exerciseType: Int): Sport? = when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            -> Sport.SWIM

            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            -> Sport.BIKE

            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
            -> Sport.RUN

            else -> null
        }
    }
}
