package com.triforge.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.data.HealthConnectAvailability
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val syncFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ITALY)

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = state.name.ifBlank { state.email },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (state.name.isNotBlank()) {
                Text(
                    text = state.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource(R.string.profile_sources),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )

        GarminCard(state, viewModel)
        HealthConnectCard(state, viewModel)

        TextButton(onClick = viewModel::signOut) {
            Text(stringResource(R.string.profile_sign_out))
        }
    }
}

@Composable
private fun SourceCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Muted(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NoticeText(notice: Notice?) {
    if (notice == null) return
    Text(
        text = stringResource(notice.text, *notice.args.toTypedArray()),
        style = MaterialTheme.typography.bodyMedium,
        color = if (notice.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun GarminCard(state: ProfileUiState, viewModel: ProfileViewModel) {
    SourceCard(stringResource(R.string.garmin_title)) {
        when {
            !state.garminConfigured -> Muted(stringResource(R.string.garmin_not_configured))

            state.garmin.connected -> {
                Text(
                    if (state.garmin.displayName.isBlank()) {
                        stringResource(R.string.garmin_connected)
                    } else {
                        stringResource(R.string.garmin_connected_as, state.garmin.displayName)
                    },
                )
                Muted(
                    state.garmin.lastSyncAt?.let {
                        stringResource(
                            R.string.garmin_last_sync,
                            syncFormat.format(it.atZone(ZoneId.systemDefault())),
                        )
                    } ?: stringResource(R.string.garmin_never_synced),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::syncGarmin, enabled = !state.garminBusy) {
                        Text(stringResource(R.string.garmin_sync))
                    }
                    OutlinedButton(onClick = viewModel::disconnectGarmin, enabled = !state.garminBusy) {
                        Text(stringResource(R.string.garmin_disconnect))
                    }
                }
            }

            state.garminAwaitingCode -> {
                var code by rememberSaveable { mutableStateOf("") }
                Muted(stringResource(R.string.garmin_mfa_intro))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.garmin_mfa_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.submitGarminCode(code) },
                    enabled = !state.garminBusy && code.isNotBlank(),
                ) {
                    Text(stringResource(R.string.garmin_mfa_confirm))
                }
            }

            else -> {
                var email by rememberSaveable { mutableStateOf("") }
                // Not saveable on purpose: a password should not be written to saved state.
                var password by remember { mutableStateOf("") }
                if (state.garmin.needsReconnect) {
                    Text(stringResource(R.string.garmin_reconnect), color = MaterialTheme.colorScheme.error)
                }
                Muted(stringResource(R.string.garmin_intro))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.garmin_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.garmin_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        viewModel.connectGarmin(email, password)
                        password = ""
                    },
                    enabled = !state.garminBusy && email.isNotBlank() && password.isNotBlank(),
                ) {
                    Text(stringResource(R.string.garmin_connect))
                }
            }
        }
        NoticeText(state.garminNotice)
    }
}

@Composable
private fun HealthConnectCard(state: ProfileUiState, viewModel: ProfileViewModel) {
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refreshHealthConnect() }

    SourceCard(stringResource(R.string.hc_title)) {
        Muted(stringResource(R.string.hc_intro))
        when (state.healthConnect) {
            HealthConnectAvailability.UNAVAILABLE -> Muted(stringResource(R.string.hc_unavailable))
            HealthConnectAvailability.NEEDS_UPDATE -> Muted(stringResource(R.string.hc_needs_update))
            HealthConnectAvailability.AVAILABLE ->
                if (state.healthConnectGranted) {
                    Button(onClick = viewModel::importFromHealthConnect, enabled = !state.healthConnectBusy) {
                        Text(stringResource(R.string.hc_import))
                    }
                } else {
                    Button(onClick = { permissionLauncher.launch(viewModel.healthPermissions) }) {
                        Text(stringResource(R.string.hc_grant))
                    }
                }
        }
        NoticeText(state.healthConnectNotice)
    }
}
