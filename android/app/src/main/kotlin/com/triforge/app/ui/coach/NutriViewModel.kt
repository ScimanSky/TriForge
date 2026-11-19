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
import com.triforge.domain.coach.BanViolation
import com.triforge.domain.coach.BodyData
import com.triforge.domain.coach.CoachBriefs
import com.triforge.domain.coach.DayTargets
import com.triforge.domain.coach.FoodBans
import com.triforge.domain.coach.NutriEnergy
import com.triforge.domain.coach.NutriSafety
import com.triforge.domain.coach.PlannedSession
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
    private var profileEdited = false
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
        // Checked again on every show: a day written before the profile changed never shows a banned food.
        val day = nutri.day?.takeIf { it.date == today() }?.let { day -> nutri.profile?.let { FoodBans.clean(day, it) } ?: day }
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
     * Called when the screen opens: writes today's plan if there is none, if
     * the Coach changed the training after it was written, or if it holds a
     * food the profile bans. Once per change.
     */
    fun ensureDay() {
        viewModelScope.launch {
            val nutri = repository.nutriDesk(uid).first()
            val coach = repository.coachDesk(uid).first()
            val profile = nutri.profile ?: return@launch
            val key = today() to coach.brief?.updatedAt
            if (autoPlannedFor == key) return@launch
            val missing = nutri.day?.date != today()
            val broken = !missing && nutri.day?.let { FoodBans.violations(it, profile).isNotEmpty() } == true
            if (missing || broken || isStale(nutri, coach)) {
                autoPlannedFor = key
                planDay()
            }
        }
    }

    fun planDay() = run(CoachTask.PLANNING) { writeDay() }

    /** Saves how the athlete eats; a new profile changes the suggestions, so today is rewritten. */
    fun saveProfile(profile: NutritionProfile) = run(CoachTask.PLANNING) {
        profileEdited = false
        repository.saveNutritionProfile(uid, profile)
        writeDay(profile)
    }

    /**
     * Keeps a change made in the profile editor as soon as it is made, so
     * closing the editor without «Salva» loses nothing. The meals on screen
     * are checked against it at once; today is rewritten by [doneEditing].
     */
    fun keepProfile(profile: NutritionProfile) {
        profileEdited = true
        viewModelScope.launch {
            try {
                repository.saveNutritionProfile(uid, profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = CoachException.Reason.NETWORK
            }
        }
    }

    /** The editor was closed without «Salva»: today is rewritten if the profile changed meanwhile. */
    fun doneEditing() {
        if (!profileEdited || task.value != null) return
        profileEdited = false
        planDay()
    }

    /** The app's numbers for [date] from the athlete's body and the Coach's sessions; null without a weight. */
    private suspend fun targetsFor(profile: NutritionProfile, sessions: List<PlannedSession>): DayTargets? {
        val data = athlete.current()
        val body = BodyData(data.profile.weightKg, data.profile.heightCm, data.age, profile.sex)
        return NutriEnergy.targets(profile, body, sessions)
    }

    /**
     * NutriAI writes the day on the app's numbers; the app checks every meal
     * against the profile, asks once more when one breaks it, and drops what
     * still does.
     */
    private suspend fun writeDay(profileOverride: NutritionProfile? = null) {
        val date = today()
        val nutri = repository.nutriDesk(uid).first()
        val coach = repository.coachDesk(uid).first()
        val profile = profileOverride ?: nutri.profile ?: NutritionProfile()
        val plan = coach.plan?.takeIf { it.weekStart == TrainingStats.weekStart(date) }
        val targets = targetsFor(profile, plan?.day(date)?.sessions.orEmpty())
            ?: throw CoachException(CoachException.Reason.NEEDS_WEIGHT)
        val recent = state.value.chat.takeLast(RECENT_MESSAGES).map { it.message }
        val described = athlete.describe()
        fun request(violations: List<BanViolation>) =
            CoachPrompts.nutritionDayRequest(described, date, profile, targets, coach.brief, plan, recent, violations)
        var day = NutriEnergy.apply(agent.plan(request(emptyList())).copy(date = date), targets)
        val violations = FoodBans.violations(day, profile)
        if (violations.isNotEmpty()) {
            day = NutriEnergy.apply(agent.plan(request(violations)).copy(date = date), targets)
        }
        day = FoodBans.clean(day, profile)
        repository.saveNutritionDay(uid, day, CoachBriefs.of(day), coach.brief?.updatedAt)
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || task.value != null) return
        run(CoachTask.REPLYING) {
            val history = state.value.chat.map { it.message }
            repository.addMessage(uid, CoachRole.NUTRI_AI, fromAthlete = true, text = message)
            // Signs of an eating disorder: a fixed, kind answer, and no model.
            if (NutriSafety.flags(message)) {
                repository.addMessage(uid, CoachRole.NUTRI_AI, fromAthlete = false, text = NutriSafety.REPLY)
                return@run
            }
            val nutri = repository.nutriDesk(uid).first()
            val coach = repository.coachDesk(uid).first()
            val date = today()
            val plan = coach.plan?.takeIf { it.weekStart == TrainingStats.weekStart(date) }
            val profile = nutri.profile ?: NutritionProfile()
            val targets = targetsFor(profile, plan?.day(date)?.sessions.orEmpty())
            val turn = CoachPrompts.chatTurn(
                role = CoachRole.NUTRI_AI,
                athlete = athlete.describe(),
                partner = coach.brief,
                ownPlan = nutri.day?.takeIf { it.date == date }?.let(CoachPrompts::describe),
                extra = buildString {
                    targets?.let {
                        appendLine("## Obiettivi del giorno")
                        appendLine(NutriEnergy.describe(it, profile.goal))
                        appendLine("Se proponi un giorno nuovo, l'app ricalcola i numeri: cambia pasti e alimentazione, non i numeri.")
                        appendLine()
                    }
                    appendLine("## Profilo alimentare")
                    appendLine(CoachPrompts.describe(profile))
                    plan?.day(date)?.let { append("Sedute di oggi secondo il Coach: ${CoachPrompts.describeDay(it)}") }
                }.trimEnd(),
                message = message,
            )
            val reply = agent.reply(history, turn)
            val proposal = reply.proposal?.copy(date = date)?.let { FoodBans.clean(NutriEnergy.apply(it, targets), profile) }
            repository.addMessage(uid, CoachRole.NUTRI_AI, fromAthlete = false, text = reply.text, proposal = proposal?.let(CoachMapper::nutritionDayToFields))
            if (reply.noteForPartner.isNotBlank()) repository.leaveNote(uid, CoachRole.NUTRI_AI, reply.noteForPartner)
        }
    }

    fun apply(entry: ChatEntry) = run(null) {
        val proposed = entry.proposal?.let { CoachMapper.nutritionDayFromFields(it, today()) } ?: return@run
        // The profile may have changed since NutriAI made the proposal.
        val day = repository.nutriDesk(uid).first().profile?.let { FoodBans.clean(proposed, it) } ?: proposed
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
