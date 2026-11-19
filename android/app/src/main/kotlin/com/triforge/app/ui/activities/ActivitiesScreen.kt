package com.triforge.app.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.unit.dp
import com.triforge.app.R
import com.triforge.app.data.GarminStatus
import com.triforge.app.ui.TrainingState
import com.triforge.app.ui.common.ActivityRow
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.common.LegendDot
import com.triforge.app.ui.common.LoadingBox
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.StackedWeekBars
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.labelRes
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.domain.Activity
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.TrainingStats
import com.triforge.domain.groupedByWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TREND_WEEKS = 8

private val weekDate = DateTimeFormatter.ofPattern("d MMM", Locale.ITALY)
private val syncDate = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ITALY)

@Composable
fun ActivitiesScreen(
    state: TrainingState,
    garmin: GarminStatus,
    onOpenActivity: (String) -> Unit,
    modifier: Modifier = Modifier,
    cssSecPer100: Int? = null,
    onUseCss: (Int) -> Unit = {},
    /** Each activity's load in the app's scale, by activity id; empty while it is worked out. */
    loads: Map<String, Int> = emptyMap(),
) {
    // The selected sport's name, or null for all. A String survives rotation.
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val sport = Sport.entries.firstOrNull { it.name == selected }

    Column(modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text(
                text = stringResource(R.string.nav_activities),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = syncLabel(garmin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SportTabs(selected = sport, onSelect = { selected = it?.name })

        when (state) {
            TrainingState.Loading -> LoadingBox()

            TrainingState.Failed -> CenteredMessage(stringResource(R.string.load_error))

            is TrainingState.Loaded ->
                when {
                    sport == null -> AllActivities(state.activities, loads, onOpenActivity)
                    sport in TrainingStats.triathlonSports -> SportTab(state.activities, sport, onOpenActivity, cssSecPer100, onUseCss)
                    else -> SupportTab(state.activities, sport, loads, onOpenActivity)
                }
        }
    }
}

@Composable
private fun syncLabel(garmin: GarminStatus): String {
    val lastSync = garmin.lastSyncAt
    return when {
        garmin.needsReconnect -> stringResource(R.string.activities_garmin_reconnect)
        !garmin.connected -> stringResource(R.string.activities_garmin_off)
        lastSync == null -> stringResource(R.string.garmin_never_synced)
        else -> {
            val minutes = Duration.between(lastSync, Instant.now()).toMinutes()
            when {
                minutes < 1 -> stringResource(R.string.activities_synced_now)
                minutes < 60 -> stringResource(R.string.activities_synced_minutes, minutes.toInt())
                minutes < 24 * 60 -> stringResource(R.string.activities_synced_hours, (minutes / 60).toInt())
                else -> stringResource(
                    R.string.activities_synced_on,
                    syncDate.format(lastSync.atZone(ZoneId.systemDefault())),
                )
            }
        }
    }
}

/**
 * Tutte, Nuoto, Bici, Corsa, Palestra, Altro: each sport's tab takes that
 * sport's color. Six tabs do not fit a phone, so the row scrolls.
 */
@Composable
private fun SportTabs(selected: Sport?, onSelect: (Sport?) -> Unit) {
    val tabs: List<Sport?> = listOf(null) + TrainingStats.allSports

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp)
                .selectableGroup(),
        ) {
            tabs.forEach { sport ->
                val isSelected = sport == selected
                val color = if (sport == null) MaterialTheme.colorScheme.primary else SportColors.of(sport)
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .height(48.dp)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(sport) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(sport?.labelRes() ?: R.string.activities_all),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (isSelected) color else Color.Transparent),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TriColors.border),
        )
    }
}

@Composable
private fun AllActivities(activities: List<Activity>, loads: Map<String, Int>, onOpenActivity: (String) -> Unit) {
    if (activities.isEmpty()) {
        CenteredMessage(
            title = stringResource(R.string.today_empty_title),
            body = stringResource(R.string.activities_empty),
        )
        return
    }

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val trend = remember(activities, today) { TrainingStats.weeklyTrend(activities, TREND_WEEKS, today, zone) }
    val weeks = remember(activities) { activities.groupedByWeek(zone) }
    val thisWeek = TrainingStats.weekStart(today)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "trend") {
            TriCard {
                CardHeader(
                    title = stringResource(R.string.activities_hours_title),
                    trailing = stringResource(R.string.period_last_weeks, TREND_WEEKS),
                )
                StackedWeekBars(
                    trend = trend,
                    description = stringResource(
                        R.string.activities_hours_chart,
                        TREND_WEEKS,
                        Formatters.duration(trend.last().durationSec),
                    ),
                    modifier = Modifier.padding(top = 14.dp),
                )
                // Only what the weeks on the chart hold: a legend entry without a bar is noise.
                val shown = TrainingStats.allSports.filter { sport ->
                    sport in TrainingStats.triathlonSports || trend.any { it.of(sport).sessions > 0 }
                }
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    shown.forEach { sport ->
                        LegendDot(SportColors.of(sport), stringResource(sport.labelRes()))
                    }
                }
            }
        }

        weeks.forEach { (weekStart, inWeek) ->
            item(key = "week-$weekStart") {
                SectionLabel(
                    text = when (weekStart) {
                        thisWeek -> stringResource(R.string.today_this_week)
                        thisWeek.minusWeeks(1) -> stringResource(R.string.activities_last_week)
                        else -> stringResource(R.string.activities_week_of, weekDate.format(weekStart))
                    },
                    trailing = stringResource(
                        R.string.today_week_total,
                        Formatters.duration(inWeek.sumOf { it.durationSec }),
                        inWeek.size,
                    ),
                )
            }
            items(inWeek, key = { it.id }) { activity ->
                ActivityRow(activity, onClick = { onOpenActivity(activity.id) }, load = loads[activity.id])
            }
        }
    }
}
