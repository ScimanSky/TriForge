package com.triforge.app.data

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.triforge.domain.AccountInfo
import com.triforge.domain.AccountMapper
import com.triforge.domain.ConsentType
import com.triforge.domain.LegalTexts
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** How much the athlete has stored, for the privacy page. Null when it could not be counted. */
data class StoredDataCounts(val activities: Long?, val wellnessDays: Long?, val checkIns: Long?)

/**
 * The account side of `users/{uid}`: name, photo, year of birth, consents
 * and the end of the first-launch flow. Also the privacy operations that
 * need the service: withdrawing the health consent and deleting the account.
 */
class AccountRepository(
    private val firestore: FirebaseFirestore,
    private val auth: AuthRepository,
    private val service: CloudService,
) {

    private fun user(uid: String) = firestore.collection("users").document(uid)

    private fun avatarDoc(uid: String) = user(uid).collection("media").document("avatar")

    fun account(uid: String): Flow<AccountInfo> = callbackFlow {
        val registration = user(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(AccountMapper.fromFields(snapshot?.data.orEmpty().withInstants()))
        }
        awaitClose { registration.remove() }
    }

    /** The profile photo as JPEG bytes, or null when there is none. */
    fun avatar(uid: String): Flow<ByteArray?> = callbackFlow {
        val registration = avatarDoc(uid).addSnapshotListener { snapshot, _ ->
            trySend((snapshot?.get("jpeg") as? Blob)?.toBytes())
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveAvatar(uid: String, jpeg: ByteArray) {
        write(avatarDoc(uid).set(mapOf("jpeg" to Blob.fromBytes(jpeg), "updatedAt" to FieldValue.serverTimestamp())))
    }

    suspend fun removeAvatar(uid: String) {
        write(avatarDoc(uid).delete())
    }

    suspend fun saveIdentity(uid: String, name: String, bio: String) {
        val cleanName = AccountMapper.cleanName(name)
        write(
            user(uid).set(
                mapOf("displayName" to cleanName, "bio" to AccountMapper.cleanBio(bio)),
                SetOptions.merge(),
            ),
        )
        // The login keeps its own copy of the name; a failure there is not worth an error.
        runCatching { auth.updateDisplayName(cleanName) }
    }

    suspend fun saveBirthYear(uid: String, year: Int) {
        write(user(uid).set(mapOf("birthYear" to year), SetOptions.merge()))
    }

    /**
     * Records the athlete's answers: the current state in the user document,
     * and one entry per answer in the append-only consent log, in one batch.
     */
    suspend fun saveConsents(uid: String, answers: Map<ConsentType, Boolean>) {
        if (answers.isEmpty()) return
        val batch = firestore.batch()
        val current = answers.entries.associate { (type, granted) ->
            type.key to mapOf(
                "granted" to granted,
                "version" to LegalTexts.VERSION,
                "at" to FieldValue.serverTimestamp(),
            )
        }
        batch.set(user(uid), mapOf("consents" to current), SetOptions.merge())
        answers.forEach { (type, granted) ->
            batch.set(
                user(uid).collection("consentLog").document(),
                mapOf(
                    "type" to type.key,
                    "granted" to granted,
                    "version" to LegalTexts.VERSION,
                    "at" to FieldValue.serverTimestamp(),
                ),
            )
        }
        write(batch.commit())
    }

    suspend fun completeOnboarding(uid: String) {
        write(user(uid).set(mapOf("onboardingCompletedAt" to FieldValue.serverTimestamp()), SetOptions.merge()))
    }

    suspend fun counts(uid: String): StoredDataCounts {
        suspend fun count(name: String): Long? = runCatching {
            user(uid).collection(name).count().get(AggregateSource.SERVER).await().count
        }.getOrNull()
        return StoredDataCounts(count("activities"), count("wellness"), count("checkIns"))
    }

    /**
     * Withdraws the health consent. The service first disconnects Garmin and
     * erases activities, recovery data and check-ins; only then is the
     * refusal recorded, so a failure leaves the athlete where they were.
     */
    suspend fun withdrawHealthConsent(uid: String) {
        service.call("DELETE", "/account/health-data")
        saveConsents(uid, mapOf(ConsentType.HEALTH to false, ConsentType.AI_COACH to false))
    }

    /**
     * Deletes everything: the service erases the data and the login. If it
     * could not delete the login, the phone tries. The caller signs out and
     * clears what is cached on the phone.
     */
    suspend fun deleteAccount() {
        val result = service.call("DELETE", "/account")
        if (!result.optBoolean("authDeleted")) {
            runCatching { auth.deleteLogin() }
        }
    }

    /**
     * Offline, Firestore applies a write locally at once and confirms it when
     * the server answers. Waiting for that would freeze the screen, so after
     * a short wait the write is left to finish in the background.
     */
    private suspend fun write(task: com.google.android.gms.tasks.Task<*>) {
        withTimeoutOrNull(SERVER_WAIT_MS) { task.await() }
    }

    private companion object {
        const val SERVER_WAIT_MS = 4_000L
    }
}
