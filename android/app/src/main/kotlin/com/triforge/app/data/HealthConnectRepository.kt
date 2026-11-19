package com.triforge.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.math.roundToInt
import kotlin.reflect.KClass

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
    private val wellness: WellnessRepository,
) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
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

    /**
     * Copies resting heart rate, HRV and sleep since [since] into the
     * athlete's daily wellness, one `hc_{date}` document per day. Garmin does
     * not share HRV with Health Connect, so for Garmin users HRV comes from
     * the Garmin service; other watches may provide it here.
     */
    suspend fun importWellness(uid: String, since: Instant, zone: ZoneId = ZoneId.systemDefault()): Int {
        val days = mutableMapOf<LocalDate, MutableMap<String, Any?>>()
        fun day(date: LocalDate) = days.getOrPut(date) {
            mutableMapOf("source" to "health_connect", "date" to date.toString())
        }

        // Morning readings only: a daytime HRV spot check is not comparable.
        readAll(HeartRateVariabilityRmssdRecord::class, since)
            .filter { it.time.atZone(zone).hour < 12 }
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .forEach { (date, list) -> day(date)["hrvRmssd"] = list.map { it.heartRateVariabilityMillis }.average() }

        readAll(RestingHeartRateRecord::class, since)
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .forEach { (date, list) -> day(date)["restingHr"] = list.minOf { it.beatsPerMinute } }

        // A night is filed under the morning it ends, as Garmin does.
        readAll(SleepSessionRecord::class, since)
            .groupBy { it.endTime.atZone(zone).toLocalDate() }
            .forEach { (date, sessions) ->
                val asleep = sessions.sumOf { session ->
                    val total = Duration.between(session.startTime, session.endTime).seconds
                    val awake = session.stages.filter { it.stage in AWAKE_STAGES }
                        .sumOf { Duration.between(it.startTime, it.endTime).seconds }
                    (total - awake).coerceAtLeast(0)
                }
                if (asleep > 0) day(date)["sleepSec"] = asleep
            }

        val documents = days.filterValues { it.size > 2 }.mapKeys { (date, _) -> "hc_$date" }
            .mapValues { (_, fields) -> fields + ("syncedAt" to FieldValue.serverTimestamp()) }
        wellness.upsertHealthConnect(uid, documents)
        return documents.size
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, since: Instant): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(recordType = type, timeRangeFilter = TimeRangeFilter.after(since), pageToken = pageToken),
            )
            records += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return records
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
        private val AWAKE_STAGES = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        )

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
