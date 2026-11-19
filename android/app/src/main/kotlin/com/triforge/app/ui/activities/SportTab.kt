package com.triforge.app.ui.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.TrainingViewModel
import com.triforge.app.ui.common.ActivityRow
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.LineChart
import com.triforge.app.ui.common.LineSeries
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.StatGrid
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.TriChip
import com.triforge.app.ui.common.VolumeBars
import com.triforge.app.ui.common.dayLabel
import com.triforge.app.ui.common.labelRes
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.Activity
import com.triforge.domain.Formatters
import com.triforge.domain.PersonalRecord
import com.triforge.domain.RecordKind
import com.triforge.domain.Sport
import com.triforge.domain.SportReport
import com.triforge.domain.SportStats
import com.triforge.domain.WeekVolume
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PERIODS = listOf(4, 8, 16)
private const val DEFAULT_PERIOD = 8
private const val RECENT_COUNT = 5

/** Statistics of one discipline: headline cards, charts, then detail on demand. */
@Composable
fun SportTab(activities: List<Activity>, sport: Sport, onOpenActivity: (String) -> Unit) {
    var weeks by rememberSaveable(sport.name) { mutableStateOf(DEFAULT_PERIOD) }
    val today = LocalDate.now()
    val report = remember(activities, sport, weeks, today) {
        SportStats.report(
            activities = activities,
            sport = sport,
            weeks = weeks,
            today = today,
            zone = ZoneId.systemDefault(),
            loadedWeeks = TrainingViewModel.WINDOW_WEEKS.toInt(),
        )
    }
    val recent = remember(activities, sport) { activities.filter { it.sport == sport }.take(RECENT_COUNT) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "period") {
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PERIODS.forEach { period ->
                    TriChip(
                        label = stringResource(R.string.period_weeks_short, period),
                        selected = weeks == period,
                        onClick = { weeks = period },
                    )
                }
            }
        }

        if (report.sessions == 0 && recent.isEmpty()) {
            item(key = "empty") {
                CenteredMessage(
                    title = stringResource(R.string.sport_empty_title, stringResource(sport.labelRes())),
                    body = stringResource(R.string.sport_empty_body),
                )
            }
            return@LazyColumn
        }

        item(key = "kpi") { KpiGrid(report) }
        item(key = "volume") { VolumeCard(report) }
        item(key = "trend") { PaceTrendCard(report) }
        item(key = "technical") { TechnicalCard(report) }
        item(key = "records") { RecordsCard(report.records, onOpenActivity) }

        if (recent.isNotEmpty()) {
            item(key = "recent-title") { SectionLabel(stringResource(R.string.today_recent)) }
            items(recent, key = { it.id }) { activity ->
                ActivityRow(activity, onClick = { onOpenActivity(activity.id) })
            }
        }
    }
}

private val weekDate = DateTimeFormatter.ofPattern("d/M", Locale.ITALY)

private fun wholeMeters(meters: Double): String = "%,d m".format(Locale.ITALY, meters.toLong())

private fun oneDecimal(value: Double): String = "%.1f".format(Locale.ITALY, value)

