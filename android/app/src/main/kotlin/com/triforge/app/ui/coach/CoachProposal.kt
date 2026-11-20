package com.triforge.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.data.ChatEntry
import com.triforge.app.data.ProposalStatus
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.domain.AthleteProfile
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.NoticeLine
import com.triforge.domain.coach.PlanUndo
import com.triforge.domain.coach.PlannedSession
import com.triforge.domain.coach.PreviewLine
import com.triforge.domain.coach.ProposalPreview
import com.triforge.domain.coach.TriRegia
import com.triforge.domain.coach.WeekPlan
import com.triforge.domain.coach.WorkoutStep
import com.triforge.domain.coach.Workouts
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val palette = CoachPalette.Coach

/**
 * Board 6h: what a coach proposes in its chat, read before accepting it.
 * Every session with its content, what else changes in the week («Cambia
 * anche») and, once accepted, what was done with «Annulla».
 *
 * [view] is the proposal worked out on the week as it is now; without it
 * (the week is still loading, or the proposal was already answered) the
 * sessions are listed as the coach wrote them. [undo] is the week kept
 * from before this proposal was accepted, while it can be put back.
 */
@Composable
internal fun ProposalCard(
    entry: ChatEntry,
    sport: Sport,
    view: ProposalView?,
    undo: PlanUndo?,
    profile: AthleteProfile,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
    onShowWeek: () -> Unit,
) {
    val proposal = remember(entry.proposal) { entry.proposal?.let { CoachMapper.weekPlanFromFields(it) } } ?: return
    val open = entry.status == ProposalStatus.PENDING || entry.status == ProposalStatus.UNDONE
    when {
        entry.status == ProposalStatus.APPLIED && undo != null -> Applied(undo, enabled, onUndo, onShowWeek)
        open && view != null -> Preview(entry.status, sport, view, profile, enabled, onApply, onDismiss)
        else -> Listed(entry.status, sport, proposal, enabled, onApply, onDismiss)
    }
}

