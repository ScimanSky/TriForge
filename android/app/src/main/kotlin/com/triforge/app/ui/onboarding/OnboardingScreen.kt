package com.triforge.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.ui.account.AccountViewModel
import com.triforge.app.ui.account.PrivacyTask
import com.triforge.app.ui.auth.Wordmark
import com.triforge.app.ui.common.ConfirmDialog
import com.triforge.app.ui.common.ConsentCheck
import com.triforge.app.ui.common.IconBox
import com.triforge.app.ui.common.NoticeLine
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.TriTextField
import com.triforge.app.ui.legal.LegalDoc
import com.triforge.app.ui.legal.LegalScreen
import com.triforge.app.ui.profile.GoalChips
import com.triforge.app.ui.profile.IdentityFields
import com.triforge.app.ui.profile.PhotoPicker
import com.triforge.app.ui.profile.ProfileViewModel
import com.triforge.app.ui.settings.DataSourceCards
import com.triforge.app.ui.settings.DeleteAccountDialog
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.domain.AccountGate
import com.triforge.domain.AgePolicy
import com.triforge.domain.ConsentType
import com.triforge.domain.Onboarding
import com.triforge.domain.OnboardingStep
import java.time.LocalDate

/**
 * The first launch: welcome, age, privacy and consents, a short profile, the
 * data sources. When only the consents are missing (new legal texts, or a
 * withdrawn health consent) [gate] is [AccountGate.Reconsent] and only the
 * privacy step is shown.
 */
@Composable
fun OnboardingScreen(
    gate: AccountGate,
    account: AccountViewModel,
    profile: ProfileViewModel,
    modifier: Modifier = Modifier,
) {
    val reconsent = gate is AccountGate.Reconsent
    val first = (gate as? AccountGate.Onboarding)?.from ?: OnboardingStep.PRIVACY
    var step by rememberSaveable { mutableStateOf(first) }
    var legal by rememberSaveable { mutableStateOf<LegalDoc?>(null) }

    val shownLegal = legal
    if (shownLegal != null) {
        BackHandler { legal = null }
        Box(modifier.fillMaxSize().safeDrawingPadding()) {
            LegalScreen(doc = shownLegal, onBack = { legal = null })
        }
        return
    }

    val back = Onboarding.previous(step)?.takeIf { !reconsent && step != OnboardingStep.DONE && it >= first }
    if (back != null) BackHandler { step = back }
    val next = { step = Onboarding.next(step) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
    ) {
        if (!reconsent && step in Onboarding.countedSteps) {
            StepHeader(step = step, onBack = back?.let { { step = it } })
        }
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(onStart = next)
            OnboardingStep.AGE -> AgeStep(account, onDone = next)
            OnboardingStep.PRIVACY -> PrivacyStep(
                account = account,
                reconsent = reconsent,
                onOpen = { legal = it },
                onDone = { if (!reconsent) next() },
            )
            OnboardingStep.PROFILE -> ProfileStep(account, profile, onDone = next)
            OnboardingStep.SOURCES -> SourcesStep(profile, onDone = next)
            OnboardingStep.DONE -> DoneStep(onFinish = account::completeOnboarding)
        }
    }
}

// -- Frame -------------------------------------------------------------------

/** Four short bars for the four steps, and the way back. */
@Composable
private fun StepHeader(step: OnboardingStep, onBack: (() -> Unit)?) {
    val index = Onboarding.countedSteps.indexOf(step)
    val total = Onboarding.countedSteps.size
    val description = stringResource(R.string.onboarding_step_of, index + 1, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (onBack != null) com.triforge.app.ui.common.BackButton(onBack)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= index) MaterialTheme.colorScheme.primary else TriColors.track),
                )
            }
        }
        Spacer(Modifier.size(48.dp))
    }
}

/** A step's scrolling body, with its actions pinned at the bottom. */
@Composable
private fun ColumnScope.StepBody(
    title: String,
    body: String?,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        if (body != null) {
            Text(text = body, style = MaterialTheme.typography.bodyLarge, color = TriColors.textDim)
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = actions,
    )
}

// -- Steps -------------------------------------------------------------------

