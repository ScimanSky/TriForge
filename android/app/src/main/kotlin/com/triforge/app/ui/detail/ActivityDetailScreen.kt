package com.triforge.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.triforge.app.R
import com.triforge.app.ui.common.BackButton
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.KeyValueRow
import com.triforge.app.ui.common.LineChart
import com.triforge.app.ui.common.LineSeries
import com.triforge.app.ui.common.SportIconBox
import com.triforge.app.ui.common.StatGrid
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.kindLabelRes
import com.triforge.app.ui.common.sourceLabel
import com.triforge.app.ui.theme.SportColors
import com.triforge.domain.Activity
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.SwimAnalysis
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** How many earlier sessions of the same sport the comparison chart shows. */
private const val HISTORY_COUNT = 10

private val startFormat = DateTimeFormatter.ofPattern("EEE d MMM, H:mm", Locale.ITALY)

/**
 * One activity in full. [history] is the athlete's loaded activities, newest
 * first, used to place this session among the recent ones of its sport.
 */
@Composable
fun ActivityDetailScreen(
    activity: Activity?,
    history: List<Activity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    cssSecPer100: Int? = null,
    /** The session's load in the app's scale, when it has been worked out. */
    load: Int? = null,
    /** The athlete's weight, for what the session adds to the day's energy. */
    weightKg: Double? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (activity == null) {
            BackButton(onBack)
            CenteredMessage(stringResource(R.string.detail_missing))
            return@Column
        }

        Header(activity, onBack)
        if (activity.sport == Sport.STRENGTH || activity.sport == Sport.OTHER) {
            // No pace to read: the watch's numbers, the exercises, and what the session counts for.
            SupportSections(activity, load, weightKg)
            EffectCard(activity)
            return@Column
        }
        HeadlineCard(activity, cssSecPer100)
        if (activity.sport == Sport.SWIM) SwimSections(activity, history, cssSecPer100)
        TimingCard(activity)
        ComparisonCard(activity, history)
        HeartRateCard(activity)
        TechniqueCard(activity)
        EffectCard(activity)
    }
}

