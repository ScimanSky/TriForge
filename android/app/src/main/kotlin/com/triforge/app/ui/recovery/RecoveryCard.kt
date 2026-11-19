package com.triforge.app.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.triforge.domain.CheckIn
import java.time.LocalDate
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
 * never relies on colour alone. Under it, a sideways row with the night
 * markers and the check-in over the last seven days, against the athlete's
 * own normal range.
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
            Marker(stringResource(R.string.recovery_part_hrv), report.hrvBand, higherIsBetter = true) { v -> "${v.roundToInt()} ms" },
            Marker(stringResource(R.string.recovery_part_resting_hr), report.restingHrBand, higherIsBetter = false) { v ->
                "${v.roundToInt()} bpm"
            },
            Marker(stringResource(R.string.recovery_part_sleep), report.sleepBand, higherIsBetter = true) { v ->
                Formatters.duration((v * 3600).roundToInt().toLong())
            },
        ).filter { it.band?.value != null }
        val feel = loaded.recentCheckIns.map { it.date to it.feelScore() }
        if (markers.isNotEmpty() || feel.isNotEmpty()) {
            MarkerRow(markers = markers, feel = feel, modifier = Modifier.padding(top = 16.dp))
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
    bands: List<MarkerBand>,
    val higherIsBetter: Boolean,
    val format: (Double) -> String,
) {
    /** The last seven days, oldest first; the last one is today's. */
    val week: List<MarkerBand> = bands.takeLast(7)
    val band: MarkerBand? get() = week.lastOrNull()
}

/** How the athlete feels from a check-in, 1 (bad) to 5 (great): the total runs from 5 (great) to 35 (bad). */
private fun CheckIn.feelScore(): Double = 5.0 - (total - 5) / 30.0 * 4.0

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

private val CARD_WIDTH = 140.dp
private val CARD_GAP = 10.dp

/**
 * The night markers and how the athlete feels, one small card each with the
 * last seven days against their usual range. The row scrolls sideways: the
 * next card shows halfway and the dots say how many there are.
 */
@Composable
private fun MarkerRow(markers: List<Marker>, feel: List<Pair<LocalDate, Double>>, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val count = markers.size + if (feel.isNotEmpty()) 1 else 0
    val step = with(LocalDensity.current) { (CARD_WIDTH + CARD_GAP).toPx() }
    val current = if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue) count - 1 else (scroll.value / step).roundToInt()
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.recovery_week_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.recovery_week_band),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            markers.forEach { MarkerCard(it) }
            if (feel.isNotEmpty()) FeelCard(feel)
        }
        if (count > 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            ) {
                repeat(count) { index ->
                    val on = index == current.coerceIn(0, count - 1)
                    Box(
                        Modifier
                            .size(width = if (on) 16.dp else 5.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkerCard(marker: Marker) {
    val band = marker.band ?: return
    val position = band.position()
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
    val arrow = when (position) {
        Position.BELOW -> "↓ "
        Position.ABOVE -> "↑ "
        else -> ""
    }
    val value = band.value?.let(marker.format).orEmpty()
    TrendCard(
        label = marker.label,
        value = value,
        note = arrow + note,
        noteColor = if (worse) TriColors.caution else MaterialTheme.colorScheme.onSurfaceVariant,
        lineColor = if (worse) TriColors.caution else TriColors.good,
        values = marker.week.map { it.value },
        lows = marker.week.map { it.low },
        highs = marker.week.map { it.high },
        description = "${marker.label} $value, $note",
    )
}

@Composable
private fun FeelCard(feel: List<Pair<LocalDate, Double>>) {
    val today = feel.last().second
    val label = stringResource(R.string.recovery_feel_title)
    val value = today.roundToInt().coerceIn(1, 5).toString()
    val note = stringResource(R.string.recovery_feel_note)
    TrendCard(
        label = label,
        value = "$value/5",
        note = note,
        noteColor = MaterialTheme.colorScheme.onSurfaceVariant,
        lineColor = TriColors.good,
        values = feel.map { it.second },
        lows = emptyList(),
        highs = emptyList(),
        description = "$label $value su 5, $note",
    )
}

@Composable
private fun TrendCard(
    label: String,
    value: String,
    note: String,
    noteColor: Color,
    lineColor: Color,
    values: List<Double?>,
    lows: List<Double?>,
    highs: List<Double?>,
    description: String,
) {
    val bandColor = Color.White.copy(alpha = 0.09f)
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clip(RoundedCornerShape(16.dp))
            .background(TriColors.track.copy(alpha = 0.5f))
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = numberStyle(22.sp), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        Text(
            note,
            style = MaterialTheme.typography.labelSmall,
            color = noteColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(top = 8.dp),
        ) {
            val all = (values + lows + highs).filterNotNull()
            if (all.isEmpty()) return@Canvas
            var min = all.min()
            var max = all.max()
            if (max - min < 1e-6) { min -= 1; max += 1 }
            val n = values.size
            fun x(i: Int) = if (n <= 1) size.width else i * size.width / (n - 1)
            fun y(v: Double) = (size.height * (1 - (v - min) / (max - min))).toFloat()
            // The usual range, day by day.
            val bandIdx = lows.indices.filter { lows[it] != null && highs.getOrNull(it) != null }
            if (bandIdx.size >= 2) {
                val band = Path()
                bandIdx.forEachIndexed { k, i -> if (k == 0) band.moveTo(x(i), y(highs[i]!!)) else band.lineTo(x(i), y(highs[i]!!)) }
                bandIdx.reversed().forEach { i -> band.lineTo(x(i), y(lows[i]!!)) }
                band.close()
                drawPath(band, bandColor)
            }
            val points = values.indices.filter { values[it] != null }
            if (points.size >= 2) {
                val line = Path()
                points.forEachIndexed { k, i -> if (k == 0) line.moveTo(x(i), y(values[i]!!)) else line.lineTo(x(i), y(values[i]!!)) }
                drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            points.lastOrNull()?.let { i -> drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(x(i), y(values[i]!!))) }
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
