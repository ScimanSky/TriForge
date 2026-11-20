package com.triforge.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.common.ScreenTopBar
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.AthleteProfile
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.coach.Intensity
import com.triforge.domain.coach.PlannedDay
import com.triforge.domain.coach.PlannedSession
import com.triforge.domain.coach.SportCoachPrompts
import com.triforge.domain.coach.TriRegia
import com.triforge.domain.coach.WorkoutStep
import com.triforge.domain.coach.Workouts
import com.triforge.domain.coach.Zones
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

/**
 * The colour of a zone on the bar: grey for recovery, cold for easy, warm
 * for hard. The swim has two zones more than bike and run, above the
 * threshold: each has its own colour, so that C1 is not mistaken for B2.
 */
private fun zoneColor(zone: String): Color = when (zone) {
    "C1" -> Color(0xFFFF5C64)
    "C2" -> Color(0xFFE0457B)
    "C3" -> Color(0xFFB45AD6)
    else -> when (Zones.level(zone)) {
        1 -> Color(0xFF5B6472)
        2 -> Color(0xFF4CC2FF)
        3 -> Color(0xFF7FE3B0)
        4 -> Color(0xFFFF5B2E)
        else -> Color(0xFFFF5C64)
    }
}

/** Background and text of a zone's chip. */
private fun zoneChip(zone: String): Pair<Color, Color> = when (Zones.level(zone)) {
    1 -> Color(0xFF262B33) to Color(0xFFC9CED6)
    2 -> Color(0xFF10273A) to Color(0xFF8FD8FF)
    3 -> Color(0xFF12291F) to Color(0xFFA8F0CB)
    else -> Color(0xFF3A1D14) to Color(0xFFFFB59C)
}

/**
 * Board 6e: one session as a workout. The coach wrote the steps; pace, watts,
 * heart rate and the totals come from the athlete's zones.
 */
