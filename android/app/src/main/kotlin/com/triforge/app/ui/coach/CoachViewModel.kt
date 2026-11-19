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
import com.triforge.domain.Sport
import com.triforge.domain.TrainingStats
import com.triforge.domain.coach.AthleteContext
import com.triforge.domain.coach.CoachBriefs
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.CoachPrompts
import com.triforge.domain.coach.CoachRole
import com.triforge.domain.coach.CoachTable
import com.triforge.domain.coach.NutriSafety
import com.triforge.domain.coach.RecentTraining
import com.triforge.domain.coach.RegiaConflict
import com.triforge.domain.coach.SportCoachPrompts
import com.triforge.domain.coach.SportProposal
import com.triforge.domain.coach.TableNote
import com.triforge.domain.coach.TableVoice
import com.triforge.domain.coach.TriRegia
import com.triforge.domain.coach.WeekBudget
import com.triforge.domain.coach.WeekPlan
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    /** While planning: which round of the coaches' table is running, 1 or 2. */
    val round: Int = 0,
    val error: CoachException.Reason? = null,
)

/**
 * The training coach's screen. The week is written by the three sport
 * coaches at one table ([sportCoaches]); the chat is still held by the one
 * Coach ([agent]). NutriAI is never called from here: only its brief on the
 * shared board is read.
 */
class CoachViewModel(
    private val uid: String,
    private val agent: CoachAgent<WeekPlan>,
    private val sportCoaches: Map<Sport, CoachAgent<SportProposal>>,
    private val repository: CoachRepository,
    private val athlete: AthleteSource,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val task = MutableStateFlow<CoachTask?>(null)
    private val error = MutableStateFlow<CoachException.Reason?>(null)
    private val tableRound = MutableStateFlow(0)
    private var autoPlanTried = false

    private fun thisWeek(): LocalDate = TrainingStats.weekStart(LocalDate.now(zone))

    val state: StateFlow<CoachUiState> = combine(
        repository.coachDesk(uid).onStart { emit(CoachDesk()) },
        repository.nutriDesk(uid).onStart { emit(NutriDesk()) },
        repository.chat(uid, CoachRole.COACH).onStart { emit(emptyList()) },
        task.combine(tableRound) { task, round -> task to round },
        error,
    ) { desk, nutri, chat, (task, round), error ->
        CoachUiState(
            loaded = true,
            plan = desk.plan?.takeIf { it.weekStart == thisWeek() },
            chat = chat,
            nutriNote = nutri.brief?.noteForPartner.orEmpty(),
            task = task,
            round = if (task == CoachTask.PLANNING) round else 0,
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

    /**
     * The three sport coaches write the week at one table. First round: each
     * proposes its own sessions, in parallel, inside the regia's limits. The
     * app joins the three and checks them; if a rule is broken, a second
     * round shows each coach the others' proposals and what to fix. What is
     * still open after that the app settles itself, and says so.
     */
    fun planWeek() = run(CoachTask.PLANNING) {
        val weekStart = thisWeek()
        val nutri = repository.nutriDesk(uid).first()
        val recent = state.value.chat.takeLast(RECENT_MESSAGES).map { it.message }
        val data = athlete.current()
        val today = data.today
        val budget = TriRegia.budget(data.profile, data.availability, RecentTraining.of(data.activities, weekStart, data.zone), weekStart)
        val regia = TriRegia.describe(budget, data.availability)
        val described = AthleteContext.describe(data)
        val past = CoachTable.pastDays(data.activities, weekStart, today, data.zone)
        val done = CoachTable.doneMinutes(past)

        suspend fun ask(first: List<SportProposal>, conflicts: List<RegiaConflict>): List<SportProposal> = coroutineScope {
            TriRegia.SPORTS.map { sport ->
                async {
                    val request = SportCoachPrompts.weekRequest(
                        sport = sport,
                        athlete = described,
                        weekStart = weekStart,
                        today = today,
                        regia = regia,
                        budget = budget,
                        doneMinutes = done[sport] ?: 0,
                        nutri = nutri.brief,
                        others = first.filter { it.sport != sport },
                        own = first.firstOrNull { it.sport == sport },
                        conflicts = conflicts,
                        recentChat = recent,
                    )
                    sportCoaches.getValue(sport).plan(request)
                }
            }.awaitAll()
        }
        fun join(proposals: List<SportProposal>) =
            CoachTable.merge(weekStart, today, past, proposals, budget, data.profile, data.availability)

        tableRound.value = 1
        var proposals = ask(emptyList(), emptyList())
        var plan = join(proposals)
        var rounds = 1
        val conflicts = TriRegia.check(plan, budget, data.availability, today, data.profile)
        if (conflicts.isNotEmpty()) {
            tableRound.value = 2
            try {
                proposals = ask(proposals, conflicts)
                plan = join(proposals)
                rounds = 2
            } catch (e: CoachException) {
                // The first round stands: the app settles it below.
            }
        }
        val (settled, fixes) = CoachTable.settle(plan, budget, data.availability, data.profile, today)
        // Three empty answers on a week with nothing done yet is not a plan.
        if (past.all { it.sessions.isEmpty() } && settled.days.all { it.sessions.isEmpty() }) {
            throw CoachException(CoachException.Reason.BAD_ANSWER)
        }
        val minutes = CoachTable.priority(budget).mapNotNull { sport ->
            val proposal = proposals.firstOrNull { it.sport == sport } ?: return@mapNotNull null
            val text = proposal.note.ifBlank { proposal.rationale }
            TableVoice.of(sport)?.takeIf { text.isNotBlank() }?.let { TableNote(it, text) }
        } + fixes + listOfNotNull(nutri.brief?.noteForPartner?.takeIf { it.isNotBlank() }?.let { TableNote(TableVoice.NUTRI, it) })
        val agreed = settled.copy(table = minutes, rounds = rounds)
        repository.saveWeekPlan(uid, agreed, CoachBriefs.of(agreed, today))
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
        val proposal = entry.proposal?.let { CoachMapper.weekPlanFromFields(it, thisWeek()) } ?: return@run
        val plan = keepWorkouts(proposal, repository.coachDesk(uid).first().plan?.takeIf { it.weekStart == thisWeek() })
        repository.saveWeekPlan(uid, plan, CoachBriefs.of(plan, LocalDate.now(zone)))
        repository.setProposalStatus(uid, CoachRole.COACH, entry.id, ProposalStatus.APPLIED)
    }

    /**
     * A week proposed in the chat is written as text. Sessions it leaves as
     * they were (same day, sport and title) keep the structured workout the
     * sport coaches wrote, and the week keeps the regia's numbers.
     */
    private fun keepWorkouts(proposal: WeekPlan, current: WeekPlan?): WeekPlan {
        current ?: return proposal
        return proposal.copy(
            days = proposal.days.map { day ->
                val before = current.day(day.date)?.sessions.orEmpty()
                day.copy(sessions = day.sessions.map { session ->
                    before.firstOrNull { it.steps.isNotEmpty() && it.sport == session.sport && it.title.equals(session.title, ignoreCase = true) } ?: session
                })
            },
            availableMin = current.availableMin,
            maxHard = current.maxHard,
        )
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
                tableRound.value = 0
                task.value = null
            }
        }
    }

    private companion object {
        const val RECENT_MESSAGES = 6
    }
}
