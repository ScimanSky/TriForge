package com.triforge.app.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.TrainingViewModel
import com.triforge.app.ui.common.ActivityRow
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.KeyValueRow
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.TriChip
import com.triforge.app.ui.common.countLabel
import com.triforge.app.ui.common.labelRes
import com.triforge.app.ui.common.wholeNumber
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.Activity
import com.triforge.domain.ExerciseTotal
import com.triforge.domain.Formatters
import com.triforge.domain.OtherKind
import com.triforge.domain.Sport
import com.triforge.domain.SportStats
import com.triforge.domain.StrengthStats
import com.triforge.domain.TrainingStats
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Exercises listed before the card says "and N more" would be needed: the frequent ones are what matters. */
private const val MAX_EXERCISES = 8

/** What the tab says about its sessions of the period besides the report. */
private data class SupportData(
    val inPeriod: List<Activity>,
    /** Average load of a session, when the loads are known. */
    val avgLoad: Int?,
    /** This sport's part of the whole load of the period, in percent. */
    val loadShare: Int?,
    val exercises: List<ExerciseTotal>,
    val byKind: List<Triple<OtherKind, Int, Long>>,
    val maxHr: Int?,
    val calories: Int?,
)

private fun supportData(
    activities: List<Activity>,
    sport: Sport,
    loads: Map<String, Int>,
    periodStart: LocalDate,
    zone: ZoneId,
): SupportData {
    val all = activities.filter { !it.startTime.atZone(zone).toLocalDate().isBefore(periodStart) }
    val inPeriod = all.filter { it.sport == sport }
    val own = inPeriod.mapNotNull { loads[it.id] }
    val total = all.sumOf { loads[it.id] ?: 0 }
    return SupportData(
        inPeriod = inPeriod,
        avgLoad = own.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        loadShare = if (total > 0 && own.isNotEmpty()) (own.sum() * 100.0 / total).roundToInt() else null,
        exercises = if (sport == Sport.STRENGTH) {
            StrengthStats.frequentExercises(activities, periodStart.atStartOfDay(zone).toInstant())
        } else {
            emptyList()
        },
        byKind = if (sport == Sport.OTHER) {
            inPeriod.groupBy { it.kind }
                .map { (kind, list) -> Triple(kind, list.size, list.sumOf { it.durationSec }) }
                .sortedByDescending { it.third }
        } else {
            emptyList()
        },
        maxHr = inPeriod.mapNotNull { it.maxHr }.maxOrNull(),
        calories = inPeriod.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }?.sum(),
    )
}

/**
 * The gym, or everything else the watch records. They have no pace to
 * follow: the tab counts time, sessions and what they weigh in the load, and
 * for the gym the exercises the watch recognised.
 */
