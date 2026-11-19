package com.triforge.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.LegendDot
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.Activity
import com.triforge.domain.Formatters
import com.triforge.domain.PacingRegularity
import com.triforge.domain.SwimAnalysis
import com.triforge.domain.SwimRepeat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Zone 1 to 5 colours, the same steps as the bike and run zones. */
internal val SwimZoneColors = listOf(
    Color(0xFF5E6672),
    Color(0xFF8C95A3),
    Color(0xFF2E86B3),
    SportColors.swim,
    Color(0xFFFF5B2E),
)

private fun pace(seconds: Double): String = Formatters.minutesAndSeconds(seconds)

private fun twoDecimals(value: Double): String = "%.2f".format(Locale.ITALY, value)

private fun signedSeconds(value: Double): String {
    val rounded = value.roundToInt()
    return if (rounded > 0) "+$rounded″" else if (rounded < 0) "−${-rounded}″" else "0″"
}

private fun signedPercent(value: Double): String {
    val rounded = value.roundToInt()
    return if (rounded > 0) "+$rounded%" else if (rounded < 0) "−${-rounded}%" else "0%"
}

/**
 * The swim-only part of the detail, from the laps: pace per repeat against
 * the CSS, how the main set held up, stroke efficiency, pace zones and the
 * repeats one by one. Each card disappears when its data is missing.
 */
@Composable
fun SwimSections(activity: Activity, history: List<Activity>, cssSecPer100: Int?) {
    val repeats = remember(activity.id, activity.laps) { SwimAnalysis.repeats(activity) }
    if (repeats.isNotEmpty()) RepeatPaceCard(repeats, cssSecPer100)
    SetHoldCards(activity)
    EfficiencyCard(activity, history)
    if (repeats.isNotEmpty()) ZonesCard(activity, cssSecPer100)
    if (repeats.isNotEmpty()) RepeatsCard(repeats)
}

private fun zoneColor(repeat: SwimRepeat, css: Int?): Color =
    if (css == null) SportColors.swim else SwimZoneColors[SwimAnalysis.zoneOf(repeat.paceSecPer100, css) - 1]

