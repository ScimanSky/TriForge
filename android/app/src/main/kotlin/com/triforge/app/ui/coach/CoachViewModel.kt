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
import com.triforge.domain.coach.NutriSafety
import com.triforge.domain.coach.RecentTraining
import com.triforge.domain.coach.RegiaConflict
import com.triforge.domain.coach.TriRegia
import com.triforge.domain.coach.WeekBudget
import com.triforge.domain.coach.WeekPlan
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

enum class CoachTask { PLANNING, REPLYING }

data class CoachUiState(
    val loaded: Boolean = false,
    /** This week's plan, or null when the coach has not written it yet. */
    val plan: WeekPlan? = null,
    val chat: List<ChatEntry> = emptyList(),
    /** NutriAI's latest note for the Coach, shown so the athlete sees them work together. */
    val nutriNote: String = "",
    val task: CoachTask? = null,
    val error: CoachException.Reason? = null,
)

/**
 * The training coach's screen. It drives one agent, the Coach; it reads
 * NutriAI's brief from the shared board and never calls NutriAI itself.
 */
class CoachViewModel(
    private val uid: String,
    private val agent: CoachAgent<WeekPlan>,
    private val repository: CoachRepository,
    private val athlete: AthleteSource,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val task = MutableStateFlow<CoachTask?>(null)
    private val error = MutableStateFlow<CoachException.Reason?>(null)
    private var autoPlanTried = false

    private fun thisWeek(): LocalDate = TrainingStats.weekStart(LocalDate.now(zone))

    val state: StateFlow<CoachUiState> = combine(
        repository.coachDesk(uid).onStart { emit(CoachDesk()) },
        repository.nutriDesk(uid).onStart { emit(NutriDesk()) },
        repository.chat(uid, CoachRole.COACH).onStart { emit(emptyList()) },
        task,
        error,
    ) { desk, nutri, chat, task, error ->
        CoachUiState(
            loaded = true,
            plan = desk.plan?.takeIf { it.weekStart == thisWeek() },
            chat = chat,
            nutriNote = nutri.brief?.noteForPartner.orEmpty(),
            task = task,
            error = error,
        )
    }
        .catch { emit(CoachUiState(loaded = true, error = CoachException.Reason.NETWORK)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoachUiState())

    /** Called when the screen opens: writes this week's plan once if there is none. */
    fun ensurePlan() {
        if (autoPlanTried) return
        autoPlanTried = true
        viewModelScope.launch {
            val desk = repository.coachDesk(uid).first()
            if (desk.plan?.weekStart != thisWeek()) planWeek()
        }
    }

    fun planWeek() = run(CoachTask.PLANNING) {
        val weekStart = thisWeek()
        val nutri = repository.nutriDesk(uid).first()
        val previous = repository.coachDesk(uid).first().plan
        val recent = state.value.chat.takeLast(RECENT_MESSAGES).map { it.message }
        val data = athlete.current()
        val budget = weekBudget(weekStart)
        val regia = TriRegia.describe(budget, data.availability)
        val described = athlete.describe()
        fun request(conflicts: List<RegiaConflict>) =
            CoachPrompts.weekPlanRequest(described, weekStart, nutri.brief, previous, recent, regia, conflicts)
        val today = LocalDate.now(zone)
        var plan = agent.plan(request(emptyList())).copy(weekStart = weekStart)
        var conflicts = TriRegia.check(plan, budget, data.availability, from = today)
        if (conflicts.isNotEmpty()) {
            plan = agent.plan(request(conflicts)).copy(weekStart = weekStart)
            conflicts = TriRegia.check(plan, budget, data.availability, from = today)
        }
        // What the second round could not fix stays visible under the plan.
        if (conflicts.isNotEmpty()) {
            plan = plan.copy(rationale = (plan.rationale + "\n\nDa rivedere secondo la regia:\n" + TriRegia.describe(conflicts)).trim())
        }
        repository.saveWeekPlan(uid, plan, CoachBriefs.of(plan, today))
    }

    /** The regia's budget for the week starting on [weekStart]. */
    private suspend fun weekBudget(weekStart: LocalDate): WeekBudget {
        val data = athlete.current()
        val recent = RecentTraining.of(data.activities, weekStart, data.zone)
        return TriRegia.budget(data.profile, data.availability, recent, weekStart)
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || task.value != null) return
        run(CoachTask.REPLYING) {
            val history = state.value.chat.map { it.message }
            repository.addMessage(uid, CoachRole.COACH, fromAthlete = true, text = message)
            if (NutriSafety.flags(message)) {
                repository.addMessage(uid, CoachRole.COACH, fromAthlete = false, text = NutriSafety.REPLY)
                return@run
            }
            val nutri = repository.nutriDesk(uid).first()
            val plan = repository.coachDesk(uid).first().plan?.takeIf { it.weekStart == thisWeek() }
            val regia = TriRegia.describe(weekBudget(thisWeek()), athlete.current().availability)
            val turn = CoachPrompts.chatTurn(
                role = CoachRole.COACH,
                athlete = athlete.describe(),
                partner = nutri.brief,
                ownPlan = plan?.let(CoachPrompts::describe),
                extra = buildString {
                    appendLine("La settimana va da lunedì ${thisWeek()} a domenica ${thisWeek().plusDays(6)}.")
                    appendLine("Se proponi una settimana nuova, rispetta la regia:")
                    append(regia)
                },
                message = message,
            )
            val reply = agent.reply(history, turn)
            val proposal = reply.proposal?.copy(weekStart = thisWeek())
            repository.addMessage(uid, CoachRole.COACH, fromAthlete = false, text = reply.text, proposal = proposal?.let(CoachMapper::weekPlanToFields))
            if (reply.noteForPartner.isNotBlank()) repository.leaveNote(uid, CoachRole.COACH, reply.noteForPartner)
        }
    }

    /** Applies a plan the coach proposed in the chat: it becomes the week, and NutriAI sees it. */
    fun apply(entry: ChatEntry) = run(null) {
        val plan = entry.proposal?.let { CoachMapper.weekPlanFromFields(it, thisWeek()) } ?: return@run
        repository.saveWeekPlan(uid, plan, CoachBriefs.of(plan, LocalDate.now(zone)))
        repository.setProposalStatus(uid, CoachRole.COACH, entry.id, ProposalStatus.APPLIED)
    }

    fun dismiss(entry: ChatEntry) = run(null) {
        repository.setProposalStatus(uid, CoachRole.COACH, entry.id, ProposalStatus.DISMISSED)
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
