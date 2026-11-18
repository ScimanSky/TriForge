package com.triforge.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.triforge.app.R
import com.triforge.app.ui.TrainingState
import com.triforge.app.ui.common.ActivityRow
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.common.labelRes
import com.triforge.app.ui.theme.SportColors
import com.triforge.domain.Activity
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.SportTotals
import com.triforge.domain.TrainingStats
import com.triforge.domain.WeekSummary
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TREND_WEEKS = 8
private const val RECENT_COUNT = 5

@Composable
fun TodayScreen(state: TrainingState, modifier: Modifier = Modifier) {
    when (state) {
        TrainingState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        TrainingState.Failed -> CenteredMessage(stringResource(R.string.load_error), modifier = modifier)

        is TrainingState.Loaded -> Dashboard(state.activities, modifier)
    }
}

@Composable
private fun Dashboard(activities: List<Activity>, modifier: Modifier = Modifier) {
    val trend = remember(activities) {
        TrainingStats.weeklyTrend(activities, TREND_WEEKS, LocalDate.now(), ZoneId.systemDefault())
    }
    val thisWeek = trend.last()
    val lastWeek = trend[trend.size - 2]

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.today_this_week),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        R.string.today_week_total,
                        thisWeek.sessions,
                        Formatters.duration(thisWeek.durationSec),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(TrainingStats.triathlonSports, key = { it.name }) { sport ->
            SportCard(current = thisWeek.of(sport), previous = lastWeek.of(sport))
        }

        item { TrendCard(trend) }

        if (activities.isEmpty()) {
            item {
                CenteredMessage(
                    title = stringResource(R.string.today_empty_title),
                    body = stringResource(R.string.today_empty_body),
                )
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.today_recent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(activities.take(RECENT_COUNT), key = { it.id }) { ActivityRow(it) }
        }
    }
}

@Composable
private fun SportCard(current: SportTotals, previous: SportTotals) {
    val color = SportColors.of(current.sport)
    val change = TrainingStats.percentChange(current.distanceMeters, previous.distanceMeters)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(current.sport.labelRes()),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = Formatters.distance(current.sport, current.distanceMeters),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = listOfNotNull(
                stringResource(R.string.today_sessions, current.sessions),
                Formatters.duration(current.durationSec),
                Formatters.pace(current.sport, current.distanceMeters, current.durationSec),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (change != null) {
            Text(
                text = stringResource(R.string.today_vs_last_week, if (change > 0) "+$change" else "$change"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val weekLabel = DateTimeFormatter.ofPattern("d/M", Locale.ITALY)

/** Stacked bars of weekly training time, one segment per discipline. */
@Composable
private fun TrendCard(trend: List<WeekSummary>) {
    val sports = TrainingStats.triathlonSports + Sport.OTHER
    val peak = trend.maxOf { it.durationSec }.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.today_trend_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.today_trend_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            trend.forEach { week ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // Empty space above the bar, then one segment per sport.
                    val idle = (peak - week.durationSec).toFloat()
                    if (idle > 0f) Box(Modifier.weight(idle))
                    sports.forEach { sport ->
                        val seconds = week.of(sport).durationSec.toFloat()
                        if (seconds > 0f) {
                            Box(
                                Modifier
                                    .weight(seconds)
                                    .fillMaxWidth()
                                    .background(SportColors.of(sport)),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            trend.forEach { week ->
                Text(
                    text = weekLabel.format(week.weekStart),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
