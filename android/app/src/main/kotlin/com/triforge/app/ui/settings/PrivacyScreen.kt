package com.triforge.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.ui.account.AccountUiState
import com.triforge.app.ui.account.AccountViewModel
import com.triforge.app.ui.account.PrivacyTask
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.ConfirmDialog
import com.triforge.app.ui.common.ConsentCheck
import com.triforge.app.ui.common.IconBox
import com.triforge.app.ui.common.NoticeLine
import com.triforge.app.ui.common.ScreenTopBar
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.SettingsDivider
import com.triforge.app.ui.common.SettingsGroup
import com.triforge.app.ui.common.SettingsRow
import com.triforge.app.ui.common.StatGrid
import com.triforge.app.ui.common.SwitchRow
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.domain.ConsentType
import com.triforge.domain.LegalTexts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val consentDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALY)

/**
 * The athlete's privacy controls in one place: the consents and their dates,
 * a copy of the data, what is stored and why, and deleting the account.
 */
@Composable
fun PrivacyScreen(
    account: AccountViewModel,
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by account.state.collectAsStateWithLifecycle()
    var confirmWithdraw by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) account.exportTo(uri)
    }
    val exportName = stringResource(R.string.privacy_export_file, LocalDate.now().toString())

    LaunchedEffect(Unit) {
        account.clearNotice()
        account.loadCounts()
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.settings_privacy), onBack = onBack)
        if (state.task != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IntroCard()
            NoticeLine(state.notice)

            SectionLabel(stringResource(R.string.privacy_consents))
            ConsentsGroup(
                state = state,
                onHealthOff = { confirmWithdraw = true },
                onAiCoach = account::setAiCoach,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                onOpenTerms = onOpenTerms,
            )

            SectionLabel(stringResource(R.string.privacy_your_data))
            StoredDataCard(state)
            SettingsGroup {
                SettingsRow(
                    icon = TriIcons.Download,
                    title = stringResource(R.string.privacy_export),
                    subtitle = stringResource(
                        if (state.task == PrivacyTask.EXPORT) R.string.privacy_export_running else R.string.privacy_export_hint,
                    ),
                    enabled = state.task == null,
                    onClick = { exporter.launch(exportName) },
                )
            }
            WhatWeCollectCard()

            SectionLabel(stringResource(R.string.privacy_delete_section))
            SettingsGroup {
                SettingsRow(
                    icon = TriIcons.Trash,
                    title = stringResource(R.string.privacy_delete),
                    subtitle = stringResource(
                        if (state.task == PrivacyTask.DELETE_ACCOUNT) R.string.privacy_delete_running else R.string.privacy_delete_hint,
                    ),
                    danger = true,
                    enabled = state.task == null,
                    onClick = { confirmDelete = true },
                )
            }
        }
    }

    if (confirmWithdraw) {
        ConfirmDialog(
            title = stringResource(R.string.privacy_withdraw_title),
            body = stringResource(R.string.privacy_withdraw_body),
            confirmLabel = stringResource(R.string.privacy_withdraw_confirm),
            danger = true,
            onConfirm = {
                confirmWithdraw = false
                account.withdrawHealthConsent()
            },
            onDismiss = { confirmWithdraw = false },
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

/** Asks twice, in a way: the button only works once the box is ticked. */
@Composable
fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var understood by rememberSaveable { mutableStateOf(false) }
    ConfirmDialog(
        title = stringResource(R.string.privacy_delete_title),
        body = stringResource(R.string.privacy_delete_body),
        confirmLabel = stringResource(R.string.privacy_delete_confirm),
        danger = true,
        confirmEnabled = understood,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        ConsentCheck(
            checked = understood,
            onCheckedChange = { understood = it },
            title = stringResource(R.string.privacy_delete_check),
            body = stringResource(R.string.privacy_delete_check_hint),
            tag = "",
        )
    }
}

@Composable
private fun IntroCard() {
    TriCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBox(background = MaterialTheme.colorScheme.primaryContainer) {
                Icon(TriIcons.Shield, contentDescription = null, tint = TriColors.accentText, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(R.string.privacy_intro_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.privacy_intro_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TriColors.textDim,
                )
            }
        }
    }
}

