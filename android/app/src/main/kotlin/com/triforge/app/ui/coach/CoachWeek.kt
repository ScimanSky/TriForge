package com.triforge.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.AthleteProfile
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.coach.CoachTable
import com.triforge.domain.coach.PlannedDay
import com.triforge.domain.coach.PlannedSession
import com.triforge.domain.coach.SportCoachPrompts
import com.triforge.domain.coach.TableNote
import com.triforge.domain.coach.TableVoice
import com.triforge.domain.coach.TriRegia
import com.triforge.domain.coach.WeekPlan
import com.triforge.domain.coach.WorkoutBlock
import com.triforge.domain.coach.Workouts
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)
private val sessionBackground = Color(0xFF1D2127)
private val restText = Color(0xFF9AA3AF)

/** Which session of the week is open, kept across rotations: "2026-11-28#0". */
fun sessionKey(date: LocalDate, index: Int): String = "$date#$index"

fun WeekPlan.session(key: String): Pair<PlannedDay, PlannedSession>? {
    val date = key.substringBefore('#')
    val index = key.substringAfter('#', "").toIntOrNull() ?: return null
    val day = days.firstOrNull { it.date.toString() == date } ?: return null
    return day.sessions.getOrNull(index)?.let { day to it }
}

/** "Corsa · Ripetute" when the coach named a focus, else the session's own title. */
fun sessionHeading(session: PlannedSession): String {
    val sport = session.sport?.takeIf { it in TriRegia.SPORTS }
    return if (sport != null && session.focus.isNotBlank() && session.steps.isNotEmpty()) "${TriRegia.sportName(sport)} · ${session.focus}" else session.title
}

/** Whether the session has more to show than its row. */
fun PlannedSession.hasDetail(): Boolean = steps.isNotEmpty() || details.isNotBlank() || why.isNotBlank()

