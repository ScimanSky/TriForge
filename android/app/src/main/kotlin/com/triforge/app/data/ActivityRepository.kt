package com.triforge.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.triforge.domain.Activity
import com.triforge.domain.ActivityMapper
import com.triforge.domain.SwimGearMarks
import com.triforge.domain.withoutDuplicates
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The athlete's activities, and what the athlete adds to them.
 *
 * - `users/{uid}/activities/{id}`: one document per activity. The Garmin
 *   ones are written by the service and the app may not change them.
 * - `users/{uid}/coaching/swim`, field `gear`: the equipment the athlete
 *   marked on each swim, by activity id (see [ActivityMapper.gearFromFields]).
 *   It lives on the swim coach's document because that is who it is said
 *   to, and because the app may write there.
 */
class ActivityRepository(private val firestore: FirebaseFirestore) {

    private fun collection(uid: String) =
        firestore.collection("users").document(uid).collection("activities")

    private fun swimDesk(uid: String) =
        firestore.collection("users").document(uid).collection("coaching").document("swim")

    /**
     * The athlete's activities since [since], newest first, updated live,
     * each swim with the equipment the athlete marked on it.
     *
     * Firestore keeps a local cache, so the list is available offline and
     * refreshes by itself when the Garmin service writes new activities.
     */
    fun activities(uid: String, since: Instant): Flow<List<Activity>> =
        combine(recorded(uid, since), swimGear(uid)) { activities, gear ->
            if (gear.isEmpty()) activities else activities.map { a -> gear[a.id]?.let { a.copy(gear = it) } ?: a }
        }

    /**
     * The equipment marked on the swims, by activity id. The marks come from
     * the cache in an instant; should they not (offline, on a phone that
     * never read them), the activities do not wait for them longer than
     * [GEAR_WAIT_MS].
     */
    private fun swimGear(uid: String): Flow<Map<String, SwimGearMarks>> = channelFlow {
        var arrived = false
        launch {
            delay(GEAR_WAIT_MS)
            if (!arrived) send(emptyMap())
        }
        storedSwimGear(uid).collect {
            arrived = true
            send(it)
        }
    }

    private fun storedSwimGear(uid: String): Flow<Map<String, SwimGearMarks>> = callbackFlow {
        val registration = swimDesk(uid).addSnapshotListener { snapshot, error ->
            // The marks are an extra: without them the activities are still shown.
            if (error != null) {
                trySend(emptyMap())
                return@addSnapshotListener
            }
            val stored = snapshot?.get(GEAR_FIELD) as? Map<*, *>
            trySend(
                stored.orEmpty().mapNotNull { (id, fields) ->
                    val marks = ActivityMapper.gearFromFields(fields) ?: return@mapNotNull null
                    (id as? String)?.let { it to marks }
                }.toMap(),
            )
        }
        awaitClose { registration.remove() }
    }

    /**
     * Stores the equipment the athlete marked on the swim [activityId];
     * [SwimGearMarks.NONE] is the answer "none". The whole entry of that
     * swim is replaced, so a mark taken off does not linger.
     */
    suspend fun setSwimGear(uid: String, activityId: String, marks: SwimGearMarks) {
        swimDesk(uid).set(
            mapOf(GEAR_FIELD to mapOf(activityId to ActivityMapper.gearToFields(marks))),
            SetOptions.mergeFieldPaths(listOf(FieldPath.of(GEAR_FIELD, activityId))),
        ).await()
    }

    private fun recorded(uid: String, since: Instant): Flow<List<Activity>> = callbackFlow {
        val registration = collection(uid)
            .whereGreaterThanOrEqualTo("startTime", Timestamp(Date.from(since)))
            .orderBy("startTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val activities = snapshot?.documents.orEmpty().mapNotNull { document ->
                    val start = (document.get("startTime") as? Timestamp)?.toDate()?.toInstant()
                    ActivityMapper.fromFields(document.id, document.data.orEmpty(), start)
                }
                trySend(activities.withoutDuplicates())
            }
        awaitClose { registration.remove() }
    }

    /** Writes Health Connect sessions, keyed by document id. */
    suspend fun upsertHealthConnect(uid: String, documents: Map<String, Map<String, Any?>>) {
        // A Firestore batch holds at most 500 writes.
        documents.entries.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (id, fields) ->
                batch.set(collection(uid).document(id), fields, SetOptions.merge())
            }
            batch.commit().await()
        }
    }

    private companion object {
        const val GEAR_FIELD = "gear"
        const val GEAR_WAIT_MS = 2_000L
    }
}
