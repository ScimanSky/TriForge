package com.triforge.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triforge.app.ai.CoachAgent
import com.triforge.app.ai.CoachException
import com.triforge.app.ai.DishAdvisor
import com.triforge.app.data.ChatEntry
import com.triforge.app.data.CoachDesk
import com.triforge.app.data.CoachRepository
import com.triforge.app.data.NutriDesk
import com.triforge.app.data.ProposalStatus
import com.triforge.domain.TrainingStats
import com.triforge.domain.coach.AthleteContext
import com.triforge.domain.coach.AthleteData
import com.triforge.domain.coach.BanHit
import com.triforge.domain.coach.BanViolation
import com.triforge.domain.coach.BodyData
import com.triforge.domain.coach.CoachBrief
import com.triforge.domain.coach.CoachBriefs
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.CoachPrompts
import com.triforge.domain.coach.CoachRole
import com.triforge.domain.coach.DayContext
import com.triforge.domain.coach.DayTargets
import com.triforge.domain.coach.Dish
import com.triforge.domain.coach.DishKey
import com.triforge.domain.coach.FoodBans
import com.triforge.domain.coach.MealSuggestion
import com.triforge.domain.coach.NutriEnergy
import com.triforge.domain.coach.NutriFueling
import com.triforge.domain.coach.NutriMeals
import com.triforge.domain.coach.NutriSafety
import com.triforge.domain.coach.NutritionDay
import com.triforge.domain.coach.NutritionProfile
import com.triforge.domain.coach.PlannedSession
import com.triforge.domain.coach.TypedDish
import com.triforge.domain.coach.WeekPlan
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One day of the week on NutriAI's screen. */
data class NutriDayView(
    val date: LocalDate,
    /** The coaches' sessions of the day, rest left out. */
    val sessions: List<PlannedSession> = emptyList(),
    /** The app's numbers for the day, always current; null without a weight. */
    val targets: DayTargets? = null,
    /** What NutriAI prepared for the day, only if the athlete asked. */
    val day: NutritionDay? = null,
    /** The dishes the food profile bans now, by position. */
    val flags: Map<DishKey, BanHit> = emptyMap(),
    /** The training changed after the meals were written: their numbers are no more the day's. */
    val outdated: Boolean = false,
) {
    val hasMeals: Boolean get() = day?.meals?.isNotEmpty() == true

    /** Meals the athlete should look at again: NutriAI does not rewrite them by itself. */
    val toReview: Boolean get() = hasMeals && (outdated || flags.isNotEmpty())
}

/** The screen where the athlete changes one dish of a meal. */
data class ChangeDishUi(
    val date: LocalDate,
    val key: DishKey,
    val mealName: String,
    /** The dish to replace. */
    val target: Dish,
    /** NutriAI's alternatives, already checked and scaled by the app. */
    val alternatives: List<Dish> = emptyList(),
    val loading: Boolean = true,
    /** NutriAI was asked again and had nothing new. */
    val exhausted: Boolean = false,
    val picked: Int? = null,
    val checking: Boolean = false,
    /** The app's answer to the dish the athlete wrote. */
    val typed: TypedDish? = null,
    /** Whether the food NutriAI advises beside a lighter dish goes in with it. */
    val withComplement: Boolean = true,
    /** The text showed a sign NutriAI must not answer with food: the fixed reply is shown. */
    val unsafe: Boolean = false,
    val saving: Boolean = false,
    val error: CoachException.Reason? = null,
    /** Every alternative proposed so far, not to be proposed again. */
    internal val seen: List<String> = emptyList(),
) {
    /** The dish «Sostituisci» would put in: the one written, else the alternative picked. */
    val chosen: Dish? get() = (typed as? TypedDish.Ready)?.dish ?: picked?.let(alternatives::getOrNull)
}

/** A dish just replaced, for «Annulla». */
data class NutriUndo(val date: LocalDate, val mealName: String, internal val before: NutritionDay)

