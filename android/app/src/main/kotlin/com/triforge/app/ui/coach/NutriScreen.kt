package com.triforge.app.ui.coach

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.data.ChatEntry
import com.triforge.app.data.ProposalStatus
import com.triforge.app.ui.common.LoadingBox
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.ProgressBar
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.NutritionDay
import com.triforge.domain.coach.NutritionProfile
import java.text.NumberFormat
import java.util.Locale

private val palette = CoachPalette.Nutri
private val numbers = NumberFormat.getIntegerInstance(Locale.ITALY)

@Composable
fun NutriScreen(
    viewModel: NutriViewModel,
    aiAllowed: Boolean,
    onEnableAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(aiAllowed, state.profile != null, state.stale) { if (aiAllowed) viewModel.ensureDay() }

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
                OutlineChip(
                    stringResource(R.string.nutri_profile),
                    onClick = {
                        if (editing) viewModel.doneEditing()
                        editing = !editing
                    },
                )
            }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(state.chat.size) {
            if (state.chat.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        }
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
                        saving = state.task != null,
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
                item { ErrorCard(reason, onRetry = if (state.day == null) viewModel::planDay else null, onDismiss = viewModel::clearError) }
            }
            if (state.task == CoachTask.PLANNING) {
                item { ThinkingRow(stringResource(if (state.stale) R.string.nutri_replanning else R.string.nutri_planning), palette) }
            }
            val day = state.day
            if (day == null) {
                if (state.task == null && state.error == null) {
                    item {
                        TriCard {
                            Text(stringResource(R.string.nutri_no_day), style = MaterialTheme.typography.bodyMedium)
                            PrimaryButton(stringResource(R.string.nutri_plan_day), viewModel::planDay, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                        }
                    }
                }
            } else {
                item { TargetsCard(day) }
                if (day.fueling.isNotEmpty()) item { FuelingCard(day, state) }
                if (day.meals.isNotEmpty()) item { MealsCard(day) }
                if (day.rationale.isNotBlank()) {
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
                        Text(state.coachNote, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE6E9EE))
                    }
                }
            }
            items(state.chat, key = { it.id }) { entry -> NutriMessage(entry, state, viewModel) }
            if (state.task == CoachTask.REPLYING) item { ThinkingRow(stringResource(R.string.nutri_thinking), palette) }
        }
        // Not while the food profile is open: above the keyboard the chat bar covered its fields and buttons.
        if (profile != null && !editing) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                Composer(stringResource(R.string.nutri_composer), palette, enabled = state.task == null, onSend = viewModel::send)
            }
        }
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

@Composable
private fun TargetsCard(day: NutritionDay) {
    TriCard {
        Row(verticalAlignment = Alignment.Bottom) {
            // The title keeps its line; a long headline wraps beside it instead of squeezing it.
            Text(stringResource(R.string.nutri_today), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
            if (day.headline.isNotBlank()) {
                Text(
                    day.headline,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD23F),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
            Text(numbers.format(day.kcal), style = numberStyle(36.sp))
            Text(" kcal", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        val energy = (day.carbsG * 4 + day.proteinG * 4 + day.fatG * 9).coerceAtLeast(1)
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Macro(stringResource(R.string.nutri_carbs), day.carbsG, day.carbsG * 4f / energy)
            Macro(stringResource(R.string.nutri_protein), day.proteinG, day.proteinG * 4f / energy)
            Macro(stringResource(R.string.nutri_fat), day.fatG, day.fatG * 9f / energy)
        }
    }
}

@Composable
private fun Macro(label: String, grams: Int, share: Float) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$grams g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("  ${(share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ProgressBar(fraction = share, color = palette.accent, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun FuelingCard(day: NutritionDay, state: NutriUiState) {
    val session = state.todaySessions?.sessions?.firstOrNull { it.sport != null }?.title
    val title = session?.let { stringResource(R.string.nutri_fuel_for, it) } ?: stringResource(R.string.nutri_fuel)
    HighlightCard(title, palette) {
        day.fueling.forEach { step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(step.time, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.softText, modifier = Modifier.width(64.dp))
                Text(step.text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE6E9EE), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MealsCard(day: NutritionDay) {
    TriCard {
        Text(stringResource(R.string.nutri_meals), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.padding(top = 8.dp)) {
            day.meals.forEach { meal ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(palette.soft), contentAlignment = Alignment.Center) {
                        Icon(TriIcons.Nutrition, contentDescription = null, tint = palette.accent, modifier = Modifier.size(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(meal.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (meal.description.isNotBlank()) {
                            Text(meal.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    meal.kcal?.let { Text("${numbers.format(it)} kcal", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun NutriMessage(entry: ChatEntry, state: NutriUiState, viewModel: NutriViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatBubble(entry.message, palette)
        val proposal = entry.proposal?.let { CoachMapper.nutritionDayFromFields(it) } ?: return@Column
        TriCard(Modifier.fillMaxWidth(0.88f), padding = 14.dp) {
            Text(
                stringResource(R.string.nutri_proposal, numbers.format(proposal.kcal), proposal.carbsG, proposal.proteinG, proposal.fatG),
                style = MaterialTheme.typography.bodyMedium,
            )
            when (entry.status) {
                ProposalStatus.PENDING -> ProposalActions(palette, { viewModel.apply(entry) }, { viewModel.dismiss(entry) }, enabled = state.task == null)
                ProposalStatus.APPLIED -> StatusLine(stringResource(R.string.coach_applied))
                ProposalStatus.DISMISSED -> StatusLine(stringResource(R.string.coach_dismissed))
                ProposalStatus.NONE -> Unit
            }
        }
    }
}
