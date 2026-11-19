package com.triforge.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.triforge.domain.AthleteProfile
import com.triforge.domain.ProfileMapper
import com.triforge.domain.coach.AvailabilityMapper
import com.triforge.domain.coach.TrainingAvailability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** The athlete's own profile: goal, body and thresholds, in `users/{uid}`. */
class ProfileRepository(private val firestore: FirebaseFirestore) {

    private fun document(uid: String) = firestore.collection("users").document(uid)

    fun profile(uid: String): Flow<AthleteProfile> = callbackFlow {
        val registration = document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(ProfileMapper.fromFields(snapshot?.data.orEmpty()))
        }
        awaitClose { registration.remove() }
    }

    /** The athlete's days and hours for each sport (board 8), in the same document. */
    fun availability(uid: String): Flow<TrainingAvailability> = callbackFlow {
        val registration = document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(AvailabilityMapper.fromFields(snapshot?.data.orEmpty()))
        }
        awaitClose { registration.remove() }
    }

    /** Saves the race, the hours and the days together, as the "Giorni, orari e gara" page does. */
    suspend fun saveWeek(uid: String, profile: AthleteProfile, availability: TrainingAvailability) {
        val fields = ProfileMapper.toFields(profile) + AvailabilityMapper.toFields(availability)
        val write = document(uid).set(fields, SetOptions.merge())
        withTimeoutOrNull(SERVER_WAIT_MS) { write.await() }
    }

    /**
     * Saves the profile next to the fields the document already has.
     *
     * Offline, Firestore applies the write locally at once and only confirms
     * it when the server answers. Waiting for that would freeze the form, so
     * after a short wait the write is left to finish in the background.
     */
    suspend fun save(uid: String, profile: AthleteProfile) {
        val write = document(uid).set(ProfileMapper.toFields(profile), SetOptions.merge())
        withTimeoutOrNull(SERVER_WAIT_MS) { write.await() }
    }

    private companion object {
        const val SERVER_WAIT_MS = 4_000L
    }
}