data class NutriUiState(
    val loaded: Boolean = false,
    /** Null until the athlete has told NutriAI how they eat. */
    val profile: NutritionProfile? = null,
    val today: LocalDate = LocalDate.now(),
    val selected: LocalDate = today,
    /** Monday to Sunday of this week. */
    val week: List<NutriDayView> = emptyList(),
    /** The app cannot work out the numbers: the athlete's weight is missing. */
    val needsWeight: Boolean = false,
    val coachNote: String = "",
    val chat: List<ChatEntry> = emptyList(),
    val task: CoachTask? = null,
    /** The day NutriAI is writing now. */
    val preparing: LocalDate? = null,
    val error: CoachException.Reason? = null,
    val change: ChangeDishUi? = null,
    val undo: NutriUndo? = null,
) {
    val day: NutriDayView? get() = week.firstOrNull { it.date == selected }

    /** The days from today to Sunday that still have no meals. */
    val toPrepare: Int get() = week.count { !it.date.isBefore(today) && !it.hasMeals }
}

/**
 * NutriAI's screen. It drives one agent, NutriAI, which builds on the plan
 * the coaches published on the shared board and leaves its own brief there.
 *
 * NutriAI writes meals only when the athlete asks: for a day, or for the
 * days of the week still without. The numbers of every day are the app's and
 * always current; when the training or the food profile changes, the meals
 * already written are marked and the athlete decides whether to redo them.
 */
