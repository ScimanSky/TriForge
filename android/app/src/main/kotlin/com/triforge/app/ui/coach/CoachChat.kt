package com.triforge.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.domain.AthleteProfile
import com.triforge.domain.Sport
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.PlanUndo
import com.triforge.domain.coach.SportCoachPrompts
import com.triforge.domain.coach.SportMode
import com.triforge.domain.coach.TrainingAvailability
import com.triforge.domain.coach.TriRegia

private val palette = CoachPalette.Coach

/** The order of the selector on board 6d. */
private val chatSports = listOf(Sport.SWIM, Sport.RUN, Sport.BIKE)

/**
 * Boards 6d and 6g: one chat per sport coach. The athlete picks the coach; a
 * question for someone else is handed over with one button. With the
 * keyboard open ([typing]) the screen above is one line, so the messages
 * stay in view.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachChat(
    state: CoachUiState,
    viewModel: CoachViewModel,
    sport: Sport,
    onSelect: (Sport) -> Unit,
    onOpenNutri: () -> Unit,
    profile: AthleteProfile = AthleteProfile(),
    availability: TrainingAvailability = TrainingAvailability(),
    typing: Boolean = false,
    onShowWeek: () -> Unit = {},
) {
    val chat = state.chats[sport].orEmpty()
    val coach = SportCoachPrompts.coachName(sport)
    val writing = state.task == CoachTask.REPLYING && state.replying == sport
    val listState = rememberLazyListState()
    val keyboard = WindowInsets.isImeVisible
    LaunchedEffect(sport, chat.size, writing, keyboard) {
        // An empty chat stays at the top, on its introduction. The last answer stays in view when the keyboard opens.
        val last = listState.layoutInfo.totalItemsCount - 1
        if (chat.isNotEmpty() && last >= 0) listState.animateScrollToItem(last)
    }
    KeyboardBack()
    Column(Modifier.fillMaxSize().imePadding()) {
        if (!typing) CoachSelector(sport, onSelect, availability)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (chat.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.coach_chat_hint),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.coach_chat_empty, coach),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(chat, key = { "${sport.name}-${it.id}" }) { entry ->
                CoachMessage(
                    entry = entry,
                    sport = sport,
                    view = state.previews[entry.id],
                    undo = state.undo?.takeIf { it.messageId == entry.id && it.sport == sport },
                    profile = profile,
                    enabled = state.task == null,
                    onApply = { viewModel.apply(sport, entry) },
                    onDismiss = { viewModel.dismiss(sport, entry) },
                    onUndo = { viewModel.undo(sport, entry) },
                    onShowWeek = onShowWeek,
                    onSelect = onSelect,
                    onOpenNutri = onOpenNutri,
                )
            }
            if (writing) item { ThinkingRow(stringResource(R.string.coach_thinking, coach), palette) }
            // An answer that did not come: «Riprova» asks again with the message already in the chat.
            val unanswered = state.unanswered(sport)
            val failed = state.error
            if (failed != null) {
                item {
                    ErrorCard(
                        failed,
                        who = coach,
                        onRetry = if (unanswered != null && state.task == null) ({ viewModel.retry(sport) }) else null,
                        onDismiss = viewModel::clearError,
                        hint = stringResource(R.string.coach_error_retry_message),
                    )
                }
            } else if (unanswered != null && state.task == null) {
                item { UnansweredCard(coach, onRetry = { viewModel.retry(sport) }) }
            }
            // The ready-made questions are a way in: once the chat has started they would only push the answers up.
            if (chat.isEmpty() && !writing) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.coach_suggest_title).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // A coach in pause, or of a sport done with a trainer, writes no session to make easier or shorter.
                        val writes = availability.isCoached(sport)
                        SuggestionChips(
                            listOfNotNull(
                                stringResource(R.string.coach_suggest_tired).takeIf { writes },
                                stringResource(R.string.coach_suggest_short).takeIf { writes },
                                stringResource(R.string.coach_suggest_review),
                            ),
                            enabled = state.task == null,
                            onPick = { viewModel.send(sport, it) },
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Composer(stringResource(R.string.coach_composer, coach), palette, enabled = state.task == null, onSend = { viewModel.send(sport, it) })
        }
    }
}

/**
 * Board 6g, «Mentre scrivi»: all that stays above the chat while the
 * keyboard is open. The coach's name opens the three coaches; «Settimana»
 * goes back to the week.
 */
@Composable
fun ChatBar(sport: Sport, onSelect: (Sport) -> Unit, availability: TrainingAvailability, onShowWeek: () -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val change = stringResource(R.string.coach_chat_who)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClickLabel = change) { choosing = !choosing }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Dot(SportColors.of(sport))
                Text(SportCoachPrompts.coachName(sport), style = MaterialTheme.typography.titleMedium)
                Icon(
                    TriIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).rotate(if (choosing) 180f else 0f),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onShowWeek)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.coach_tab_week), style = MaterialTheme.typography.labelLarge, color = TriColors.accentText)
            }
        }
        if (choosing) {
            CoachSelector(sport, onSelect = { choosing = false; onSelect(it) }, availability = availability)
        }
    }
}

/** The athlete's last message has no answer, e.g. the app was closed while the coach was writing. */
@Composable
internal fun UnansweredCard(who: String, onRetry: () -> Unit) {
    TriCard {
        Text(stringResource(R.string.coach_unanswered, who), style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(stringResource(R.string.coach_retry), onRetry, compact = true, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun CoachSelector(selected: Sport, onSelect: (Sport) -> Unit, availability: TrainingAvailability) {
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
            // A sport in pause still has its coach: it says where to turn the sport back on.
            val paused = availability.mode(sport) == SportMode.PAUSED
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(if (on) color.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (on) color else TriColors.borderStrong, shape)
                    .clickable(role = Role.Tab) { onSelect(sport) }
                    .semantics { this.selected = on },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Dot(if (paused) color.copy(alpha = 0.45f) else color)
                Column {
                    Text(
                        TriRegia.sportName(sport),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (paused) {
                        Text(stringResource(R.string.coach_chat_paused), fontSize = 11.sp, color = TriColors.textDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachMessage(
    entry: ChatEntry,
    sport: Sport,
    view: ProposalView?,
    undo: PlanUndo?,
    profile: AthleteProfile,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
    onShowWeek: () -> Unit,
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
        ProposalCard(entry, sport, view, undo, profile, enabled, onApply, onDismiss, onUndo, onShowWeek)
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
