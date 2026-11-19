package com.triforge.app.ui.coach

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.data.ChatEntry
import com.triforge.app.data.ProposalStatus
import com.triforge.app.ui.common.LegendDot
import com.triforge.app.ui.common.LoadingBox
import com.triforge.app.ui.common.ProgressBar
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.coach.AthleteContext
import com.triforge.domain.coach.BanHit
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.DayType
import com.triforge.domain.coach.Dish
import com.triforge.domain.coach.DishKey
import com.triforge.domain.coach.DishOrigin
import com.triforge.domain.coach.DishUnit
import com.triforge.domain.coach.MealSuggestion
import com.triforge.domain.coach.NutriMeals
import com.triforge.domain.coach.NutritionDay
import com.triforge.domain.coach.NutritionProfile
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

private val palette = CoachPalette.Nutri
internal val nutriNumbers: NumberFormat = NumberFormat.getIntegerInstance(Locale.ITALY)
private val Bright = Color(0xFFE6E9EE)

/** How long «Annulla» stays after a dish is replaced. */
private const val UNDO_MS = 8_000L

/**
 * NutriAI (boards 7d–7i): the week, the app's numbers of the selected day,
 * and its meals, which NutriAI writes only when asked. A meal opens on its
 * dishes, each with «Cambia».
 */
