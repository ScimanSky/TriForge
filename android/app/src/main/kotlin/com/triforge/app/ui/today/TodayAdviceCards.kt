package com.triforge.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.coach.CoachPalette
import com.triforge.app.ui.common.IconBox
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.SportIconBox
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.detail.GearColor
import com.triforge.app.ui.detail.QuietButton
import com.triforge.app.ui.detail.gearGuessTitle
import com.triforge.app.ui.detail.gearGuessWhy
import com.triforge.app.ui.detail.proposal
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.domain.Activity
import com.triforge.domain.AthleteProfile
import com.triforge.domain.CssCheck
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.SwimThreshold
import com.triforge.domain.coach.DayLeft
import com.triforge.domain.coach.KeyMeal
import com.triforge.domain.coach.PlannedSession
import com.triforge.domain.coach.TodayAdvice
import com.triforge.domain.coach.TodayCard
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Board 3d: what to do today, in a card no bigger than what it has to say.
 * Today first: the sessions still to do, or in a word how the day stands
 * («Giorno libero», «Allenamento fatto»), and one sentence that puts
 * recovery and form together. Tomorrow comes under its own label, in a line
 * or in full once today has nothing left, so the two days are never mixed.
 */
@Composable
internal fun TodayAdviceCard(card: TodayCard, profile: AthleteProfile, onOpenCoach: () -> Unit, onOpenNutri: () -> Unit) {
    TriCard(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DayLabel(R.string.today_card_today, card.time)
                val title = when (card.left) {
                    DayLeft.FREE -> R.string.today_card_free
                    DayLeft.DONE -> R.string.today_card_done
                    // With a session to do the rows say it; with one not known as done, the line below does.
                    DayLeft.SESSION, DayLeft.OPEN -> null
                }
                title?.let { Text(stringResource(it), style = MaterialTheme.typography.titleSmall) }
            }
            card.sessions.forEach { session -> SessionRow(session, profile, mainSet = true, onOpen = onOpenCoach) }
            // A session of today whose time is gone: the card does not skip it in silence.
            card.earlier?.let { earlier -> Note(earlier) }
            if (card.sentence.isNotBlank()) {
                Text(card.sentence, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD5DAE1))
            }
            if (card.sessions.isNotEmpty()) card.meal?.let { MealRow(it, onOpenNutri) }
            if (card.tomorrow.isNotEmpty()) {
                HorizontalDivider(color = TriColors.border)
                DayLabel(R.string.today_card_tomorrow, card.tomorrowTime)
                card.tomorrow.forEach { session -> SessionRow(session, profile, mainSet = false, onOpen = onOpenCoach) }
                card.meal?.let { MealRow(it, onOpenNutri) }
            }
            card.next?.let { next -> Note(next) }
        }
    }
}

/** «OGGI», «DOMANI»: the day a part of the card is about, with the usual time of its first session. */
@Composable
private fun DayLabel(day: Int, time: LocalTime?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(day).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = CoachPalette.Coach.softText,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        time?.let {
            Text(
                stringResource(R.string.today_card_at, "%d:%02d".format(it.hour, it.minute)),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A session in two or three lines; the main set only for today's, tomorrow's stays short. */
@Composable
private fun SessionRow(session: PlannedSession, profile: AthleteProfile, mainSet: Boolean, onOpen: () -> Unit) {
    val sport = session.sport ?: return
    InnerRow(onOpen) {
        SportIconBox(sport)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleSmall)
            val line = TodayAdvice.summary(session, profile) + if (session.own) " · " + stringResource(R.string.today_card_own) else ""
            Text(line, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mainSet) TodayAdvice.mainSet(session, profile)?.let { set ->
                Text(stringResource(R.string.today_card_main_set, set), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Chevron()
    }
}

@Composable
private fun MealRow(meal: KeyMeal, onOpen: () -> Unit) {
    InnerRow(onOpen) {
        IconBox(background = CoachPalette.Nutri.soft) {
            Icon(TriIcons.Nutrition, contentDescription = null, tint = CoachPalette.Nutri.softText, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.today_card_meal), fontSize = 12.sp, lineHeight = 16.sp, color = CoachPalette.Nutri.softText)
            Text(meal.title, style = MaterialTheme.typography.titleSmall)
            Text(meal.text, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Chevron()
    }
}

/** A row of the card that opens something: flat, with no box of its own, so the card stays light. */
@Composable
private fun InnerRow(onOpen: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Chevron() {
    Icon(TriIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
}

/** The swim threshold does not fit the swims: said on Oggi until it is checked or put off. */
@Composable
internal fun CssNoticeCard(check: CssCheck, onVerify: () -> Unit) {
    TriCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A1D14)), contentAlignment = Alignment.Center) {
                Icon(TriIcons.Info, contentDescription = null, tint = TriColors.accentText, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.css_notice_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(
                    cssWhy(check) + " " + stringResource(R.string.css_notice_effect),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        PrimaryButton(stringResource(R.string.css_verify), onVerify, compact = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

/**
 * Board 5e: the question about the latest pool swim, [daysAgo] days ago. No
 * watch records fins, pull buoy and paddles: asked once, while the athlete
 * remembers, with «no» one touch away.
 */
@Composable
internal fun SwimGearQuestionCard(swim: Activity, daysAgo: Long, onYes: () -> Unit, onNo: () -> Unit) {
    val kicker = when (daysAgo) {
        0L -> R.string.gear_question_kicker_today
        1L -> R.string.gear_question_kicker_yesterday
        else -> R.string.gear_question_kicker_before
    }
    // Board 5f: when some repeats look swum with equipment the card says so, and the page opens with them ticked.
    val guess = remember(swim.id, swim.laps) { proposal(swim) }
    TriCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(kicker).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = GearColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                listOfNotNull(
                    Formatters.distance(Sport.SWIM, swim.distanceMeters),
                    Formatters.pace(Sport.SWIM, swim.distanceMeters, swim.paceSec),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            guess?.let { gearGuessTitle(it) } ?: stringResource(R.string.gear_question_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        Text(
            guess?.let { gearGuessWhy(it) } ?: stringResource(R.string.gear_question_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton(
                stringResource(if (guess != null) R.string.gear_check else R.string.gear_question_yes),
                onYes,
                modifier = Modifier.weight(1f),
                compact = true,
            )
            QuietButton(stringResource(R.string.gear_question_no), onNo)
        }
    }
}

/** What the swims say, in a sentence: most of the metres at threshold and above, or whole swims faster than it. */
@Composable
internal fun cssWhy(check: CssCheck): String =
    if (check.highShare > SwimThreshold.HIGH_SHARE) {
        stringResource(R.string.css_why_share, (check.highShare * 100).roundToInt())
    } else {
        stringResource(R.string.css_why_faster, check.fasterSessions)
    }

internal fun swimPace(secPer100: Int): String = Formatters.minutesAndSeconds(secPer100.toDouble())
