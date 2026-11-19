package com.triforge.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.ui.profile.ProfileUiState
import com.triforge.app.ui.profile.ProfileViewModel
import com.triforge.app.data.HealthConnectAvailability
import com.triforge.app.ui.common.IconBox
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.NoticeLine
import com.triforge.app.ui.common.ScreenTopBar
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.TriChip
import com.triforge.app.ui.common.TriTextField
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.domain.ProfileField
import com.triforge.domain.ProfileForm
import com.triforge.domain.RaceGoal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val syncFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ITALY)

/** Garmin, Health Connect and what comes next: where the data comes from. */
@Composable
fun SourcesScreen(viewModel: ProfileViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(title = stringResource(R.string.settings_sources), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Muted(stringResource(R.string.sources_intro))
            DataSourceCards(state, viewModel)
        }
    }
}

/** The source cards, shared by the settings page and the first launch. */
@Composable
fun DataSourceCards(state: ProfileUiState, viewModel: ProfileViewModel) {
    GarminCard(state, viewModel)
    HealthConnectCard(state, viewModel)
    StravaRow()
}

// -- Data sources ---------------------------------------------------------

@Composable
private fun SourceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    TriCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBox {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status != null) {
                Text(text = status, style = MaterialTheme.typography.labelMedium, color = TriColors.good)
            }
        }
        Column(
            modifier = Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun Muted(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}


@Composable
private fun GarminCard(state: ProfileUiState, viewModel: ProfileViewModel) {
    SourceCard(
        icon = TriIcons.Watch,
        title = stringResource(R.string.garmin_title),
        subtitle = when {
            !state.garmin.connected -> stringResource(R.string.garmin_subtitle)
            state.garmin.displayName.isBlank() -> stringResource(R.string.garmin_connected)
            else -> stringResource(R.string.garmin_connected_as, state.garmin.displayName)
        },
        status = if (state.garmin.connected) stringResource(R.string.source_connected) else null,
    ) {
        when {
            !state.garminConfigured -> Muted(stringResource(R.string.garmin_not_configured))

            state.garmin.connected -> {
                Muted(
                    state.garmin.lastSyncAt?.let {
                        stringResource(
                            R.string.garmin_last_sync,
                            syncFormat.format(it.atZone(ZoneId.systemDefault())),
                        )
                    } ?: stringResource(R.string.garmin_never_synced),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.garmin_sync),
                        onClick = viewModel::syncGarmin,
                        enabled = !state.garminBusy,
                        compact = true,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.garmin_disconnect),
                        onClick = viewModel::disconnectGarmin,
                        enabled = !state.garminBusy,
                        compact = true,
                    )
                }
            }

            state.garminAwaitingCode -> {
                var code by rememberSaveable { mutableStateOf("") }
                Muted(stringResource(R.string.garmin_mfa_intro))
                TriTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = stringResource(R.string.garmin_mfa_code),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = stringResource(R.string.garmin_mfa_confirm),
                    onClick = { viewModel.submitGarminCode(code) },
                    enabled = !state.garminBusy && code.isNotBlank(),
                    compact = true,
                )
            }

            else -> {
                var email by rememberSaveable { mutableStateOf("") }
                // Not saveable on purpose: a password should not be written to saved state.
                var password by remember { mutableStateOf("") }
                if (state.garmin.needsReconnect) {
                    Text(
                        text = stringResource(R.string.garmin_reconnect),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Muted(stringResource(R.string.garmin_intro))
                TriTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = stringResource(R.string.garmin_email),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                TriTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.garmin_password),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = stringResource(R.string.garmin_connect),
                    onClick = {
                        viewModel.connectGarmin(email, password)
                        password = ""
                    },
                    enabled = !state.garminBusy && email.isNotBlank() && password.isNotBlank(),
                    compact = true,
                )
            }
        }
        NoticeLine(state.garminNotice)
    }
}

@Composable
private fun HealthConnectCard(state: ProfileUiState, viewModel: ProfileViewModel) {
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refreshHealthConnect() }
    val ready = state.healthConnect == HealthConnectAvailability.AVAILABLE && state.healthConnectGranted

    SourceCard(
        icon = TriIcons.Heart,
        title = stringResource(R.string.hc_title),
        subtitle = stringResource(R.string.hc_subtitle),
        status = if (ready) stringResource(R.string.source_active) else null,
    ) {
        Muted(stringResource(R.string.hc_intro))
        when (state.healthConnect) {
            HealthConnectAvailability.UNAVAILABLE -> Muted(stringResource(R.string.hc_unavailable))
            HealthConnectAvailability.NEEDS_UPDATE -> Muted(stringResource(R.string.hc_needs_update))
            HealthConnectAvailability.AVAILABLE ->
                if (state.healthConnectGranted) {
                    PrimaryButton(
                        text = stringResource(R.string.hc_import),
                        onClick = viewModel::importFromHealthConnect,
                        enabled = !state.healthConnectBusy,
                        compact = true,
                    )
                } else {
                    PrimaryButton(
                        text = stringResource(R.string.hc_grant),
                        onClick = { permissionLauncher.launch(viewModel.healthPermissions) },
                        compact = true,
                    )
                }
        }
        NoticeLine(state.healthConnectNotice)
    }
}

/** Strava is planned, not built: a dashed outline says "not yet" without a dead button. */
@Composable
private fun StravaRow() {
    val outline = TriColors.borderStrong
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBox(background = Color.Transparent) {
            Icon(
                imageVector = TriIcons.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = stringResource(R.string.strava_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.source_coming),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
