package com.triforge.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.triforge.domain.Activity
import com.triforge.domain.ActivityMapper
import com.triforge.domain.withoutDuplicates
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ActivityRepository(private val firestore: FirebaseFirestore) {

    private fun collection(uid: String) =
        firestore.collection("users").document(uid).collection("activities")

    /**
     * The athlete's activities since [since], newest first, updated live.
     *
     * Firestore keeps a local cache, so the list is available offline and
     * refreshes by itself when the Garmin service writes new activities.
     */
    fun activities(uid: String, since: Instant): Flow<List<Activity>> = callbackFlow {
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
}