@Composable
private fun ColumnScope.WelcomeStep(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Wordmark()
        Spacer(Modifier.height(20.dp))
        Text(text = stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.displaySmall)
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = TriColors.textDim,
        )
        Spacer(Modifier.height(8.dp))
        Feature(TriIcons.Activity, SportColors.swim, R.string.onboarding_feature_stats_title, R.string.onboarding_feature_stats_body)
        Feature(TriIcons.Heart, SportColors.run, R.string.onboarding_feature_recovery_title, R.string.onboarding_feature_recovery_body)
        Feature(TriIcons.Sparkle, SportColors.bike, R.string.onboarding_feature_coach_title, R.string.onboarding_feature_coach_body)
        Feature(TriIcons.Shield, TriColors.good, R.string.onboarding_feature_privacy_title, R.string.onboarding_feature_privacy_body)
    }
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(text = stringResource(R.string.onboarding_start), onClick = onStart, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.onboarding_time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, color: Color, title: Int, body: Int) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBox(background = color.copy(alpha = 0.14f)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium, color = TriColors.textDim)
        }
    }
}

@Composable
private fun ColumnScope.AgeStep(account: AccountViewModel, onDone: () -> Unit) {
    val state by account.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(state.account.birthYear?.toString().orEmpty()) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var tooYoung by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()

    val submit = {
        val year = AgePolicy.parseYear(text)
        when {
            year == null || !AgePolicy.isPlausible(year, today) -> error = R.string.onboarding_age_error
            !AgePolicy.isOldEnough(year, today) -> tooYoung = true
            else -> account.saveBirthYear(year, onDone)
        }
    }

    if (tooYoung) {
        StepBody(
            title = stringResource(R.string.onboarding_young_title),
            body = stringResource(R.string.onboarding_young_body, AgePolicy.MIN_AGE),
            actions = {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_young_delete),
                    onClick = { confirmDelete = true },
                    enabled = state.task == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { tooYoung = false; text = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_young_mistake), color = TriColors.accentText)
                }
                NoticeLine(state.notice)
            },
        ) {}
        if (confirmDelete) {
            DeleteAccountDialog(
                onConfirm = {
                    confirmDelete = false
                    account.deleteAccount()
                },
                onDismiss = { confirmDelete = false },
            )
        }
        return
    }

    StepBody(
        title = stringResource(R.string.onboarding_age_title),
        body = stringResource(R.string.onboarding_age_body, AgePolicy.MIN_AGE),
        actions = {
            PrimaryButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = submit,
                enabled = text.length == 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        TriTextField(
            value = text,
            onValueChange = { typed ->
                text = typed.filter { it.isDigit() }.take(4)
                error = null
            },
            label = stringResource(R.string.onboarding_age_field),
            placeholder = stringResource(R.string.onboarding_age_hint),
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Text(
            text = stringResource(R.string.onboarding_age_why),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColumnScope.PrivacyStep(
    account: AccountViewModel,
    reconsent: Boolean,
    onOpen: (LegalDoc) -> Unit,
    onDone: () -> Unit,
) {
    val state by account.state.collectAsStateWithLifecycle()
    val stored = state.account
    var terms by rememberSaveable { mutableStateOf(stored.hasValidConsent(ConsentType.TERMS)) }
    var health by rememberSaveable { mutableStateOf(stored.hasValidConsent(ConsentType.HEALTH)) }
    var ai by rememberSaveable { mutableStateOf(stored.consent(ConsentType.AI_COACH)?.granted == true) }
    var declined by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val required = stringResource(R.string.consent_required)
    val optional = stringResource(R.string.consent_optional)
    val healthWithdrawn = stored.consent(ConsentType.HEALTH)?.granted == false

    StepBody(
        title = stringResource(
            when {
                !reconsent -> R.string.onboarding_privacy_title
                healthWithdrawn -> R.string.reconsent_health_title
                else -> R.string.reconsent_title
            },
        ),
        body = stringResource(
            when {
                !reconsent -> R.string.onboarding_privacy_body
                healthWithdrawn -> R.string.reconsent_health_body
                else -> R.string.reconsent_body
            },
        ),
        actions = {
            NoticeLine(state.notice)
            PrimaryButton(
                text = stringResource(R.string.onboarding_privacy_accept),
                onClick = {
                    account.answerConsents(
                        mapOf(ConsentType.TERMS to terms, ConsentType.HEALTH to health, ConsentType.AI_COACH to ai),
                        onSaved = onDone,
                    )
                },
                enabled = terms && health && state.task == null,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { declined = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_privacy_decline), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        TriCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Summary(TriIcons.Activity, R.string.privacy_point_what_title, R.string.privacy_point_what_short)
                Summary(TriIcons.Heart, R.string.privacy_point_why_title, R.string.privacy_point_why_short)
                Summary(TriIcons.Lock, R.string.privacy_point_where_title, R.string.privacy_point_where_short)
                Summary(TriIcons.Shield, R.string.privacy_point_rights_title, R.string.privacy_point_rights_short)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkChip(stringResource(R.string.onboarding_read_privacy)) { onOpen(LegalDoc.PRIVACY) }
            LinkChip(stringResource(R.string.onboarding_read_terms)) { onOpen(LegalDoc.TERMS) }
        }
        SectionLabel(stringResource(R.string.privacy_consents))
        ConsentCheck(
            checked = terms,
            onCheckedChange = { terms = it },
            title = stringResource(R.string.consent_terms_title),
            body = stringResource(R.string.consent_terms_body),
            tag = required,
        )
        HorizontalDivider(color = TriColors.border)
        ConsentCheck(
            checked = health,
            onCheckedChange = { health = it },
            title = stringResource(R.string.consent_health_title),
            body = stringResource(R.string.consent_health_body),
            tag = required,
        )
        HorizontalDivider(color = TriColors.border)
        ConsentCheck(
            checked = ai,
            onCheckedChange = { ai = it },
            title = stringResource(R.string.consent_ai_title),
            body = stringResource(R.string.consent_ai_body),
            tag = optional,
        )
        Text(
            text = stringResource(R.string.onboarding_privacy_change_later),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (declined) {
        ConfirmDialog(
            title = stringResource(R.string.onboarding_declined_title),
            body = stringResource(R.string.onboarding_declined_body),
            confirmLabel = stringResource(R.string.onboarding_declined_delete),
            danger = true,
            onConfirm = {
                declined = false
                confirmDelete = true
            },
            onDismiss = { declined = false },
        )
    }
    if (confirmDelete) {
        DeleteAccountDialog(
            onConfirm = {
                confirmDelete = false
                account.deleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Summary(icon: ImageVector, title: Int, body: Int) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = TriColors.accentText, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(text = stringResource(body), style = MaterialTheme.typography.bodySmall, color = TriColors.textDim)
        }
    }
}

@Composable
private fun LinkChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(TriIcons.Document, contentDescription = null, tint = TriColors.accentText, modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = TriColors.accentText)
    }
}

@Composable
private fun ColumnScope.ProfileStep(account: AccountViewModel, profile: ProfileViewModel, onDone: () -> Unit) {
    val accountState by account.state.collectAsStateWithLifecycle()
    val profileState by profile.state.collectAsStateWithLifecycle()
    var name by rememberSaveable(accountState.loaded) {
        mutableStateOf(accountState.account.displayName.ifBlank { profileState.name })
    }

    StepBody(
        title = stringResource(R.string.onboarding_profile_title),
        body = stringResource(R.string.onboarding_profile_body),
        actions = {
            NoticeLine(accountState.identityNotice)
            PrimaryButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = {
                    if (profileState.formDirty) profile.saveProfile()
                    account.saveIdentity(name, accountState.account.bio, onSaved = onDone)
                },
                enabled = name.isNotBlank() && !accountState.identitySaving,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        PhotoPicker(accountState, account, modifier = Modifier.align(Alignment.CenterHorizontally))
        IdentityFields(name = name, onName = { name = it }, bio = null, onBio = {})
        SectionLabel(stringResource(R.string.onboarding_profile_goal))
        GoalChips(
            selected = profileState.form.goal,
            onSelect = { goal -> profile.editForm { it.copy(goal = goal) } },
        )
        Text(
            text = stringResource(R.string.onboarding_profile_later),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColumnScope.SourcesStep(profile: ProfileViewModel, onDone: () -> Unit) {
    val state by profile.state.collectAsStateWithLifecycle()
    StepBody(
        title = stringResource(R.string.onboarding_sources_title),
        body = stringResource(R.string.onboarding_sources_body),
        actions = {
            val connected = state.garmin.connected || state.healthConnectGranted
            if (connected) {
                PrimaryButton(stringResource(R.string.onboarding_continue), onClick = onDone, modifier = Modifier.fillMaxWidth())
            } else {
                SecondaryButton(stringResource(R.string.onboarding_sources_later), onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        DataSourceCards(state, profile)
    }
}

@Composable
private fun ColumnScope.DoneStep(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TriIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Text(
            text = stringResource(R.string.onboarding_done_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_done_body),
            style = MaterialTheme.typography.bodyLarge,
            color = TriColors.textDim,
            textAlign = TextAlign.Center,
        )
    }
    PrimaryButton(
        text = stringResource(R.string.onboarding_done_cta),
        onClick = onFinish,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}