@Composable
fun NutriScreen(
    viewModel: NutriViewModel,
    aiAllowed: Boolean,
    onEnableAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    // Kept here, above the dish screen, so that coming back finds the list where it was.
    val listState = rememberLazyListState()
    var openMeals by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val change = state.change?.takeIf { aiAllowed }
    if (change != null) {
        BackHandler { viewModel.closeChange() }
        NutriChangeDish(
            change = change,
            view = state.week.firstOrNull { it.date == change.date },
            profile = state.profile,
            today = state.today,
            viewModel = viewModel,
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize().imePadding()) {
        CoachHeader(
            title = stringResource(R.string.nutri_title),
            subtitle = stringResource(R.string.nutri_subtitle),
            icon = TriIcons.Nutrition,
            palette = palette,
        )
        if (!aiAllowed) {
            Column(Modifier.padding(16.dp)) { AiConsentCard(palette, onEnableAi) }
            return@Column
        }
        if (!state.loaded) {
            LoadingBox()
            return@Column
        }
        val profile = state.profile
        if (profile != null) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profile.chips().forEach { ProfileChip(it) }
                OutlineChip(stringResource(R.string.nutri_profile), onClick = { editing = !editing })
            }
        }
        // Follows the chat when a message arrives, not when the screen opens on a chat already there.
        var chatSeen by remember { mutableIntStateOf(-1) }
        LaunchedEffect(state.chat.size) {
            val arrived = state.chat.size - chatSeen
            if (chatSeen >= 0 && arrived in 1..2) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
            chatSeen = state.chat.size
        }
        val busy = state.task != null
        val view = state.day
        val word = view?.let { dayWord(it.date, state.today) }.orEmpty()
        val preparing = (state.preparing ?: view?.date)?.let { dayWord(it, state.today) }.orEmpty()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (profile == null || editing) {
                item {
                    NutriProfileEditor(
                        initial = profile ?: NutritionProfile(),
                        first = profile == null,
                        saving = false,
                        // Not on the first profile: saving it would close this form half filled.
                        onKeep = if (profile != null) viewModel::keepProfile else null,
                        onSave = {
                            viewModel.saveProfile(it)
                            editing = false
                        },
                    )
                }
                if (profile == null) return@LazyColumn
            }
            state.error?.let { reason ->
                item { ErrorCard(reason, onRetry = null, onDismiss = viewModel::clearError) }
            }
            item { WeekStrip(state, viewModel::select) }
            if (view != null) {
                if (state.task == CoachTask.PLANNING) {
                    item { ThinkingRow(stringResource(R.string.nutri_preparing_day, preparing), palette) }
                }
                if (state.needsWeight) {
                    item { TriCard { Text(stringResource(R.string.nutri_error_weight), style = MaterialTheme.typography.bodyMedium) } }
                }
                if (view.outdated && view.hasMeals) {
                    item { ChangedCard(view, busy, onUpdate = { viewModel.prepareDay(view.date) }, onKeep = { viewModel.keepDay(view.date) }) }
                }
                if (view.targets != null || view.day != null) item { TargetsCard(view, state.today) }
                val day = view.day
                if (day != null && day.fueling.isNotEmpty()) item { FuelingCard(day, view) }
                if (day != null && view.hasMeals) {
                    item {
                        MealsCard(
                            view = view,
                            day = day,
                            word = word,
                            busy = busy,
                            open = openMeals,
                            onToggle = { key -> openMeals = if (key in openMeals) openMeals - key else openMeals + key },
                            onChange = { viewModel.openChange(view.date, it) },
                            onRestore = { viewModel.restoreDish(view.date, it) },
                            onRedo = { viewModel.prepareDay(view.date) },
                        )
                    }
                } else if (!state.needsWeight) {
                    item {
                        NoMealsCard(
                            word = word,
                            past = view.date.isBefore(state.today),
                            week = state.toPrepare > 1,
                            busy = busy,
                            onDay = { viewModel.prepareDay(view.date) },
                            onWeek = viewModel::prepareWeek,
                        )
                    }
                }
                if (day != null && day.rationale.isNotBlank()) {
                    item {
                        TriCard {
                            Text(stringResource(R.string.nutri_why), style = MaterialTheme.typography.titleSmall)
                            Text(day.rationale, style = MaterialTheme.typography.bodyMedium, color = TriColors.textDim, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            if (state.coachNote.isNotBlank()) {
                item {
                    HighlightCard(stringResource(R.string.nutri_from_coach), CoachPalette.Coach) {
                        Text(state.coachNote, style = MaterialTheme.typography.bodyMedium, color = Bright)
                    }
                }
            }
            items(state.chat, key = { it.id }) { entry -> NutriMessage(entry, state, viewModel) }
            if (state.task == CoachTask.REPLYING) item { ThinkingRow(stringResource(R.string.nutri_thinking), palette) }
        }
        state.undo?.let { undo -> UndoBar(undo, onUndo = viewModel::undoChange, onGone = viewModel::dismissUndo) }
        // Not while the food profile is open: above the keyboard the chat bar covered its fields and buttons.
        if (profile != null && !editing) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                Composer(stringResource(R.string.nutri_composer), palette, enabled = !busy, onSend = viewModel::send)
            }
        }
    }
}

// ---- Words and numbers -----------------------------------------------------

/** «oggi», or the weekday: «i pasti di lunedì». */
@Composable
internal fun dayWord(date: LocalDate, today: LocalDate): String =
    if (date == today) stringResource(R.string.nutri_today_lower) else AthleteContext.dayLabel(date)

/** «Lunedì 24», with «· oggi» on today. */
@Composable
internal fun dayTitle(date: LocalDate, today: LocalDate): String {
    val name = AthleteContext.dayLabel(date).replaceFirstChar { it.titlecase(Locale.ITALIAN) }
    return stringResource(if (date == today) R.string.nutri_day_title_today else R.string.nutri_day_title, name, date.dayOfMonth)
}

/** «130 g», «250 ml», «1 pezzo», «2 pezzi». */
@Composable
internal fun dishAmount(dish: Dish): String = when {
    dish.unit != DishUnit.PIECE -> NutriMeals.amount(dish)
    dish.quantity == 1.0 -> stringResource(R.string.nutri_pieces_one)
    else -> stringResource(R.string.nutri_pieces, NutriMeals.amount(dish))
}

@Composable
private fun dayTypeLabel(type: DayType): String = stringResource(
    when (type) {
        DayType.REST -> R.string.nutri_day_rest
        DayType.LIGHT -> R.string.nutri_day_light
        DayType.MODERATE -> R.string.nutri_day_moderate
        DayType.HEAVY -> R.string.nutri_day_heavy
        DayType.RACE -> R.string.nutri_day_race
    },
)

// ---- Small parts -----------------------------------------------------------

/** NutriAI's main action, in its green. */
@Composable
internal fun NutriButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (compact) 44.dp else 56.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = if (compact) 18.dp else 24.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.accent,
            contentColor = palette.onAccent,
            disabledContainerColor = TriColors.track,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontSize = if (compact) 14.sp else 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small mark on a dish: who chose it, or why it is offered. */
@Composable
internal fun NutriTag(text: String, color: Color = palette.softText, background: Color = palette.soft) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun ProfileChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(palette.soft).padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = palette.softText) }
}

@Composable
private fun OutlineChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .border(1.dp, TriColors.borderStrong, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = Color(0xFFD5DAE1)) }
}

// ---- The week --------------------------------------------------------------

