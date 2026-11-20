package com.triforge.app.ui.coach

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.delay

/**
 * The whole food profile (board 7c), in collapsible sections: body, goal and
 * diet, allergies, intolerances, favourite foods, foods the athlete does not
 * eat with their exceptions, red meat, supplements, drinks. Allergies and
 * intolerances are checked by the app on every meal NutriAI proposes.
 */
@Composable
fun NutriProfileEditor(
    initial: NutritionProfile,
    first: Boolean,
    saving: Boolean,
    onSave: (NutritionProfile) -> Unit,
    onKeep: ((NutritionProfile) -> Unit)? = null,
    /** Board 7j: the profile is a page of its own; the sections scroll and «Salva» stays in view. */
    page: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Not keyed on [initial]: every change is kept through [onKeep] and comes back
    // as a new [initial], which must not reset what is being typed.
    var draft by remember { mutableStateOf(initial) }
    // What is typed in a list field but not added yet: saving adds it too, so nothing typed is lost.
    var likeInput by rememberSaveable { mutableStateOf("") }
    var allergyInput by rememberSaveable { mutableStateOf("") }
    var intoleranceInput by rememberSaveable { mutableStateOf("") }
    var otherAllergies by rememberSaveable { mutableStateOf(initial.otherAllergies) }
    var otherIntolerances by rememberSaveable { mutableStateOf(initial.otherIntolerances) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }

    // Every choice, added or removed item is kept at once; the notes a moment after the last key.
    if (onKeep != null) {
        var kept by remember { mutableStateOf(initial) }
        val current = draft.copy(otherAllergies = otherAllergies, otherIntolerances = otherIntolerances, notes = kept.notes)
        LaunchedEffect(current) {
            if (current != kept) {
                kept = current
                onKeep(current)
            }
        }
        LaunchedEffect(notes) {
            delay(NOTES_KEEP_DELAY_MS)
            val withNotes = kept.copy(notes = notes.trim())
            if (withNotes != kept) {
                kept = withNotes
                onKeep(withNotes)
            }
        }
    }

    val save = {
        onSave(
            draft.copy(
                likes = merge(draft.likes, likeInput),
                otherAllergies = joinCapped(merge(NutritionProfile.parseList(otherAllergies), allergyInput)),
                otherIntolerances = joinCapped(merge(NutritionProfile.parseList(otherIntolerances), intoleranceInput)),
                notes = notes.trim(),
            ),
        )
    }

    Column(modifier) {
        Column(
            modifier = if (page) Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp) else Modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (page) {
                // The page has its own title: here only what the profile is for.
                Hint(stringResource(R.string.nutri_profile_body), Modifier.padding(horizontal = 4.dp))
            } else {
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
                TextItems(
                    items = NutritionProfile.parseList(otherAllergies),
                    onChange = { otherAllergies = joinCapped(it) },
                    input = allergyInput,
                    onInput = { allergyInput = it },
                    label = stringResource(R.string.nutri_other),
                    placeholder = stringResource(R.string.nutri_other_allergy_hint),
                    fits = ::fitsOther,
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
                TextItems(
                    items = NutritionProfile.parseList(otherIntolerances),
                    onChange = { otherIntolerances = joinCapped(it) },
                    input = intoleranceInput,
                    onInput = { intoleranceInput = it },
                    label = stringResource(R.string.nutri_other),
                    placeholder = stringResource(R.string.nutri_other_intolerance_hint),
                    fits = ::fitsOther,
                )
            }

            CollapsibleCard(
                title = stringResource(R.string.nutri_section_likes),
                summary = count(draft.likes.size),
                initiallyExpanded = first || draft.likes.isNotEmpty(),
            ) {
                Hint(stringResource(R.string.nutri_likes_hint))
                TextItems(
                    items = draft.likes,
                    onChange = { draft = draft.copy(likes = it) },
                    input = likeInput,
                    onInput = { likeInput = it },
                    label = stringResource(R.string.nutri_likes_field),
                    placeholder = stringResource(R.string.nutri_likes_placeholder),
                )
            }

            CollapsibleCard(
                title = stringResource(R.string.nutri_section_dislikes),
                summary = count(draft.dislikes.size),
                initiallyExpanded = first || draft.dislikes.isNotEmpty(),
            ) {
                Hint(stringResource(R.string.nutri_dislikes_hint))
                var food by rememberSaveable { mutableStateOf("") }
                var except by rememberSaveable { mutableStateOf("") }
                val canAdd = food.isNotBlank() && draft.dislikes.size < NutritionProfile.MAX_ITEMS
                val firstField = remember { FocusRequester() }
                val add = {
                    if (canAdd) {
                        NutritionProfile.dislike(food, except)?.let { draft = draft.copy(dislikes = draft.dislikes + it) }
                        food = ""
                        except = ""
                        firstField.requestFocus()
                    }
                }
                AddGroup {
                    TriTextField(food, { food = it.take(NutritionProfile.NAME_MAX) }, stringResource(R.string.nutri_dislike_food), Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(firstField), placeholder = stringResource(R.string.nutri_dislike_food_hint), keyboardOptions = NextField)
                    TriTextField(except, { except = it.take(NutritionProfile.TEXT_MAX) }, stringResource(R.string.nutri_dislike_exceptions), Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = stringResource(R.string.nutri_dislike_exceptions_hint), keyboardOptions = LastField, keyboardActions = KeyboardActions(onDone = { add() }))
                    SecondaryButton(stringResource(R.string.nutri_add), enabled = canAdd, compact = true, onClick = add, modifier = Modifier.padding(top = 10.dp))
                }
                ItemsFold(draft.dislikes.map { it.food }) {
                    draft.dislikes.forEachIndexed { index, entry ->
                        ItemRow(
                            title = entry.food,
                            subtitle = entry.exceptions.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.nutri_dislike_except, it.joinToString(", ")) },
                            removeLabel = stringResource(R.string.nutri_remove, entry.food),
                            onRemove = { draft = draft.copy(dislikes = draft.dislikes.filterIndexed { i, _ -> i != index }) },
                        )
                    }
                }
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
                var name by rememberSaveable { mutableStateOf("") }
                var dose by rememberSaveable { mutableStateOf("") }
                var timing by rememberSaveable { mutableStateOf(SupplementTiming.DAILY.key) }
                val canAdd = name.isNotBlank() && draft.supplements.size < NutritionProfile.MAX_SUPPLEMENTS
                val firstField = remember { FocusRequester() }
                val add = {
                    if (canAdd) {
                        val item = Supplement(name.trim(), dose.trim(), SupplementTiming.of(timing) ?: SupplementTiming.DAILY)
                        draft = draft.copy(supplements = draft.supplements + item)
                        name = ""
                        dose = ""
                        firstField.requestFocus()
                    }
                }
                AddGroup {
                    TriTextField(name, { name = it.take(NutritionProfile.NAME_MAX) }, stringResource(R.string.nutri_supplement_name), Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(firstField), placeholder = stringResource(R.string.nutri_supplement_name_hint), keyboardOptions = NextField)
                    TriTextField(dose, { dose = it.take(NutritionProfile.NAME_MAX) }, stringResource(R.string.nutri_supplement_dose), Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = stringResource(R.string.nutri_supplement_dose_hint), keyboardOptions = LastField, keyboardActions = KeyboardActions(onDone = { add() }))
                    Label(stringResource(R.string.nutri_supplement_when))
                    Chips { SupplementTiming.entries.forEach { TriChip(it.label, selected = timing == it.key, onClick = { timing = it.key }) } }
                    SecondaryButton(stringResource(R.string.nutri_add), enabled = canAdd, compact = true, onClick = add, modifier = Modifier.padding(top = 10.dp))
                }
                ItemsFold(draft.supplements.map { it.name }) {
                    draft.supplements.forEachIndexed { index, item ->
                        ItemRow(
                            title = item.name,
                            subtitle = listOf(item.dose, item.timing.label).filter(String::isNotBlank).joinToString(" · "),
                            removeLabel = stringResource(R.string.nutri_remove, item.name),
                            onRemove = { draft = draft.copy(supplements = draft.supplements.filterIndexed { i, _ -> i != index }) },
                        )
                    }
                }
            }

            CollapsibleCard(
                title = stringResource(R.string.nutri_section_drinks),
                summary = if (draft.noCaffeine) stringResource(R.string.nutri_no_caffeine_short) else count(draft.drinks.size),
                initiallyExpanded = first,
            ) {
                var name by rememberSaveable { mutableStateOf("") }
                var perDay by rememberSaveable { mutableStateOf("") }
                val canAdd = name.isNotBlank() && draft.drinks.size < NutritionProfile.MAX_DRINKS
                val firstField = remember { FocusRequester() }
                val add = {
                    if (canAdd) {
                        draft = draft.copy(drinks = draft.drinks + Drink(name.trim(), perDay.trim()))
                        name = ""
                        perDay = ""
                        firstField.requestFocus()
                    }
                }
                AddGroup {
                    TriTextField(name, { name = it.take(NutritionProfile.NAME_MAX) }, stringResource(R.string.nutri_drink_name), Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(firstField), placeholder = stringResource(R.string.nutri_drink_name_hint), keyboardOptions = NextField)
                    TriTextField(perDay, { perDay = it.take(NutritionProfile.NAME_MAX) }, stringResource(R.string.nutri_drink_per_day), Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = stringResource(R.string.nutri_drink_per_day_hint), keyboardOptions = LastField, keyboardActions = KeyboardActions(onDone = { add() }))
                    SecondaryButton(stringResource(R.string.nutri_add), enabled = canAdd, compact = true, onClick = add, modifier = Modifier.padding(top = 10.dp))
                }
                ItemsFold(draft.drinks.map { it.name }) {
                    draft.drinks.forEachIndexed { index, item ->
                        ItemRow(
                            title = item.name,
                            subtitle = item.perDay.takeIf { it.isNotBlank() },
                            removeLabel = stringResource(R.string.nutri_remove, item.name),
                            onRemove = { draft = draft.copy(drinks = draft.drinks.filterIndexed { i, _ -> i != index }) },
                        )
                    }
                }
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

            if (!page) {
                PrimaryButton(
                    text = stringResource(R.string.nutri_profile_save),
                    enabled = !saving,
                    onClick = save,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Hint(stringResource(R.string.nutri_profile_footer), Modifier.padding(horizontal = 4.dp))
        }
        if (page) {
            // Always in view, above the keyboard too.
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.nutri_profile_save),
                    enabled = !saving,
                    onClick = save,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

private fun count(n: Int): String? = if (n == 0) null else n.toString()

/** The list plus what was typed, split on commas, without duplicates and within the limits. */
private fun merge(items: List<String>, typed: String): List<String> =
    NutritionProfile.parseList((items + typed).joinToString(","))

/** Joins the items of an «Altro» field, dropping the last ones if the text would get too long. */
private fun joinCapped(items: List<String>): String {
    var kept = items
    while (kept.joinToString(", ").length > NutritionProfile.TEXT_MAX) kept = kept.dropLast(1)
    return kept.joinToString(", ")
}

/** An «Altro» field is saved as one text: a new item is accepted only if the whole text still fits. */
private fun fitsOther(items: List<String>): Boolean = items.joinToString(", ").length <= NutritionProfile.TEXT_MAX

private const val NOTES_KEEP_DELAY_MS = 700L

private val NextField = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
private val LastField = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)

/**
 * The fields and the «Aggiungi» button of a list section. While one of its
 * fields has the focus the whole group is scrolled above the keyboard, so the
 * button stays reachable without closing it.
 */
@Composable
private fun AddGroup(content: @Composable ColumnScope.() -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, keyboard) { if (focused) requester.bringIntoView() }
    Column(Modifier.bringIntoViewRequester(requester).onFocusChanged { focused = it.hasFocus }, content = content)
}

/**
 * A list of plain names (favourite foods, other allergies): one field with
 * «Aggiungi» beside it, then the items folded under it. The keyboard's confirm
 * key adds too and leaves the keyboard open for the next one.
 */
@Composable
private fun TextItems(
    items: List<String>,
    onChange: (List<String>) -> Unit,
    input: String,
    onInput: (String) -> Unit,
    label: String,
    placeholder: String,
    fits: (List<String>) -> Boolean = { true },
) {
    val merged = merge(items, input)
    val canAdd = input.isNotBlank() && items.size < NutritionProfile.MAX_ITEMS && fits(merged)
    val add = {
        if (canAdd) {
            onChange(merged)
            onInput("")
        }
    }
    AddGroup {
        // The button sits beside the field, so it is on screen whenever the field is.
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TriTextField(
                value = input,
                onValueChange = { onInput(it.take(NutritionProfile.TEXT_MAX)) },
                label = label,
                placeholder = placeholder,
                modifier = Modifier.weight(1f),
                keyboardOptions = LastField,
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            SecondaryButton(stringResource(R.string.nutri_add), enabled = canAdd, compact = true, onClick = add, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        }
    }
    ItemsFold(items) {
        items.forEachIndexed { index, item ->
            ItemRow(
                title = item,
                subtitle = null,
                removeLabel = stringResource(R.string.nutri_remove, item),
                onRemove = { onChange(items.filterIndexed { i, _ -> i != index }) },
            )
        }
    }
}

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

/**
 * The items of a list, folded under the field that adds them: one line with
 * the names, newest first, that opens the full list with the remove buttons.
 * Folded, a long list never pushes the field and «Aggiungi» under the keyboard.
 */
@Composable
private fun ItemsFold(names: List<String>, content: @Composable ColumnScope.() -> Unit) {
    if (names.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (open) 180f else 0f, label = "chevron")
    Column(Modifier.padding(top = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = stringResource(if (open) R.string.action_collapse else R.string.action_expand),
                    role = Role.Button,
                ) { open = !open },
        ) {
            Text(
                names.asReversed().joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(names.size.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(TriIcons.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).rotate(rotation))
        }
        if (open) {
            HorizontalDivider(color = TriColors.border)
            content()
        }
    }
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
