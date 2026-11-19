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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.data.ChatEntry
import com.triforge.app.data.ProposalStatus
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.SportCoachPrompts
import com.triforge.domain.coach.TriRegia
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val palette = CoachPalette.Coach

/** The order of the selector on board 6d. */
private val chatSports = listOf(Sport.SWIM, Sport.RUN, Sport.BIKE)

/**
 * Board 6d: one chat per sport coach. The athlete picks the coach; a question
 * for someone else is handed over with one button.
 */
@Composable
fun CoachChat(
    state: CoachUiState,
    viewModel: CoachViewModel,
    sport: Sport,
    onSelect: (Sport) -> Unit,
    onOpenNutri: () -> Unit,
) {
    val chat = state.chats[sport].orEmpty()
    val coach = SportCoachPrompts.coachName(sport)
    val writing = state.task == CoachTask.REPLYING && state.replying == sport
    val listState = rememberLazyListState()
    LaunchedEffect(sport, chat.size, writing) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        CoachSelector(sport, onSelect)
        Text(
            stringResource(R.string.coach_chat_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (chat.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.coach_chat_empty, coach),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(chat, key = { "${sport.name}-${it.id}" }) { entry ->
                CoachMessage(
                    entry = entry,
                    sport = sport,
                    enabled = state.task == null,
                    onApply = { viewModel.apply(sport, entry) },
                    onDismiss = { viewModel.dismiss(sport, entry) },
                    onSelect = onSelect,
                    onOpenNutri = onOpenNutri,
                )
            }
            if (writing) item { ThinkingRow(stringResource(R.string.coach_thinking, coach), palette) }
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
                onPick = { viewModel.send(sport, it) },
            )
            Composer(stringResource(R.string.coach_composer, coach), palette, enabled = state.task == null, onSend = { viewModel.send(sport, it) })
        }
    }
}

@Composable
private fun CoachSelector(selected: Sport, onSelect: (Sport) -> Unit) {
    val group = stringResource(R.string.coach_chat_who)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            .semantics { contentDescription = group },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chatSports.forEach { sport ->
            val on = sport == selected
            val color = SportColors.of(sport)
            val shape = RoundedCornerShape(22.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(if (on) color.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (on) color else TriColors.borderStrong, shape)
                    .clickable(role = Role.Tab) { onSelect(sport) }
                    .semantics { this.selected = on },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Dot(color)
                Text(
                    TriRegia.sportName(sport),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CoachMessage(
    entry: ChatEntry,
    sport: Sport,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Sport) -> Unit,
    onOpenNutri: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!entry.message.fromAthlete) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Dot(SportColors.of(sport))
                Text(SportCoachPrompts.coachName(sport), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SportColors.of(sport))
            }
        }
        ChatBubble(entry.message, palette)
        entry.handoff?.let { Handoff(it, onSelect, onOpenNutri) }
        val proposal = entry.proposal?.let { CoachMapper.weekPlanFromFields(it) } ?: return@Column
        TriCard(Modifier.fillMaxWidth(0.88f), padding = 14.dp) {
            Text(
                stringResource(R.string.coach_proposal_title, TriRegia.sportName(sport).lowercase()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            val today = LocalDate.now()
            proposal.days.filter { !it.date.isBefore(today) }.forEach { day ->
                day.sessions.forEach { session ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        Dot(session.sport?.let(SportColors::of) ?: Color(0xFF5E6672))
                        Text(
                            "${day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() }} · ${sessionHeading(session)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (TriRegia.isHard(session)) HardPill()
                        if (session.durationMin > 0) {
                            Text(
                                Formatters.duration(session.durationMin * 60L),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            when (entry.status) {
                ProposalStatus.PENDING -> {
                    Text(
                        stringResource(R.string.coach_proposal_note),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    ProposalActions(palette, onApply, onDismiss, enabled = enabled)
                }
                ProposalStatus.APPLIED -> StatusLine(stringResource(R.string.coach_applied))
                ProposalStatus.DISMISSED -> StatusLine(stringResource(R.string.coach_dismissed))
                ProposalStatus.NONE -> Spacer(Modifier)
            }
        }
    }
}

/** The button that takes a question to who can answer it: another coach's chat, or NutriAI. */
@Composable
private fun Handoff(key: String, onSelect: (Sport) -> Unit, onOpenNutri: () -> Unit) {
    val other = TriRegia.SPORTS.firstOrNull { CoachMapper.sportKey(it) == key }
    val toNutri = key == SportCoachPrompts.HANDOFF_NUTRI
    if (other == null && !toNutri) return
    val background = if (other != null) SportColors.of(other).copy(alpha = 0.16f) else CoachPalette.Nutri.soft
    val content = if (other != null) MaterialTheme.colorScheme.onSurface else CoachPalette.Nutri.softText
    Box(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button) { if (other != null) onSelect(other) else onOpenNutri() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (other != null) stringResource(R.string.coach_ask_coach, SportCoachPrompts.coachName(other)) else stringResource(R.string.coach_ask_nutri),
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

@Composable
fun StatusLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}
