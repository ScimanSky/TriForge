package com.triforge.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.triforge.app.R
import com.triforge.app.ui.activities.ExerciseRow
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.KeyValueRow
import com.triforge.app.ui.common.StatGrid
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.wholeNumber
import com.triforge.domain.Activity
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.StrengthStats
import com.triforge.domain.coach.NutriEnergy
import kotlin.math.roundToInt

/**
 * A gym session or another activity in full: what the watch measured, the
 * exercises when it counted them, and what the session counts for in the
 * app. There is no pace to read here, so the load comes first.
 */
@Composable
internal fun SupportSections(activity: Activity, load: Int?, weightKg: Double?) {
    SupportHeadline(activity, load)
    if (activity.sport == Sport.STRENGTH) ExercisesCard(activity)
    CountsCard(activity, load, weightKg)
}

@Composable
private fun SupportHeadline(activity: Activity, load: Int?) {
    val stats = buildList {
        add(Formatters.duration(activity.durationSec) to stringResource(R.string.support_detail_duration))
        load?.let { add(it.toString() to stringResource(R.string.support_kpi_load)) }
        activity.calories?.let { add(wholeNumber(it) to stringResource(R.string.metric_calories)) }
        if (activity.distanceMeters > 0) {
            add(Formatters.kilometers(activity.distanceMeters) to stringResource(R.string.unit_km))
        }
        activity.elevationGainMeters?.takeIf { it >= 1 }?.let {
            add(wholeNumber(it.roundToInt()) to stringResource(R.string.detail_elevation))
        }
        activity.avgHr?.let { add(it.toString() to stringResource(R.string.metric_avg_hr)) }
        activity.maxHr?.let { add(it.toString() to stringResource(R.string.metric_max_hr)) }
        activity.sets?.takeIf { it > 0 }?.let { add(it.toString() to stringResource(R.string.support_detail_sets)) }
        activity.reps?.takeIf { it > 0 }?.let { add(wholeNumber(it) to stringResource(R.string.support_detail_reps)) }
    }
    TriCard {
        StatGrid(stats)
        activity.liftedKg?.takeIf { it > 0 }?.let { kg ->
            KeyValueRow(
                label = stringResource(R.string.support_detail_lifted),
                value = stringResource(R.string.unit_kg_value, wholeNumber(kg.roundToInt())),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun ExercisesCard(activity: Activity) {
    val exercises = StrengthStats.merged(activity.exercises)
    CollapsibleCard(
        title = stringResource(R.string.support_detail_exercises),
        summary = exercises.size.takeIf { it > 0 }?.let { stringResource(R.string.support_detail_exercises_count, it) },
        initiallyExpanded = exercises.isNotEmpty(),
    ) {
        exercises.forEach { exercise ->
            ExerciseRow(
                name = StrengthStats.exerciseName(exercise.category),
                under = null,
                sets = exercise.sets,
                reps = exercise.reps ?: 0,
                volumeKg = exercise.volumeKg,
            )
        }
        Text(
            text = stringResource(R.string.support_detail_exercises_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = if (exercises.isEmpty()) 0.dp else 10.dp),
        )
    }
}

/** What the session weighs in the rest of the app: load, form, the coaches, NutriAI. */
@Composable
private fun CountsCard(activity: Activity, load: Int?, weightKg: Double?) {
    val gym = activity.sport == Sport.STRENGTH
    val kcal = weightKg?.takeIf { it > 0 }?.let { weight ->
        NutriEnergy.trainingKcal(NutriEnergy.withDone(emptyList(), listOf(activity)), weight).roundToInt()
    }?.takeIf { it > 0 }

    TriCard {
        CardHeader(title = stringResource(R.string.support_counts_title))
        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CountLine(
                lead = load?.let { stringResource(R.string.support_counts_load_value, it) } ?: stringResource(R.string.support_counts_load),
                text = stringResource(if (gym) R.string.support_counts_load_gym else R.string.support_counts_load_other),
            )
            CountLine(stringResource(R.string.support_counts_form), stringResource(R.string.support_counts_form_text))
            CountLine(stringResource(R.string.support_counts_coach), stringResource(R.string.support_counts_coach_text))
            CountLine(
                lead = stringResource(R.string.support_counts_nutri),
                // One number for the session's energy: the watch's when it gave one, the app's estimate otherwise.
                text = when {
                    (activity.calories ?: 0) > 0 -> stringResource(R.string.support_counts_nutri_watch)
                    kcal != null -> stringResource(R.string.support_counts_nutri_kcal, wholeNumber(kcal))
                    else -> stringResource(R.string.support_counts_nutri_text)
                },
            )
        }
    }
}

@Composable
private fun CountLine(lead: String, text: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(lead) }
            append(" ")
            append(text)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