/** One bar per repeat: width is the distance, height the speed, colour the zone. */
@Composable
private fun RepeatPaceCard(repeats: List<SwimRepeat>, css: Int?) {
    val paces = repeats.map { it.paceSecPer100 } + listOfNotNull(css?.toDouble())
    // Slowest at the bottom, fastest at the top, with some room on both sides.
    val slow = (paces.max() + 5).coerceAtMost(paces.min() + 90)
    val fast = paces.min() - 5
    fun barHeight(secPer100: Double): Float = ((slow - secPer100) / (slow - fast)).toFloat().coerceIn(0.08f, 1f)
    val description = stringResource(
        R.string.swim_repeats_chart,
        repeats.size,
        pace(repeats.minOf { it.paceSecPer100 }),
        pace(repeats.maxOf { it.paceSecPer100 }),
    )

    TriCard {
        CardHeader(
            title = stringResource(R.string.swim_repeats_pace_title),
            trailing = css?.let { stringResource(R.string.swim_repeats_pace_css, pace(it.toDouble())) }
                ?: stringResource(R.string.swim_per_100),
        )
        val cssFraction = css?.let { barHeight(it.toDouble()) }
        Row(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = description }
                .drawBehind {
                    if (cssFraction != null) {
                        val y = size.height * (1f - cssFraction)
                        drawLine(
                            color = Color(0xFFFF5B2E),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                        )
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            repeats.forEach { repeat ->
                Box(
                    Modifier
                        .weight(repeat.distanceMeters.toFloat())
                        .fillMaxHeight(barHeight(repeat.paceSecPer100))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
                        .background(zoneColor(repeat, css)),
                )
            }
        }
        if (css != null) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SwimZoneColors.forEachIndexed { index, color ->
                    LegendDot(color, "Z${index + 1}")
                }
            }
        }
        Text(
            text = stringResource(if (css == null) R.string.swim_repeats_no_css else R.string.swim_repeats_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Two tiles: does the pace hold in the main set, and does the stroke. */
@Composable
private fun SetHoldCards(activity: Activity) {
    val hold = remember(activity.id, activity.laps) { SwimAnalysis.setHold(activity) } ?: return
    val setName = "${hold.repeats} × ${hold.distanceMeters.roundToInt()}"
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HoldTile(
            label = stringResource(R.string.swim_hold_pace),
            value = signedSeconds(hold.fadeSecPer100),
            verdict = stringResource(
                when (hold.regularity) {
                    PacingRegularity.VERY_STEADY -> R.string.swim_hold_very_steady
                    PacingRegularity.STEADY -> R.string.swim_hold_steady
                    PacingRegularity.UNEVEN -> R.string.swim_hold_uneven
                },
            ),
            verdictColor = if (hold.regularity == PacingRegularity.UNEVEN) TriColors.warn else TriColors.good,
            note = stringResource(
                R.string.swim_hold_pace_note,
                setName,
                "%.1f".format(Locale.ITALY, hold.variationPct),
            ),
            modifier = Modifier.weight(1f),
        )
        val change = hold.strokeLengthChangePct
        if (change != null) {
            val shorter = change <= -STROKE_DROP_PCT
            HoldTile(
                label = stringResource(R.string.swim_hold_stroke),
                value = signedPercent(change),
                verdict = stringResource(if (shorter) R.string.swim_hold_stroke_shorter else R.string.swim_hold_stroke_kept),
                verdictColor = if (shorter) TriColors.warn else TriColors.good,
                note = stringResource(
                    R.string.swim_hold_stroke_note,
                    twoDecimals(hold.firstMetersPerStroke ?: 0.0),
                    twoDecimals(hold.lastMetersPerStroke ?: 0.0),
                    (hold.firstStrokeRate ?: 0.0).roundToInt(),
                    (hold.lastStrokeRate ?: 0.0).roundToInt(),
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A stroke this much shorter at the end of the set is fatigue, not noise. */
private const val STROKE_DROP_PCT = 3.0

@Composable
private fun HoldTile(
    label: String,
    value: String,
    verdict: String,
    verdictColor: Color,
    note: String,
    modifier: Modifier = Modifier,
) {
    TriCard(modifier = modifier, padding = 16.dp) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = numberStyle(22.sp), modifier = Modifier.padding(top = 4.dp))
        Text(verdict, style = MaterialTheme.typography.bodySmall, color = verdictColor)
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = TriColors.textDim,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Metres per stroke, stroke rate and SWOLF against the athlete's own average. */
@Composable
private fun EfficiencyCard(activity: Activity, history: List<Activity>) {
    val now = remember(activity.id, activity.laps) { SwimAnalysis.efficiency(activity) } ?: return
    val usual = remember(activity.id, history) { SwimAnalysis.usualEfficiency(history, activity.startTime) }

    TriCard {
        CardHeader(
            title = stringResource(R.string.swim_efficiency_title),
            trailing = stringResource(R.string.swim_efficiency_freestyle),
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EfficiencyCell(
                value = now.metersPerStroke?.let { stringResource(R.string.unit_m_value, twoDecimals(it)) },
                label = stringResource(R.string.swim_efficiency_dps),
                comparison = comparison(now.metersPerStroke, usual?.metersPerStroke, higherIsBetter = true, threshold = 0.02) {
                    twoDecimals(abs(it))
                },
                modifier = Modifier.weight(1f),
            )
            EfficiencyCell(
                value = now.strokeRate?.toString(),
                label = stringResource(R.string.swim_efficiency_rate),
                comparison = comparison(now.strokeRate?.toDouble(), usual?.strokeRate?.toDouble(), higherIsBetter = null, threshold = 1.0) {
                    abs(it).roundToInt().toString()
                },
                modifier = Modifier.weight(1f),
            )
            EfficiencyCell(
                value = now.swolf?.toString(),
                label = stringResource(R.string.metric_swolf),
                comparison = comparison(now.swolf?.toDouble(), usual?.swolf?.toDouble(), higherIsBetter = false, threshold = 1.0) {
                    abs(it).roundToInt().toString()
                },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.swim_efficiency_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private data class Comparison(val text: String, val color: Color)

/**
 * This swim against the usual value. [higherIsBetter] null means the
 * direction is neither good nor bad (stroke rate): neutral ink.
 */
@Composable
private fun comparison(
    now: Double?,
    usual: Double?,
    higherIsBetter: Boolean?,
    threshold: Double,
    format: (Double) -> String,
): Comparison? {
    if (now == null || usual == null) return null
    val delta = now - usual
    if (abs(delta) < threshold) {
        return Comparison(stringResource(R.string.swim_efficiency_same), MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val text = stringResource(
        if (delta > 0) R.string.swim_efficiency_above else R.string.swim_efficiency_below,
        format(delta),
    )
    val color = when {
        higherIsBetter == null -> MaterialTheme.colorScheme.onSurfaceVariant
        (delta > 0) == higherIsBetter -> TriColors.good
        else -> TriColors.warn
    }
    return Comparison(text, color)
}

@Composable
private fun EfficiencyCell(value: String?, label: String, comparison: Comparison?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value ?: stringResource(R.string.value_none), style = numberStyle(20.sp), maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (comparison != null) {
            Text(comparison.text, style = MaterialTheme.typography.bodySmall, color = comparison.color)
        }
    }
}

/** Metres in each pace zone, on the CSS of the profile. */
@Composable
private fun ZonesCard(activity: Activity, css: Int?) {
    val meters = remember(activity.id, activity.laps, css) { SwimAnalysis.zoneMeters(activity, css) } ?: return
    val total = meters.sum().takeIf { it > 0 } ?: return
    val top = meters.indices.maxBy { meters[it] }
    CollapsibleCard(
        title = stringResource(R.string.swim_zones_title),
        summary = stringResource(R.string.swim_zones_summary, wholeMeters(meters[top]), top + 1),
        initiallyExpanded = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            meters.forEachIndexed { index, value ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Z${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TriColors.textDim,
                        modifier = Modifier.width(28.dp),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(TriColors.track),
                    ) {
                        if (value > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth((value / total).toFloat())
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(SwimZoneColors[index]),
                            )
                        }
                    }
                    Text(
                        wholeMeters(value),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(64.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.swim_zones_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private fun wholeMeters(meters: Double): String = "%,d m".format(Locale.ITALY, meters.roundToInt())

/** How many repeats the table shows before "show all". */
private const val REPEATS_SHOWN = 12

/** The repeats one by one, and on demand every pool length. */
@Composable
private fun RepeatsCard(repeats: List<SwimRepeat>) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    var showLengths by rememberSaveable { mutableStateOf(false) }
    val lengthsCount = repeats.sumOf { it.lengths.size }
    val summary = if (lengthsCount > 0) {
        stringResource(R.string.swim_table_summary_lengths, repeats.size, lengthsCount)
    } else {
        stringResource(R.string.swim_table_summary, repeats.size)
    }

    CollapsibleCard(title = stringResource(R.string.swim_table_title), summary = summary, initiallyExpanded = true) {
        TableRow(
            listOf(
                stringResource(R.string.swim_table_repeat),
                stringResource(R.string.detail_time),
                stringResource(R.string.swim_table_pace),
                stringResource(R.string.metric_swolf),
                stringResource(R.string.swim_table_rest),
            ),
            header = true,
        )
        val shown = if (showAll) repeats else repeats.take(REPEATS_SHOWN)
        shown.forEach { repeat ->
            TableRow(
                listOf(
                    "${repeat.number} · ${repeat.distanceMeters.roundToInt()}",
                    Formatters.clock(repeat.durationSec.roundToInt().toLong()),
                    pace(repeat.paceSecPer100),
                    repeat.swolf?.toString() ?: "",
                    repeat.restAfterSec?.takeIf { it >= 1 }?.let { pace(it) } ?: "",
                ),
            )
            if (showLengths) {
                repeat.lengths.forEachIndexed { index, length ->
                    TableRow(
                        listOf(
                            "   ${repeat.number}.${index + 1}",
                            Formatters.clock(length.durationSec.roundToInt().toLong()),
                            if (length.distanceMeters > 0) pace(length.durationSec / length.distanceMeters * 100) else "",
                            length.swolf?.toString() ?: "",
                            "",
                        ),
                        dim = true,
                    )
                }
            }
        }
        if (repeats.size > REPEATS_SHOWN) {
            SecondaryButton(
                text = stringResource(if (showAll) R.string.swim_table_less else R.string.swim_table_all, repeats.size),
                onClick = { showAll = !showAll },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                compact = true,
            )
        }
        if (lengthsCount > 0) {
            SecondaryButton(
                text = stringResource(if (showLengths) R.string.swim_table_hide_lengths else R.string.swim_table_lengths, lengthsCount),
                onClick = { showLengths = !showLengths },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                compact = true,
            )
        }
    }
}

private val COLUMN_WEIGHTS = listOf(1.6f, 1f, 1f, 0.9f, 0.8f)

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, dim: Boolean = false) {
    Column {
        if (!header) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TriColors.border),
            )
        }
        Row(Modifier.padding(vertical = 8.dp)) {
            cells.forEachIndexed { index, cell ->
                Text(
                    text = cell,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        header || dim -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    modifier = Modifier.weight(COLUMN_WEIGHTS[index]),
                )
            }
        }
    }
}