@Composable
fun SupportTab(
    activities: List<Activity>,
    sport: Sport,
    loads: Map<String, Int>,
    onOpenActivity: (String) -> Unit,
) {
    var weeks by rememberSaveable(sport.name) { mutableStateOf(DEFAULT_PERIOD) }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val report = remember(activities, sport, weeks, today) {
        SportStats.report(
            activities = activities,
            sport = sport,
            weeks = weeks,
            today = today,
            zone = zone,
            loadedWeeks = TrainingViewModel.WINDOW_WEEKS.toInt(),
        )
    }
    val data = remember(activities, sport, weeks, today, loads) {
        supportData(activities, sport, loads, TrainingStats.weekStart(today).minusWeeks(weeks - 1L), zone)
    }
    val none = stringResource(R.string.value_none)

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

        if (report.sessions == 0) {
            item(key = "empty") {
                CenteredMessage(
                    title = stringResource(
                        if (sport == Sport.STRENGTH) R.string.support_empty_gym_title else R.string.support_empty_other_title,
                    ),
                    body = stringResource(
                        if (sport == Sport.STRENGTH) R.string.support_empty_gym_body else R.string.support_empty_other_body,
                    ),
                )
            }
            return@LazyColumn
        }

        item(key = "kpi") {
            val change = report.volumeChangePercent
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard(
                        label = stringResource(R.string.support_kpi_time),
                        value = Formatters.duration(report.durationSec),
                        note = change?.let {
                            stringResource(R.string.sport_kpi_change, if (it >= 0) "↑ $it" else "↓ ${-it}")
                        },
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        label = stringResource(R.string.sport_kpi_sessions),
                        value = report.sessions.toString(),
                        note = stringResource(
                            R.string.sport_kpi_per_week,
                            "%.1f".format(java.util.Locale.ITALY, report.sessionsPerWeek),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard(
                        label = stringResource(R.string.sport_kpi_per_session),
                        value = Formatters.duration(report.durationSec / report.sessions),
                        note = stringResource(R.string.sport_kpi_average),
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        label = stringResource(R.string.support_kpi_load),
                        value = data.avgLoad?.toString() ?: none,
                        note = data.loadShare?.let { stringResource(R.string.support_kpi_load_share, it) }
                            ?: stringResource(R.string.sport_kpi_average),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item(key = "volume") { VolumeCard(report, title = stringResource(R.string.support_hours_title)) }

        if (sport == Sport.STRENGTH) {
            item(key = "exercises") { ExercisesCard(data.exercises) }
        } else if (data.byKind.size > 1 || data.byKind.any { it.first != OtherKind.GENERIC }) {
            item(key = "kinds") { KindsCard(data.byKind) }
        }

        if (report.avgHr != null || data.calories != null) {
            item(key = "heart") {
                CollapsibleCard(
                    title = stringResource(R.string.support_heart_title),
                    summary = report.avgHr?.let { stringResource(R.string.sport_technical_summary, it) },
                ) {
                    report.avgHr?.let {
                        KeyValueRow(stringResource(R.string.metric_avg_hr), stringResource(R.string.unit_bpm_value, it))
                    }
                    data.maxHr?.let {
                        KeyValueRow(stringResource(R.string.support_heart_peak), stringResource(R.string.unit_bpm_value, it))
                    }
                    data.calories?.let { kcal ->
                        KeyValueRow(
                            stringResource(R.string.support_calories_total),
                            stringResource(R.string.unit_kcal_value, wholeNumber(kcal)),
                        )
                        KeyValueRow(
                            stringResource(R.string.support_calories_session),
                            stringResource(R.string.unit_kcal_value, wholeNumber(kcal / report.sessions)),
                        )
                    }
                }
            }
        }

        item(key = "sessions-title") {
            SectionLabel(
                text = stringResource(if (sport == Sport.STRENGTH) R.string.sport_kpi_sessions else R.string.nav_activities),
                trailing = stringResource(R.string.support_sessions_in, data.inPeriod.size, weeks),
            )
        }
        items(data.inPeriod, key = { it.id }) { activity ->
            ActivityRow(activity, onClick = { onOpenActivity(activity.id) }, load = loads[activity.id])
        }
    }
}

/** The exercises done most in the period, from the sets the watch counted. */
@Composable
private fun ExercisesCard(exercises: List<ExerciseTotal>) {
    CollapsibleCard(
        title = stringResource(R.string.support_exercises_title),
        summary = stringResource(R.string.support_exercises_source),
        initiallyExpanded = exercises.isNotEmpty(),
    ) {
        exercises.take(MAX_EXERCISES).forEach { exercise ->
            ExerciseRow(
                name = StrengthStats.exerciseName(exercise.category),
                under = countLabel(exercise.sessions, R.string.coach_sessions_one, R.string.coach_sessions_many),
                sets = exercise.sets,
                reps = exercise.reps,
                volumeKg = exercise.volumeKg,
            )
        }
        Text(
            text = stringResource(R.string.support_exercises_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = if (exercises.isEmpty()) 0.dp else 10.dp),
        )
    }
}

/** One exercise: its name on the left, sets and repetitions and the weight moved on the right. */
@Composable
internal fun ExerciseRow(name: String, under: String?, sets: Int, reps: Int, volumeKg: Double?, modifier: Modifier = Modifier) {
    // One session: serie × ripetizioni × chili when the watch counted them all and the sets are even.
    // Over a period, or with uneven sets, in words.
    val kg = StrengthStats.kgPerRep(volumeKg, reps)?.let { wholeNumber(it.roundToInt()) }
    val work = if (under == null && kg != null && sets > 0 && reps % sets == 0) {
        stringResource(R.string.support_exercise_work, sets, reps / sets, kg)
    } else {
        listOfNotNull(
            stringResource(R.string.activity_sets, sets),
            reps.takeIf { it > 0 }?.let { countLabel(it, R.string.activity_reps_one, R.string.activity_reps_many) },
            kg?.let { stringResource(R.string.support_exercise_kg_avg, it) },
        ).joinToString(" · ")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = under ?: work,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (volumeKg != null && volumeKg > 0) {
                Text(text = stringResource(R.string.support_exercise_total, wholeNumber(volumeKg.roundToInt())), style = numberStyle(15.sp), maxLines = 1)
            } else {
                Text(
                    text = stringResource(R.string.support_bodyweight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (under != null) {
                Text(
                    text = work,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** What the other activities were: walks, hikes, yoga, cardio, the rest. */
@Composable
private fun KindsCard(kinds: List<Triple<OtherKind, Int, Long>>) {
    CollapsibleCard(
        title = stringResource(R.string.support_kinds_title),
        summary = kinds.firstOrNull()?.let { stringResource(it.first.labelRes()) },
        initiallyExpanded = true,
    ) {
        kinds.forEach { (kind, sessions, seconds) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = stringResource(kind.labelRes()), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.support_kind_count, sessions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = Formatters.duration(seconds), style = numberStyle(15.sp))
            }
        }
    }
}
