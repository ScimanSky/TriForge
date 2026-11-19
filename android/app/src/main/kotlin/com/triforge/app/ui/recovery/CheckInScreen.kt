package com.triforge.app.ui.recovery

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.triforge.app.R
import com.triforge.app.ui.common.BackButton
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.TriChip
import com.triforge.domain.CheckIn
import java.time.LocalDate

/**
 * The 30-second morning check-in: four Hooper-style items plus motivation,
 * each from 1 (very good) to 7 (very bad), and an "I feel ill" switch.
 */
@Composable
fun CheckInScreen(
    initial: CheckIn?,
    onSave: (CheckIn) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sleep by rememberSaveable { mutableIntStateOf(initial?.sleepQuality ?: 0) }
    var fatigue by rememberSaveable { mutableIntStateOf(initial?.fatigue ?: 0) }
    var soreness by rememberSaveable { mutableIntStateOf(initial?.soreness ?: 0) }
    var stress by rememberSaveable { mutableIntStateOf(initial?.stress ?: 0) }
    var motivation by rememberSaveable { mutableIntStateOf(initial?.motivation ?: 0) }
    var sick by rememberSaveable { mutableStateOf(initial?.sick ?: false) }
    val complete = listOf(sleep, fatigue, soreness, stress, motivation).all { it in CheckIn.MIN..CheckIn.MAX }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackButton(onBack)
            Text(
                text = stringResource(R.string.checkin_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            text = stringResource(R.string.checkin_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TriCard {
            ScaleQuestion(R.string.checkin_sleep, sleep) { sleep = it }
            ScaleQuestion(R.string.checkin_fatigue, fatigue) { fatigue = it }
            ScaleQuestion(R.string.checkin_soreness, soreness) { soreness = it }
            ScaleQuestion(R.string.checkin_stress, stress) { stress = it }
            ScaleQuestion(R.string.checkin_motivation, motivation) { motivation = it }
        }
        TriCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = sick, role = Role.Switch, onValueChange = { sick = it }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.checkin_sick), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.checkin_sick_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The row handles the tap, so the switch itself stays passive.
                Switch(checked = sick, onCheckedChange = null)
            }
        }
        PrimaryButton(
            text = stringResource(R.string.checkin_save),
            enabled = complete,
            onClick = {
                onSave(CheckIn(LocalDate.now(), sleep, fatigue, soreness, stress, motivation, sick))
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ScaleQuestion(@StringRes label: Int, value: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (CheckIn.MIN..CheckIn.MAX).forEach { score ->
                TriChip(
                    label = score.toString(),
                    selected = value == score,
                    onClick = { onChange(score) },
                    modifier = Modifier.weight(1f),
                    horizontalPadding = 0.dp,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.checkin_scale_low),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.checkin_scale_high),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
