package com.triforge.app.data

import android.util.Base64
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A copy of everything TriForge keeps about the athlete, as JSON: the right
 * of access and the right to data portability (articles 15 and 20 GDPR).
 *
 * The Garmin tokens are left out: the app cannot read them, and they are
 * credentials, not data about the athlete.
 */
class DataExport(
    private val firestore: FirebaseFirestore,
    private val auth: AuthRepository,
) {

    suspend fun write(uid: String, out: OutputStream) {
        val json = build(uid)
        withContext(Dispatchers.IO) {
            out.bufferedWriter().use { it.write(json.toString(2)) }
        }
    }

    private suspend fun build(uid: String): JSONObject {
        val user = firestore.collection("users").document(uid)
        val root = JSONObject()
            .put("format", "triforge-export-1")
            .put("exportedAt", Instant.now().toString())
            .put(
                "login",
                JSONObject()
                    .put("uid", uid)
                    .put("email", auth.email)
                    .put("name", auth.displayName),
            )
            .put("profile", toJson(user.get().await().data.orEmpty()))
        COLLECTIONS.forEach { name ->
            val documents = JSONObject()
            user.collection(name).get().await().documents.forEach { document ->
                documents.put(document.id, toJson(document.data.orEmpty()))
            }
            root.put(name, documents)
        }
        // The two AI coaches: each one's document (plan, brief, eating
        // profile) and its own chat.
        val coaching = JSONObject()
        user.collection("coaching").get().await().documents.forEach { document ->
            val messages = JSONObject()
            document.reference.collection("messages").get().await().documents.forEach { message ->
                messages.put(message.id, toJson(message.data.orEmpty()))
            }
            coaching.put(document.id, toJson(document.data.orEmpty()).put("messages", messages))
        }
        root.put("coaching", coaching)
        return root
    }

    private fun toJson(fields: Map<String, Any?>): JSONObject =
        JSONObject().also { json -> fields.forEach { (key, value) -> json.put(key, value.toJsonValue()) } }

    private fun Any?.toJsonValue(): Any = when (this) {
        null -> JSONObject.NULL
        is Timestamp -> toDate().toInstant().toString()
        is Blob -> Base64.encodeToString(toBytes(), Base64.NO_WRAP)
        is Map<*, *> -> JSONObject().also { json -> forEach { (key, value) -> json.put(key.toString(), value.toJsonValue()) } }
        is List<*> -> JSONArray().also { array -> forEach { array.put(it.toJsonValue()) } }
        is Number, is Boolean, is String -> this
        else -> toString()
    }

    private companion object {
        /** Everything under `users/{uid}` the app may read. */
        val COLLECTIONS = listOf("activities", "wellness", "checkIns", "integrations", "consentLog", "media")
    }
}