/** Monday to Sunday: the sports of each day, and whether its meals are ready or to look at again. */
@Composable
private fun WeekStrip(state: NutriUiState, onSelect: (LocalDate) -> Unit) {
    TriCard(padding = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.week.forEach { view ->
                val selected = view.date == state.selected
                val shape = RoundedCornerShape(12.dp)
                val status = stringResource(
                    when {
                        view.toReview -> R.string.nutri_week_review
                        view.hasMeals -> R.string.nutri_week_ready
                        else -> R.string.nutri_week_none
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(if (selected) palette.soft else Color.Transparent)
                        .border(1.dp, if (selected) palette.highlightBorder else Color.Transparent, shape)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(view.date) })
                        .semantics { stateDescription = status }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = view.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.titlecase(Locale.ITALIAN) },
                        fontSize = 11.sp,
                        fontWeight = if (view.date == state.today) FontWeight.Bold else FontWeight.Medium,
                        color = if (view.date == state.today) palette.softText else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(view.date.dayOfMonth.toString(), style = numberStyle(16.sp), color = if (selected) Color.White else Bright)
                    Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        view.sessions.mapNotNull { it.sport }.distinct().take(3).forEach { sport ->
                            Box(Modifier.size(6.dp).clip(CircleShape).background(SportColors.of(sport)))
                        }
                    }
                    Box(
                        Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(
                            when {
                                view.toReview -> TriColors.caution
                                view.hasMeals -> palette.accent
                                else -> TriColors.track
                            },
                        ),
                    )
                }
            }
        }
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(palette.accent, stringResource(R.string.nutri_week_ready))
            if (state.week.any { it.toReview }) LegendDot(TriColors.caution, stringResource(R.string.nutri_week_review))
        }
    }
}

// ---- The day's numbers -----------------------------------------------------

/**
 * The app's numbers of the day, there with or without meals. With meals made
 * of dishes it shows what they add up to against each target.
 */
