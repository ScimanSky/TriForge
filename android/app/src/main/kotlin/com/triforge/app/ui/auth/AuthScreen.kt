package com.triforge.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
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
import com.triforge.app.data.GoogleSignInResult
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.TriTextField
import com.triforge.app.ui.theme.SpaceGrotesk
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(viewModel: AuthViewModel, requestGoogleToken: suspend () -> GoogleSignInResult) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Not saveable on purpose: a password should not be written to saved state.
    var password by remember { mutableStateOf("") }

    val submit = { viewModel.submit(name, email, password) }

    // The account sheet belongs to this screen: leaving it cancels the request.
    val scope = rememberCoroutineScope()
    val continueWithGoogle: () -> Unit = {
        if (viewModel.beginGoogle()) {
            scope.launch {
                var result: GoogleSignInResult = GoogleSignInResult.Cancelled
                try {
                    result = requestGoogleToken()
                } finally {
                    viewModel.finishGoogle(result)
                }
            }
        }
    }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HorizontalDivider(Modifier.weight(1f), color = TriColors.border)
            Text(
                text = stringResource(R.string.auth_or),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f), color = TriColors.border)
        }
        GoogleButton(
            onClick = continueWithGoogle,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
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

/** Google's own mark and wording: one button for both sign-in and sign-up. */
@Composable
private fun GoogleButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, TriColors.borderStrong),
        contentPadding = PaddingValues(horizontal = 24.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Image(
            imageVector = GoogleMark,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            alpha = if (enabled) 1f else 0.4f,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.auth_google),
            style = MaterialTheme.typography.labelLarge,
            fontSize = 16.sp,
        )
    }
}

/** The four-color "G", from Google's sign-in branding assets. */
private val GoogleMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "GoogleMark",
        defaultWidth = 20.dp,
        defaultHeight = 20.dp,
        viewportWidth = 48f,
        viewportHeight = 48f,
    ).apply {
        listOf(
            0xFFEA4335 to "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 " +
                "14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
            0xFF4285F4 to "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18" +
                "l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
            0xFFFBBC05 to "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19" +
                "C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
            0xFF34A853 to "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3" +
                "-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
        ).forEach { (color, path) ->
            addPath(pathData = addPathNodes(path), fill = SolidColor(Color(color)))
        }
    }.build()
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
