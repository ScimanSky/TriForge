package com.triforge.app.ui.recovery

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.triforge.app.R
import com.triforge.app.ui.theme.TriColors
import com.triforge.domain.ConfidenceLevel
import com.triforge.domain.LoadResponsePattern
import com.triforge.domain.RecoveryComponent
import com.triforge.domain.RecoveryDay
import com.triforge.domain.RecoveryLight
import com.triforge.domain.RecoveryPhase

fun RecoveryLight?.color(): Color = when (this) {
    RecoveryLight.GREEN -> TriColors.good
    RecoveryLight.YELLOW -> TriColors.caution
    RecoveryLight.ORANGE -> TriColors.alert
    RecoveryLight.RED -> TriColors.danger
    null -> TriColors.textDim
}

@StringRes
fun RecoveryLight.labelRes(): Int = when (this) {
    RecoveryLight.GREEN -> R.string.recovery_light_green
    RecoveryLight.YELLOW -> R.string.recovery_light_yellow
    RecoveryLight.ORANGE -> R.string.recovery_light_orange
    RecoveryLight.RED -> R.string.recovery_light_red
}

@StringRes
fun RecoveryComponent.labelRes(): Int = when (this) {
    RecoveryComponent.CHECK_IN -> R.string.recovery_part_check_in
    RecoveryComponent.HRV -> R.string.recovery_part_hrv
    RecoveryComponent.RESTING_HR -> R.string.recovery_part_resting_hr
    RecoveryComponent.SLEEP -> R.string.recovery_part_sleep
    RecoveryComponent.LOAD -> R.string.recovery_part_load
    RecoveryComponent.PERFORMANCE -> R.string.recovery_part_performance
}

@StringRes
fun RecoveryComponent.missingRes(): Int = when (this) {
    RecoveryComponent.CHECK_IN -> R.string.recovery_missing_check_in
    RecoveryComponent.HRV -> R.string.recovery_missing_hrv
    RecoveryComponent.RESTING_HR -> R.string.recovery_missing_resting_hr
    RecoveryComponent.SLEEP -> R.string.recovery_missing_sleep
    RecoveryComponent.LOAD -> R.string.recovery_missing_load
    RecoveryComponent.PERFORMANCE -> R.string.recovery_missing_performance
}

@StringRes
fun ConfidenceLevel.labelRes(): Int = when (this) {
    ConfidenceLevel.HIGH -> R.string.recovery_confidence_high
    ConfidenceLevel.MEDIUM -> R.string.recovery_confidence_medium
    ConfidenceLevel.LOW -> R.string.recovery_confidence_low
}

/**
 * What to tell the athlete today. Descriptive, never a diagnosis, and always
 * with something concrete and reversible to do.
 */
@StringRes
fun RecoveryDay.messageRes(): Int = when {
    phase == RecoveryPhase.NO_DATA -> R.string.recovery_msg_no_data
    pattern == LoadResponsePattern.ILLNESS -> R.string.recovery_msg_illness
    score == null -> R.string.recovery_msg_learning
    level >= 3 -> R.string.recovery_msg_persistent
    pattern == LoadResponsePattern.PERSISTING_AFTER_TAPER -> R.string.recovery_msg_persistent
    pattern == LoadResponsePattern.ATYPICAL_HIGH_HRV -> R.string.recovery_msg_atypical
    pattern == LoadResponsePattern.UNEXPLAINED -> R.string.recovery_msg_unexplained
    pattern == LoadResponsePattern.LOAD_FATIGUE && (level >= 2 || (smoothedScore ?: 0) >= 55) ->
        R.string.recovery_msg_load_fatigue
    pattern == LoadResponsePattern.LOAD_FATIGUE -> R.string.recovery_msg_load_fatigue_light
    pattern == LoadResponsePattern.ABSORBING -> R.string.recovery_msg_absorbing
    else -> R.string.recovery_msg_baseline
}

/** The four colour zones from 0 to 100 with a marker on [score]. */
@Composable
fun RecoveryScale(score: Int, description: String, modifier: Modifier = Modifier) {
    val zones = listOf(
        0f to TriColors.good,
        30f to TriColors.caution,
        55f to TriColors.alert,
        75f to TriColors.danger,
    )
    val markerColor = TriColors.textDim
    val background = TriColors.track
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .semantics { contentDescription = description },
    ) {
        val barHeight = 6.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(background, Offset(0f, top), Size(size.width, barHeight), radius)
        zones.forEachIndexed { index, (from, color) ->
            val to = zones.getOrNull(index + 1)?.first ?: 100f
            val gap = if (index < zones.lastIndex) 2.dp.toPx() else 0f
            drawRoundRect(
                color = color.copy(alpha = 0.55f),
                topLeft = Offset(size.width * from / 100f, top),
                size = Size(size.width * (to - from) / 100f - gap, barHeight),
                cornerRadius = radius,
            )
        }
        val x = size.width * score.coerceIn(0, 100) / 100f
        drawCircle(markerColor, radius = 7.dp.toPx(), center = Offset(x, size.height / 2))
        drawCircle(Color.Black, radius = 3.dp.toPx(), center = Offset(x, size.height / 2))
    }
}
