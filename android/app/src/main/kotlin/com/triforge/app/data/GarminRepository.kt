package com.triforge.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
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
 * A refusal from the Garmin service. [code] is the service's `detail` value,
 * such as `garmin_bad_credentials`, or `network` when it could not be reached.
 */
class GarminException(val code: String, val retryAfterSec: Int? = null) : Exception(code)

/**
 * Talks to the Garmin sync service on Cloud Run.
 *
 * The app never contacts Garmin itself: the unofficial library needs the
 * athlete's password and long-lived tokens, which must not live on the phone.
 */
class GarminRepository(
    private val firestore: FirebaseFirestore,
    private val auth: AuthRepository,
    private val baseUrl: String,
) {

    /** False when the build has no service URL. */
    val configured: Boolean get() = baseUrl.isNotBlank()

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
        withContext(Dispatchers.IO) {
            if (!configured) throw GarminException("not_configured")
            val token = auth.idToken() ?: throw GarminException("not_signed_in")
            val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                // A first sync reads months of activities from Garmin.
                connection.readTimeout = 120_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (status !in 200..299) {
                    throw GarminException(
                        code = json.optString("detail", "http_$status"),
                        retryAfterSec = connection.getHeaderField("Retry-After")?.toIntOrNull(),
                    )
                }
                json
            } catch (error: IOException) {
                throw GarminException("network")
            } finally {
                connection.disconnect()
            }
        }
}
