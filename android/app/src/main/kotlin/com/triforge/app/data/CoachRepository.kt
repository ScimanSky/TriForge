package com.triforge.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.triforge.domain.coach.ChatMessage
import com.triforge.domain.coach.CoachBrief
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.CoachRole
import com.triforge.domain.coach.NutritionDay
import com.triforge.domain.coach.NutritionProfile
import com.triforge.domain.coach.WeekPlan
import java.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** The training coach's own state: its plan and what it told NutriAI. */
data class CoachDesk(val plan: WeekPlan? = null, val brief: CoachBrief? = null)

/** NutriAI's own state: the athlete's eating profile, the day's plan and its brief. */
data class NutriDesk(
    val profile: NutritionProfile? = null,
    val day: NutritionDay? = null,
    val brief: CoachBrief? = null,
    /** When the coach brief that [day] was built on was written; a newer one makes [day] stale. */
    val basedOnCoachAt: Instant? = null,
)

/** One message in a coach chat, with the plan change it proposes, if any. */
data class ChatEntry(
    val id: String,
    val message: ChatMessage,
    val proposal: Map<String, Any?>? = null,
    val status: ProposalStatus = ProposalStatus.NONE,
)

enum class ProposalStatus(val key: String) {
    NONE("none"), PENDING("pending"), APPLIED("applied"), DISMISSED("dismissed");

    companion object {
        fun of(key: Any?) = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/**
 * The two coaches' data in Firestore:
 *
 * - `users/{uid}/coaching/coach`: the Coach's week plan and its brief.
 * - `users/{uid}/coaching/nutriai`: NutriAI's profile, day plan and brief.
 * - `users/{uid}/coaching/{role}/messages/{id}`: each coach's own chat.
 *
 * Each coach writes only its own document; the brief field is the shared
 * board the other one reads.
 */
class CoachRepository(private val firestore: FirebaseFirestore) {

    private fun desk(uid: String, role: CoachRole) =
        firestore.collection("users").document(uid).collection("coaching").document(role.key)

    private fun messages(uid: String, role: CoachRole) = desk(uid, role).collection("messages")

    fun coachDesk(uid: String): Flow<CoachDesk> = document(uid, CoachRole.COACH) { fields ->
        CoachDesk(
            plan = (fields["plan"] as? Map<*, *>)?.let { CoachMapper.weekPlanFromFields(it.strings() + ("createdAt" to fields["planAt"])) },
            brief = brief(CoachRole.COACH, fields),
        )
    }

    fun nutriDesk(uid: String): Flow<NutriDesk> = document(uid, CoachRole.NUTRI_AI) { fields ->
        NutriDesk(
            profile = (fields["profile"] as? Map<*, *>)?.let { CoachMapper.nutritionProfileFromFields(it.strings()) },
            day = (fields["day"] as? Map<*, *>)?.let { CoachMapper.nutritionDayFromFields(it.strings() + ("createdAt" to fields["dayAt"])) },
            brief = brief(CoachRole.NUTRI_AI, fields),
            basedOnCoachAt = fields["basedOnCoachAt"] as? Instant,
        )
    }

    private fun brief(role: CoachRole, fields: Map<String, Any?>): CoachBrief? =
        CoachMapper.briefFromFields(role, (fields["brief"] as? Map<*, *>)?.strings()?.plus("updatedAt" to fields["briefAt"]))

    private fun <T> document(uid: String, role: CoachRole, read: (Map<String, Any?>) -> T): Flow<T> = callbackFlow {
        val registration = desk(uid, role).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(read(snapshot?.data.orEmpty().withInstants()))
        }
        awaitClose { registration.remove() }
    }

    /** The last [limit] messages, oldest first. */
    fun chat(uid: String, role: CoachRole, limit: Long = 60): Flow<List<ChatEntry>> = callbackFlow {
        val registration = messages(uid, role)
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents.orEmpty().mapNotNull { document ->
                    // A message just written has no server time yet: it is the newest.
                    val fields = document.data.orEmpty().withInstants().let { if (it["at"] == null) it + ("at" to Instant.now()) else it }
                    val message = CoachMapper.messageFromFields(fields) ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    ChatEntry(document.id, message, fields["proposal"] as? Map<String, Any?>, ProposalStatus.of(fields["status"]))
                }
                trySend(entries.reversed())
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveWeekPlan(uid: String, plan: WeekPlan, brief: CoachBrief) = write(
        desk(uid, CoachRole.COACH).set(
            mapOf(
                "plan" to CoachMapper.weekPlanToFields(plan),
                "planAt" to FieldValue.serverTimestamp(),
                "brief" to CoachMapper.briefToFields(brief),
                "briefAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ),
    )

    suspend fun saveNutritionDay(uid: String, day: NutritionDay, brief: CoachBrief, basedOnCoachAt: Instant?) = write(
        desk(uid, CoachRole.NUTRI_AI).set(
            mapOf(
                "day" to CoachMapper.nutritionDayToFields(day),
                "dayAt" to FieldValue.serverTimestamp(),
                "brief" to CoachMapper.briefToFields(brief),
                "briefAt" to FieldValue.serverTimestamp(),
                "basedOnCoachAt" to basedOnCoachAt?.let { com.google.firebase.Timestamp(it.epochSecond, it.nano) },
            ),
            SetOptions.merge(),
        ),
    )

    suspend fun saveNutritionProfile(uid: String, profile: NutritionProfile) = write(
        desk(uid, CoachRole.NUTRI_AI).set(mapOf("profile" to CoachMapper.nutritionProfileToFields(profile)), SetOptions.merge()),
    )

    /** A note for the other coach, left from a chat, without changing the plan. */
    suspend fun leaveNote(uid: String, role: CoachRole, note: String) = write(
        desk(uid, role).set(
            mapOf("brief" to mapOf("noteForPartner" to note), "briefAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ),
    )

    suspend fun addMessage(uid: String, role: CoachRole, fromAthlete: Boolean, text: String, proposal: Map<String, Any?>? = null) = write(
        messages(uid, role).document().set(
            buildMap {
                put("from", if (fromAthlete) "athlete" else "coach")
                put("text", text)
                put("at", FieldValue.serverTimestamp())
                if (proposal != null) {
                    put("proposal", proposal)
                    put("status", ProposalStatus.PENDING.key)
                }
            },
        ),
    )

    suspend fun setProposalStatus(uid: String, role: CoachRole, messageId: String, status: ProposalStatus) = write(
        messages(uid, role).document(messageId).update("status", status.key),
    )

    /** Offline, Firestore applies writes locally at once; do not freeze on the server. */
    private suspend fun write(task: com.google.android.gms.tasks.Task<Void>) {
        withTimeoutOrNull(SERVER_WAIT_MS) { task.await() }
    }

    private companion object {
        const val SERVER_WAIT_MS = 4_000L
    }
}

private fun Map<*, *>.strings(): Map<String, Any?> = entries.associate { (key, value) -> key.toString() to value }
