package com.triforge.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triforge.app.ai.CoachAgent
import com.triforge.app.ai.CoachException
import com.triforge.app.data.ChatEntry
import com.triforge.app.data.CoachDesk
import com.triforge.app.data.CoachRepository
import com.triforge.app.data.NutriDesk
import com.triforge.app.data.ProposalStatus
import com.triforge.domain.TrainingStats
import com.triforge.domain.coach.CoachBriefs
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.CoachPrompts
import com.triforge.domain.coach.CoachRole
import com.triforge.domain.coach.NutritionDay
import com.triforge.domain.coach.NutritionProfile
import com.triforge.domain.coach.PlannedDay
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NutriUiState(
    val loaded: Boolean = false,
    /** Null until the athlete has told NutriAI how they eat. */
    val profile: NutritionProfile? = null,
    /** Today's plan, or null when NutriAI has not written it yet. */
    val day: NutritionDay? = null,
    /** True when the Coach changed the plan after NutriAI wrote [day]. */
    val stale: Boolean = false,
    /** Today's sessions from the Coach's plan, if there is one. */
    val todaySessions: PlannedDay? = null,
    val coachNote: String = "",
    val chat: List<ChatEntry> = emptyList(),
    val task: CoachTask? = null,
    val error: CoachException.Reason? = null,
)

/**
 * NutriAI's screen. It drives one agent, NutriAI, which builds on the plan
 * the Coach published on the shared board and leaves its own brief there.
 */
class NutriViewModel(
    private val uid: String,
    private val agent: CoachAgent<NutritionDay>,
    private val repository: CoachRepository,
    private val athlete: AthleteSource,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val task = MutableStateFlow<CoachTask?>(null)
    private val error = MutableStateFlow<CoachException.Reason?>(null)
    private var autoPlannedFor: Pair<LocalDate, java.time.Instant?>? = null

    private fun today(): LocalDate = LocalDate.now(zone)

    private fun isStale(nutri: NutriDesk, coach: CoachDesk): Boolean {
        val coachAt = coach.brief?.updatedAt ?: return false
        val basedOn = nutri.basedOnCoachAt ?: return true
        return coachAt.isAfter(basedOn)
    }

    val state: StateFlow<NutriUiState> = combine(
        repository.nutriDesk(uid).onStart { emit(NutriDesk()) },
        repository.coachDesk(uid).onStart { emit(CoachDesk()) },
        repository.chat(uid, CoachRole.NUTRI_AI).onStart { emit(emptyList()) },
        task,
        error,
    ) { nutri, coach, chat, task, error ->
        val day = nutri.day?.takeIf { it.date == today() }
        NutriUiState(
            loaded = true,
            profile = nutri.profile,
            day = day,
            stale = day != null && isStale(nutri, coach),
            todaySessions = coach.plan?.day(today()),
            coachNote = coach.brief?.noteForPartner.orEmpty(),
            chat = chat,
            task = task,
            error = error,
        )
    }
        .catch { emit(NutriUiState(loaded = true, error = CoachException.Reason.NETWORK)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutriUiState())

    /**
     * Called when the screen opens: writes today's plan if there is none, or
     * if the Coach changed the training after it was written. Once per change.
     */
    fun ensureDay() {
        viewModelScope.launch {
            val nutri = repository.nutriDesk(uid).first()
            val coach = repository.coachDesk(uid).first()
            if (nutri.profile == null) return@launch
            val key = today() to coach.brief?.updatedAt
            if (autoPlannedFor == key) return@launch
            val missing = nutri.day?.date != today()
            if (missing || isStale(nutri, coach)) {
                autoPlannedFor = key
                planDay()
            }
        }
    }

    fun planDay() = run(CoachTask.PLANNING) { writeDay() }

    /** Saves how the athlete eats; a new profile changes the suggestions, so today is rewritten. */
    fun saveProfile(profile: NutritionProfile) = run(CoachTask.PLANNING) {
        repository.saveNutritionProfile(uid, profile)
        writeDay(profile)
    }

    private suspend fun writeDay(profileOverride: NutritionProfile? = null) {
        val date = today()
        val nutri = repository.nutriDesk(uid).first()
        val coach = repository.coachDesk(uid).first()
        val profile = profileOverride ?: nutri.profile ?: NutritionProfile()
        val plan = coach.plan?.takeIf { it.weekStart == TrainingStats.weekStart(date) }
        val recent = state.value.chat.takeLast(RECENT_MESSAGES).map { it.message }
        val request = CoachPrompts.nutritionDayRequest(athlete.describe(), date, profile, coach.brief, plan, recent)
        val day = agent.plan(request).copy(date = date)
        repository.saveNutritionDay(uid, day, CoachBriefs.of(day), coach.brief?.updatedAt)
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || task.value != null) return
        run(CoachTask.REPLYING) {
            val history = state.value.chat.map { it.message }
            repository.addMessage(uid, CoachRole.NUTRI_AI, fromAthlete = true, text = message)
            val nutri = repository.nutriDesk(uid).first()
            val coach = repository.coachDesk(uid).first()
            val date = today()
            val plan = coach.plan?.takeIf { it.weekStart == TrainingStats.weekStart(date) }
            val turn = CoachPrompts.chatTurn(
                role = CoachRole.NUTRI_AI,
                athlete = athlete.describe(),
                partner = coach.brief,
                ownPlan = nutri.day?.takeIf { it.date == date }?.let(CoachPrompts::describe),
                extra = buildString {
                    appendLine("## Profilo alimentare")
                    appendLine(CoachPrompts.describe(nutri.profile ?: NutritionProfile()))
                    plan?.day(date)?.let { append("Sedute di oggi secondo il Coach: ${CoachPrompts.describeDay(it)}") }
                }.trimEnd(),
                message = message,
            )
            val reply = agent.reply(history, turn)
            val proposal = reply.proposal?.copy(date = date)
            repository.addMessage(uid, CoachRole.NUTRI_AI, fromAthlete = false, text = reply.text, proposal = proposal?.let(CoachMapper::nutritionDayToFields))
            if (reply.noteForPartner.isNotBlank()) repository.leaveNote(uid, CoachRole.NUTRI_AI, reply.noteForPartner)
        }
    }

    fun apply(entry: ChatEntry) = run(null) {
        val day = entry.proposal?.let { CoachMapper.nutritionDayFromFields(it, today()) } ?: return@run
        val coach = repository.coachDesk(uid).first()
        repository.saveNutritionDay(uid, day, CoachBriefs.of(day), coach.brief?.updatedAt)
        repository.setProposalStatus(uid, CoachRole.NUTRI_AI, entry.id, ProposalStatus.APPLIED)
    }

    fun dismiss(entry: ChatEntry) = run(null) {
        repository.setProposalStatus(uid, CoachRole.NUTRI_AI, entry.id, ProposalStatus.DISMISSED)
    }

    fun clearError() {
        error.value = null
    }

    private fun run(kind: CoachTask?, block: suspend () -> Unit) {
        viewModelScope.launch {
            task.value = kind
            error.value = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: CoachException) {
                error.value = e.reason
            } catch (e: Exception) {
                error.value = CoachException.Reason.NETWORK
            } finally {
                task.value = null
            }
        }
    }

    private companion object {
        const val RECENT_MESSAGES = 6
    }
}
