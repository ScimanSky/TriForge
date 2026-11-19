package com.triforge.app.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.RecoveryState
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.Formatters
import com.triforge.domain.MarkerBand
import kotlin.math.roundToInt

/**
 * "How am I today": the load-recovery balance on the Today tab. The number
 * is in plain text colour and its state is a word with a coloured dot, so it
 * never relies on colour alone. Under it, the three night markers against
 * the athlete's own normal range.
 */
@Composable
fun RecoveryCard(
    state: RecoveryState,
    onOpen: () -> Unit,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = state as? RecoveryState.Loaded ?: return
    val report = loaded.report
    val today = report.today
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
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = shown.toString(), style = numberStyle(40.sp))
                    Text(
                        text = stringResource(R.string.recovery_score_of_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
                    )
                }
                StatusChip(label = label, color = light.color())
            }
            RecoveryScale(
                score = shown,
                description = stringResource(R.string.recovery_score_description, shown, label),
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(
                    stringResource(R.string.recovery_scale_low),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.recovery_scale_high),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val markers = listOfNotNull(
            report.hrvBand.lastOrNull()?.let {
                Marker(stringResource(R.string.recovery_part_hrv), it, higherIsBetter = true) { v -> "${v.roundToInt()} ms" }
            },
            report.restingHrBand.lastOrNull()?.let {
                Marker(stringResource(R.string.recovery_part_resting_hr), it, higherIsBetter = false) { v -> "${v.roundToInt()} bpm" }
            },
            report.sleepBand.lastOrNull()?.let {
                Marker(stringResource(R.string.recovery_part_sleep), it, higherIsBetter = true) { v ->
                    Formatters.duration((v * 3600).roundToInt().toLong())
                }
            },
        ).filter { it.band.value != null }
        if (markers.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                markers.forEach { MarkerChip(it, Modifier.weight(1f)) }
            }
        }

        Text(
            text = stringResource(today.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (report.runLoadJump) {
            Text(
                text = "▲ " + stringResource(R.string.recovery_run_jump),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LinkRow(
            text = stringResource(
                if (loaded.todayCheckIn == null) R.string.recovery_checkin_row else R.string.recovery_details,
            ),
            onClick = if (loaded.todayCheckIn == null) onCheckIn else onOpen,
        )
    }
}

private class Marker(
    val label: String,
    val band: MarkerBand,
    val higherIsBetter: Boolean,
    val format: (Double) -> String,
)

/** Where a marker sits against the athlete's normal range. */
private enum class Position { BELOW, INSIDE, ABOVE, UNKNOWN }

private fun MarkerBand.position(): Position {
    val v = value ?: return Position.UNKNOWN
    val lo = low ?: return Position.UNKNOWN
    val hi = high ?: return Position.UNKNOWN
    return when {
        v < lo -> Position.BELOW
        v > hi -> Position.ABOVE
        else -> Position.INSIDE
    }
}

@Composable
private fun MarkerChip(marker: Marker, modifier: Modifier = Modifier) {
    val position = marker.band.position()
    val worse = (position == Position.BELOW && marker.higherIsBetter) ||
        (position == Position.ABOVE && !marker.higherIsBetter)
    val note = stringResource(
        when (position) {
            Position.BELOW -> R.string.marker_below
            Position.ABOVE -> R.string.marker_above
            Position.INSIDE -> R.string.marker_inside
            Position.UNKNOWN -> R.string.marker_learning
        },
    )
    val value = marker.band.value?.let(marker.format).orEmpty()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(TriColors.track.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { contentDescription = "${marker.label} $value, $note" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = marker.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(text = value, style = numberStyle(16.sp), maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val arrow = when (position) {
                Position.BELOW -> "↓"
                Position.ABOVE -> "↑"
                else -> "="
            }
            Text(
                text = "$arrow $note",
                style = MaterialTheme.typography.labelSmall,
                color = if (worse) TriColors.caution else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** A status word with its coloured dot: the colour is never the only signal. */
@Composable
fun StatusChip(label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val shape = CircleShape
    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A one-line action at the bottom of a card: text in the link colour and a chevron. */
@Composable
fun LinkRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
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