@Composable
private fun TargetsCard(view: NutriDayView, today: LocalDate) {
    val day = view.day
    val targets = view.targets
    val kcal = targets?.kcal ?: day?.kcal ?: return
    val carbs = targets?.carbsG ?: day?.carbsG ?: 0
    val protein = targets?.proteinG ?: day?.proteinG ?: 0
    val fat = targets?.fatG ?: day?.fatG ?: 0
    val eaten = day?.let { NutriMeals.totals(it) }
    TriCard {
        Row(verticalAlignment = Alignment.Bottom) {
            // The title keeps its line; a long headline wraps beside it instead of squeezing it.
            Text(dayTitle(view.date, today), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false, modifier = Modifier.semantics { heading() })
            Text(
                text = day?.headline?.takeIf { it.isNotBlank() } ?: dayTypeLabel(targets?.dayType ?: day?.dayType ?: DayType.MODERATE),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TriColors.caution,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
        Text(
            text = view.sessions.takeIf { it.isNotEmpty() }?.joinToString(" · ") { if (it.durationMin > 0) "${it.title} ${it.durationMin}′" else it.title }
                ?: stringResource(R.string.nutri_rest),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
            Text(nutriNumbers.format(eaten?.kcal ?: kcal), style = numberStyle(36.sp))
            Text(
                text = " " + if (eaten != null) stringResource(R.string.nutri_in_meals, nutriNumbers.format(kcal)) else stringResource(R.string.nutri_kcal),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        val energy = (carbs * 4 + protein * 4 + fat * 9).coerceAtLeast(1)
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Macro(stringResource(R.string.nutri_carbs), carbs, carbs * 4f / energy, eaten?.carbsG)
            Macro(stringResource(R.string.nutri_protein), protein, protein * 4f / energy, eaten?.proteinG)
            Macro(stringResource(R.string.nutri_fat), fat, fat * 9f / energy, eaten?.fatG)
        }
        if (targets != null) {
            Text(
                text = listOfNotNull(
                    stringResource(R.string.nutri_app_numbers),
                    targets.carbLoad.takeIf { it.isNotBlank() }?.let { stringResource(R.string.nutri_carb_load, it) },
                    if (targets.estimated) stringResource(R.string.nutri_estimated) else null,
                ).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** One macronutrient: its share of the day's energy, or what the meals give of it. */
@Composable
private fun Macro(label: String, grams: Int, share: Float, eaten: Int?) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (eaten != null) {
                Text(stringResource(R.string.nutri_of_grams, eaten, grams), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            } else {
                Text("$grams g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("  ${(share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ProgressBar(
            fraction = if (eaten != null) eaten.toFloat() / grams.coerceAtLeast(1) else share,
            color = palette.accent,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Board 7i: the training changed after the meals were written. The athlete decides, not NutriAI. */
@Composable
private fun ChangedCard(view: NutriDayView, busy: Boolean, onUpdate: () -> Unit, onKeep: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val gap = (view.day?.kcal ?: 0) - (view.targets?.kcal ?: 0)
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(Color(0xFF1F1B0C)).border(1.dp, Color(0xFF5C5226), shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.nutri_changed_title), style = MaterialTheme.typography.titleSmall, color = TriColors.caution)
        Text(
            text = when {
                gap > 0 -> stringResource(R.string.nutri_changed_more, nutriNumbers.format(gap))
                gap < 0 -> stringResource(R.string.nutri_changed_less, nutriNumbers.format(-gap))
                else -> stringResource(R.string.nutri_changed_same)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Bright,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NutriButton(stringResource(R.string.nutri_changed_update), onUpdate, Modifier.weight(1f), enabled = !busy, compact = true)
            SecondaryButton(stringResource(R.string.nutri_changed_keep), onKeep, Modifier.weight(1f), enabled = !busy, compact = true)
        }
        Text(stringResource(R.string.nutri_changed_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FuelingCard(day: NutritionDay, view: NutriDayView) {
    val session = view.sessions.firstOrNull()?.title
    val title = session?.let { stringResource(R.string.nutri_fuel_for, it) } ?: stringResource(R.string.nutri_fuel)
    HighlightCard(title, palette) {
        day.fueling.forEach { step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(step.time, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.softText, modifier = Modifier.width(64.dp))
                Text(step.text, style = MaterialTheme.typography.bodyMedium, color = Bright, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---- Meals -----------------------------------------------------------------

/** Board 7d: no meals yet. NutriAI writes them when one of these is pressed, never before. */
@Composable
private fun NoMealsCard(word: String, past: Boolean, week: Boolean, busy: Boolean, onDay: () -> Unit, onWeek: () -> Unit) {
    TriCard {
        Text(stringResource(R.string.nutri_meals), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (past) {
            Text(
                stringResource(R.string.nutri_no_meals_past),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@TriCard
        }
        Text(
            stringResource(R.string.nutri_no_meals, word),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        NutriButton(stringResource(R.string.nutri_prepare_day, word), onDay, Modifier.fillMaxWidth().padding(top = 14.dp), enabled = !busy)
        if (week) {
            SecondaryButton(stringResource(R.string.nutri_prepare_week), onWeek, Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !busy)
            Text(
                stringResource(R.string.nutri_prepare_week_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** Boards 7e and 7h: the meals of the day; each opens on its dishes. */
@Composable
private fun MealsCard(
    view: NutriDayView,
    day: NutritionDay,
    word: String,
    busy: Boolean,
    open: Set<String>,
    onToggle: (String) -> Unit,
    onChange: (DishKey) -> Unit,
    onRestore: (DishKey) -> Unit,
    onRedo: () -> Unit,
) {
    TriCard {
        Text(stringResource(R.string.nutri_meals), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (view.flags.isNotEmpty()) {
            Text(
                stringResource(R.string.nutri_flags_note),
                style = MaterialTheme.typography.bodySmall,
                color = TriColors.alert,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Column(Modifier.padding(top = 6.dp)) {
            day.meals.forEachIndexed { index, meal ->
                if (index > 0) HorizontalDivider(color = TriColors.border)
                val key = "${view.date}/$index"
                MealBlock(
                    meal = meal,
                    index = index,
                    flags = view.flags,
                    expanded = key in open,
                    busy = busy,
                    onToggle = { onToggle(key) },
                    onChange = onChange,
                    onRestore = onRestore,
                )
            }
        }
        if (day.meals.any { it.dishes.isEmpty() }) {
            Text(
                stringResource(R.string.nutri_legacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SecondaryButton(stringResource(R.string.nutri_redo_day, word), onRedo, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !busy, compact = true)
        Text(
            stringResource(R.string.nutri_redo_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun MealBlock(
    meal: MealSuggestion,
    index: Int,
    flags: Map<DishKey, BanHit>,
    expanded: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onChange: (DishKey) -> Unit,
    onRestore: (DishKey) -> Unit,
) {
    val hasDishes = meal.dishes.isNotEmpty()
    val flagged = flags.keys.any { it.meal == index }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "meal")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (hasDishes) {
                        Modifier.clickable(
                            onClickLabel = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                            role = Role.Button,
                            onClick = onToggle,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(palette.soft), contentAlignment = Alignment.Center) {
                Icon(TriIcons.Nutrition, contentDescription = null, tint = palette.accent, modifier = Modifier.size(14.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(meal.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (!expanded && meal.description.isNotBlank()) {
                    Text(
                        meal.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (hasDishes) 2 else 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (flagged && !expanded) NutriTag(stringResource(R.string.nutri_tag_flagged), color = TriColors.alert, background = Color(0xFF2B1A12))
            }
            meal.kcal?.let { Text(stringResource(R.string.nutri_meal_kcal, nutriNumbers.format(it)), style = MaterialTheme.typography.bodyMedium) }
            if (hasDishes) {
                Icon(TriIcons.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).rotate(rotation))
            }
        }
        AnimatedVisibility(visible = expanded && hasDishes) {
            Column(Modifier.padding(bottom = 8.dp)) {
                meal.dishes.forEachIndexed { d, dish ->
                    val key = DishKey(index, d)
                    DishRow(dish, flags[key], busy, onChange = { onChange(key) }, onRestore = { onRestore(key) })
                }
            }
        }
    }
}

/** One dish: what and how much, who chose it, and «Cambia». */
@Composable
private fun DishRow(dish: Dish, hit: BanHit?, busy: Boolean, onChange: () -> Unit, onRestore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dish.name, style = MaterialTheme.typography.bodyMedium, color = Bright)
            Text(
                stringResource(R.string.nutri_dish_line, dishAmount(dish), nutriNumbers.format(dish.kcal)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                hit != null -> Text(
                    stringResource(R.string.nutri_flag, hit.term, hit.detail),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TriColors.alert,
                )
                dish.origin == DishOrigin.ADDED -> NutriTag(stringResource(R.string.nutri_tag_added))
                dish.origin.fixed -> NutriTag(stringResource(R.string.nutri_tag_yours))
            }
            dish.replaced?.let { before ->
                Text(
                    text = stringResource(R.string.nutri_restore, before.name),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !busy, role = Role.Button, onClick = onRestore)
                        .padding(vertical = 8.dp),
                )
            }
        }
        val color = if (hit != null) TriColors.alert else MaterialTheme.colorScheme.onSurface
        OutlinedButton(
            onClick = onChange,
            enabled = !busy,
            modifier = Modifier.heightIn(min = 40.dp),
            shape = CircleShape,
            border = BorderStroke(1.dp, if (hit != null) TriColors.alert else TriColors.borderStrong),
            contentPadding = PaddingValues(horizontal = 14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        ) {
            Text(stringResource(R.string.nutri_change), style = MaterialTheme.typography.labelLarge, fontSize = 13.sp)
        }
    }
}

/** Board 7h: a dish was just replaced, and for a few seconds it can be undone. */
@Composable
private fun UndoBar(undo: NutriUndo, onUndo: () -> Unit, onGone: () -> Unit) {
    LaunchedEffect(undo) {
        delay(UNDO_MS)
        onGone()
    }
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF23272E))
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.nutri_dish_replaced, undo.mealName), style = MaterialTheme.typography.bodyMedium, color = Bright, modifier = Modifier.weight(1f))
        TextButton(onClick = onUndo) { Text(stringResource(R.string.action_cancel), color = palette.accent, fontWeight = FontWeight.SemiBold) }
    }
}

// ---- Chat ------------------------------------------------------------------

@Composable
private fun NutriMessage(entry: ChatEntry, state: NutriUiState, viewModel: NutriViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatBubble(entry.message, palette)
        val proposal = entry.proposal?.let { CoachMapper.nutritionDayFromFields(it) } ?: return@Column
        TriCard(Modifier.fillMaxWidth(0.88f), padding = 14.dp) {
            Text(stringResource(R.string.nutri_proposal_for, dayTitle(proposal.date, state.today)), style = MaterialTheme.typography.titleSmall)
            proposal.meals.forEach { meal ->
                Text(
                    text = meal.name + (meal.kcal?.let { " · " + stringResource(R.string.nutri_meal_kcal, nutriNumbers.format(it)) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (meal.description.isNotBlank()) {
                    Text(meal.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (entry.status) {
                ProposalStatus.PENDING -> ProposalActions(palette, { viewModel.apply(entry) }, { viewModel.dismiss(entry) }, enabled = state.task == null)
                ProposalStatus.APPLIED -> StatusLine(stringResource(R.string.coach_applied))
                ProposalStatus.DISMISSED -> StatusLine(stringResource(R.string.coach_dismissed))
                ProposalStatus.NONE -> Unit
            }
        }
    }
}