@Composable
private fun Header(activity: Activity, onBack: () -> Unit) {
    val start = remember(activity.startTime) {
        startFormat.format(activity.startTime.atZone(ZoneId.systemDefault()))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.name.ifBlank { stringResource(activity.kindLabelRes()) },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${stringResource(activity.kindLabelRes())} · $start · ${activity.sourceLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SportIconBox(activity.sport)
    }
}

private fun whole(value: Double): String = "%,d".format(Locale.ITALY, value.roundToInt())

private fun oneDecimal(value: Double): String = "%.1f".format(Locale.ITALY, value)

/** The six numbers an athlete looks for first. Only those the source gave are shown. */
@Composable
private fun HeadlineCard(activity: Activity, cssSecPer100: Int?) {
    val sport = activity.sport
    val stats = buildList {
        if (activity.distanceMeters > 0) {
            if (sport == Sport.SWIM) {
                add(whole(activity.distanceMeters) to stringResource(R.string.unit_m))
            } else {
                add(Formatters.kilometers(activity.distanceMeters) to stringResource(R.string.unit_km))
            }
        }
        add(Formatters.clock(activity.durationSec) to stringResource(R.string.detail_time))
        Formatters.paceValue(sport, activity.distanceMeters, activity.paceSec)?.let { value ->
            val label = if (sport == Sport.BIKE) {
                Formatters.paceUnit(sport)
            } else {
                stringResource(R.string.detail_pace, Formatters.paceUnit(sport))
            }
            add(value to label)
        }
        if (sport == Sport.SWIM) {
            // In the water the wrist sensor is unreliable: the stroke tells more.
            activity.swolf?.takeIf { it > 0 }?.let { add(it.toString() to stringResource(R.string.metric_swolf)) }
            SwimAnalysis.metersPerStroke(activity)?.let {
                add("%.2f".format(Locale.ITALY, it) to stringResource(R.string.swim_headline_dps))
            }
        } else {
            activity.avgHr?.let { add(it.toString() to stringResource(R.string.metric_avg_hr)) }
        }
        activity.elevationGainMeters?.takeIf { it > 0 }?.let {
            add(whole(it) to stringResource(R.string.detail_elevation))
        }
        val lapLoad = if (sport == Sport.SWIM) SwimAnalysis.lapTss(activity, cssSecPer100) else null
        if (lapLoad != null) {
            add(whole(lapLoad) to stringResource(R.string.swim_headline_tss))
        } else {
            activity.trainingLoad?.takeIf { it > 0 }?.let {
                add(whole(it) to stringResource(R.string.detail_load))
            }
        }
    }
    TriCard { StatGrid(stats) }
}

/**
 * Total time against time in motion, and the pace on each. Shown when the
 * source measured the time in motion and the two differ enough to matter:
 * a pool session with rests, a ride with a café stop.
 */
@Composable
private fun TimingCard(activity: Activity) {
    val rest = activity.restSec ?: return
    if (rest < MIN_REST_SEC || activity.durationSec <= 0) return
    val sport = activity.sport
    val isSpeed = sport == Sport.BIKE
    val movingPace = Formatters.pace(sport, activity.distanceMeters, activity.movingSec)
    val totalPace = Formatters.pace(sport, activity.distanceMeters, activity.durationSec)

    CollapsibleCard(
        title = stringResource(
            when {
                movingPace == null -> R.string.detail_timing_title
                isSpeed -> R.string.detail_timing_title_speed
                else -> R.string.detail_timing_title_pace
            },
        ),
        summary = stringResource(R.string.detail_timing_summary, Formatters.clock(activity.movingSec)),
        initiallyExpanded = true,
    ) {
        KeyValueRow(stringResource(R.string.detail_total_time), Formatters.clock(activity.durationSec))
        KeyValueRow(stringResource(R.string.detail_moving_time), Formatters.clock(activity.movingSec))
        KeyValueRow(stringResource(R.string.detail_rest_time), Formatters.clock(rest))
        if (movingPace != null && totalPace != null) {
            KeyValueRow(
                stringResource(if (isSpeed) R.string.detail_moving_speed else R.string.detail_moving_pace),
                movingPace,
            )
            KeyValueRow(
                stringResource(if (isSpeed) R.string.detail_total_speed else R.string.detail_total_pace),
                totalPace,
            )
        }
        if (sport == Sport.SWIM) {
            Text(
                text = stringResource(R.string.detail_timing_swim_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Under a minute of stops is noise, not a second pace worth showing. */
private const val MIN_REST_SEC = 60L

/** This session's pace or speed next to the previous ones of the same sport. */
@Composable
private fun ComparisonCard(activity: Activity, history: List<Activity>) {
    val sport = activity.sport
    if (sport == Sport.OTHER || activity.distanceMeters <= 0 || activity.paceSec <= 0) return
    val isSpeed = sport == Sport.BIKE

    val sessions = remember(activity.id, history) {
        history
            .filter {
                it.sport == sport && it.distanceMeters > 0 && it.paceSec > 0 &&
                    !it.startTime.isAfter(activity.startTime)
            }
            .take(HISTORY_COUNT)
            .reversed()
    }
    if (sessions.size < 3) return

    val values = sessions.map {
        val secondsPerMeter = it.paceSec / it.distanceMeters
        (if (isSpeed) 3.6 / secondsPerMeter else secondsPerMeter).toFloat()
    }
    val best = sessions.minBy { it.paceSec / it.distanceMeters }
    val thisPace = Formatters.pace(sport, activity.distanceMeters, activity.paceSec).orEmpty()
    val bestPace = Formatters.pace(sport, best.distanceMeters, best.paceSec).orEmpty()

    TriCard {
        CardHeader(
            title = stringResource(if (isSpeed) R.string.detail_compare_speed else R.string.detail_compare_pace),
            trailing = stringResource(R.string.detail_compare_count, sessions.size),
        )
        LineChart(
            series = listOf(LineSeries(values, SportColors.of(sport), filled = true)),
            description = stringResource(R.string.detail_compare_chart, sessions.size, thisPace, bestPace),
            invert = !isSpeed,
            modifier = Modifier.padding(top = 14.dp),
        )
        // Which way is better: a pace chart is drawn upside down, and without an axis nobody can tell.
        Text(
            text = stringResource(if (isSpeed) R.string.detail_compare_direction_speed else R.string.detail_compare_direction_pace),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.detail_compare_this, thisPace),
                style = MaterialTheme.typography.bodySmall,
                color = SportColors.of(sport),
            )
            Text(
                text = stringResource(R.string.detail_compare_best, bestPace),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeartRateCard(activity: Activity) {
    val avg = activity.avgHr
    val max = activity.maxHr
    if (avg == null && max == null) return

    CollapsibleCard(
        title = stringResource(R.string.detail_hr_title),
        summary = avg?.let { stringResource(R.string.unit_bpm_value, it) },
        initiallyExpanded = true,
    ) {
        avg?.let { KeyValueRow(stringResource(R.string.metric_avg_hr), stringResource(R.string.unit_bpm_value, it)) }
        max?.let { KeyValueRow(stringResource(R.string.metric_max_hr), stringResource(R.string.unit_bpm_value, it)) }
    }
}

@Composable
private fun TechniqueCard(activity: Activity) {
    val rows = buildList {
        activity.avgPowerWatts?.let {
            add(stringResource(R.string.metric_avg_power) to stringResource(R.string.unit_watt_value, it))
        }
        activity.normPowerWatts?.let {
            add(stringResource(R.string.metric_norm_power) to stringResource(R.string.unit_watt_value, it))
        }
        activity.avgCadence?.let {
            val label = when (activity.sport) {
                Sport.BIKE -> R.string.metric_cadence_rpm
                Sport.SWIM -> R.string.metric_cadence_strokes
                else -> R.string.metric_cadence_spm
            }
            add(stringResource(label) to it.toString())
        }
        activity.swolf?.let { add(stringResource(R.string.metric_swolf) to it.toString()) }
        activity.strokes?.let { add(stringResource(R.string.metric_strokes) to whole(it.toDouble())) }
        activity.lengths?.let { add(stringResource(R.string.metric_lengths) to it.toString()) }
        if (activity.openWater == true) {
            add(stringResource(R.string.metric_water) to stringResource(R.string.metric_open_water))
        } else {
            activity.poolLengthMeters?.takeIf { it > 0 }?.let {
                // A 25 yd pool is 22,86 m: whole metres only when it is whole.
                val length = if (it % 1.0 == 0.0) whole(it) else oneDecimal(it)
                add(stringResource(R.string.metric_pool) to stringResource(R.string.unit_m_value, length))
            }
        }
    }
    if (rows.isEmpty()) return

    CollapsibleCard(title = stringResource(R.string.detail_technique_title)) {
        rows.forEach { (label, value) -> KeyValueRow(label, value) }
    }
}

@Composable
private fun EffectCard(activity: Activity) {
    val rows = buildList {
        activity.aerobicTe?.let { add(stringResource(R.string.metric_aerobic_te) to oneDecimal(it)) }
        activity.anaerobicTe?.let { add(stringResource(R.string.metric_anaerobic_te) to oneDecimal(it)) }
        activity.vo2max?.let { add(stringResource(R.string.metric_vo2max) to oneDecimal(it)) }
        activity.calories?.let {
            add(stringResource(R.string.metric_calories) to stringResource(R.string.unit_kcal_value, whole(it.toDouble())))
        }
    }
    if (rows.isEmpty()) return

    CollapsibleCard(
        title = stringResource(R.string.detail_effect_title),
        summary = activity.aerobicTe?.let { stringResource(R.string.detail_effect_summary, oneDecimal(it)) },
    ) {
        rows.forEach { (label, value) -> KeyValueRow(label, value) }
        Text(
            text = stringResource(R.string.detail_effect_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
