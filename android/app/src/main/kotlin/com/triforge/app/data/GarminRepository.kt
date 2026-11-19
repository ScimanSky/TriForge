package com.triforge.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

/** Connection state, as the Garmin service publishes it in Firestore. */
data class GarminStatus(
    val connected: Boolean = false,
    val displayName: String = "",
    val lastSyncAt: Instant? = null,
    val lastSyncStatus: String = "",
) {
    val needsReconnect: Boolean get() = !connected && lastSyncStatus == "reconnect_required"
}

/**
 * Talks to the Garmin sync service on Cloud Run.
 *
 * The app never contacts Garmin itself: the unofficial library needs the
 * athlete's password and long-lived tokens, which must not live on the phone.
 */
class GarminRepository(
    private val firestore: FirebaseFirestore,
    private val service: CloudService,
) {

    /** False when the build has no service URL. */
    val configured: Boolean get() = service.configured

    fun status(uid: String): Flow<GarminStatus> = callbackFlow {
        val registration = firestore.collection("users").document(uid)
            .collection("integrations").document("garmin")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(GarminStatus())
                    return@addSnapshotListener
                }
                trySend(
                    GarminStatus(
                        connected = snapshot.getBoolean("connected") ?: false,
                        displayName = snapshot.get("displayName") as? String ?: "",
                        lastSyncAt = (snapshot.get("lastSyncAt") as? Timestamp)?.toDate()?.toInstant(),
                        lastSyncStatus = snapshot.get("lastSyncStatus") as? String ?: "",
                    ),
                )
            }
        awaitClose { registration.remove() }
    }

    /** Returns true when Garmin asks for a verification code. */
    suspend fun connect(email: String, password: String): Boolean {
        val body = JSONObject().put("email", email).put("password", password)
        return call("POST", "/garmin/connect", body).optString("status") == "mfa_required"
    }

    suspend fun submitMfaCode(code: String) {
        call("POST", "/garmin/mfa", JSONObject().put("code", code))
    }

    /** Returns the number of activities imported. */
    suspend fun sync(): Int = call("POST", "/garmin/sync").optInt("synced")

    suspend fun disconnect() {
        call("DELETE", "/garmin/connection")
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        service.call(method, path, body)
}
