package com.triforge.app.ui.coach

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.triforge.app.R
import com.triforge.app.ai.CoachException
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.domain.coach.ChatMessage

/** The two coaches' colors, from the prototype: orange for the Coach, green for NutriAI. */
data class CoachPalette(
    val accent: Color,
    val onAccent: Color,
    val soft: Color,
    val softText: Color,
    val highlight: Color,
    val highlightBorder: Color,
) {
    companion object {
        val Coach = CoachPalette(
            accent = Color(0xFFFF5B2E),
            onAccent = Color(0xFF0E1013),
            soft = Color(0xFF3A1D14),
            softText = Color(0xFFFFB59C),
            highlight = Color(0xFF1C1512),
            highlightBorder = Color(0xFF4A2518),
        )
        val Nutri = CoachPalette(
            accent = Color(0xFF7FE3B0),
            onAccent = Color(0xFF0E1013),
            soft = Color(0xFF12291F),
            softText = Color(0xFFA8F0CB),
            highlight = Color(0xFF10201A),
            highlightBorder = Color(0xFF1F4535),
        )
    }
}

@Composable
fun CoachHeader(title: String, subtitle: String, icon: ImageVector, palette: CoachPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(palette.soft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.softText, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A tinted card with an uppercase title, for the coach's reasoning and notes. */
@Composable
fun HighlightCard(title: String, palette: CoachPalette, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.highlight)
            .border(1.dp, palette.highlightBorder, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = palette.softText,
        )
        content()
    }
}

/**
 * Shown instead of a coach while the AI consent is off. Turning it on here is
 * the same consent as in Privacy, with the same words.
 */
@Composable
fun AiConsentCard(palette: CoachPalette, onEnable: () -> Unit, modifier: Modifier = Modifier) {
    TriCard(modifier) {
        Text(stringResource(R.string.coach_consent_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.consent_ai_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.coach_consent_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.coach_consent_action),
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

@StringRes
fun errorText(reason: CoachException.Reason): Int = when (reason) {
    CoachException.Reason.NETWORK -> R.string.coach_error_network
    CoachException.Reason.BUSY -> R.string.coach_error_busy
    CoachException.Reason.NOT_ENABLED -> R.string.coach_error_not_enabled
    CoachException.Reason.BAD_ANSWER -> R.string.coach_error_bad_answer
    CoachException.Reason.BLOCKED -> R.string.coach_error_blocked
}

@Composable
fun ErrorCard(reason: CoachException.Reason, onRetry: (() -> Unit)?, onDismiss: () -> Unit) {
    TriCard {
        Text(stringResource(errorText(reason)), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onRetry != null) PrimaryButton(stringResource(R.string.coach_retry), onRetry, compact = true)
            SecondaryButton(stringResource(R.string.coach_close), onDismiss, compact = true)
        }
    }
}

/** A quiet line with a spinner while a coach is thinking. */
@Composable
fun ThinkingRow(text: String, palette: CoachPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One chat bubble: the athlete's on the right in the coach's color, the coach's on the left. */
@Composable
fun ChatBubble(message: ChatMessage, palette: CoachPalette) {
    val mine = message.fromAthlete
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        val shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(if (mine) palette.accent else MaterialTheme.colorScheme.surface)
                .then(if (mine) Modifier else Modifier.border(1.dp, TriColors.border, shape))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (mine) palette.onAccent else Color(0xFFE6E9EE),
            )
        }
    }
}

/** Ready-made questions under the chat, as in the prototype. */
@Composable
fun SuggestionChips(suggestions: List<String>, enabled: Boolean, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { text ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, TriColors.borderStrong, RoundedCornerShape(18.dp))
                    .clickable(enabled = enabled, role = Role.Button) { onPick(text) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(text, style = MaterialTheme.typography.labelLarge, color = Color(0xFFD5DAE1))
            }
        }
    }
}

/** The text field and round send button at the bottom of a chat. */
@Composable
fun Composer(placeholder: String, palette: CoachPalette, enabled: Boolean, onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank() && enabled) {
            onSend(text)
            text = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholder) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = TriColors.borderStrong,
                focusedBorderColor = palette.accent,
                cursorColor = palette.accent,
            ),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (enabled && text.isNotBlank()) palette.accent else TriColors.track)
                .clickable(enabled = enabled && text.isNotBlank(), role = Role.Button, onClick = send),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TriIcons.Send, contentDescription = stringResource(R.string.coach_send), tint = palette.onAccent, modifier = Modifier.size(20.dp))
        }
    }
}

/** Apply / Ignore under a plan the coach proposed in the chat. */
@Composable
fun ProposalActions(palette: CoachPalette, onApply: () -> Unit, onDismiss: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Button(
            onClick = onApply,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
        ) { Text(stringResource(R.string.coach_apply)) }
        SecondaryButton(stringResource(R.string.coach_ignore), onDismiss, modifier = Modifier.weight(1f), enabled = enabled, compact = true)
    }
}
