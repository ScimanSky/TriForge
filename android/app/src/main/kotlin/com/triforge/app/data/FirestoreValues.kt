package com.triforge.app.data

import com.google.firebase.Timestamp

/**
 * Turns Firestore timestamps into [java.time.Instant], at any depth, so the
 * domain module can read the fields without knowing Firebase.
 */
internal fun Map<String, Any?>.withInstants(): Map<String, Any?> = mapValues { (_, value) -> value.withInstants() }

private fun Any?.withInstants(): Any? = when (this) {
    is Timestamp -> toDate().toInstant()
    is Map<*, *> -> entries.associate { (key, value) -> key.toString() to value.withInstants() }
    is List<*> -> map { it.withInstants() }
    else -> this
}