@Composable
private fun KpiGrid(report: SportReport) {
    val sport = report.sport
    val byTime = SportStats.measuresVolumeByTime(sport)
    val change = report.volumeChangePercent
    val none = stringResource(R.string.value_none)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                label = stringResource(R.string.sport_kpi_volume),
                value = if (byTime) Formatters.duration(report.durationSec) else Formatters.distance(sport, report.distanceMeters),
                // Neutral ink: more volume is not "good" in itself.
                note = change?.let {
                    stringResource(R.string.sport_kpi_change, if (it >= 0) "↑ $it" else "↓ ${-it}")
                },
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(R.string.sport_kpi_sessions),
                value = report.sessions.toString(),
                note = stringResource(R.string.sport_kpi_per_week, oneDecimal(report.sessionsPerWeek)),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                label = stringResource(if (sport == Sport.BIKE) R.string.sport_kpi_speed else R.string.sport_kpi_pace),
                value = Formatters.paceValue(sport, report.distanceMeters, report.paceSec) ?: none,
                note = Formatters.paceUnit(sport),
                modifier = Modifier.weight(1f),
            )
            if (sport == Sport.SWIM) {
                KpiCard(
                    label = stringResource(R.string.sport_kpi_per_session),
                    value = if (report.sessions > 0) wholeMeters(report.distanceMeters / report.sessions) else none,
                    note = stringResource(R.string.sport_kpi_average),
                    modifier = Modifier.weight(1f),
                )
            } else {
                KpiCard(
                    label = stringResource(R.string.sport_kpi_elevation),
                    value = wholeMeters(report.elevationGainMeters),
                    note = stringResource(
                        R.string.sport_kpi_elevation_note,
                        if (byTime) Formatters.distance(sport, report.distanceMeters) else Formatters.duration(report.durationSec),
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    note: String?,
    modifier: Modifier = Modifier,
    noteColor: Color? = null,
) {
    TriCard(modifier = modifier, padding = 16.dp) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = numberStyle(24.sp),
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = note.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = noteColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun VolumeCard(report: SportReport) {
    val sport = report.sport
    val byTime = SportStats.measuresVolumeByTime(sport)
    val weekly = report.weekly
    // Labels on every bar stop fitting past eight weeks.
    val dense = weekly.size > 8

    fun label(week: WeekVolume): String = when {
        byTime -> Formatters.hours(week.durationSec)
        sport == Sport.SWIM -> oneDecimal(week.distanceMeters / 1000.0)
        else -> "%.0f".format(Locale.ITALY, week.distanceMeters / 1000.0)
    }

    TriCard {
        CardHeader(
            title = stringResource(R.string.sport_volume_title),
            trailing = stringResource(if (byTime) R.string.unit_hours else R.string.unit_km),
        )
        VolumeBars(
            values = weekly.map { if (byTime) it.durationSec.toFloat() else it.distanceMeters.toFloat() },
            topLabels = if (dense) null else weekly.map { if (it.sessions > 0) label(it) else "" },
            bottomLabels = weekly.mapIndexed { index, week ->
                if (dense && (weekly.lastIndex - index) % 2 != 0) "" else weekDate.format(week.weekStart)
            },
            color = SportColors.of(sport),
            lastInProgress = true,
            description = stringResource(
                R.string.sport_volume_chart,
                weekly.size,
                if (byTime) Formatters.duration(weekly.last().durationSec) else Formatters.distance(sport, weekly.last().distanceMeters),
            ),
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.sport_volume_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Average pace or speed week by week. Weeks without training are gaps. */
@Composable
private fun PaceTrendCard(report: SportReport) {
    val sport = report.sport
    val isSpeed = sport == Sport.BIKE
    // Speed in km/h for the bike, seconds per metre otherwise.
    val values = report.weekly.map { week ->
        week.secondsPerMeter?.let { if (isSpeed) (3.6 / it).toFloat() else it.toFloat() }
    }
    val trained = report.weekly.filter { it.secondsPerMeter != null }
    if (trained.size < 2) return

    fun text(week: WeekVolume): String =
        Formatters.pace(sport, week.distanceMeters, week.paceSec).orEmpty()

    val from = text(trained.first())
    val to = text(trained.last())

    TriCard {
        CardHeader(
            title = stringResource(if (isSpeed) R.string.sport_trend_speed else R.string.sport_trend_pace),
            trailing = stringResource(R.string.sport_trend_note),
        )
        LineChart(
            series = listOf(LineSeries(values, SportColors.of(sport), filled = true)),
            description = stringResource(R.string.sport_trend_chart, from, to),
            invert = !isSpeed,
            // Spans under which a change is noise, so the chart does not magnify it.
            minSpan = when (sport) {
                Sport.SWIM -> 0.15f // 15 s per 100 m
                Sport.RUN -> 0.06f // 60 s per km
                else -> 5f // 5 km/h
            },
            axisLabel = { value ->
                when (sport) {
                    Sport.SWIM -> Formatters.minutesAndSeconds(value * 100.0)
                    Sport.RUN -> Formatters.minutesAndSeconds(value * 1000.0)
                    else -> "%.0f".format(Locale.ITALY, value)
                }
            },
            modifier = Modifier.padding(top = 14.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.sport_trend_from, weekDate.format(trained.first().weekStart), from),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.sport_trend_to, weekDate.format(trained.last().weekStart), to),
                style = MaterialTheme.typography.bodySmall,
                color = TriColors.textDim,
            )
        }
    }
}

@Composable
private fun TechnicalCard(report: SportReport) {
    val cadenceLabel = stringResource(
        when (report.sport) {
            Sport.BIKE -> R.string.metric_cadence_rpm
            Sport.SWIM -> R.string.metric_cadence_strokes
            else -> R.string.metric_cadence_spm
        },
    )
    val stats = listOfNotNull(
        report.avgHr?.let { it.toString() to stringResource(R.string.metric_avg_hr) },
        report.avgCadence?.let { it.toString() to cadenceLabel },
        report.avgPowerWatts?.let { it.toString() to stringResource(R.string.metric_avg_power) },
        report.avgSwolf?.let { swolf ->
            swolf.toString() to (
                report.swolfPoolMeters?.let { stringResource(R.string.metric_swolf_pool, "%.0f".format(Locale.ITALY, it)) }
                    ?: stringResource(R.string.metric_swolf)
                )
        },
    )

    CollapsibleCard(
        title = stringResource(R.string.sport_technical_title),
        summary = report.avgHr?.let { stringResource(R.string.sport_technical_summary, it) },
    ) {
        if (stats.isEmpty()) {
            Text(
                text = stringResource(R.string.sport_technical_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StatGrid(stats)
            Text(
                text = stringResource(R.string.sport_technical_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun RecordsCard(records: List<PersonalRecord>, onOpenActivity: (String) -> Unit) {
    CollapsibleCard(
        title = stringResource(R.string.sport_records_title),
        summary = stringResource(R.string.period_last_weeks, TrainingViewModel.WINDOW_WEEKS.toInt()),
    ) {
        if (records.isEmpty()) {
            Text(
                text = stringResource(R.string.sport_records_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        records.forEach { record ->
            val activity = record.activity
            val value = when (record.kind) {
                RecordKind.LONGEST_DISTANCE -> Formatters.distance(activity.sport, activity.distanceMeters)
                RecordKind.LONGEST_DURATION -> Formatters.duration(activity.durationSec)
                RecordKind.FASTEST ->
                    Formatters.pace(activity.sport, activity.distanceMeters, activity.paceSec).orEmpty()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { onOpenActivity(activity.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            when (record.kind) {
                                RecordKind.LONGEST_DISTANCE -> R.string.record_longest_distance
                                RecordKind.LONGEST_DURATION -> R.string.record_longest_duration
                                RecordKind.FASTEST ->
                                    if (activity.sport == Sport.BIKE) R.string.record_fastest_speed else R.string.record_fastest_pace
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = remember(activity.startTime) { activity.dayLabel() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = value, style = numberStyle(17.sp))
            }
        }
    }
}
