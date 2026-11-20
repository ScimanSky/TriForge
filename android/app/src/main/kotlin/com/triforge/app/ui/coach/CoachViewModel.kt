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
import com.triforge.domain.CssCheck
import com.triforge.domain.FitnessTrend
import com.triforge.domain.Sport
import com.triforge.domain.TrainingStats
import com.triforge.domain.coach.AthleteContext
import com.triforge.domain.coach.AthleteData
import com.triforge.domain.coach.CoachBriefs
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.CoachPrompts
import com.triforge.domain.coach.CoachTable
import com.triforge.domain.coach.NoticeLine
import com.triforge.domain.coach.NutriSafety
import com.triforge.domain.coach.PlanUndo
import com.triforge.domain.coach.PreviewLine
import com.triforge.domain.coach.ProposalPreview
import com.triforge.domain.coach.RecentTraining
import com.triforge.domain.coach.RegiaConflict
import com.triforge.domain.coach.Revision
import com.triforge.domain.coach.SportCoachPrompts
import com.triforge.domain.coach.SportProposal
import com.triforge.domain.coach.TableNote
import com.triforge.domain.coach.TableVoice
import com.triforge.domain.coach.TodayAdvice
import com.triforge.domain.coach.TodayCard
import com.triforge.domain.coach.TriRegia
import com.triforge.domain.coach.WeekBudget
import com.triforge.domain.coach.WeekPlan
import com.triforge.domain.coach.WeekReview
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CoachTask { PLANNING, REPLYING }

/**
 * A coach's proposal as the athlete reads it before accepting: worked out
 * on the week as it is now, without touching it.
 */
data class ProposalView(
    /** The coach's own sessions from today on, each with what happens to it. */
    val lines: List<PreviewLine>,
    /** «Cambia anche»: what accepting does to the other sessions of the week. */
    val also: List<NoticeLine>,
    /** The regia's reasons for it. */
    val reasons: List<String>,
    /** What it means for NutriAI, or null when no day changes. */
    val nutri: String?,
)

