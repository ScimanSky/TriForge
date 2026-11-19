package com.triforge.app.ui.today

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.RecoveryState
import com.triforge.app.ui.TrainingState
import com.triforge.app.ui.common.ActivityRow
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.Avatar
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.common.LegendDot
import com.triforge.app.ui.common.LineChart
import com.triforge.app.ui.common.LineSeries
import com.triforge.app.ui.common.LoadingBox
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.ProgressBar
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.StatCell
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.labelRes
import com.triforge.app.ui.recovery.RecoveryCard
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.Activity
import com.triforge.domain.FitnessModel
import com.triforge.domain.FormPoint
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.SportStats
import com.triforge.domain.TrainingStats
import com.triforge.domain.WeekSummary
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Weeks the current one is measured against. */
private const val BASELINE_WEEKS = 4

private val headerDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALY)

@Composable
fun TodayScreen(
    state: TrainingState,
    recovery: RecoveryState,
    name: String,
    onOpenProfile: () -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenRecovery: () -> Unit,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: ByteArray? = null,
    onConnectSource: () -> Unit = onOpenProfile,
) {
    when (state) {
        TrainingState.Loading -> LoadingBox(modifier)

        TrainingState.Failed -> CenteredMessage(stringResource(R.string.load_error), modifier = modifier)

        is TrainingState.Loaded -> Dashboard(
            activities = state.activities,
            recovery = recovery,
            name = name,
            avatar = avatar,
            onOpenProfile = onOpenProfile,
            onConnectSource = onConnectSource,
            onOpenActivity = onOpenActivity,
            onOpenStats = onOpenStats,
            onOpenRecovery = onOpenRecovery,
            onCheckIn = onCheckIn,
            modifier = modifier,
        )
    }
}

@Composable
private fun Dashboard(
    activities: List<Activity>,
    recovery: RecoveryState,
    name: String,
    avatar: ByteArray?,
    onOpenProfile: () -> Unit,
    onConnectSource: () -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenRecovery: () -> Unit,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val trend = remember(activities, today) {
        TrainingStats.weeklyTrend(activities, BASELINE_WEEKS + 1, today, zone)
    }
    val form = remember(activities, today) { FitnessModel.series(activities, today, zone) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(today, name, avatar, onOpenProfile) }

        if (activities.isEmpty()) {
            item {
                TriCard {
                    Text(
                        text = stringResource(R.string.today_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.today_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.today_connect_source),
                        onClick = onConnectSource,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            item { RecoveryCard(recovery, onOpen = onOpenRecovery, onCheckIn = onCheckIn) }
            if (form.isNotEmpty()) item { FormCard(form) }
            item { WeekCard(current = trend.last(), baseline = trend.dropLast(1), onOpenStats = onOpenStats) }
            item { SectionLabel(stringResource(R.string.today_last_activity)) }
            item {
                val last = activities.first()
                ActivityRow(last, onClick = { onOpenActivity(last.id) })
            }
        }
    }
}

@Composable
private fun Header(today: LocalDate, name: String, avatar: ByteArray?, onOpenProfile: () -> Unit) {
    val date = remember(today) { headerDate.format(today).replaceFirstChar { it.titlecase(Locale.ITALY) } }
    val firstName = name.trim().substringBefore(' ')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (firstName.isEmpty()) {
                    stringResource(R.string.today_greeting)
                } else {
                    stringResource(R.string.today_greeting_name, firstName)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Avatar(
            jpeg = avatar,
            name = name,
            size = 44.dp,
            contentDescription = stringResource(R.string.nav_profile),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    onClickLabel = stringResource(R.string.nav_profile),
                    role = Role.Button,
                    onClick = onOpenProfile,
                ),
        )
    }
}

private fun signed(value: Double): String {
    val rounded = value.roundToInt()
    return if (rounded < 0) "−${-rounded}" else "+$rounded"
}

/** Fitness, fatigue and their gap, from the training load the watch reports. */
@Composable
private fun FormCard(points: List<FormPoint>) {
    val latest = points.last()
    val fitnessColor = MaterialTheme.colorScheme.primary
    val fatigueColor = TriColors.textDim
    val fitnessLabel = stringResource(R.string.today_fitness)
    val fatigueLabel = stringResource(R.string.today_fatigue)

    TriCard {
        CardHeader(
            title = stringResource(R.string.today_form_title),
            trailing = stringResource(R.string.today_form_period, points.size),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCell(latest.fitness.roundToInt().toString(), fitnessLabel, Modifier.weight(1f))
            StatCell(latest.fatigue.roundToInt().toString(), fatigueLabel, Modifier.weight(1f))
            StatCell(
                value = signed(latest.form),
                label = stringResource(R.string.today_form),
                modifier = Modifier.weight(1f),
                valueColor = if (latest.form >= 0) TriColors.good else TriColors.warn,
            )
        }
        LineChart(
            series = listOf(
                LineSeries(points.map { it.fatigue.toFloat() }, fatigueColor, dashed = true),
                LineSeries(points.map { it.fitness.toFloat() }, fitnessColor, filled = true),
            ),
            description = stringResource(
                R.string.today_form_chart,
                latest.fitness.roundToInt(),
                latest.fatigue.roundToInt(),
            ),
        )
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LegendDot(fitnessColor, fitnessLabel)
            LegendDot(fatigueColor, fatigueLabel)
        }
    }
}

/** This week per discipline, against the athlete's own recent average. */
@Composable
private fun WeekCard(current: WeekSummary, baseline: List<WeekSummary>, onOpenStats: () -> Unit) {
    TriCard {
        CardHeader(
            title = stringResource(R.string.today_this_week),
            trailing = stringResource(
                R.string.today_week_total,
                Formatters.duration(current.durationSec),
                current.sessions,
            ),
        )
        Spacer(Modifier.height(6.dp))
        TrainingStats.triathlonSports.forEach { sport -> SportProgress(sport, current, baseline) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onOpenStats),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.today_sport_stats),
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

@Composable
private fun SportProgress(sport: Sport, current: WeekSummary, baseline: List<WeekSummary>) {
    val byTime = SportStats.measuresVolumeByTime(sport)
    val now = current.of(sport)
    val nowValue = if (byTime) now.durationSec.toDouble() else now.distanceMeters
    val usual = if (baseline.isEmpty()) {
        0.0
    } else {
        baseline.sumOf { week ->
            val totals = week.of(sport)
            if (byTime) totals.durationSec.toDouble() else totals.distanceMeters
        } / baseline.size
    }

    fun text(value: Double) =
        if (byTime) Formatters.duration(value.toLong()) else Formatters.distance(sport, value)

    Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(sport.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = SportColors.of(sport),
                modifier = Modifier.weight(1f),
            )
            Text(text = text(nowValue), style = numberStyle(17.sp))
            if (usual > 0) {
                Text(
                    text = stringResource(R.string.today_usual, text(usual)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ProgressBar(
            fraction = when {
                usual > 0 -> (nowValue / usual).toFloat()
                nowValue > 0 -> 1f
                else -> 0f
            },
            color = SportColors.of(sport),
        )
    }
}