@Composable
fun SessionDetail(
    day: PlannedDay,
    session: PlannedSession,
    profile: AthleteProfile,
    onBack: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sport = session.sport?.takeIf { it in TriRegia.SPORTS }
    Column(modifier.fillMaxSize()) {
        ScreenTopBar(title = day.date.format(fullDate).replaceFirstChar { it.uppercase() }, onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    // The athlete's own sessions say whose they are instead of naming a coach.
                    val labelSport = if (session.own) session.sport else sport
                    if (labelSport != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dot(SportColors.of(labelSport))
                            Text(
                                if (session.own) ownLabel(session) else SportCoachPrompts.coachName(labelSport),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SportColors.of(labelSport),
                            )
                        }
                    }
                    Text(
                        sessionHeading(session),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 6.dp).semantics { heading() },
                    )
                    if (sessionHeading(session) != session.title) {
                        Text(
                            session.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            item { Stats(session, sport, profile) }
            if (sport != null && session.steps.isNotEmpty()) {
                item { ZoneTime(session, sport, profile) }
                item { Steps(Workouts.withTargets(session, profile).steps) }
            } else if (session.details.isNotBlank()) {
                item {
                    TriCard {
                        Text(session.details, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE6E9EE))
                    }
                }
            }
            if (session.why.isNotBlank()) {
                item {
                    HighlightCard(stringResource(R.string.coach_session_why), CoachPalette.Coach) {
                        Text(session.why, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE6E9EE))
                    }
                }
            }
            if (sport != null && session.steps.isNotEmpty()) {
                item {
                    Text(
                        targetsNote(session, sport, profile),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            item {
                SecondaryButton(
                    if (sport != null) stringResource(R.string.coach_ask_coach, SportCoachPrompts.coachName(sport)) else stringResource(R.string.coach_ask_change),
                    onAsk,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Stats(session: PlannedSession, sport: Sport?, profile: AthleteProfile) {
    val hard = TriRegia.isHard(session)
    val distance = sport?.let { Workouts.distanceM(session, profile) }
    TriCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stringResource(R.string.coach_session_duration), Formatters.duration(session.durationMin * 60L), Modifier.weight(1f))
            if (sport != null && distance != null) {
                // Metres are written by the coach in the pool and worked out from the pace on the road.
                val text = Formatters.distance(sport, distance.toDouble())
                Stat(stringResource(R.string.coach_session_distance), if (sport == Sport.SWIM) text else "≈ $text", Modifier.weight(1f))
            }
            Stat(
                stringResource(R.string.coach_session_intensity),
                stringResource(
                    when {
                        hard -> R.string.coach_hard
                        session.intensity == Intensity.TEMPO -> R.string.coach_medium
                        else -> R.string.coach_easy
                    },
                ),
                Modifier.weight(1f),
                color = if (hard) TriColors.accentText else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = numberStyle(22.sp), color = color, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ZoneTime(session: PlannedSession, sport: Sport, profile: AthleteProfile) {
    val zones = Workouts.zoneMinutes(session, profile)
    if (zones.isEmpty()) return
    val spoken = zones.map { (zone, minutes) -> stringResource(R.string.coach_zone_spoken, zone, minutes) }.joinToString(", ")
    TriCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.coach_zone_time),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                stringResource(R.string.coach_zone_time_from, TriRegia.sportName(sport).lowercase()),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
            Row(
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                zones.forEach { (zone, minutes) -> Box(Modifier.weight(minutes.toFloat()).height(14.dp).background(zoneColor(zone))) }
            }
            Row(Modifier.padding(top = 10.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                zones.forEach { (zone, minutes) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(zoneColor(zone))
                        Text("$zone $minutes′", fontSize = 12.sp, color = Color(0xFFC9CED6))
                    }
                }
            }
        }
    }
}

@Composable
private fun Steps(steps: List<WorkoutStep>) {
    TriCard(padding = 0.dp) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 8.dp)) {
            steps.forEachIndexed { index, step ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TriColors.border))
                val firstOfBlock = index == 0 || steps[index - 1].block != step.block
                Column(Modifier.padding(vertical = 14.dp)) {
                    if (firstOfBlock) {
                        Text(
                            step.block.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
                        )
                    }
                    StepRow(step)
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: WorkoutStep) {
    val size = step.distanceM?.let { Formatters.distance(Sport.SWIM, it.toDouble()) } ?: Workouts.clock(step.durationSec ?: 0)
    val head = listOf(if (step.reps > 1) "${step.reps} × $size" else size, step.text).filter { it.isNotBlank() }.joinToString(" ")
    val recovery = if (step.recoverySec > 0) stringResource(R.string.coach_step_recovery, Workouts.clock(step.recoverySec)) else ""
    val below = listOf(step.target, recovery).filter { it.isNotBlank() }.joinToString(" · ")
    val (chipBackground, chipText) = zoneChip(step.zone)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(head, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (below.isNotBlank()) {
                Text(below, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
            }
            if (step.note.isNotBlank()) {
                Text(step.note, fontSize = 12.sp, color = TriColors.textDim, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Text(
            step.zone,
            style = numberStyle(12.sp),
            color = chipText,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(chipBackground)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { contentDescription = "${step.zone}, ${Zones.label(step.zone)}" },
        )
    }
}

/** Where the targets come from, and how much of the session is at threshold or above. */
@Composable
private fun targetsNote(session: PlannedSession, sport: Sport, profile: AthleteProfile): String {
    val threshold = when (sport) {
        Sport.SWIM -> profile.cssSecPer100m?.let { "${Formatters.minutesAndSeconds(it.toDouble())} /100 m" }
        Sport.RUN -> profile.thresholdPaceSecPerKm?.let { "${Formatters.minutesAndSeconds(it.toDouble())} /km" }
        Sport.BIKE -> profile.ftpWatts?.let { "$it W" }
        else -> null
    }
    val source = when {
        threshold != null -> stringResource(R.string.coach_targets_from, TriRegia.sportName(sport).lowercase(), threshold)
        sport != Sport.SWIM && profile.maxHr != null -> stringResource(R.string.coach_targets_hr)
        else -> stringResource(R.string.coach_targets_missing)
    }
    val high = Workouts.highMinutes(session, profile)
    return if (high > 0) source + " " + stringResource(R.string.coach_high_minutes, high) else source
}
