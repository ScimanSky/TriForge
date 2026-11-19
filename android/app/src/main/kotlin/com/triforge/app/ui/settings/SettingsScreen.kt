package com.triforge.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.triforge.app.ui.common.Avatar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.BuildConfig
import com.triforge.app.R
import com.triforge.app.data.HealthConnectAvailability
import com.triforge.app.ui.account.AccountViewModel
import com.triforge.app.ui.common.NoticeLine
import com.triforge.app.ui.common.ScreenTopBar
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.SettingsDivider
import com.triforge.app.ui.common.SettingsGroup
import com.triforge.app.ui.common.SettingsRow
import com.triforge.app.ui.profile.ProfileViewModel
import com.triforge.app.ui.theme.TriIcons

/**
 * The account, apart from the athlete's profile: sign-in, data sources,
 * privacy, legal texts. Every row opens its own page or does one thing.
 */
@Composable
fun SettingsScreen(
    account: AccountViewModel,
    profile: ProfileViewModel,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountState by account.state.collectAsStateWithLifecycle()
    val profileState by profile.state.collectAsStateWithLifecycle()

    val sourcesSummary = buildList {
        if (profileState.garmin.connected) add(stringResource(R.string.settings_sources_garmin_on))
        if (profileState.healthConnect == HealthConnectAvailability.AVAILABLE && profileState.healthConnectGranted) {
            add(stringResource(R.string.settings_sources_hc_on))
        }
    }.joinToString(" · ").ifEmpty { stringResource(R.string.settings_sources_none) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountHeader(
                name = accountState.account.displayName,
                email = accountState.email,
                avatar = accountState.avatar,
                onClick = onEditProfile,
            )

            SectionLabel(stringResource(R.string.settings_account))
            SettingsGroup {
                SettingsRow(
                    icon = TriIcons.Person,
                    title = stringResource(R.string.settings_profile),
                    onClick = onEditProfile,
                )
                if (accountState.usesPassword) {
                    SettingsDivider()
                    SettingsRow(
                        icon = TriIcons.Key,
                        title = stringResource(R.string.settings_password),
                        subtitle = stringResource(R.string.settings_password_hint),
                        onClick = account::sendPasswordReset,
                    )
                }
            }
            NoticeLine(accountState.notice)

            SectionLabel(stringResource(R.string.settings_data))
            SettingsGroup {
                SettingsRow(
                    icon = TriIcons.Watch,
                    title = stringResource(R.string.settings_sources),
                    subtitle = sourcesSummary,
                    onClick = onOpenSources,
                )
                SettingsDivider()
                SettingsRow(
                    icon = TriIcons.Shield,
                    title = stringResource(R.string.settings_privacy),
                    subtitle = stringResource(R.string.settings_privacy_hint),
                    onClick = onOpenPrivacy,
                )
            }

            SectionLabel(stringResource(R.string.settings_info))
            SettingsGroup {
                SettingsRow(
                    icon = TriIcons.Document,
                    title = stringResource(R.string.legal_privacy_title),
                    onClick = onOpenPrivacyPolicy,
                )
                SettingsDivider()
                SettingsRow(
                    icon = TriIcons.Document,
                    title = stringResource(R.string.legal_terms_title),
                    onClick = onOpenTerms,
                )
                SettingsDivider()
                SettingsRow(
                    icon = TriIcons.Info,
                    title = stringResource(R.string.about_title),
                    subtitle = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    onClick = onOpenAbout,
                )
            }

            SettingsGroup(modifier = Modifier.padding(top = 8.dp)) {
                SettingsRow(
                    icon = TriIcons.SignOut,
                    title = stringResource(R.string.profile_sign_out),
                    danger = true,
                    onClick = profile::signOut,
                    trailing = null,
                )
            }
            Text(
                text = stringResource(R.string.settings_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

/** Who is signed in, as at the top of the design: photo, name and email; opens the profile editor. */
@Composable
private fun AccountHeader(name: String, email: String, avatar: ByteArray?, onClick: () -> Unit) {
    SettingsGroup {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Avatar(jpeg = avatar, name = name.ifBlank { email }, size = 52.dp)
            Column(modifier = Modifier.weight(1f)) {
                if (name.isNotBlank()) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (email.isNotBlank()) {
                    Text(
                        email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(TriIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
