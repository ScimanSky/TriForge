package com.triforge.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.triforge.app.ui.common.SportIconBox
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.domain.AthleteProfile
import com.triforge.domain.CssCheck
import com.triforge.domain.Formatters
import com.triforge.domain.SwimThreshold
import com.triforge.domain.coach.KeyMeal
import com.triforge.domain.coach.PlannedSession
import com.triforge.domain.coach.TodayAdvice
import com.triforge.domain.coach.TodayCard
import kotlin.math.roundToInt

/**
 * Board 3c, «Oggi tocca a»: what to do today in one card. One sentence that
 * puts recovery and form together, the session with its main set, and the
 * meal that counts. Once today is done or free it is about tomorrow.
 */
@Composable
internal fun TodayAdviceCard(card: TodayCard, profile: AthleteProfile, onOpenCoach: () -> Unit, onOpenNutri: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val palette = CoachPalette.Coach
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.highlight)
            .border(1.dp, palette.highlightBorder, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (card.tomorrow) R.string.today_card_tomorrow else R.string.today_card_today).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = palette.softText,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            card.time?.let { time ->
                Text(
                    stringResource(R.string.today_card_at, "%d:%02d".format(time.hour, time.minute)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (card.sentence.isNotBlank()) {
            Text(card.sentence, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE6E9EE))
        }
        if (card.sessions.isEmpty()) {
            Text(
                stringResource(if (card.tomorrow) R.string.today_card_free_tomorrow else R.string.today_card_free),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        card.sessions.forEach { session -> SessionRow(session, profile, onOpenCoach) }
        card.meal?.let { MealRow(it, onOpenNutri) }
        card.next?.let { next ->
            Text(next, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionRow(session: PlannedSession, profile: AthleteProfile, onOpen: () -> Unit) {
    val sport = session.sport ?: return
    InnerRow(onOpen) {
        SportIconBox(sport)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleSmall)
            val line = TodayAdvice.summary(session, profile) + if (session.own) " · " + stringResource(R.string.today_card_own) else ""
            Text(line, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TodayAdvice.mainSet(session, profile)?.let { set ->
                Text(stringResource(R.string.today_card_main_set, set), fontSize = 13.sp, color = Color(0xFFD5DAE1))
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
            Text(stringResource(R.string.today_card_meal), fontSize = 12.sp, color = CoachPalette.Nutri.softText)
            Text(meal.title, style = MaterialTheme.typography.titleSmall)
            Text(meal.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Chevron()
    }
}

@Composable
private fun InnerRow(onOpen: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, TriColors.border, shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(12.dp),
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

/** What the swims say, in a sentence: most of the metres at threshold and above, or whole swims faster than it. */
@Composable
internal fun cssWhy(check: CssCheck): String =
    if (check.highShare > SwimThreshold.HIGH_SHARE) {
        stringResource(R.string.css_why_share, (check.highShare * 100).roundToInt())
    } else {
        stringResource(R.string.css_why_faster, check.fasterSessions)
    }

internal fun swimPace(secPer100: Int): String = Formatters.minutesAndSeconds(secPer100.toDouble())
