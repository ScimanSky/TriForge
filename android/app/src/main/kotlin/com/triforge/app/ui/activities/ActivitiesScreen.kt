package com.triforge.app.ui.activities

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.triforge.app.R
import com.triforge.app.ui.TrainingState
import com.triforge.app.ui.common.ActivityRow
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.common.labelRes
import com.triforge.domain.Activity
import com.triforge.domain.Sport
import com.triforge.domain.TrainingStats

@Composable
fun ActivitiesScreen(state: TrainingState, modifier: Modifier = Modifier) {
    when (state) {
        TrainingState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        TrainingState.Failed -> CenteredMessage(stringResource(R.string.load_error), modifier = modifier)

        is TrainingState.Loaded -> ActivityList(state.activities, modifier)
    }
}

@Composable
private fun ActivityList(activities: List<Activity>, modifier: Modifier = Modifier) {
    // The selected sport's name, or null for all. A String survives rotation.
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = remember(activities, selected) {
        if (selected == null) activities else activities.filter { it.sport.name == selected }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { selected = null },
                label = { Text(stringResource(R.string.activities_all)) },
            )
            TrainingStats.triathlonSports.forEach { sport: Sport ->
                FilterChip(
                    selected = selected == sport.name,
                    onClick = { selected = sport.name },
                    label = { Text(stringResource(sport.labelRes())) },
                )
            }
        }

        if (shown.isEmpty()) {
            CenteredMessage(stringResource(R.string.activities_empty))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { ActivityRow(it) }
            }
        }
    }
}
