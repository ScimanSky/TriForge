package com.triforge.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.ui.account.AccountUiState
import com.triforge.app.ui.account.AccountViewModel
import com.triforge.app.ui.common.Avatar
import com.triforge.app.ui.common.NoticeLine
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.ScreenTopBar
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.TriChip
import com.triforge.app.ui.common.TriTextField
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.domain.AccountMapper
import com.triforge.domain.ProfileField
import com.triforge.domain.ProfileForm
import com.triforge.domain.RaceGoal

/**
 * Everything the athlete can change about themselves: photo, name, a short
 * bio, goal, body and thresholds. One Save button stores all of it.
 */
@Composable
fun EditProfileScreen(
    account: AccountViewModel,
    profile: ProfileViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountState by account.state.collectAsStateWithLifecycle()
    val profileState by profile.state.collectAsStateWithLifecycle()
    var name by rememberSaveable(accountState.loaded) { mutableStateOf(accountState.account.displayName) }
    var bio by rememberSaveable(accountState.loaded) { mutableStateOf(accountState.account.bio) }
    val identityDirty = name != accountState.account.displayName || bio != accountState.account.bio
    val saving = accountState.identitySaving || profileState.formSaving

    val leave = {
        profile.discardForm()
        account.clearIdentityNotice()
        onBack()
    }
    BackHandler(onBack = leave)

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(title = stringResource(R.string.edit_profile_title), onBack = leave)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoPicker(accountState, account, modifier = Modifier.align(Alignment.CenterHorizontally))
            SectionLabel(stringResource(R.string.edit_profile_about))
            IdentityFields(name = name, onName = { name = it }, bio = bio, onBio = { bio = it })
            ProfileFormSection(profileState, profile)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NoticeLine(accountState.identityNotice ?: profileState.formNotice)
            PrimaryButton(
                text = stringResource(if (saving) R.string.profile_saving else R.string.profile_save),
                onClick = {
                    if (identityDirty) account.saveIdentity(name, bio)
                    if (profileState.formDirty) profile.saveProfile()
                },
                enabled = (identityDirty || profileState.formDirty) && !saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Name and bio, shared with the first launch. */
@Composable
internal fun IdentityFields(name: String, onName: (String) -> Unit, bio: String?, onBio: (String) -> Unit) {
    TriTextField(
        value = name,
        onValueChange = { onName(it.take(AccountMapper.NAME_MAX_LENGTH)) },
        label = stringResource(R.string.edit_profile_name),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    if (bio != null) {
        TriTextField(
            value = bio,
            onValueChange = { onBio(it.take(AccountMapper.BIO_MAX_LENGTH)) },
            label = stringResource(R.string.edit_profile_bio),
            placeholder = stringResource(R.string.edit_profile_bio_hint),
            unit = "${bio.length}/${AccountMapper.BIO_MAX_LENGTH}",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The photo with a camera badge. Android's photo picker needs no permission
 * and lets the athlete share one picture only, not the whole gallery.
 */
@Composable
internal fun PhotoPicker(state: AccountUiState, account: AccountViewModel, modifier: Modifier = Modifier) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) account.setAvatar(uri)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val changeLabel = stringResource(R.string.edit_profile_photo_change)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = changeLabel, onClick = pick),
        ) {
            Avatar(
                jpeg = state.avatar,
                name = state.account.displayName,
                size = 112.dp,
                ring = true,
                contentDescription = stringResource(R.string.profile_photo),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(3.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (state.avatarBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = TriIcons.Camera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = pick) {
                Text(text = changeLabel, color = TriColors.accentText)
            }
            if (state.avatar != null) {
                TextButton(onClick = account::removeAvatar) {
                    Text(
                        text = stringResource(R.string.edit_profile_photo_remove),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// -- Athlete profile ------------------------------------------------------

private val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
private val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)

// A pace is typed with a colon, which the numeric keyboards lack.
private val paceKeyboard = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)

internal fun RaceGoal.labelRes(): Int = when (this) {
    RaceGoal.SPRINT -> R.string.goal_sprint
    RaceGoal.OLYMPIC -> R.string.goal_olympic
    RaceGoal.HALF -> R.string.goal_half
    RaceGoal.FULL -> R.string.goal_full
    RaceGoal.FITNESS -> R.string.goal_fitness
}

@Composable
private fun ProfileFormSection(state: ProfileUiState, viewModel: ProfileViewModel) {
    val form = state.form
    val errors = state.formErrors
    val edit: ((ProfileForm) -> ProfileForm) -> Unit = viewModel::editForm

    SectionLabel(stringResource(R.string.profile_goal))
    GoalChips(selected = form.goal, onSelect = { goal -> edit { it.copy(goal = goal) } })
    TriTextField(
        value = form.raceName,
        onValueChange = { text -> edit { it.copy(raceName = text) } },
        label = stringResource(R.string.profile_race),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TriTextField(
            value = form.raceDate,
            onValueChange = { text -> edit { it.copy(raceDate = text) } },
            label = stringResource(R.string.profile_race_date),
            placeholder = stringResource(R.string.profile_race_date_hint),
            isError = ProfileField.RACE_DATE in errors,
            keyboardOptions = paceKeyboard,
            modifier = Modifier.weight(1f),
        )
        TriTextField(
            value = form.weeklyHours,
            onValueChange = { text -> edit { it.copy(weeklyHours = text) } },
            label = stringResource(R.string.profile_weekly_hours),
            unit = stringResource(R.string.unit_h),
            isError = ProfileField.WEEKLY_HOURS in errors,
            keyboardOptions = decimalKeyboard,
            modifier = Modifier.weight(1f),
        )
    }

    SectionLabel(stringResource(R.string.profile_body))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TriTextField(
            value = form.weight,
            onValueChange = { text -> edit { it.copy(weight = text) } },
            label = stringResource(R.string.profile_weight),
            unit = stringResource(R.string.unit_kg),
            isError = ProfileField.WEIGHT in errors,
            keyboardOptions = decimalKeyboard,
            modifier = Modifier.weight(1f),
        )
        TriTextField(
            value = form.height,
            onValueChange = { text -> edit { it.copy(height = text) } },
            label = stringResource(R.string.profile_height),
            unit = stringResource(R.string.unit_cm),
            isError = ProfileField.HEIGHT in errors,
            keyboardOptions = numberKeyboard,
            modifier = Modifier.weight(1f),
        )
    }
    TriTextField(
        value = form.maxHr,
        onValueChange = { text -> edit { it.copy(maxHr = text) } },
        label = stringResource(R.string.profile_max_hr),
        unit = stringResource(R.string.unit_bpm),
        isError = ProfileField.MAX_HR in errors,
        keyboardOptions = numberKeyboard,
        modifier = Modifier.fillMaxWidth(),
    )

    SectionLabel(stringResource(R.string.profile_thresholds))
    ThresholdField(
        color = SportColors.swim,
        value = form.css,
        onValueChange = { text -> edit { it.copy(css = text) } },
        label = stringResource(R.string.profile_css),
        unit = stringResource(R.string.unit_min_100m),
        isError = ProfileField.CSS in errors,
        keyboardOptions = paceKeyboard,
    )
    ThresholdField(
        color = SportColors.bike,
        value = form.ftp,
        onValueChange = { text -> edit { it.copy(ftp = text) } },
        label = stringResource(R.string.profile_ftp),
        unit = stringResource(R.string.unit_watt),
        isError = ProfileField.FTP in errors,
        keyboardOptions = numberKeyboard,
    )
    ThresholdField(
        color = SportColors.run,
        value = form.thresholdPace,
        onValueChange = { text -> edit { it.copy(thresholdPace = text) } },
        label = stringResource(R.string.profile_run_pace),
        unit = stringResource(R.string.unit_min_km),
        isError = ProfileField.THRESHOLD_PACE in errors,
        keyboardOptions = paceKeyboard.copy(imeAction = ImeAction.Done),
    )
    Text(
        text = stringResource(R.string.profile_thresholds_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The race distances as chips. Tapping the chosen one again clears it. */
@Composable
internal fun GoalChips(selected: RaceGoal?, onSelect: (RaceGoal?) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RaceGoal.entries.forEach { goal ->
            TriChip(
                label = stringResource(goal.labelRes()),
                selected = selected == goal,
                onClick = { onSelect(if (selected == goal) null else goal) },
            )
        }
    }
}

/** A threshold field with its sport's color beside it. */
@Composable
private fun ThresholdField(
    color: Color,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    unit: String,
    isError: Boolean,
    keyboardOptions: KeyboardOptions,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        TriTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            unit = unit,
            isError = isError,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.weight(1f),
        )
    }
}