/** Board 6c, top card: the week by sport, against the hours and the hard sessions the regia allows. */
@Composable
fun WeekSummary(plan: WeekPlan) {
    val totals = plan.totals()
    val sports = TriRegia.SPORTS.sortedByDescending { totals[it]?.second ?: 0 }
    val minutes = sports.sumOf { totals[it]?.second ?: 0 }
    val hard = plan.days.sumOf { day -> day.sessions.count(TriRegia::isHard) }
    val end = plan.weekStart.plusDays(6)
    TriCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${plan.weekStart.dayOfMonth} – ${end.format(dayMonth)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (plan.phase.isNotBlank()) {
                Text(plan.phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (minutes > 0) {
            Row(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                sports.forEach { sport ->
                    val share = totals[sport]?.second ?: 0
                    if (share > 0) Box(Modifier.weight(share.toFloat()).height(12.dp).background(SportColors.of(sport)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sports.forEach { sport ->
                val (count, sportMinutes) = totals[sport] ?: (0 to 0)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(SportColors.of(sport))
                        Text(TriRegia.sportName(sport), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Formatters.duration(sportMinutes * 60L), style = numberStyle(20.sp), modifier = Modifier.padding(top = 4.dp))
                    Text(
                        if (count == 1) stringResource(R.string.coach_sessions_one) else stringResource(R.string.coach_sessions_many, count),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(TriColors.border))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val total = Formatters.duration(minutes * 60L)
            val available = plan.availableMin
            Text(
                if (available != null) stringResource(R.string.coach_week_available, total, Formatters.duration(available * 60L))
                else stringResource(R.string.coach_week_total, total),
                fontSize = 13.sp,
                color = Color(0xFFC9CED6),
            )
            val maxHard = plan.maxHard
            Text(
                if (maxHard != null) stringResource(R.string.coach_week_hard_of, hard, maxHard) else stringResource(R.string.coach_week_hard, hard),
                fontSize = 13.sp,
                color = Color(0xFFC9CED6),
            )
        }
    }
}

/** Board 6c, the seven days in one card: every session in its coach's colour. */
@Composable
fun WeekDays(plan: WeekPlan, profile: AthleteProfile, onOpen: (String) -> Unit) {
    val today = LocalDate.now()
    TriCard(padding = 0.dp) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
            plan.days.forEachIndexed { position, day ->
                if (position > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TriColors.border))
                val past = day.date.isBefore(today)
                val isToday = day.date == today
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).alpha(if (past) 0.7f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.width(44.dp).padding(top = 6.dp)) {
                        Text(
                            day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isToday) TriColors.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            day.date.dayOfMonth.toString(),
                            style = numberStyle(18.sp),
                            color = if (isToday) TriColors.accentText else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val sessions = day.sessions.withIndex().filter { it.value.sport != null || it.value.durationMin > 0 }
                        if (sessions.isEmpty()) {
                            RestRow(stringResource(if (past && plan.fromTable) R.string.coach_nothing_done else R.string.coach_rest))
                        } else {
                            sessions.forEach { (index, session) ->
                                SessionRow(session, profile, onOpen = { onOpen(sessionKey(day.date, index)) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestRow(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .drawBehind {
                drawRoundRect(
                    color = TriColors.borderStrong,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = restText)
    }
}

@Composable
private fun SessionRow(session: PlannedSession, profile: AthleteProfile, onOpen: () -> Unit) {
    val sport = session.sport
    // On a day already gone the table shows what the athlete did.
    val done = session.focus == CoachTable.DONE
    val shape = RoundedCornerShape(14.dp)
    val subtitle = when {
        done -> stringResource(R.string.coach_done)
        sport != null && sport in TriRegia.SPORTS && session.steps.isNotEmpty() ->
            listOf(SportCoachPrompts.coachName(sport), sessionGist(session, profile)).filter { it.isNotBlank() }.joinToString(" · ")
        else -> ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(sessionBackground)
            .clickable(enabled = session.hasDetail(), role = Role.Button, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dot(sport?.let(SportColors::of) ?: Color(0xFF5E6672))
        Column(Modifier.weight(1f)) {
            Text(
                if (done) session.title else sessionHeading(session),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (TriRegia.isHard(session)) HardPill()
        if (session.durationMin > 0) {
            Text(Formatters.duration(session.durationMin * 60L), style = numberStyle(13.sp), color = Color(0xFFC9CED6))
        }
    }
}

/** The one thing to know about the workout at a glance: metres in the pool, the main set, or the zone. */
private fun sessionGist(session: PlannedSession, profile: AthleteProfile): String {
    if (session.sport == Sport.SWIM) {
        Workouts.distanceM(session, profile)?.let { return Formatters.distance(Sport.SWIM, it.toDouble()) }
    }
    val main = session.steps.filter { it.block == WorkoutBlock.MAIN }
    val set = main.filter { it.reps > 1 }.maxByOrNull { it.reps * ((it.durationSec ?: 0) + (it.distanceM ?: 0)) }
    if (set != null) {
        val size = set.distanceM?.let { "$it m" } ?: Workouts.clock(set.durationSec ?: 0)
        return "${set.reps} × $size ${set.zone}"
    }
    return Workouts.zoneMinutes(session, profile).maxByOrNull { it.value }?.key.orEmpty()
}

@Composable
fun HardPill() {
    Text(
        stringResource(R.string.coach_hard),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = TriColors.accentText,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A1D14)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(color))
}

/** Board 6c, the minutes of the table: who said what, and how many rounds it took. */
@Composable
fun TableCard(plan: WeekPlan) {
    var open by rememberSaveable { mutableStateOf(true) }
    val title = stringResource(R.string.coach_table_title)
    TriCard(padding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .heightIn(min = 56.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
            Icon(
                TriIcons.ChevronDown,
                contentDescription = stringResource(if (open) R.string.coach_table_close else R.string.coach_table_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(if (open) 180f else 0f),
            )
        }
        if (open) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                plan.table.forEach { note -> TableLine(note) }
                val settledByApp = plan.table.any { it.voice == TableVoice.REGIA }
                Text(
                    when {
                        settledByApp -> stringResource(R.string.coach_table_settled, plan.rounds)
                        plan.rounds == 1 -> stringResource(R.string.coach_table_one_round)
                        else -> stringResource(R.string.coach_table_rounds, plan.rounds)
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TableLine(note: TableNote) {
    val (name, color) = when (note.voice) {
        TableVoice.SWIM -> SportCoachPrompts.coachName(Sport.SWIM) to SportColors.swim
        TableVoice.BIKE -> SportCoachPrompts.coachName(Sport.BIKE) to SportColors.bike
        TableVoice.RUN -> SportCoachPrompts.coachName(Sport.RUN) to SportColors.run
        TableVoice.NUTRI -> stringResource(R.string.nutri_title) to CoachPalette.Nutri.accent
        TableVoice.REGIA -> stringResource(R.string.coach_table_regia) to CoachPalette.Coach.accent
    }
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$name: ${note.text}" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dot(color, Modifier.padding(top = 6.dp))
        Column(Modifier.weight(1f).clearAndSetSemantics {}) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(note.text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE6E9EE))
        }
    }
}