/** Before «Applica»: the coach's sessions one by one, then what changes besides them. */
@Composable
private fun Preview(
    status: ProposalStatus,
    sport: Sport,
    view: ProposalView,
    profile: AthleteProfile,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val undone = status == ProposalStatus.UNDONE
    TriCard(padding = 16.dp) {
        if (undone) {
            Text(stringResource(R.string.coach_undone_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(
                stringResource(R.string.coach_undone_body),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
        Text(
            stringResource(
                when (sport) {
                    Sport.SWIM -> R.string.coach_proposal_heading_swim
                    Sport.BIKE -> R.string.coach_proposal_heading_bike
                    else -> R.string.coach_proposal_heading_run
                },
            ),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.coach_proposal_untouched),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (view.lines.isEmpty()) {
            Text(
                stringResource(R.string.coach_proposal_empty, TriRegia.sportName(sport).lowercase()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        view.lines.forEach { line ->
            Divider(Modifier.padding(top = 12.dp))
            SessionLine(line, sport, profile)
        }
        val nutri = view.nutri
        if (view.also.isNotEmpty() || view.reasons.isNotEmpty() || nutri != null) {
            Divider(Modifier.padding(top = 12.dp))
            Text(
                stringResource(R.string.coach_proposal_also).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = palette.softText,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp).semantics { heading() },
            )
            view.also.forEach { NoticeRow(it) }
            // Why the regia touches them.
            view.reasons.forEach { reason ->
                Text(reason, fontSize = 12.sp, color = TriColors.textDim, modifier = Modifier.padding(start = 18.dp, top = 4.dp))
            }
            if (nutri != null) NutriRow(nutri)
        }
        if (undone) {
            PrimaryButton(
                stringResource(R.string.coach_apply_again),
                onApply,
                enabled = enabled,
                compact = true,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
            ProposalActions(palette, onApply, onDismiss, enabled = enabled, dismiss = stringResource(R.string.coach_no_thanks))
        }
    }
}

/** One session of the proposal: day, title, what happens to it, and its content. */
@Composable
private fun SessionLine(line: PreviewLine, sport: Sport, profile: AthleteProfile) {
    val session = line.session
    val removed = line.mark == PreviewLine.Mark.REMOVED
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                ProposalPreview.day(line.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
            )
            Text(
                sessionHeading(session),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (removed) TriColors.textDim else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (removed) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            MarkTag(line)
        }
        if (removed) return@Column
        val distance = Workouts.distanceM(session, profile)?.takeIf { it > 0 }?.let { Formatters.distance(sport, it.toDouble()) }
        Text(
            listOfNotNull(Formatters.duration(session.durationMin * 60L), distance).joinToString(" · "),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 56.dp),
        )
        // A session that stays as it is needs no more than its line.
        if (line.mark == PreviewLine.Mark.SAME) return@Column
        if (session.steps.isEmpty() && session.details.isNotBlank()) {
            Text(session.details, fontSize = 13.sp, color = Color(0xFFD5DAE1), modifier = Modifier.padding(start = 56.dp))
        }
        session.steps.forEach { step ->
            Text(stepText(step), fontSize = 13.sp, color = Color(0xFFD5DAE1), modifier = Modifier.padding(start = 56.dp))
        }
    }
}

/** «6 × 800 m in Z4 (4:05 – 4:15 /km), recupero 2′». */
@Composable
private fun stepText(step: WorkoutStep): String {
    val size = step.distanceM?.let { Formatters.distance(Sport.SWIM, it.toDouble()) } ?: Workouts.clock(step.durationSec ?: 0)
    val head = listOf(if (step.reps > 1) "${step.reps} × $size" else size, step.text).filter { it.isNotBlank() }.joinToString(" ")
    val zone = if (step.zone.isNotBlank()) " " + stringResource(R.string.coach_step_in_zone, step.zone) else ""
    val target = if (step.target.isNotBlank()) " (${step.target})" else ""
    val recovery = if (step.recoverySec > 0) ", " + stringResource(R.string.coach_step_recovery, Workouts.clock(step.recoverySec)) else ""
    return head + zone + target + recovery
}

@Composable
private fun MarkTag(line: PreviewLine) {
    val text = when (line.mark) {
        PreviewLine.Mark.NEW -> stringResource(R.string.coach_mark_new)
        PreviewLine.Mark.MOVED -> stringResource(
            R.string.coach_mark_moved,
            (line.movedFrom ?: line.date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replace(".", ""),
        )
        PreviewLine.Mark.CHANGED -> stringResource(R.string.coach_mark_changed)
        PreviewLine.Mark.SAME -> stringResource(R.string.coach_mark_same)
        PreviewLine.Mark.REMOVED -> stringResource(R.string.coach_mark_removed)
    }
    val quiet = line.mark == PreviewLine.Mark.SAME || line.mark == PreviewLine.Mark.REMOVED
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (quiet) TriColors.textDim else palette.softText,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (quiet) TriColors.track else palette.soft)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** One line of what changes, with the colour of the sport it is about. */
@Composable
internal fun NoticeRow(line: NoticeLine, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dot(line.sport?.let(SportColors::of) ?: TriColors.borderStrong, Modifier.padding(top = 5.dp))
        Text(line.text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE6E9EE), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NutriRow(text: String) {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dot(CoachPalette.Nutri.accent, Modifier.padding(top = 5.dp))
        Text(
            stringResource(R.string.coach_proposal_nutri, text),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE6E9EE),
            modifier = Modifier.weight(1f),
        )
    }
}

/** After «Applica»: what was done, and one tap to put the week back. */
@Composable
private fun Applied(undo: PlanUndo, enabled: Boolean, onUndo: () -> Unit, onShowWeek: () -> Unit) {
    TriCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(CoachPalette.Nutri.soft), contentAlignment = Alignment.Center) {
                Icon(TriIcons.Check, contentDescription = null, tint = CoachPalette.Nutri.softText, modifier = Modifier.size(16.dp))
            }
            Text(stringResource(R.string.coach_applied_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        }
        if (undo.summary.isNotEmpty() || undo.nutri.isNotBlank()) {
            Text(
                stringResource(R.string.coach_applied_what).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            undo.summary.forEach { NoticeRow(it) }
            if (undo.nutri.isNotBlank()) NutriRow(undo.nutri)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.coach_undo), onUndo, modifier = Modifier.weight(1f), enabled = enabled, compact = true)
            SecondaryButton(stringResource(R.string.coach_show_week), onShowWeek, modifier = Modifier.weight(1f), compact = true)
        }
        Text(
            stringResource(R.string.coach_undo_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** The proposal as the coach wrote it, a line per session: once answered, or while the week loads. */
@Composable
private fun Listed(
    status: ProposalStatus,
    sport: Sport,
    proposal: WeekPlan,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val open = status == ProposalStatus.PENDING || status == ProposalStatus.UNDONE
    TriCard(Modifier.fillMaxWidth(0.88f), padding = 14.dp) {
        Text(
            stringResource(R.string.coach_proposal_title, TriRegia.sportName(sport).lowercase()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        val today = LocalDate.now()
        // An answered proposal is kept whole; an open one can only be about the days still to come.
        proposal.days.filter { !open || !it.date.isBefore(today) }.forEach { day ->
            day.sessions.forEach { session -> ListedSession(day.date, session) }
        }
        when (status) {
            ProposalStatus.PENDING, ProposalStatus.UNDONE -> {
                Text(
                    stringResource(R.string.coach_proposal_note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                ProposalActions(palette, onApply, onDismiss, enabled = enabled)
            }
            ProposalStatus.APPLIED -> StatusLine(stringResource(R.string.coach_applied))
            ProposalStatus.DISMISSED -> StatusLine(stringResource(R.string.coach_dismissed))
            ProposalStatus.NONE -> Spacer(Modifier)
        }
    }
}

@Composable
private fun ListedSession(date: LocalDate, session: PlannedSession) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        Dot(session.sport?.let(SportColors::of) ?: Color(0xFF5E6672))
        Text(
            "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() }} · ${sessionHeading(session)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (TriRegia.isHard(session)) HardPill()
        if (session.durationMin > 0) {
            Text(
                Formatters.duration(session.durationMin * 60L),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColumnScope.Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(TriColors.border))
}
