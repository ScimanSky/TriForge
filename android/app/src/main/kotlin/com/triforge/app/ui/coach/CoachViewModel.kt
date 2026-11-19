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
import com.triforge.domain.coach.AthleteData
import com.triforge.domain.coach.CoachBriefs
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.CoachPrompts
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
import com.triforge.domain.coach.WeekReview
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
    /** One chat per sport coach. */
    val chats: Map<Sport, List<ChatEntry>> = emptyMap(),
    /** NutriAI's latest note for the Coach, shown so the athlete sees them work together. */
    val nutriNote: String = "",
    val task: CoachTask? = null,
    /** While planning: which round of the coaches' table is running, 1 or 2. */
    val round: Int = 0,
    /** While replying: the coach that is writing. */
    val replying: Sport? = null,
    val error: CoachException.Reason? = null,
)

/**
 * The training coaches' screen. The week is written by the three sport
 * coaches at one table, and each of them has its own chat with the athlete.
 * NutriAI is never called from here: only its brief on the shared board is
 * read.
 */
class CoachViewModel(
    private val uid: String,
    private val sportCoaches: Map<Sport, CoachAgent<SportProposal>>,
    private val repository: CoachRepository,
    private val athlete: AthleteSource,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val task = MutableStateFlow<CoachTask?>(null)
    private val error = MutableStateFlow<CoachException.Reason?>(null)
    private val tableRound = MutableStateFlow(0)
    private val replying = MutableStateFlow<Sport?>(null)
    private var autoPlanTried = false

    private fun thisWeek(): LocalDate = TrainingStats.weekStart(LocalDate.now(zone))

    val state: StateFlow<CoachUiState> = combine(
        repository.coachDesk(uid).onStart { emit(CoachDesk()) },
        repository.nutriDesk(uid).onStart { emit(NutriDesk()) },
        combine(TriRegia.SPORTS.map { sport -> repository.chat(uid, sport).onStart { emit(emptyList()) } }) { chats ->
            TriRegia.SPORTS.zip(chats.toList()).toMap()
        },
        combine(task, tableRound, replying) { task, round, sport -> Triple(task, round, sport) },
        error,
    ) { desk, nutri, chats, (task, round, sport), error ->
        CoachUiState(
            loaded = true,
            plan = desk.plan?.takeIf { it.weekStart == thisWeek() },
            chats = chats,
            nutriNote = nutri.brief?.noteForPartner.orEmpty(),
            task = task,
            round = if (task == CoachTask.PLANNING) round else 0,
            replying = sport.takeIf { task == CoachTask.REPLYING },
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
     * The sport coaches write the week at one table. First round: each
     * proposes its own sessions, in parallel, inside the regia's limits. The
     * app joins them with the athlete's own sessions (the gym, a sport done
     * with a trainer) and checks the whole; if a rule is broken, a second
     * round shows each coach the others' proposals and what to fix. What is
     * still open after that the app settles itself, and says so.
     *
     * A sport in pause has nobody at the table. The coach of a sport done
     * with a trainer writes nothing: it reads the sessions done and comments
     * them in the minutes.
     */
    fun planWeek() = run(CoachTask.PLANNING) {
        val weekStart = thisWeek()
        val nutri = repository.nutriDesk(uid).first()
        val chats = state.value.chats
        val data = athlete.current()
        val today = data.today
        val budget = weekBudget(data, weekStart)
        val regia = TriRegia.describe(budget, data.availability)
        val described = AthleteContext.describe(data)
        val past = CoachTable.pastDays(data.activities, weekStart, today, data.zone)
        val done = CoachTable.doneMinutes(past)
        // The week as written so far: its days gone are kept, and the coaches are told what was done of it.
        val previous = repository.coachDesk(uid).first().plan?.takeIf { it.weekStart == weekStart }
        val review = previous?.let { reviewOf(it, data) }.orEmpty()
        val writers = budget.coached
        val readers = TriRegia.SPORTS.filter { it in budget.own }

        suspend fun ask(first: List<SportProposal>, comments: List<SportProposal>, conflicts: List<RegiaConflict>): List<SportProposal> = coroutineScope {
            writers.map { sport ->
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
                        others = (first + comments).filter { it.sport != sport },
                        own = first.firstOrNull { it.sport == sport },
                        conflicts = conflicts,
                        recentChat = chats[sport].orEmpty().takeLast(RECENT_MESSAGES).map { it.message },
                        review = review,
                    )
                    sportCoaches.getValue(sport).plan(request)
                }
            }.awaitAll()
        }

        /** What the coaches of the sports done with a trainer say of the sessions done. A week does not fail for a comment. */
        suspend fun read(): List<SportProposal> = coroutineScope {
            readers.map { sport ->
                async {
                    try {
                        val request = SportCoachPrompts.reviewRequest(sport, described, today, regia, nutri.brief, review)
                        sportCoaches.getValue(sport).plan(request).copy(sport = sport, days = emptyList())
                    } catch (e: CoachException) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        tableRound.value = 1
        val (first, comments) = coroutineScope {
            val reading = async { read() }
            ask(emptyList(), emptyList(), emptyList()) to reading.await()
        }
        fun join(proposals: List<SportProposal>) =
            CoachTable.merge(weekStart, today, past, proposals + comments, budget, data.profile, data.availability)

        var proposals = first
        var plan = join(proposals)
        var rounds = 1
        val conflicts = TriRegia.check(plan, budget, data.availability, today, data.profile)
        if (conflicts.isNotEmpty() && writers.isNotEmpty()) {
            tableRound.value = 2
            try {
                proposals = ask(proposals, comments, conflicts)
                plan = join(proposals)
                rounds = 2
            } catch (e: CoachException) {
                // The first round stands: the app settles it below.
            }
        }
        val (settled, fixes) = CoachTable.settle(plan, budget, data.availability, data.profile, today)
        // Empty answers from every coach on a week with nothing done yet is not a plan.
        if (writers.isNotEmpty() && budget.totalMinutes > 0 && past.all { it.sessions.isEmpty() } && settled.days.all { day -> day.sessions.none { !it.own } }) {
            throw CoachException(CoachException.Reason.BAD_ANSWER)
        }
        val said = proposals + comments
        val minutes = listOfNotNull(CoachTable.opening(budget, data.availability)) + CoachTable.priority(budget).mapNotNull { sport ->
            val proposal = said.firstOrNull { it.sport == sport } ?: return@mapNotNull null
            val text = proposal.note.ifBlank { proposal.rationale }
            TableVoice.of(sport)?.takeIf { text.isNotBlank() }?.let { TableNote(it, text) }
        } + fixes + listOfNotNull(nutri.brief?.noteForPartner?.takeIf { it.isNotBlank() }?.let { TableNote(TableVoice.NUTRI, it) })
        // The regia checked the week against what was done; what is saved keeps what was planned for the days gone.
        val agreed = CoachTable.keepPlanned(settled, previous, today).copy(table = minutes, rounds = rounds)
        repository.saveWeekPlan(uid, agreed, CoachBriefs.of(agreed, today))
    }

    /** The days gone of [plan] in words: what was planned and what the athlete did of it. */
    private fun reviewOf(plan: WeekPlan, data: AthleteData): String =
        WeekReview.of(plan, data.activities, data.today, data.zone).describe(data.today)

    /** A message to one sport coach, in its own chat. */
    fun send(sport: Sport, text: String) {
        val message = text.trim()
        if (message.isEmpty() || task.value != null) return
        replying.value = sport
        run(CoachTask.REPLYING) {
            val history = state.value.chats[sport].orEmpty().map { it.message }
            repository.addMessage(uid, sport, fromAthlete = true, text = message)
            if (NutriSafety.flags(message)) {
                repository.addMessage(uid, sport, fromAthlete = false, text = NutriSafety.REPLY)
                return@run
            }
            val weekStart = thisWeek()
            val data = athlete.current()
            val today = data.today
            val budget = weekBudget(data, weekStart)
            val nutri = repository.nutriDesk(uid).first()
            val plan = repository.coachDesk(uid).first().plan?.takeIf { it.weekStart == weekStart }
            val turn = SportCoachPrompts.chatTurn(
                sport = sport,
                athlete = AthleteContext.describe(data),
                weekStart = weekStart,
                today = today,
                week = plan?.let(CoachPrompts::describe),
                regia = TriRegia.describe(budget, data.availability),
                budget = budget,
                nutri = nutri.brief,
                message = message,
                review = plan?.let { reviewOf(it, data) }.orEmpty(),
            )
            val reply = sportCoaches.getValue(sport).reply(history, turn)
            // Kept as the athlete will see it: only this coach's sessions from today on, with targets and totals.
            val card = reply.proposal
                ?.takeIf { plan != null && it.days.isNotEmpty() }
                ?.let { it.copy(note = reply.noteForPartner.ifBlank { it.note }) }
                ?.let { CoachTable.preview(it, weekStart, today, budget, data.profile, data.availability) }
                ?.takeIf { week -> week.days.any { it.sessions.isNotEmpty() } }
            repository.addMessage(
                uid,
                sport,
                fromAthlete = false,
                text = reply.text,
                proposal = card?.let(CoachMapper::weekPlanToFields),
                handoff = reply.handoff.takeIf { it.isNotBlank() },
            )
        }
    }

    /**
     * Applies a change one coach proposed in its chat: its sessions are
     * replaced, the regia checks the whole week again, and NutriAI sees the
     * new week on the board.
     */
    fun apply(sport: Sport, entry: ChatEntry) = run(null) {
        val weekStart = thisWeek()
        val proposal = entry.proposal?.let { CoachMapper.sportProposalFromFields(it, sport, weekStart) } ?: return@run
        val current = repository.coachDesk(uid).first().plan?.takeIf { it.weekStart == weekStart } ?: return@run
        val data = athlete.current()
        val revised = CoachTable.revise(
            current = current,
            proposal = proposal,
            today = data.today,
            budget = weekBudget(data, weekStart),
            profile = data.profile,
            availability = data.availability,
            // The regia checks the change against what was really done in the days gone.
            done = CoachTable.pastDays(data.activities, weekStart, data.today, data.zone),
        )
        repository.saveWeekPlan(uid, revised, CoachBriefs.of(revised, data.today))
        repository.setProposalStatus(uid, sport, entry.id, ProposalStatus.APPLIED)
    }

    fun dismiss(sport: Sport, entry: ChatEntry) = run(null) {
        repository.setProposalStatus(uid, sport, entry.id, ProposalStatus.DISMISSED)
    }

    /** The regia's budget for the week starting on [weekStart]. */
    private fun weekBudget(data: AthleteData, weekStart: LocalDate): WeekBudget =
        TriRegia.budget(data.profile, data.availability, RecentTraining.of(data.activities, weekStart, data.zone), weekStart)

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
                replying.value = null
                task.value = null
            }
        }
    }

    private companion object {
        const val RECENT_MESSAGES = 6
    }
}
