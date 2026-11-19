package com.triforge.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.triforge.app.R
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.TriChip
import com.triforge.app.ui.common.TriTextField
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.domain.coach.Allergen
import com.triforge.domain.coach.BodyGoal
import com.triforge.domain.coach.Diet
import com.triforge.domain.coach.Drink
import com.triforge.domain.coach.Intolerance
import com.triforge.domain.coach.NutritionProfile
import com.triforge.domain.coach.Sex
import com.triforge.domain.coach.Supplement
import com.triforge.domain.coach.SupplementTiming

/**
 * The whole food profile (board 7c), in collapsible sections: body, goal and
 * diet, allergies, intolerances, favourite foods, foods the athlete does not
 * eat with their exceptions, red meat, supplements, drinks. Allergies and
 * intolerances are checked by the app on every meal NutriAI proposes.
 */
@Composable
fun NutriProfileEditor(initial: NutritionProfile, first: Boolean, saving: Boolean, onSave: (NutritionProfile) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var likes by rememberSaveable(initial) { mutableStateOf(initial.likes.joinToString(", ")) }
    var otherAllergies by rememberSaveable(initial) { mutableStateOf(initial.otherAllergies) }
    var otherIntolerances by rememberSaveable(initial) { mutableStateOf(initial.otherIntolerances) }
    var notes by rememberSaveable(initial) { mutableStateOf(initial.notes) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TriCard {
            Text(
                stringResource(if (first) R.string.nutri_profile_first_title else R.string.nutri_profile_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(if (first) R.string.nutri_profile_first_body else R.string.nutri_profile_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        CollapsibleCard(
            title = stringResource(R.string.nutri_section_body),
            summary = draft.sex?.label,
            initiallyExpanded = first,
        ) {
            Hint(stringResource(R.string.nutri_body_hint))
            Label(stringResource(R.string.nutri_profile_sex))
            Chips {
                Sex.entries.forEach { sex ->
                    TriChip(sex.label, selected = draft.sex == sex, onClick = { draft = draft.copy(sex = sex) })
                }
            }
        }

        CollapsibleCard(
            title = stringResource(R.string.nutri_section_goal),
            summary = draft.diet.label,
            initiallyExpanded = first,
        ) {
            Label(stringResource(R.string.nutri_profile_goal))
            Chips { BodyGoal.entries.forEach { TriChip(it.label, selected = draft.goal == it, onClick = { draft = draft.copy(goal = it) }) } }
            Label(stringResource(R.string.nutri_profile_diet))
            Chips { Diet.entries.forEach { TriChip(it.label, selected = draft.diet == it, onClick = { draft = draft.copy(diet = it) }) } }
            Label(stringResource(R.string.nutri_profile_meals))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (NutritionProfile.MIN_MEALS + 1..NutritionProfile.MAX_MEALS).forEach { n ->
                    TriChip(n.toString(), selected = draft.mealsPerDay == n, onClick = { draft = draft.copy(mealsPerDay = n) }, modifier = Modifier.weight(1f))
                }
            }
        }

        CollapsibleCard(
            title = stringResource(R.string.nutri_section_allergies),
            summary = count(draft.allergies.size + NutritionProfile.parseList(otherAllergies).size),
            initiallyExpanded = first || draft.allergies.isNotEmpty(),
        ) {
            Hint(stringResource(R.string.nutri_allergies_hint))
            Chips {
                Allergen.entries.forEach { allergen ->
                    TriChip(allergen.label, selected = allergen in draft.allergies, onClick = { draft = draft.copy(allergies = draft.allergies.toggle(allergen)) })
                }
            }
            TriTextField(
                value = otherAllergies,
                onValueChange = { otherAllergies = it.take(NutritionProfile.TEXT_MAX) },
                label = stringResource(R.string.nutri_other),
                placeholder = stringResource(R.string.nutri_other_allergy_hint),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }

        CollapsibleCard(
            title = stringResource(R.string.nutri_section_intolerances),
            summary = count(draft.intolerances.size + NutritionProfile.parseList(otherIntolerances).size),
            initiallyExpanded = first || draft.intolerances.isNotEmpty(),
        ) {
            Hint(stringResource(R.string.nutri_intolerances_hint))
            Chips {
                Intolerance.entries.forEach { item ->
                    TriChip(item.label, selected = item in draft.intolerances, onClick = { draft = draft.copy(intolerances = draft.intolerances.toggle(item)) })
                }
            }
            TriTextField(
                value = otherIntolerances,
                onValueChange = { otherIntolerances = it.take(NutritionProfile.TEXT_MAX) },
                label = stringResource(R.string.nutri_other),
                placeholder = stringResource(R.string.nutri_other_intolerance_hint),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }

        CollapsibleCard(
            title = stringResource(R.string.nutri_section_likes),
            summary = count(NutritionProfile.parseList(likes).size),
            initiallyExpanded = first,
        ) {
            Hint(stringResource(R.string.nutri_likes_hint))
            TriTextField(
                value = likes,
                onValueChange = { likes = it.take(NutritionProfile.TEXT_MAX * 2) },
                label = stringResource(R.string.nutri_likes_field),
                placeholder = stringResource(R.string.nutri_likes_placeholder),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        CollapsibleCard(
            title = stringResource(R.string.nutri_section_dislikes),
            summary = count(draft.dislikes.size),
            initiallyExpanded = first || draft.dislikes.isNotEmpty(),
        ) {
            Hint(stringResource(R.string.nutri_dislikes_hint))
            draft.dislikes.forEachIndexed { index, entry ->
                ItemRow(
                    title = entry.food,
                    subtitle = entry.exceptions.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.nutri_dislike_except, it.joinToString(", ")) },
                    removeLabel = stringResource(R.string.nutri_remove, entry.food),
                    onRemove = { draft = draft.copy(dislikes = draft.dislikes.filterIndexed { i, _ -> i != index }) },
                )
            }
            var food by rememberSaveable { mutableStateOf("") }
            var except by rememberSaveable { mutableStateOf("") }
            TriTextField(food, { food = it.take(NutritionProfile.NAME_MAX) }, stringResource(R.string.nutri_dislike_food), Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = stringResource(R.string.nutri_dislike_food_hint))
            TriTextField(except, { except = it.take(NutritionProfile.TEXT_MAX) }, stringResource(R.string.nutri_dislike_exceptions), Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = stringResource(R.string.nutri_dislike_exceptions_hint))
            SecondaryButton(
                stringResource(R.string.nutri_add),
                enabled = food.isNotBlank() && draft.dislikes.size < NutritionProfile.MAX_ITEMS,
                compact = true,
                onClick = {
                    NutritionProfile.dislike(food, except)?.let { draft = draft.copy(dislikes = draft.dislikes + it) }
                    food = ""
                    except = ""
                },
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        CollapsibleCard(
            title = stringResource(R.string.nutri_section_red_meat),
            summary = redMeatLabel(draft.redMeatPerWeek),
            initiallyExpanded = first,
        ) {
            Chips {
                (0..NutritionProfile.MAX_RED_MEAT).forEach { n ->
                    TriChip(redMeatLabel(n), selected = draft.redMeatPerWeek == n, onClick = { draft = draft.copy(redMeatPerWeek = n) })
                }
                TriChip(redMeatLabel(null), selected = draft.redMeatPerWeek == null, onClick = { draft = draft.copy(redMeatPerWeek = null) })
            }
        }

        CollapsibleCard(
            title = stringResource(R.string.nutri_section_supplements),
            summary = count(draft.supplements.size),
            initiallyExpanded = first || draft.supplements.isNotEmpty(),
        ) {
            Hint(stringResource(R.string.nutri_supplements_hint))
            draft.supplements.forEachIndexed { index, item ->
                ItemRow(
                    title = item.name,
                    subtitle = listOf(item.dose, item.timing.label).filter(String::isNotBlank).joinToString(" · "),
                    removeLabel = stringResource(R.string.nutri_remove, item.name),
                    onRemove = { draft = draft.copy(supplements = draft.supplements.filterIndexed { i, _ -> i != index }) },
                )
            }
            var name by rememberSaveable { mutableStateOf("") }
            var dose by rememberSaveable { mutableStateOf("") }
            var timing by rememberSaveable { mutableStateOf(SupplementTiming.DAILY.key) }
            TriTextField(name, { name = it.take(NutritionProfile.NAME_MAX) }, stringResource(R.string.nutri_supplement_name), Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = stringResource(R.string.nutri_supplement_name_hint))
            TriTextField(dose, { dose = it.take(NutritionProfile.NAME_MAX) }, stringResource(R.string.nutri_supplement_dose), Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = stringResource(R.string.nutri_supplement_dose_hint))
            Label(stringResource(R.string.nutri_supplement_when))
            Chips { SupplementTiming.entries.forEach { TriChip(it.label, selected = timing == it.key, onClick = { timing = it.key }) } }
            SecondaryButton(
                stringResource(R.string.nutri_add),
                enabled = name.isNotBlank() && draft.supplements.size < NutritionProfile.MAX_SUPPLEMENTS,
                compact = true,
                onClick = {
                    val item = Supplement(name.trim(), dose.trim(), SupplementTiming.of(timing) ?: SupplementTiming.DAILY)
                    draft = draft.copy(supplements = draft.supplements + item)
                    name = ""
                    dose = ""
                },
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        CollapsibleCard(
            title = stringResource(R.string.nutri_section_drinks),
            summary = if (draft.noCaffeine) stringResource(R.string.nutri_no_caffeine_short) else count(draft.drinks.size),
            initiallyExpanded = first,
        ) {
            draft.drinks.forEachIndexed { index, item ->
                ItemRow(
                    title = item.name,
                    subtitle = item.perDay.takeIf { it.isNotBlank() },
                    removeLabel = stringResource(R.string.nutri_remove, item.name),
                    onRemove = { draft = draft.copy(drinks = draft.drinks.filterIndexed { i, _ -> i != index }) },
                )
            }
            var name by rememberSaveable { mutableStateOf("") }
            var perDay by rememberSaveable { mutableStateOf("") }
            TriTextField(name, { name = it.take(NutritionProfile.NAME_MAX) }, stringResource(R.string.nutri_drink_name), Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = stringResource(R.string.nutri_drink_name_hint))
            TriTextField(perDay, { perDay = it.take(NutritionProfile.NAME_MAX) }, stringResource(R.string.nutri_drink_per_day), Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = stringResource(R.string.nutri_drink_per_day_hint))
            SecondaryButton(
                stringResource(R.string.nutri_add),
                enabled = name.isNotBlank() && draft.drinks.size < NutritionProfile.MAX_DRINKS,
                compact = true,
                onClick = {
                    draft = draft.copy(drinks = draft.drinks + Drink(name.trim(), perDay.trim()))
                    name = ""
                    perDay = ""
                },
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = 52.dp)
                    .toggleable(value = draft.noCaffeine, role = Role.Switch, onValueChange = { draft = draft.copy(noCaffeine = it) }),
            ) {
                Text(stringResource(R.string.nutri_no_caffeine), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.noCaffeine,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedTrackColor = TriColors.track,
                        uncheckedBorderColor = TriColors.borderStrong,
                    ),
                )
            }
        }

        CollapsibleCard(title = stringResource(R.string.nutri_profile_notes), initiallyExpanded = first) {
            TriTextField(
                value = notes,
                onValueChange = { notes = it.take(500) },
                label = stringResource(R.string.nutri_profile_notes),
                placeholder = stringResource(R.string.nutri_profile_notes_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PrimaryButton(
            text = stringResource(R.string.nutri_profile_save),
            enabled = !saving,
            onClick = {
                onSave(
                    draft.copy(
                        likes = NutritionProfile.parseList(likes),
                        otherAllergies = otherAllergies.trim(),
                        otherIntolerances = otherIntolerances.trim(),
                        notes = notes.trim(),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(stringResource(R.string.nutri_profile_footer), Modifier.padding(horizontal = 4.dp))
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

private fun count(n: Int): String? = if (n == 0) null else n.toString()

@Composable
private fun redMeatLabel(n: Int?): String = when (n) {
    null -> stringResource(R.string.nutri_red_meat_free)
    0 -> stringResource(R.string.nutri_red_meat_never)
    else -> stringResource(R.string.nutri_red_meat_times, n)
}

@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun ItemRow(title: String, subtitle: String?, removeLabel: String, onRemove: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(TriIcons.Close, contentDescription = removeLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = TriColors.border)
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier.padding(bottom = 4.dp)) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}
