package com.triforge.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A refusal from the TriForge service. [code] is the service's `detail` value,
 * such as `garmin_bad_credentials`, or `network` when it could not be reached.
 */
class ServiceException(val code: String, val retryAfterSec: Int? = null) : Exception(code)

/**
 * The TriForge service on Cloud Run: the Garmin connection, and the privacy
 * operations the app is not allowed to do on its own, such as deleting the
 * Garmin tokens or the login. Every call carries the athlete's ID token.
 */
class CloudService(
    private val auth: AuthRepository,
    private val baseUrl: String,
) {

    /** False when the build has no service URL. */
    val configured: Boolean get() = baseUrl.isNotBlank()

    suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            if (!configured) throw ServiceException("not_configured")
            val token = auth.idToken() ?: throw ServiceException("not_signed_in")
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
                    throw ServiceException(
                        code = json.optString("detail", "http_$status"),
                        retryAfterSec = connection.getHeaderField("Retry-After")?.toIntOrNull(),
                    )
                }
                json
            } catch (error: IOException) {
                throw ServiceException("network")
            } finally {
                connection.disconnect()
            }
        }
}
