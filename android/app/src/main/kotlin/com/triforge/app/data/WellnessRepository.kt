package com.triforge.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.triforge.domain.CheckIn
import com.triforge.domain.CheckInMapper
import com.triforge.domain.DailyWellness
import com.triforge.domain.WellnessMapper
import java.time.LocalDate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Daily recovery data and morning check-ins.
 *
 * - `users/{uid}/wellness/{source}_{date}`: HRV, resting HR and sleep, one
 *   document per source and day. The Garmin service writes `garmin_*`, the
 *   Health Connect import writes `hc_*`.
 * - `users/{uid}/checkIns/{date}`: the athlete's own 30-second check-in.
 */
class WellnessRepository(private val firestore: FirebaseFirestore) {

    private fun user(uid: String) = firestore.collection("users").document(uid)

    /** Merged per day (Garmin first), oldest first, updated live. */
    fun wellness(uid: String, since: LocalDate): Flow<List<DailyWellness>> = callbackFlow {
        val registration = user(uid).collection("wellness")
            .whereGreaterThanOrEqualTo("date", since.toString())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val records = snapshot?.documents.orEmpty().mapNotNull { WellnessMapper.fromFields(it.data.orEmpty()) }
                trySend(WellnessMapper.merge(records))
            }
        awaitClose { registration.remove() }
    }

    fun checkIns(uid: String, since: LocalDate): Flow<List<CheckIn>> = callbackFlow {
        val registration = user(uid).collection("checkIns")
            .whereGreaterThanOrEqualTo("date", since.toString())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { CheckInMapper.fromFields(it.data.orEmpty()) }
                        .sortedBy { it.date },
                )
            }
        awaitClose { registration.remove() }
    }

    /** One check-in per day: saving again the same day replaces it. */
    suspend fun saveCheckIn(uid: String, checkIn: CheckIn) {
        val write = user(uid).collection("checkIns")
            .document(CheckInMapper.documentId(checkIn.date))
            .set(CheckInMapper.toFields(checkIn))
        // Offline the write lands locally at once; do not freeze on the server.
        withTimeoutOrNull(SERVER_WAIT_MS) { write.await() }
    }

    /** Writes Health Connect days, keyed by document id. */
    suspend fun upsertHealthConnect(uid: String, documents: Map<String, Map<String, Any?>>) {
        documents.entries.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (id, fields) ->
                batch.set(user(uid).collection("wellness").document(id), fields, SetOptions.merge())
            }
            batch.commit().await()
        }
    }

    private companion object {
        const val SERVER_WAIT_MS = 4_000L
    }
}