/** What Oggi shows of the coaches' work. */
data class TodayUi(
    /** «Oggi tocca a»; null without a week. */
    val card: TodayCard? = null,
    /**
     * The swim threshold does not fit the swims: while it is so, load and
     * form of the swim may be higher than the truth.
     */
    val cssCheck: CssCheck? = null,
    /** «Lascia per ora»: the notice is not shown for three weeks; form still says its numbers are in doubt. */
    val cssSnoozed: Boolean = false,
)

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
    /** The proposals still to accept, by message, as the athlete reads them. */
    val previews: Map<String, ProposalView> = emptyMap(),
    /** The week before the last change accepted in a chat, while «Annulla» can put it back. */
    val undo: PlanUndo? = null,
    /** What the app changed in the week when the profile changed, until «Va bene». */
    val notice: List<NoticeLine> = emptyList(),
    val today: TodayUi = TodayUi(),
    /** A coach's chat to show, asked from another screen; [CoachViewModel.chatOpened] once shown. */
    val openChat: Sport? = null,
) {
    /**
     * The athlete's last message to the coach of [sport], when it is still
     * without an answer and nobody is writing one: «Riprova» asks again
     * with it, so it never has to be typed twice.
     */
    fun unanswered(sport: Sport): ChatEntry? =
        chats[sport].orEmpty().lastOrNull()?.takeIf { it.message.fromAthlete && !(task == CoachTask.REPLYING && replying == sport) }
}

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
    private val chatRequest = MutableStateFlow<Sport?>(null)
    private var autoPlanTried = false

    private fun thisWeek(): LocalDate = TrainingStats.weekStart(LocalDate.now(zone))

    /**
     * Where a week can still be written from: [day] is today, or tomorrow
     * once the evening is gone, and [now] the time when [day] is today. The
     * coaches and the regia never write in a time already past.
     */
    private class Clock(val day: LocalDate, val now: LocalTime?)

    private fun clock(today: LocalDate): Clock {
        val time = LocalTime.now(zone)
        val day = CoachTable.planningDay(today, time)
        return Clock(day, time.takeIf { day == today })
    }

    init {
        // "Giorni, orari e gara" changed (a sport in pause, the gym days): the week follows at once, and says so.
        viewModelScope.launch {
            athlete.data.map { it.availability to it.profile }.distinctUntilChanged().collect {
                try {
                    alignWithProfile()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The week stays as it is: the next change, or «Rifai la settimana», brings it in line.
                }
            }
        }
    }

    /**
     * Brings the week in line with the profile without asking any coach:
     * see [CoachTable.realign]. What it changes is written in the minutes.
     */
    private suspend fun alignWithProfile() {
        if (task.value != null) return
        val weekStart = thisWeek()
        val current = repository.coachDesk(uid).first().plan?.takeIf { it.weekStart == weekStart && it.fromTable } ?: return
        val data = athlete.current()
        val clock = clock(data.today)
        val revision = CoachTable.realign(current, clock.day, weekBudget(data, weekStart), data.profile, data.availability, clock.now) ?: return
        // Said once on the week: what was taken off and put in, and why.
        val notice = ProposalPreview.notice(revision.changes, data.availability)
        repository.saveWeekPlan(uid, revision.plan, CoachBriefs.of(revision.plan, data.today), notice = notice)
    }

    /** «Va bene» on what the app changed in the week. */
    fun dismissNotice() = quietly { repository.dismissNotice(uid) }

    /** What is stored, with what the app works out of it: read together, off the main thread. */
    private class Desks(
        val coach: CoachDesk,
        val nutri: NutriDesk,
        val chats: Map<Sport, List<ChatEntry>>,
        val previews: Map<String, ProposalView>,
        val today: TodayUi,
    )

    /** Oggi's card follows the clock: a session whose time is gone gives way to tomorrow's. */
    private val clockTicks: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(CLOCK_TICK_MS)
        }
    }

    private val desks: Flow<Desks> = combine(
        repository.coachDesk(uid).onStart { emit(CoachDesk()) },
        repository.nutriDesk(uid).onStart { emit(NutriDesk()) },
        combine(TriRegia.SPORTS.map { sport -> repository.chat(uid, sport).onStart { emit(emptyList()) } }) { chats ->
            TriRegia.SPORTS.zip(chats.toList()).toMap()
        },
        athlete.data.map<AthleteData, AthleteData?> { it }.onStart { emit(null) },
        clockTicks,
    ) { coach, nutri, chats, data, _ ->
        val plan = coach.plan?.takeIf { it.weekStart == thisWeek() }
        Desks(coach, nutri, chats, previews(plan, nutri, chats, data), today(plan, coach, nutri, data))
    }.flowOn(Dispatchers.Default)

    /** Every proposal that can still be accepted, read on the week as it is now. */
    private fun previews(plan: WeekPlan?, nutri: NutriDesk, chats: Map<Sport, List<ChatEntry>>, data: AthleteData?): Map<String, ProposalView> {
        if (plan == null || data == null) return emptyMap()
        val clock = clock(data.today)
        val prepared = nutri.days.filterValues { it.meals.isNotEmpty() }.keys
        return chats.flatMap { (sport, chat) ->
            chat.filter { it.status == ProposalStatus.PENDING || it.status == ProposalStatus.UNDONE }.mapNotNull { entry ->
                val revision = try {
                    revision(entry, sport, plan, data, clock)
                } catch (e: Exception) {
                    // A proposal that cannot be read on this week is shown as it was written.
                    null
                } ?: return@mapNotNull null
                entry.id to ProposalView(
                    lines = ProposalPreview.lines(revision, sport, clock.day),
                    also = ProposalPreview.also(revision, sport),
                    reasons = revision.notes,
                    nutri = ProposalPreview.nutri(revision.changes, prepared),
                )
            }
        }.toMap()
    }

    /** What accepting [entry] does to [current]: see [CoachTable.revision]. */
    private fun revision(entry: ChatEntry, sport: Sport, current: WeekPlan, data: AthleteData, clock: Clock): Revision? {
        val proposal = entry.proposal?.let { CoachMapper.sportProposalFromFields(it, sport, current.weekStart) } ?: return null
        return CoachTable.revision(
            current = current,
            proposal = proposal,
            today = clock.day,
            budget = weekBudget(data, current.weekStart),
            profile = data.profile,
            availability = data.availability,
            // The regia checks the change against what was really done in the days gone.
            done = CoachTable.pastDays(data.activities, current.weekStart, clock.day, data.zone),
            now = clock.now,
        )
    }

    private fun today(plan: WeekPlan?, coach: CoachDesk, nutri: NutriDesk, data: AthleteData?): TodayUi {
        data ?: return TodayUi()
        val check = data.cssCheck
        val card = TodayAdvice.card(
            plan = plan,
            activities = data.activities,
            today = data.today,
            zone = data.zone,
            now = LocalTime.now(zone),
            recovery = data.recovery?.today,
            form = data.recovery?.let { FitnessTrend.from(it.history).latest?.zone },
            availability = data.availability,
            nutrition = nutri.days,
            loadInDoubt = check != null,
        )
        val snoozed = coach.cssSnoozedUntil?.let { data.today.isBefore(it) } == true
        return TodayUi(card, check, cssSnoozed = snoozed)
    }

    val state: StateFlow<CoachUiState> = combine(
        desks,
        combine(task, tableRound, replying) { task, round, sport -> Triple(task, round, sport) },
        error,
        chatRequest,
    ) { desks, (task, round, sport), error, request ->
        val desk = desks.coach
        val nutri = desks.nutri
        val plan = desk.plan?.takeIf { it.weekStart == thisWeek() }
        CoachUiState(
            loaded = true,
            plan = plan,
            chats = desks.chats,
            // NutriAI's note was written for the week as it was: after a change of plan it waits for NutriAI's next word.
            nutriNote = nutri.brief?.takeIf { brief -> desk.plan?.createdAt?.let { changed -> brief.updatedAt?.isBefore(changed) } != true }?.noteForPartner.orEmpty(),
            task = task,
            round = if (task == CoachTask.PLANNING) round else 0,
            replying = sport.takeIf { task == CoachTask.REPLYING },
            error = error,
            previews = desks.previews,
            undo = desk.undo?.takeIf { plan != null && it.plan.weekStart == plan.weekStart },
            notice = desk.notice.takeIf { plan != null }.orEmpty(),
            today = desks.today,
            openChat = request,
        )
    }
        .catch { emit(CoachUiState(loaded = true, error = CoachException.Reason.UNAVAILABLE)) }
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
        val clock = clock(data.today)
        // Late in the evening the week is written from tomorrow: today is as it went.
        val today = clock.day
        // Sunday night: nothing of this week is left to write.
        if (today.isAfter(weekStart.plusDays(6))) return@run
        val budget = weekBudget(data, weekStart)
        val regia = TriRegia.describe(budget, data.availability)
        val described = AthleteContext.describe(data, LocalTime.now(zone))
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
        // One of the athlete's own sessions whose time today is gone is kept only if it was already on the calendar.
        val keptToday = previous?.day(today)?.sessions.orEmpty().filter { it.own }
        fun join(proposals: List<SportProposal>) =
            CoachTable.merge(weekStart, today, past, proposals + comments, budget, data.profile, data.availability, now = clock.now, keptToday = keptToday)

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
        repository.saveWeekPlan(uid, agreed, CoachBriefs.of(agreed, data.today))
    }

    /** The days gone of [plan] in words: what was planned and what the athlete did of it. */
    private fun reviewOf(plan: WeekPlan, data: AthleteData): String =
        WeekReview.of(plan, data.activities, data.today, data.zone).describe(data.today)

    /** A message to one sport coach, in its own chat. */
    fun send(sport: Sport, text: String) = ask(sport, text.trim(), add = true)

    /**
     * «Riprova»: asks the coach of [sport] again with the athlete's last
     * message, which is already in the chat and is not written twice.
     */
    fun retry(sport: Sport) {
        val last = state.value.unanswered(sport) ?: return
        ask(sport, last.message.text, add = false)
    }

    private fun ask(sport: Sport, message: String, add: Boolean) {
        if (message.isEmpty() || task.value != null) return
        replying.value = sport
        run(CoachTask.REPLYING) {
            val chat = state.value.chats[sport].orEmpty().map { it.message }
            // The message being answered travels in the turn, not in the history.
            val history = if (!add && chat.lastOrNull()?.let { it.fromAthlete && it.text == message } == true) chat.dropLast(1) else chat
            if (add) repository.addMessage(uid, sport, fromAthlete = true, text = message)
            if (NutriSafety.flags(message)) {
                repository.addMessage(uid, sport, fromAthlete = false, text = NutriSafety.REPLY)
                return@run
            }
            val weekStart = thisWeek()
            val data = athlete.current()
            // A change can only start from a day that is not over yet.
            val today = clock(data.today).day
            val budget = weekBudget(data, weekStart)
            val nutri = repository.nutriDesk(uid).first()
            val plan = repository.coachDesk(uid).first().plan?.takeIf { it.weekStart == weekStart }
            val turn = SportCoachPrompts.chatTurn(
                sport = sport,
                athlete = AthleteContext.describe(data, LocalTime.now(zone)),
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
     * new week on the board. The week as it was is kept until the next
     * change, for «Annulla».
     */
    fun apply(sport: Sport, entry: ChatEntry) = run(null) {
        val weekStart = thisWeek()
        val current = repository.coachDesk(uid).first().plan?.takeIf { it.weekStart == weekStart } ?: return@run
        val data = athlete.current()
        val clock = clock(data.today)
        val revision = revision(entry, sport, current, data, clock) ?: return@run
        val prepared = repository.nutriDesk(uid).first().days.filterValues { it.meals.isNotEmpty() }.keys
        val undo = PlanUndo(
            plan = current,
            sport = sport,
            messageId = entry.id,
            summary = ProposalPreview.summary(revision, sport, clock.day),
            nutri = ProposalPreview.nutri(revision.changes, prepared).orEmpty(),
        )
        repository.saveWeekPlan(uid, revision.plan, CoachBriefs.of(revision.plan, data.today), undo = undo.takeIf { revision.changes.isNotEmpty() })
        repository.setProposalStatus(uid, sport, entry.id, ProposalStatus.APPLIED)
    }

    /**
     * «Annulla»: the week goes back to what it was before [entry] was
     * accepted, for every coach and for NutriAI. The proposal stays in the
     * chat and can be accepted again.
     */
    fun undo(sport: Sport, entry: ChatEntry) = run(null) {
        val undo = repository.coachDesk(uid).first().undo
            ?.takeIf { it.messageId == entry.id && it.sport == sport && it.plan.weekStart == thisWeek() } ?: return@run
        val data = athlete.current()
        repository.saveWeekPlan(uid, undo.plan, CoachBriefs.of(undo.plan, data.today))
        repository.setProposalStatus(uid, sport, entry.id, ProposalStatus.UNDONE)
    }

    fun dismiss(sport: Sport, entry: ChatEntry) = run(null) {
        repository.setProposalStatus(uid, sport, entry.id, ProposalStatus.DISMISSED)
    }

    /** Shows the chat of [sport]'s coach when the Coach tab opens. */
    fun openChat(sport: Sport) {
        chatRequest.value = sport
    }

    fun chatOpened() {
        chatRequest.value = null
    }

    /**
     * «Metti il test in settimana», from the page of the swim threshold:
     * the athlete's request goes to the swim coach, in its chat.
     */
    fun askSwimTest(message: String) {
        openChat(Sport.SWIM)
        send(Sport.SWIM, message)
    }

    /** «Lascia per ora» on the swim threshold: the notice comes back in three weeks, if the swims still say so. */
    fun snoozeCssCheck() = quietly { repository.snoozeCssCheck(uid, LocalDate.now(zone).plusDays(CSS_SNOOZE_DAYS)) }

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
                error.value = CoachException.Reason.UNAVAILABLE
            } finally {
                tableRound.value = 0
                replying.value = null
                task.value = null
            }
        }
    }

    /** A small write that is no coach's task: if it fails, what it was about is simply shown again. */
    private fun quietly(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing to tell the athlete.
            }
        }
    }

    private companion object {
        const val RECENT_MESSAGES = 6
        const val CLOCK_TICK_MS = 5 * 60_000L
        const val CSS_SNOOZE_DAYS = 21L
    }
}
