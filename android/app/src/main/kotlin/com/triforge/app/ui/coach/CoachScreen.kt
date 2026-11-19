package com.triforge.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.data.ChatEntry
import com.triforge.app.data.ProposalStatus
import com.triforge.app.ui.common.LoadingBox
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.labelRes
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.AthleteProfile
import com.triforge.domain.Formatters
import com.triforge.domain.TrainingStats
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.PlannedDay
import com.triforge.domain.coach.PlannedSession
import com.triforge.domain.coach.WeekPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val palette = CoachPalette.Coach
private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)

@Composable
fun CoachScreen(
    viewModel: CoachViewModel,
    aiAllowed: Boolean,
    profile: AthleteProfile,
    onEnableAi: () -> Unit,
    weekSetupDone: Boolean,
    onOpenWeekSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var chat by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(aiAllowed) { if (aiAllowed) viewModel.ensurePlan() }

    Column(modifier.fillMaxSize()) {
        CoachHeader(
            title = stringResource(R.string.coach_title),
            subtitle = subtitle(profile),
            icon = TriIcons.Sparkle,
            palette = palette,
        )
        if (!aiAllowed) {
            Column(Modifier.padding(16.dp)) { AiConsentCard(palette, onEnableAi) }
            return@Column
        }
        if (!weekSetupDone) {
            TriCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.week_setup_coach_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecondaryButton(
                    stringResource(R.string.week_setup_open),
                    onClick = onOpenWeekSetup,
                    compact = true,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        Tabs(chat = chat, onSelect = { chat = it })
        if (!state.loaded) {
            LoadingBox()
        } else if (chat) {
            ChatTab(state, viewModel)
        } else {
            WeekTab(state, viewModel, onAskChange = { chat = true })
        }
    }
}

@Composable
private fun subtitle(profile: AthleteProfile): String {
    val race = profile.raceDate?.takeIf { !it.isBefore(LocalDate.now()) }
    return if (race != null) {
        val weeks = ChronoUnit.WEEKS.between(LocalDate.now(), race).toInt()
        stringResource(R.string.coach_subtitle_race, profile.raceName.ifBlank { stringResource(R.string.coach_race_default) }, weeks)
    } else {
        stringResource(R.string.coach_subtitle)
    }
}

@Composable
private fun Tabs(chat: Boolean, onSelect: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, TriColors.border, shape)
            .padding(4.dp),
    ) {
        listOf(false to R.string.coach_tab_week, true to R.string.coach_tab_chat).forEach { (isChat, label) ->
            val on = chat == isChat
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) TriColors.avatar else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(isChat) }
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Week ------------------------------------------------------------------

@Composable
private fun WeekTab(state: CoachUiState, viewModel: CoachViewModel, onAskChange: () -> Unit) {
    val plan = state.plan
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.error?.let { reason ->
            item { ErrorCard(reason, onRetry = if (plan == null) viewModel::planWeek else null, onDismiss = viewModel::clearError) }
        }
        if (state.task == CoachTask.PLANNING) {
            item { ThinkingRow(stringResource(R.string.coach_planning), palette) }
        }
        if (plan == null) {
            if (state.task == null && state.error == null) {
                item {
                    TriCard {
                        Text(stringResource(R.string.coach_no_plan), style = MaterialTheme.typography.bodyMedium)
                        PrimaryButton(
                            stringResource(R.string.coach_plan_week),
                            viewModel::planWeek,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                }
            }
            return@LazyColumn
        }
        item { WeekSummary(plan) }
        items(plan.days, key = { it.date.toString() }) { day -> DayRow(day) }
        if (plan.rationale.isNotBlank()) {
            item {
                HighlightCard(stringResource(R.string.coach_why_week), palette) {
                    Text(plan.rationale, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE6E9EE))
                }
            }
        }
        if (state.nutriNote.isNotBlank()) {
            item {
                HighlightCard(stringResource(R.string.coach_from_nutriai), CoachPalette.Nutri) {
                    Text(state.nutriNote, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE6E9EE))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    stringResource(R.string.coach_replan),
                    viewModel::planWeek,
                    enabled = state.task == null,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(stringResource(R.string.coach_ask_change), onAskChange, compact = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeekSummary(plan: WeekPlan) {
    val totals = plan.totals()
    val end = plan.weekStart.plusDays(6)
    TriCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${plan.weekStart.dayOfMonth} – ${end.format(dayMonth)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (plan.phase.isNotBlank()) {
                Text(plan.phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrainingStats.triathlonSports.forEach { sport ->
                val (count, minutes) = totals[sport] ?: (0 to 0)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(sport.labelRes()), color = SportColors.of(sport), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "$count · ${Formatters.duration(minutes * 60L)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD5DAE1),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayRow(day: PlannedDay) {
    val today = LocalDate.now()
    val isToday = day.date == today
    val past = day.date.isBefore(today)
    var open by rememberSaveable(day.date.toString()) { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val hasDetails = day.sessions.any { it.details.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (past) 0.7f else 1f)
            .clip(shape)
            .background(if (isToday) palette.highlight else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (isToday) palette.accent else TriColors.border, shape)
            .clickable(enabled = hasDetails, role = Role.Button) { open = !open }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase(),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(day.date.dayOfMonth.toString(), style = numberStyle(18.sp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (day.sessions.isEmpty()) {
                SessionLine(PlannedSession(null, stringResource(R.string.coach_rest), 0), open = false)
            } else {
                day.sessions.forEach { SessionLine(it, open) }
            }
        }
        if (isToday) {
            Text(
                stringResource(R.string.coach_today),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.onAccent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(palette.accent).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SessionLine(session: PlannedSession, open: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(session.sport?.let(SportColors::of) ?: Color(0xFF5E6672)),
            )
            Text(session.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (session.durationMin > 0) {
                Text(
                    Formatters.duration(session.durationMin * 60L),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (open && session.details.isNotBlank()) {
            Text(
                session.details,
                style = MaterialTheme.typography.bodySmall,
                color = TriColors.textDim,
                modifier = Modifier.padding(start = 18.dp, top = 4.dp),
            )
        }
    }
}

// ---- Chat ------------------------------------------------------------------

@Composable
private fun ChatTab(state: CoachUiState, viewModel: CoachViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.chat.size, state.task) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.chat.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.coach_chat_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.chat, key = { it.id }) { entry -> CoachMessage(entry, state, viewModel) }
            if (state.task == CoachTask.REPLYING) item { ThinkingRow(stringResource(R.string.coach_thinking), palette) }
            state.error?.let { reason -> item { ErrorCard(reason, onRetry = null, onDismiss = viewModel::clearError) } }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SuggestionChips(
                listOf(
                    stringResource(R.string.coach_suggest_tired),
                    stringResource(R.string.coach_suggest_short),
                    stringResource(R.string.coach_suggest_review),
                ),
                enabled = state.task == null,
                onPick = viewModel::send,
            )
            Composer(stringResource(R.string.coach_composer), palette, enabled = state.task == null, onSend = viewModel::send)
        }
    }
}

@Composable
private fun CoachMessage(entry: ChatEntry, state: CoachUiState, viewModel: CoachViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatBubble(entry.message, palette)
        val proposal = entry.proposal?.let { CoachMapper.weekPlanFromFields(it) } ?: return@Column
        TriCard(Modifier.fillMaxWidth(0.88f), padding = 14.dp) {
            val today = LocalDate.now()
            proposal.days.filter { !it.date.isBefore(today) }.forEach { day ->
                day.sessions.forEach { session ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(session.sport?.let(SportColors::of) ?: Color(0xFF5E6672)))
                        Text(
                            "${day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() }} · ${session.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (session.durationMin > 0) {
                            Text(Formatters.duration(session.durationMin * 60L), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            when (entry.status) {
                ProposalStatus.PENDING -> ProposalActions(palette, { viewModel.apply(entry) }, { viewModel.dismiss(entry) }, enabled = state.task == null)
                ProposalStatus.APPLIED -> StatusLine(stringResource(R.string.coach_applied))
                ProposalStatus.DISMISSED -> StatusLine(stringResource(R.string.coach_dismissed))
                ProposalStatus.NONE -> Spacer(Modifier)
            }
        }
    }
}

@Composable
fun StatusLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}