class NutriViewModel(
    private val uid: String,
    private val agent: CoachAgent<NutritionDay>,
    private val advisor: DishAdvisor,
    private val repository: CoachRepository,
    private val athlete: AthleteSource,
) : ViewModel() {

    /** What only this screen knows: what is selected, running or open. */
    private data class Local(
        val selected: LocalDate? = null,
        val task: CoachTask? = null,
        val preparing: LocalDate? = null,
        val error: CoachException.Reason? = null,
        val change: ChangeDishUi? = null,
        val undo: NutriUndo? = null,
    )

    private val zone = ZoneId.systemDefault()
    private val local = MutableStateFlow(Local())
    private var alternativesJob: Job? = null
    private var typedJob: Job? = null

    private fun today(): LocalDate = LocalDate.now(zone)

    val state: StateFlow<NutriUiState> = combine(
        repository.nutriDesk(uid).map<NutriDesk, NutriDesk?> { it }.onStart { emit(null) },
        repository.coachDesk(uid).onStart { emit(CoachDesk()) },
        repository.chat(uid, CoachRole.NUTRI_AI).onStart { emit(emptyList()) },
        athlete.data.map<AthleteData, AthleteData?> { it }.onStart { emit(null) },
        local,
    ) { nutri, coach, chat, data, ui ->
        val today = today()
        val monday = TrainingStats.weekStart(today)
        val plan = coach.plan?.takeIf { it.weekStart == monday }
        val profile = nutri?.profile
        val week = (0L..6L).map { monday.plusDays(it) }.map { date ->
            val targets = if (profile != null && data != null) numbers(data, profile, plan, date).targets else null
            val day = nutri?.days?.get(date)
            NutriDayView(
                date = date,
                sessions = plan?.day(date)?.sessions.orEmpty().filter { it.sport != null },
                targets = targets,
                day = day,
                flags = if (day != null && profile != null) NutriMeals.flags(day, profile) else emptyMap(),
                outdated = day != null && targets != null && NutriMeals.outdated(day, targets),
            )
        }
        NutriUiState(
            loaded = nutri != null,
            profile = profile,
            today = today,
            selected = ui.selected?.takeIf { !it.isBefore(monday) && !it.isAfter(monday.plusDays(6)) } ?: today,
            week = week,
            needsWeight = profile != null && data != null && week.all { it.targets == null },
            coachNote = coach.brief?.noteForPartner.orEmpty(),
            chat = chat,
            task = ui.task,
            preparing = ui.preparing,
            error = ui.error,
            change = ui.change,
            undo = ui.undo,
        )
    }
        .catch { emit(NutriUiState(loaded = true, error = CoachException.Reason.NETWORK)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutriUiState())

    fun select(date: LocalDate) = local.update { it.copy(selected = date) }

    // ---- The food profile --------------------------------------------------

    /**
     * Saves how the athlete eats. The meals already written are not
     * rewritten: the screen marks the dishes the new profile bans.
     */
    fun saveProfile(profile: NutritionProfile) = run(null) { repository.saveNutritionProfile(uid, profile) }

    /**
     * Keeps a change made in the profile editor as soon as it is made, so
     * closing the editor without «Salva» loses nothing.
     */
    fun keepProfile(profile: NutritionProfile) = run(null) { repository.saveNutritionProfile(uid, profile) }

    // ---- The app's numbers -------------------------------------------------

    /** The app's numbers for a day: energy and macronutrients, and the fuelling around its sessions. */
    private data class DayNumbers(val targets: DayTargets?, val fueling: String?)

    /**
     * What the app works out for [date] from the athlete's body, the coaches'
     * sessions of the day, those of the day after, and the race: null targets
     * without a weight.
     */
    private fun numbers(data: AthleteData, profile: NutritionProfile, plan: WeekPlan?, date: LocalDate): DayNumbers {
        val body = BodyData(data.profile.weightKg, data.profile.heightCm, data.age, profile.sex)
        val sessions = plan?.day(date)?.sessions.orEmpty()
        val context = DayContext(
            tomorrow = plan?.day(date.plusDays(1))?.sessions.orEmpty(),
            daysToRace = data.profile.raceDate?.let { ChronoUnit.DAYS.between(date, it).toInt() }?.takeIf { it >= 0 },
            race = data.profile.goal,
        )
        // The energy also counts what the watch recorded that day outside the plan: a walk, a hike,
        // a gym session on a day without one. The fuelling stays on the planned sessions.
        val recorded = data.activities.filter { it.startTime.atZone(data.zone).toLocalDate() == date }
        val targets = NutriEnergy.targets(profile, body, NutriEnergy.withDone(sessions, recorded), context)
        val weight = data.profile.weightKg?.takeIf { targets != null }
        return DayNumbers(targets, weight?.let { NutriFueling.describe(sessions, it, context) })
    }

    // ---- Meals, when the athlete asks --------------------------------------

    /** «Prepara i pasti di …», «Rifai i pasti di …» and «Aggiorna i pasti»: NutriAI writes one day. */
    fun prepareDay(date: LocalDate) = run(CoachTask.PLANNING) {
        local.update { it.copy(preparing = date, undo = null) }
        val desk = repository.nutriDesk(uid).first()
        val day = write(date, desk, NutriMeals.mains(thisWeek(desk).filter { it.date != date }))
        save(desk, listOf(day), CoachBriefs.of(day))
    }

    /**
     * «Prepara tutta la settimana»: from today to Sunday, one day after the
     * other, only the days without meals. Each is saved as soon as it is
     * written, so a failure half way keeps what is done.
     */
    fun prepareWeek() = run(CoachTask.PLANNING) {
        val today = today()
        val sunday = TrainingStats.weekStart(today).plusDays(6)
        val desk = repository.nutriDesk(uid).first()
        val written = mutableListOf<NutritionDay>()
        var date = today
        while (!date.isAfter(sunday)) {
            if (desk.days[date]?.meals.isNullOrEmpty()) {
                local.update { it.copy(preparing = date, undo = null) }
                val day = write(date, desk, NutriMeals.mains(thisWeek(desk) + written))
                written += day
                save(desk, listOf(day), CoachBriefs.of(written))
            }
            date = date.plusDays(1)
        }
    }

    /**
     * «Tienili così»: the meals stay as they are and take the day's new
     * numbers, so the screen stops asking.
     */
    fun keepDay(date: LocalDate) = run(null) {
        val targets = state.value.week.firstOrNull { it.date == date }?.targets ?: return@run
        val desk = repository.nutriDesk(uid).first()
        val day = desk.days[date] ?: return@run
        save(desk, listOf(NutriEnergy.apply(day, targets)))
    }

    private fun thisWeek(desk: NutriDesk): List<NutritionDay> {
        val monday = TrainingStats.weekStart(today())
        return desk.days.values.filter { !it.date.isBefore(monday) }
    }

    /**
     * NutriAI writes [date] on the app's numbers. The app makes the answer
     * its own: every meal the sum of its dishes, the dishes the athlete
     * chose back in their place, every dish checked against the profile
     * (NutriAI is asked once more when one breaks it, and what still does is
     * dropped), the quantities brought in line with the day's energy.
     */
    private suspend fun write(date: LocalDate, nutri: NutriDesk, elsewhere: List<String>): NutritionDay {
        val coach = repository.coachDesk(uid).first()
        val profile = nutri.profile ?: NutritionProfile()
        val plan = coach.plan?.takeIf { it.weekStart == TrainingStats.weekStart(date) }
        val data = athlete.current()
        val numbers = numbers(data, profile, plan, date)
        val targets = numbers.targets ?: throw CoachException(CoachException.Reason.NEEDS_WEIGHT)
        // The dishes the athlete chose stay, unless the profile bans them now.
        val old = nutri.days[date]?.let { FoodBans.clean(it, profile) }
        val fixed = old?.let { NutriMeals.fixed(it) }.orEmpty()
        val recent = state.value.chat.takeLast(RECENT_MESSAGES).map { it.message }
        val described = AthleteContext.describe(data)
        fun request(violations: List<BanViolation>) = CoachPrompts.nutritionDayRequest(
            athlete = described,
            date = date,
            profile = profile,
            targets = targets,
            partner = coach.brief,
            plan = plan,
            recentChat = recent,
            violations = violations,
            fueling = numbers.fueling,
            fixed = fixed,
            elsewhere = elsewhere,
        )
        fun made(answer: NutritionDay) = NutriEnergy.apply(NutriMeals.keepFixed(NutriMeals.sanitized(answer.copy(date = date)), old), targets)
        var day = made(agent.plan(request(emptyList())))
        val violations = FoodBans.violations(day, profile)
        if (violations.isNotEmpty()) day = made(agent.plan(request(violations)))
        day = NutriMeals.settle(FoodBans.clean(day, profile))
        if (day.meals.isEmpty()) throw CoachException(CoachException.Reason.BAD_ANSWER)
        return day
    }

    /** Saves [days] and forgets the days of the weeks gone by. */
    private suspend fun save(desk: NutriDesk, days: List<NutritionDay>, brief: CoachBrief? = null) {
        val monday = TrainingStats.weekStart(today())
        repository.saveNutritionDays(uid, days, brief, forget = desk.days.keys.filter { it.isBefore(monday) })
    }

    // ---- Changing a dish ---------------------------------------------------

    /** «Cambia» on a dish: opens the screen and asks NutriAI for alternatives. */
    fun openChange(date: LocalDate, key: DishKey) {
        val meal = state.value.week.firstOrNull { it.date == date }?.day?.meals?.getOrNull(key.meal) ?: return
        val dish = meal.dishes.getOrNull(key.dish) ?: return
        stopChangeJobs()
        local.update { it.copy(change = ChangeDishUi(date, key, meal.name, dish), undo = null) }
        loadAlternatives()
    }

    fun closeChange() {
        stopChangeJobs()
        local.update { it.copy(change = null) }
    }

    private fun stopChangeJobs() {
        alternativesJob?.cancel()
        typedJob?.cancel()
    }

    /** Changes the open screen, unless it was closed or is another dish's by now. */
    private fun changing(of: ChangeDishUi, block: (ChangeDishUi) -> ChangeDishUi) = local.update { state ->
        val open = state.change?.takeIf { it.date == of.date && it.key == of.key } ?: return@update state
        state.copy(change = block(open))
    }

    /** What NutriAI needs to know to reason on one dish. */
    private data class DishContext(val profile: NutritionProfile, val day: NutritionDay, val meal: MealSuggestion, val sessions: String)

    private suspend fun dishContext(change: ChangeDishUi): DishContext {
        val nutri = repository.nutriDesk(uid).first()
        val day = nutri.days[change.date]
        val meal = day?.meals?.getOrNull(change.key.meal) ?: throw CoachException(CoachException.Reason.BAD_ANSWER)
        val plan = repository.coachDesk(uid).first().plan?.takeIf { it.weekStart == TrainingStats.weekStart(change.date) }
        val sessions = plan?.day(change.date)?.let(CoachPrompts::describeDay) ?: "riposo"
        return DishContext(nutri.profile ?: NutritionProfile(), day, meal, sessions)
    }

    /** Runs a step of the open screen; what goes wrong is shown there. */
    private fun changeStep(of: ChangeDishUi, block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CoachException) {
            changing(of) { it.copy(loading = false, checking = false, saving = false, error = e.reason) }
        } catch (e: Exception) {
            changing(of) { it.copy(loading = false, checking = false, saving = false, error = CoachException.Reason.NETWORK) }
        }
    }

    /** «Altre alternative», and «Riprova» when the first ones did not come. */
    fun moreAlternatives() {
        if (local.value.change?.loading == false) loadAlternatives()
    }

    /**
     * NutriAI proposes, the app decides what is shown: nothing the profile
     * bans, nothing already in the meal or already seen, each in the
     * quantity that gives the calories of the dish it replaces.
     */
    private fun loadAlternatives() {
        val open = local.value.change ?: return
        alternativesJob?.cancel()
        alternativesJob = changeStep(open) {
            changing(open) { it.copy(loading = true, error = null) }
            val context = dishContext(open)
            val request = CoachPrompts.dishAlternativesRequest(context.profile, context.day, context.meal, open.target, context.sessions, open.seen)
            val fresh = NutriMeals.alternatives(
                candidates = advisor.alternatives(request),
                target = open.target,
                profile = context.profile,
                exclude = open.seen + context.meal.dishes.map { it.name },
            )
            changing(open) {
                if (fresh.isEmpty()) {
                    it.copy(loading = false, exhausted = true)
                } else {
                    it.copy(loading = false, exhausted = false, alternatives = fresh, picked = null, seen = it.seen + fresh.map(Dish::name))
                }
            }
        }
    }

    fun pickAlternative(index: Int) {
        val open = local.value.change ?: return
        typedJob?.cancel()
        changing(open) { it.copy(picked = index, typed = null, unsafe = false, checking = false, error = null) }
    }

    /**
     * «Controlla» on the dish the athlete wrote. Before any model: the signs
     * NutriAI must not answer with food, and an allergy or an intolerance
     * in the words themselves. Then NutriAI says what the dish is worth and
     * the app checks its ingredients too.
     */
    fun checkTyped(text: String) {
        val typed = text.trim().take(NutriMeals.TYPED_MAX)
        val open = local.value.change ?: return
        if (typed.isEmpty() || open.checking || open.saving) return
        typedJob?.cancel()
        typedJob = changeStep(open) {
            changing(open) { it.copy(checking = true, typed = null, unsafe = false, picked = null, withComplement = true, error = null) }
            if (NutriSafety.flags(typed)) {
                changing(open) { it.copy(checking = false, unsafe = true) }
                return@changeStep
            }
            val context = dishContext(open)
            val blocked = NutriMeals.precheck(typed, context.profile)
            val result = if (blocked != null) {
                TypedDish.Blocked(blocked)
            } else {
                val request = CoachPrompts.dishEstimateRequest(context.profile, context.day, context.meal, open.target, typed, context.sessions)
                NutriMeals.typed(typed, advisor.estimate(request), open.target, context.profile)
            }
            changing(open) { it.copy(checking = false, typed = result) }
        }
    }

    /** The athlete is writing again: the answer to the text of before goes. */
    fun clearTyped() {
        val open = local.value.change ?: return
        if (open.typed == null && !open.unsafe && !open.checking) return
        typedJob?.cancel()
        changing(open) { it.copy(typed = null, unsafe = false, checking = false) }
    }

    fun clearChangeError() {
        val open = local.value.change ?: return
        changing(open) { it.copy(error = null) }
    }

    /** «−» and «+» on the quantity of the dish the athlete wrote. */
    fun stepTyped(direction: Int) {
        val open = local.value.change ?: return
        changing(open) { change ->
            val ready = change.typed as? TypedDish.Ready ?: return@changing change
            val quantity = NutriMeals.nextQuantity(ready.dish.quantity, ready.dish.unit, direction)
            change.copy(typed = NutriMeals.requantified(ready, quantity, change.target))
        }
    }

    fun toggleComplement() {
        val open = local.value.change ?: return
        changing(open) { it.copy(withComplement = !it.withComplement) }
    }

    /**
     * «Sostituisci»: the dish goes in the meal, the meal and the day are
     * summed again and the change can be undone. A food the athlete said
     * they do not eat goes in confirmed: pressing here is the confirmation.
     */
    fun confirmChange() {
        val open = local.value.change ?: return
        if (open.saving) return
        val ready = open.typed as? TypedDish.Ready
        val with = ready?.dish?.copy(confirmed = ready.confirm != null) ?: open.picked?.let(open.alternatives::getOrNull) ?: return
        val extra = ready?.complement?.takeIf { open.withComplement }
        changeStep(open) {
            changing(open) { it.copy(saving = true, error = null) }
            val desk = repository.nutriDesk(uid).first()
            val day = desk.days[open.date]
            // The day may have been written again meanwhile: the dish must still be the one opened.
            val there = day?.meals?.getOrNull(open.key.meal)?.dishes?.getOrNull(open.key.dish)
            val changed = day?.takeIf { there?.name == open.target.name }?.let { NutriMeals.replace(it, open.key, with, extra) }
                ?: throw CoachException(CoachException.Reason.BAD_ANSWER)
            save(desk, listOf(changed))
            stopChangeJobs()
            local.update { it.copy(change = null, undo = NutriUndo(open.date, open.mealName, day)) }
        }
    }

    /** «Annulla» after a change: the day as it was. */
    fun undoChange() {
        val undo = local.value.undo ?: return
        local.update { it.copy(undo = null) }
        run(null) { save(repository.nutriDesk(uid).first(), listOf(undo.before)) }
    }

    fun dismissUndo() = local.update { it.copy(undo = null) }

    /** «Rimetti …»: the dish that was there before the athlete changed it. */
    fun restoreDish(date: LocalDate, key: DishKey) = run(null) {
        local.update { it.copy(undo = null) }
        val desk = repository.nutriDesk(uid).first()
        val restored = desk.days[date]?.let { NutriMeals.restore(it, key) } ?: return@run
        save(desk, listOf(restored))
    }

    // ---- Chat --------------------------------------------------------------

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || local.value.task != null) return
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
            // The chat is about the day the athlete is looking at.
            val date = state.value.selected
            val plan = coach.plan?.takeIf { it.weekStart == TrainingStats.weekStart(date) }
            val profile = nutri.profile ?: NutritionProfile()
            val data = athlete.current()
            val numbers = numbers(data, profile, plan, date)
            val targets = numbers.targets
            val turn = CoachPrompts.chatTurn(
                role = CoachRole.NUTRI_AI,
                athlete = AthleteContext.describe(data),
                partner = coach.brief,
                ownPlan = nutri.days[date]?.let(CoachPrompts::describe),
                extra = buildString {
                    appendLine("L'atleta sta guardando ${AthleteContext.dayLabel(date)} $date" + if (date == today()) " (oggi)." else "; oggi è ${today()}.")
                    appendLine()
                    targets?.let {
                        appendLine("## Obiettivi del giorno")
                        appendLine(NutriEnergy.describe(it, profile.goal))
                        appendLine("Se proponi un giorno nuovo, l'app ricalcola i numeri: cambia pasti e alimentazione, non i numeri.")
                        appendLine()
                    }
                    numbers.fueling?.let {
                        appendLine("## Rifornimento intorno alle sedute")
                        appendLine(it)
                        appendLine()
                    }
                    appendLine("## Profilo alimentare")
                    appendLine(CoachPrompts.describe(profile))
                    plan?.day(date)?.let { append("Sedute di ${AthleteContext.dayLabel(date)} $date secondo i coach: ${CoachPrompts.describeDay(it)}") }
                }.trimEnd(),
                message = message,
            )
            val reply = agent.reply(history, turn)
            // A proposal is NutriAI's answer like any other: the app sums it, checks it and puts its numbers on it.
            val proposal = reply.proposal
                ?.let { NutriEnergy.apply(NutriMeals.sanitized(it.copy(date = date)), targets) }
                ?.let { NutriMeals.settle(FoodBans.clean(it, profile)) }
                ?.takeIf { it.meals.isNotEmpty() }
            repository.addMessage(uid, CoachRole.NUTRI_AI, fromAthlete = false, text = reply.text, proposal = proposal?.let(CoachMapper::nutritionDayToFields))
            if (reply.noteForPartner.isNotBlank()) repository.leaveNote(uid, CoachRole.NUTRI_AI, reply.noteForPartner)
        }
    }

    /** «Applica» on a day NutriAI proposed in the chat: it takes the place of the meals of its date. */
    fun apply(entry: ChatEntry) = run(null) {
        val fields = entry.proposal ?: return@run
        val proposed = CoachMapper.nutritionDayFromFields(fields) ?: CoachMapper.nutritionDayFromFields(fields, today()) ?: return@run
        val desk = repository.nutriDesk(uid).first()
        // The profile may have changed since NutriAI made the proposal.
        val day = desk.profile?.let { FoodBans.clean(proposed, it) } ?: proposed
        save(desk, listOf(day), CoachBriefs.of(day))
        repository.setProposalStatus(uid, CoachRole.NUTRI_AI, entry.id, ProposalStatus.APPLIED)
        local.update { it.copy(selected = day.date, undo = null) }
    }

    fun dismiss(entry: ChatEntry) = run(null) {
        repository.setProposalStatus(uid, CoachRole.NUTRI_AI, entry.id, ProposalStatus.DISMISSED)
    }

    fun clearError() = local.update { it.copy(error = null) }

    /**
     * Runs [block] and shows what goes wrong. With a [kind] the screen shows
     * NutriAI at work, and nothing else of the kind starts meanwhile.
     */
    private fun run(kind: CoachTask?, block: suspend () -> Unit) {
        if (kind != null && local.value.task != null) return
        if (kind != null) local.update { it.copy(task = kind) }
        viewModelScope.launch {
            local.update { it.copy(error = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: CoachException) {
                local.update { it.copy(error = e.reason) }
            } catch (e: Exception) {
                local.update { it.copy(error = CoachException.Reason.NETWORK) }
            } finally {
                if (kind != null) local.update { it.copy(task = null, preparing = null) }
            }
        }
    }

    private companion object {
        const val RECENT_MESSAGES = 6
    }
}
