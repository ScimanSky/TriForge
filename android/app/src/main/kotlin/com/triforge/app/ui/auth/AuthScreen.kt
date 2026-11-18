package com.triforge.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.TriTextField
import com.triforge.app.ui.theme.SpaceGrotesk
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Not saveable on purpose: a password should not be written to saved state.
    var password by remember { mutableStateOf("") }

    val submit = { viewModel.submit(name, email, password) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Wordmark()
        Waves(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.auth_headline),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = TriColors.textDim,
        )
        Spacer(Modifier.height(8.dp))

        if (state.registering) {
            TriTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.auth_name),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TriTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.auth_email),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        TriTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.auth_password),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        PrimaryButton(
            text = stringResource(if (state.registering) R.string.auth_register else R.string.auth_sign_in),
            onClick = submit,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = viewModel::toggleMode),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(
                    if (state.registering) R.string.auth_switch_to_sign_in else R.string.auth_switch_to_register,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = TriColors.accentText,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = stringResource(R.string.auth_next_step),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Three bars, one per discipline, and the name. */
@Composable
private fun Wordmark() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(SportColors.swim to 12, SportColors.bike to 20, SportColors.run to 16).forEach { (color, height) ->
                Box(
                    Modifier
                        .width(5.dp)
                        .height(height.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
            }
        }
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 2.sp,
        )
    }
}

/** Three quiet lines in the sport colors: water, road, track. Decoration only. */
@Composable
private fun Waves(modifier: Modifier = Modifier) {
    val colors = listOf(SportColors.swim, SportColors.bike, SportColors.run)
    Canvas(modifier) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val swell = size.height / 6f
        colors.forEachIndexed { index, color ->
            val y = size.height * (index + 1) / 4f
            val path = Path().apply {
                moveTo(0f, y)
                val step = size.width / 4f
                for (wave in 0 until 4) {
                    val start = step * wave
                    // Each line flattens a little more than the one above.
                    val height = swell / (index + 1) * if (wave % 2 == 0) -1f else 1f
                    cubicTo(
                        start + step / 3f, y + height,
                        start + step * 2f / 3f, y + height,
                        start + step, y,
                    )
                }
            }
            drawPath(path, color.copy(alpha = 0.55f), style = stroke)
        }
    }
}
