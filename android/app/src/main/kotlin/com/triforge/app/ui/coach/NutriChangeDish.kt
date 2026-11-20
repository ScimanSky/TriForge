package com.triforge.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.TriTextField
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.coach.BanHit
import com.triforge.domain.coach.Dish
import com.triforge.domain.coach.DishOrigin
import com.triforge.domain.coach.NutriMeals
import com.triforge.domain.coach.NutriSafety
import com.triforge.domain.coach.NutritionProfile
import com.triforge.domain.coach.TypedDish
import java.time.LocalDate
import kotlin.math.roundToInt

private val palette = CoachPalette.Nutri
private val Bright = Color(0xFFE6E9EE)

/**
 * Changing one dish of a meal (boards 7f and 7g): NutriAI's alternatives,
 * similar in calories and carbohydrates and already checked against the food
 * profile, or a dish the athlete writes. An allergy or an intolerance always
 * blocks; a food the athlete said they do not eat asks first.
 */
@Composable
fun NutriChangeDish(
    change: ChangeDishUi,
    view: NutriDayView?,
    profile: NutritionProfile?,
    today: LocalDate,
    viewModel: NutriViewModel,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(change.date.toString(), change.key.meal, change.key.dish) { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val check = {
        keyboard?.hide()
        viewModel.checkTyped(text)
    }
    val ready = change.typed as? TypedDish.Ready
    val chosen = change.chosen

    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = viewModel::closeChange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TriIcons.Close, contentDescription = stringResource(R.string.coach_close), modifier = Modifier.size(24.dp))
            }
            Text(stringResource(R.string.nutri_change_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TriCard {
                Text(
                    stringResource(R.string.nutri_change_of, change.mealName, dayWord(change.date, today)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(change.target.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                Text(dishLine(change.target), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            HighlightCard(stringResource(R.string.nutri_change_keeps_title), palette) {
                val sessions = view?.sessions.orEmpty().joinToString(", ") { if (it.durationMin > 0) "${it.title} ${it.durationMin}′" else it.title }
                Text(
                    text = if (sessions.isBlank()) stringResource(R.string.nutri_change_keeps) else stringResource(R.string.nutri_change_keeps_sessions, sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Bright,
                )
            }
            change.error?.let { ErrorCard(it, who = stringResource(R.string.nav_nutri), onRetry = null, onDismiss = viewModel::clearChangeError) }
            Alternatives(change, profile, selectable = ready == null && !change.saving, viewModel = viewModel)
            TriCard {
                Text(stringResource(R.string.nutri_typed_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TriTextField(
                        value = text,
                        onValueChange = {
                            text = it.take(NutriMeals.TYPED_MAX)
                            viewModel.clearTyped()
                        },
                        label = stringResource(R.string.nutri_typed_field),
                        modifier = Modifier.weight(1f),
                        placeholder = stringResource(R.string.nutri_typed_hint),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { check() }),
                    )
                    SecondaryButton(
                        stringResource(R.string.nutri_typed_check),
                        onClick = check,
                        enabled = text.isNotBlank() && !change.checking && !change.saving,
                        compact = true,
                    )
                }
                Text(
                    stringResource(R.string.nutri_typed_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val typed = change.typed
                when {
                    change.checking -> ThinkingRow(stringResource(R.string.nutri_typed_checking), palette)
                    change.unsafe -> Text(NutriSafety.REPLY, style = MaterialTheme.typography.bodyMedium, color = Bright, modifier = Modifier.padding(top = 12.dp))
                    typed is TypedDish.Unknown -> Text(
                        stringResource(R.string.nutri_typed_unknown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TriColors.caution,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    typed is TypedDish.Blocked -> Blocked(typed.hit)
                    typed is TypedDish.Ready -> Typed(typed, change, view, viewModel)
                }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            NutriButton(
                text = when {
                    ready?.confirm != null -> stringResource(R.string.nutri_replace_anyway)
                    chosen != null -> stringResource(R.string.nutri_replace_with, chosen.name)
                    else -> stringResource(R.string.nutri_replace)
                },
                onClick = viewModel::confirmChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = chosen != null && !change.saving && !change.checking,
            )
        }
    }
}

/** «130 g · 490 kcal · 92 g di carboidrati». */
@Composable
private fun dishLine(dish: Dish): String =
    stringResource(R.string.nutri_change_dish_line, dishAmount(dish), nutriNumbers.format(dish.kcal), dish.carbsG.roundToInt())

/** «+30», «−250», «0». */
private fun signed(value: Int): String = when {
    value > 0 -> "+" + nutriNumbers.format(value)
    value < 0 -> "−" + nutriNumbers.format(-value)
    else -> "0"
}

/** NutriAI's alternatives: one can be picked, more can be asked. */
@Composable
private fun Alternatives(change: ChangeDishUi, profile: NutritionProfile?, selectable: Boolean, viewModel: NutriViewModel) {
    TriCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nutri_alternatives), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
            Text(stringResource(R.string.nutri_alternatives_by), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (change.alternatives.isEmpty()) {
            if (change.loading) {
                ThinkingRow(stringResource(R.string.nutri_alternatives_loading), palette)
            } else if (change.error == null) {
                Text(
                    stringResource(R.string.nutri_alternatives_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (!change.loading) {
                SecondaryButton(stringResource(R.string.coach_retry), viewModel::moreAlternatives, Modifier.padding(top = 10.dp), compact = true)
            }
            return@TriCard
        }
        Column(Modifier.padding(top = 6.dp)) {
            change.alternatives.forEachIndexed { index, dish ->
                if (index > 0) HorizontalDivider(color = TriColors.border)
                val picked = change.picked == index && selectable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = picked, enabled = !change.saving, role = Role.RadioButton, onClick = { viewModel.pickAlternative(index) })
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (picked) palette.accent else TriColors.borderStrong, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (picked) Box(Modifier.size(10.dp).clip(CircleShape).background(palette.accent))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(dish.name, style = MaterialTheme.typography.bodyLarge, color = Bright)
                        Text(dishLine(dish), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (profile != null && NutriMeals.liked(dish, profile)) NutriTag(stringResource(R.string.nutri_tag_liked))
                    }
                }
            }
        }
        when {
            change.loading -> ThinkingRow(stringResource(R.string.nutri_alternatives_loading), palette)
            change.exhausted -> Text(
                stringResource(R.string.nutri_alternatives_no_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> SecondaryButton(stringResource(R.string.nutri_alternatives_more), viewModel::moreAlternatives, Modifier.padding(top = 8.dp), enabled = !change.saving, compact = true)
        }
    }
}

/** Board 7g, B: the dish holds an allergen or a food the athlete does not tolerate. It never goes in. */
@Composable
private fun Blocked(hit: BanHit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(shape).background(Color(0xFF2A1215)).border(1.dp, Color(0xFF6B2B30), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.nutri_typed_blocked_title), style = MaterialTheme.typography.titleSmall, color = TriColors.danger)
        Text(stringResource(R.string.nutri_typed_blocked, hit.term, hit.detail), style = MaterialTheme.typography.bodyMedium, color = Bright)
    }
}

/**
 * Board 7g, A: what the dish the athlete wrote is worth, how much of it,
 * what it leaves out of the dish it replaces, and the food NutriAI advises
 * beside it when that is a lot.
 */
@Composable
private fun Typed(ready: TypedDish.Ready, change: ChangeDishUi, view: NutriDayView?, viewModel: NutriViewModel) {
    val shape = RoundedCornerShape(14.dp)
    val dish = ready.dish
    val complement = ready.complement
    Column(
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(shape).background(palette.highlight).border(1.dp, palette.highlightBorder, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(dish.name, style = MaterialTheme.typography.titleSmall, color = Bright)
        Text(
            stringResource(R.string.nutri_typed_macros, nutriNumbers.format(dish.kcal), dish.carbsG.roundToInt(), dish.proteinG.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.nutri_typed_amount), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Step("−", stringResource(R.string.nutri_less), enabled = !change.saving) { viewModel.stepTyped(-1) }
            Text(dishAmount(dish), style = numberStyle(16.sp), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 76.dp))
            Step("+", stringResource(R.string.nutri_more), enabled = !change.saving) { viewModel.stepTyped(1) }
        }
        val confirm = ready.confirm
        if (confirm != null) {
            Text(stringResource(R.string.nutri_typed_confirm, confirm.term, confirm.detail), style = MaterialTheme.typography.bodySmall, color = TriColors.caution)
        } else {
            Text(stringResource(R.string.nutri_typed_ok), style = MaterialTheme.typography.bodySmall, color = palette.softText)
        }
        Text(
            stringResource(R.string.nutri_typed_gap, change.target.name, signed(ready.gap.kcal), signed(ready.gap.carbsG)),
            style = MaterialTheme.typography.bodySmall,
            color = Bright,
        )
        if (complement != null) {
            HorizontalDivider(color = palette.highlightBorder)
            Text(stringResource(R.string.nutri_complement_title).uppercase(), style = MaterialTheme.typography.labelMedium, color = palette.softText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .toggleable(value = change.withComplement, enabled = !change.saving, role = Role.Checkbox, onValueChange = { viewModel.toggleComplement() })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(
                    checked = change.withComplement,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = palette.accent, checkmarkColor = palette.onAccent, uncheckedColor = TriColors.borderStrong),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.nutri_complement_add, complement.name.replaceFirstChar { it.lowercase() }), style = MaterialTheme.typography.bodyMedium, color = Bright)
                    Text(dishLine(complement), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (ready.complementWhy.isNotBlank()) {
                Text(ready.complementWhy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val day = view?.day
        val meal = day?.meals?.getOrNull(change.key.meal)
        if (day != null && meal != null) {
            // What leaves with the old dish: itself, and what NutriAI had added beside it.
            val leaving = change.target.kcal + meal.dishes.filter { it.origin == DishOrigin.ADDED && it.addedWith == change.target.name }.sumOf { it.kcal }
            val delta = dish.kcal + (complement?.kcal?.takeIf { change.withComplement } ?: 0) - leaving
            val mealBefore = meal.kcal ?: 0
            val dayBefore = NutriMeals.totals(day)?.kcal ?: day.kcal
            Text(
                stringResource(
                    R.string.nutri_typed_totals,
                    meal.name,
                    nutriNumbers.format(mealBefore + delta),
                    nutriNumbers.format(mealBefore),
                    nutriNumbers.format(dayBefore + delta),
                    nutriNumbers.format(view?.targets?.kcal ?: day.kcal),
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Bright,
            )
        }
    }
}

/** «−» or «+» beside a quantity. */
@Composable
private fun Step(sign: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, TriColors.borderStrong, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(sign, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Bright)
    }
}
