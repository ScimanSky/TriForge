package com.triforge.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.triforge.domain.Sport
import com.triforge.domain.coach.ChatMessage
import com.triforge.domain.coach.CoachBrief
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.CoachRole
import com.triforge.domain.coach.NutritionDay
import com.triforge.domain.coach.NutritionProfile
import com.triforge.domain.coach.WeekPlan
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** The training coach's own state: its plan and what it told NutriAI. */
data class CoachDesk(val plan: WeekPlan? = null, val brief: CoachBrief? = null)

/** NutriAI's own state: the athlete's eating profile, the days it wrote and its brief. */
data class NutriDesk(
    val profile: NutritionProfile? = null,
    /** The days NutriAI prepared, by date: each only when the athlete asked for it. */
    val days: Map<LocalDate, NutritionDay> = emptyMap(),
    val brief: CoachBrief? = null,
)

/** One message in a coach chat, with the plan change it proposes, if any. */
data class ChatEntry(
    val id: String,
    val message: ChatMessage,
    val proposal: Map<String, Any?>? = null,
    val status: ProposalStatus = ProposalStatus.NONE,
    /** Who the coach sends the athlete to for this question: a sport key or "nutriai"; null when nobody. */
    val handoff: String? = null,
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
 * - `users/{uid}/coaching/nutriai`: NutriAI's profile, the days it wrote
 *   (`days`, by date) and its brief.
 * - `users/{uid}/coaching/{role}/messages/{id}`: NutriAI's chat.
 * - `users/{uid}/coaching/{swim|bike|run}/messages/{id}`: the chat of each
 *   sport coach. Their week is one, in `coaching/coach`.
 *
 * Each coach writes only its own document; the brief field is the shared
 * board the other one reads.
 */
class CoachRepository(private val firestore: FirebaseFirestore) {

    private fun desk(uid: String, key: String) =
        firestore.collection("users").document(uid).collection("coaching").document(key)

    private fun desk(uid: String, role: CoachRole) = desk(uid, role.key)

    private fun messages(uid: String, key: String) = desk(uid, key).collection("messages")

    fun coachDesk(uid: String): Flow<CoachDesk> = document(uid, CoachRole.COACH) { fields ->
        CoachDesk(
            plan = (fields["plan"] as? Map<*, *>)?.let { CoachMapper.weekPlanFromFields(it.strings() + ("createdAt" to fields["planAt"])) },
            brief = brief(CoachRole.COACH, fields),
        )
    }

    fun nutriDesk(uid: String): Flow<NutriDesk> = document(uid, CoachRole.NUTRI_AI) { fields ->
        val days = (fields["days"] as? Map<*, *>).orEmpty().values
            .mapNotNull { day -> (day as? Map<*, *>)?.let { CoachMapper.nutritionDayFromFields(it.strings()) } }
        // The one day kept before NutriAI wrote more than today.
        val legacy = (fields["day"] as? Map<*, *>)?.let { CoachMapper.nutritionDayFromFields(it.strings()) }
        NutriDesk(
            profile = (fields["profile"] as? Map<*, *>)?.let { CoachMapper.nutritionProfileFromFields(it.strings()) },
            days = (listOfNotNull(legacy) + days).associateBy { it.date },
            brief = brief(CoachRole.NUTRI_AI, fields),
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
    fun chat(uid: String, role: CoachRole, limit: Long = 60): Flow<List<ChatEntry>> = chat(uid, role.key, limit)

    /** The chat with one sport coach. */
    fun chat(uid: String, sport: Sport, limit: Long = 60): Flow<List<ChatEntry>> = chat(uid, CoachMapper.sportKey(sport), limit)

    private fun chat(uid: String, key: String, limit: Long): Flow<List<ChatEntry>> = callbackFlow {
        val registration = messages(uid, key)
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
                    ChatEntry(
                        document.id,
                        message,
                        fields["proposal"] as? Map<String, Any?>,
                        ProposalStatus.of(fields["status"]),
                        (fields["handoff"] as? String)?.takeIf { it.isNotBlank() },
                    )
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

    /**
     * Writes [days] among NutriAI's days, each in place of the one of its
     * date, and forgets those of [forget] (the weeks gone by). [brief] is
     * left on the shared board when NutriAI wrote the days again; a dish
     * the athlete changed does not concern the coaches.
     */
    suspend fun saveNutritionDays(uid: String, days: List<NutritionDay>, brief: CoachBrief? = null, forget: Collection<LocalDate> = emptyList()) = write(
        desk(uid, CoachRole.NUTRI_AI).set(
            buildMap<String, Any> {
                val changes: Map<String, Any> = forget.associate { it.toString() to FieldValue.delete() } +
                    days.associate { it.date.toString() to CoachMapper.nutritionDayToFields(it) }
                // Merged into the days already there; an empty map would take their place.
                if (changes.isNotEmpty()) put("days", changes)
                if (brief != null) {
                    put("brief", CoachMapper.briefToFields(brief))
                    put("briefAt", FieldValue.serverTimestamp())
                }
            },
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

    suspend fun addMessage(uid: String, role: CoachRole, fromAthlete: Boolean, text: String, proposal: Map<String, Any?>? = null) =
        write(messages(uid, role.key).document().set(message(fromAthlete, text, proposal, null)))

    /**
     * A message in one sport coach's chat. The coach's document is written
     * with it, so the chat is found by the data export and by the erasure,
     * which list the documents of `coaching`.
     */
    suspend fun addMessage(uid: String, sport: Sport, fromAthlete: Boolean, text: String, proposal: Map<String, Any?>? = null, handoff: String? = null) {
        val key = CoachMapper.sportKey(sport)
        val batch = firestore.batch()
        batch.set(desk(uid, key), mapOf("chatAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        batch.set(messages(uid, key).document(), message(fromAthlete, text, proposal, handoff))
        write(batch.commit())
    }

    private fun message(fromAthlete: Boolean, text: String, proposal: Map<String, Any?>?, handoff: String?): Map<String, Any?> = buildMap {
        put("from", if (fromAthlete) "athlete" else "coach")
        put("text", text)
        put("at", FieldValue.serverTimestamp())
        if (proposal != null) {
            put("proposal", proposal)
            put("status", ProposalStatus.PENDING.key)
        }
        if (handoff != null) put("handoff", handoff)
    }

    suspend fun setProposalStatus(uid: String, role: CoachRole, messageId: String, status: ProposalStatus) = write(
        messages(uid, role.key).document(messageId).update("status", status.key),
    )

    suspend fun setProposalStatus(uid: String, sport: Sport, messageId: String, status: ProposalStatus) = write(
        messages(uid, CoachMapper.sportKey(sport)).document(messageId).update("status", status.key),
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
