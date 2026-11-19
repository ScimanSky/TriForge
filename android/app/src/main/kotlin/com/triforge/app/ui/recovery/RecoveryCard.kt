package com.triforge.app.ui.recovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.RecoveryState
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle

/** The load-recovery balance on the Today tab: number, colour, one message. */
@Composable
fun RecoveryCard(
    state: RecoveryState,
    onOpen: () -> Unit,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = state as? RecoveryState.Loaded ?: return
    val today = loaded.report.today
    val shown = today.smoothedScore
    val light = today.light

    TriCard(modifier = modifier, onClick = onOpen) {
        CardHeader(
            title = stringResource(R.string.recovery_title),
            trailing = if (shown != null) stringResource(today.confidenceLevel.labelRes()) else null,
        )
        if (shown != null && light != null) {
            val label = stringResource(light.labelRes())
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = shown.toString(), style = numberStyle(40.sp), color = light.color())
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(text = label, style = MaterialTheme.typography.titleSmall, color = light.color())
                    Text(
                        text = stringResource(R.string.recovery_score_of),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RecoveryScale(
                score = shown,
                description = stringResource(R.string.recovery_score_description, shown, label),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            text = stringResource(today.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (loaded.report.runLoadJump) {
            Text(
                text = stringResource(R.string.recovery_run_jump),
                style = MaterialTheme.typography.bodySmall,
                color = TriColors.warn,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (loaded.todayCheckIn == null) {
            SecondaryButton(
                text = stringResource(R.string.recovery_checkin_cta),
                onClick = onCheckIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recovery_details),
                style = MaterialTheme.typography.labelLarge,
                color = TriColors.accentText,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = TriIcons.ChevronRight,
                contentDescription = null,
                tint = TriColors.accentText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
