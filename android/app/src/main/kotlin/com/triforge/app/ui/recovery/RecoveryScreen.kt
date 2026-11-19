package com.triforge.app.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.RecoveryState
import com.triforge.app.ui.common.BackButton
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.KeyValueRow
import com.triforge.app.ui.common.LegendDot
import com.triforge.app.ui.common.LineChart
import com.triforge.app.ui.common.LineSeries
import com.triforge.app.ui.common.LoadingBox
import com.triforge.app.ui.common.ProgressBar
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.StatGrid
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.DailyWellness
import com.triforge.domain.LoadState
import com.triforge.domain.MarkerBand
import com.triforge.domain.RecoveryDay
import com.triforge.domain.RecoveryPhase
import com.triforge.domain.RecoveryReport
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RecoveryScreen(
    state: RecoveryState,
    onBack: () -> Unit,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        RecoveryState.Loading -> LoadingBox(modifier)
        RecoveryState.Failed -> Column(modifier.padding(16.dp)) {
            BackButton(onBack)
            CenteredMessage(stringResource(R.string.load_error))
        }

        is RecoveryState.Loaded -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackButton(onBack)
                Text(
                    text = stringResource(R.string.recovery_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            val report = state.report
            ScoreCard(report, showCheckIn = state.todayCheckIn == null, onCheckIn = onCheckIn)
            if (report.today.score != null) WhyCard(report.today)
            if (report.history.any { it.smoothedScore != null }) TrendCard(report.history)
            if (report.hrvBand.any { it.value != null }) {
                BandCard(
                    title = stringResource(R.string.recovery_hrv_title),
                    summary = report.hrvBand.last().mean7?.let { stringResource(R.string.recovery_hrv_summary, it.roundToInt().toString()) },
                    band = report.hrvBand,
                    description = stringResource(R.string.recovery_hrv_chart),
                )
            }
            if (report.restingHrBand.any { it.value != null }) {
                BandCard(
                    title = stringResource(R.string.recovery_rhr_title),
                    summary = report.restingHrBand.last().mean7?.let {
                        stringResource(R.string.recovery_rhr_summary, String.format(Locale.ITALY, "%.1f", it))
                    },
                    band = report.restingHrBand,
                    description = stringResource(R.string.recovery_rhr_chart),
                )
            }
            report.today.load?.let { LoadCard(it) }
            report.latestWellness?.let { WatchCard(it) }
            Text(
                text = stringResource(R.string.recovery_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ScoreCard(report: RecoveryReport, showCheckIn: Boolean, onCheckIn: () -> Unit) {
    val today = report.today
    val shown = today.smoothedScore
    val light = today.light
    TriCard {
        if (shown != null && light != null) {
            val label = stringResource(light.labelRes())
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = shown.toString(), style = numberStyle(48.sp), color = light.color())
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(text = label, style = MaterialTheme.typography.titleMedium, color = light.color())
                    Text(
                        text = stringResource(today.confidenceLevel.labelRes()),
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
            Text(
                text = stringResource(R.string.recovery_scale_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            text = stringResource(today.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (today.phase == RecoveryPhase.PROVISIONAL && today.score != null) {
            Text(
                text = stringResource(R.string.recovery_provisional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (report.runLoadJump) {
            Text(
                text = stringResource(R.string.recovery_run_jump),
                style = MaterialTheme.typography.bodySmall,
                color = TriColors.warn,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (showCheckIn) {
            SecondaryButton(
                text = stringResource(R.string.recovery_checkin_cta),
                onClick = onCheckIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            )
        }
    }
}

private fun deviationColor(value: Double): Color = when {
    value >= 0.75 -> TriColors.danger
    value >= 0.4 -> TriColors.alert
    value > 0.0 -> TriColors.caution
    else -> TriColors.good
}

/** What pushed the score: each part's deviation and the points it adds. */
@Composable
private fun WhyCard(today: RecoveryDay) {
    CollapsibleCard(title = stringResource(R.string.recovery_why_title), initiallyExpanded = true) {
        today.contributions.entries.sortedByDescending { it.value }.forEach { (part, points) ->
            val deviation = today.deviations[part] ?: 0.0
            Column(modifier = Modifier.padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(part.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.recovery_points, points.roundToInt()),
                        style = numberStyle(15.sp),
                        color = deviationColor(deviation),
                    )
                }
                // At least a sliver, so a part that is fine still shows its track.
                ProgressBar(fraction = deviation.toFloat().coerceAtLeast(0.02f), color = deviationColor(deviation))
            }
        }
        if (today.missing.isNotEmpty()) {
            Text(
                text = stringResource(R.string.recovery_missing_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            today.missing.sortedByDescending { it.weight }.forEach { part ->
                Text(
                    text = "• " + stringResource(part.missingRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TrendCard(history: List<RecoveryDay>) {
    val latest = history.last().light
    CollapsibleCard(
        title = stringResource(R.string.recovery_trend_title),
        summary = stringResource(R.string.recovery_trend_period, history.size),
    ) {
        LineChart(
            series = listOf(
                LineSeries(history.map { it.smoothedScore?.toFloat() }, latest.color(), filled = true),
                // Fixed zone edges keep the scale honest on quiet weeks.
                LineSeries(history.map { 30f }, TriColors.caution.copy(alpha = 0.5f), dashed = true),
                LineSeries(history.map { 55f }, TriColors.alert.copy(alpha = 0.5f), dashed = true),
                LineSeries(listOf(0f) + List(history.size - 1) { null }, Color.Transparent),
            ),
            description = stringResource(R.string.recovery_trend_chart, history.size),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BandCard(title: String, summary: String?, band: List<MarkerBand>, description: String) {
    val nightColor = TriColors.textDim
    val weekColor = MaterialTheme.colorScheme.primary
    val bandColor = TriColors.good
    CollapsibleCard(title = title, summary = summary) {
        LineChart(
            series = listOf(
                LineSeries(band.map { it.low?.toFloat() }, bandColor.copy(alpha = 0.6f), dashed = true),
                LineSeries(band.map { it.high?.toFloat() }, bandColor.copy(alpha = 0.6f), dashed = true),
                LineSeries(band.map { it.value?.toFloat() }, nightColor.copy(alpha = 0.5f)),
                LineSeries(band.map { it.mean7?.toFloat() }, weekColor),
            ),
            description = description,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(nightColor, stringResource(R.string.recovery_legend_night))
            LegendDot(weekColor, stringResource(R.string.recovery_legend_week))
            LegendDot(bandColor, stringResource(R.string.recovery_legend_band))
        }
    }
}

private fun signed(value: Int): String = if (value < 0) "−${-value}" else "+$value"

@Composable
private fun LoadCard(load: LoadState) {
    CollapsibleCard(title = stringResource(R.string.recovery_load_title)) {
        StatGrid(
            stats = listOf(
                load.atl.roundToInt().toString() to stringResource(R.string.recovery_load_acute),
                load.ctl.roundToInt().toString() to stringResource(R.string.recovery_load_chronic),
                signed((load.formPct * 100).roundToInt()) + "%" to stringResource(R.string.recovery_load_form),
                signed(load.ramp.roundToInt()) to stringResource(R.string.recovery_load_ramp),
                String.format(Locale.ITALY, "%.1f", load.monotony) to stringResource(R.string.recovery_load_monotony),
            ),
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.recovery_load_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** The watch's own readings, for comparison only. */
@Composable
private fun WatchCard(latest: DailyWellness) {
    val status = latest.hrvStatus
    val sleepScore = latest.sleepScore
    if (status == null && sleepScore == null) return
    CollapsibleCard(title = stringResource(R.string.recovery_garmin_title)) {
        if (status != null) {
            val label = when (status.uppercase(Locale.ROOT)) {
                "BALANCED" -> stringResource(R.string.recovery_garmin_balanced)
                "UNBALANCED" -> stringResource(R.string.recovery_garmin_unbalanced)
                "LOW" -> stringResource(R.string.recovery_garmin_low)
                "POOR" -> stringResource(R.string.recovery_garmin_poor)
                else -> status.lowercase(Locale.ITALY)
            }
            KeyValueRow(stringResource(R.string.recovery_garmin_hrv_status), label)
        }
        if (sleepScore != null) KeyValueRow(stringResource(R.string.recovery_garmin_sleep_score), sleepScore.toString())
        Text(
            text = stringResource(R.string.recovery_garmin_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