private fun Instant?.asDate(): String? = this?.atZone(ZoneId.systemDefault())?.let(consentDateFormat::format)

@Composable
private fun ConsentsGroup(
    state: AccountUiState,
    onHealthOff: () -> Unit,
    onAiCoach: (Boolean) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
) {
    val account = state.account
    val health = account.consent(ConsentType.HEALTH)
    val ai = account.consent(ConsentType.AI_COACH)
    val terms = account.consent(ConsentType.TERMS)
    val busy = state.task != null

    SettingsGroup {
        SwitchRow(
            icon = TriIcons.Heart,
            title = stringResource(R.string.consent_health_title),
            subtitle = listOfNotNull(
                stringResource(R.string.privacy_health_hint),
                health?.takeIf { it.granted }?.at.asDate()?.let { stringResource(R.string.privacy_given_on, it) },
            ).joinToString("\n"),
            checked = account.hasValidConsent(ConsentType.HEALTH),
            // Turning it back on happens through the screen that blocks the app.
            onCheckedChange = { on -> if (!on) onHealthOff() },
            enabled = !busy,
        )
        SettingsDivider()
        SwitchRow(
            icon = TriIcons.Sparkle,
            title = stringResource(R.string.consent_ai_title),
            subtitle = listOfNotNull(
                stringResource(R.string.privacy_ai_hint),
                ai?.at.asDate()?.let {
                    stringResource(if (ai?.granted == true) R.string.privacy_given_on else R.string.privacy_refused_on, it)
                },
            ).joinToString("\n"),
            checked = account.aiCoachAllowed,
            onCheckedChange = onAiCoach,
            enabled = !busy,
        )
        SettingsDivider()
        SettingsRow(
            icon = TriIcons.Document,
            title = stringResource(R.string.legal_privacy_title),
            subtitle = terms?.at.asDate()?.let { stringResource(R.string.privacy_accepted_on, it, LegalTexts.VERSION) },
            onClick = onOpenPrivacyPolicy,
        )
        SettingsDivider()
        SettingsRow(
            icon = TriIcons.Document,
            title = stringResource(R.string.legal_terms_title),
            onClick = onOpenTerms,
        )
    }
}

/** How much is stored, as numbers: the privacy page follows the app's card rule too. */
@Composable
private fun StoredDataCard(state: AccountUiState) {
    val none = stringResource(R.string.value_none)
    val counts = state.counts
    TriCard {
        CardHeader(title = stringResource(R.string.privacy_stored_title))
        StatGrid(
            stats = listOf(
                (counts?.activities?.toString() ?: none) to stringResource(R.string.privacy_stored_activities),
                (counts?.wellnessDays?.toString() ?: none) to stringResource(R.string.privacy_stored_wellness),
                (counts?.checkIns?.toString() ?: none) to stringResource(R.string.privacy_stored_checkins),
            ),
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.privacy_stored_where),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** The short version of the privacy notice, folded: the full text is one tap away. */
@Composable
private fun WhatWeCollectCard() {
    CollapsibleCard(title = stringResource(R.string.privacy_what_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrivacyPoint(R.string.privacy_point_what_title, R.string.privacy_point_what_body)
            PrivacyPoint(R.string.privacy_point_why_title, R.string.privacy_point_why_body)
            PrivacyPoint(R.string.privacy_point_where_title, R.string.privacy_point_where_body)
            PrivacyPoint(R.string.privacy_point_who_title, R.string.privacy_point_who_body)
            PrivacyPoint(R.string.privacy_point_rights_title, R.string.privacy_point_rights_body)
        }
    }
}

@Composable
fun PrivacyPoint(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium, color = TriColors.textDim)
    }
}
