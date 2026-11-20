package com.triforge.app.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.common.BackButton
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.TriTextField
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.AthleteProfile
import com.triforge.domain.CssCheck
import com.triforge.domain.CssSource
import com.triforge.domain.Sport
import com.triforge.domain.SwimAnalysis
import com.triforge.domain.SwimThreshold
import com.triforge.domain.coach.SportMode
import com.triforge.domain.coach.Zones
import kotlin.math.roundToInt

/** The swim's own colours for the zones at threshold and above, as in the swim detail. */
private val ThresholdColor = Color(0xFFFF8A4C)
private val AboveColor = Color(0xFFFF5C64)

/**
 * Board 4f, «Soglia del nuoto»: why the threshold of the profile looks too
 * slow, what that distorts, and the two ways out: the 400 + 200 test (asked
 * of the swim coach, or with the times already in hand) or a cautious
 * estimate accepted with one tap. The threshold never changes by itself.
 */
@Composable
internal fun SwimThresholdPage(
    check: CssCheck,
    /** Who writes the swims: the test goes in the week only when it is the AI coach. */
    swimMode: SportMode,
    onBack: () -> Unit,
    onAskTest: (String) -> Unit,
    onUse: (Int) -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = swimPace(check.currentSecPer100)
    val estimate = check.suggestion.paceSecPer100
    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BackButton(onBack)
            Text(stringResource(R.string.css_page_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WhyCard(check)
            EffectsCard(check)
            SectionLabel(stringResource(R.string.css_two_ways))
            TestCard(swimMode, onAskTest, onUse)
            EstimateCard(check, onUse)
            SecondaryButton(
                stringResource(R.string.css_keep, current),
                onClick = {
                    onSnooze()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.css_keep_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                stringResource(R.string.css_after),
                fontSize = 13.sp,
                color = TriColors.textDim,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun WhyCard(check: CssCheck) {
    TriCard {
        CardHeader(stringResource(R.string.css_why_title))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PaceStat(stringResource(R.string.css_in_profile), check.currentSecPer100, TriColors.accentText, Modifier.weight(1f))
            PaceStat(stringResource(R.string.css_usual_pace), check.usualPaceSecPer100, SportColors.swim, Modifier.weight(1f))
        }
        val high = (check.highShare * 100).roundToInt()
        val top = (check.topShare * 100).roundToInt().coerceAtMost(high)
        val threshold = high - top
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.css_zone_meters), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.css_zone_scope, check.sessions, "%,d".format(java.util.Locale.ITALY, check.meters)),
                fontSize = 12.sp,
                color = TriColors.textDim,
            )
        }
        val description = stringResource(R.string.css_zone_chart, 100 - high, threshold, top)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(TriColors.track)
                .semantics { contentDescription = description },
        ) {
            // One bar for the whole: easy zones, threshold, above threshold.
            if (high < 100) Box(Modifier.weight((100 - high).toFloat()).height(18.dp).background(SportColors.swim.copy(alpha = 0.45f)))
            if (threshold > 0) Box(Modifier.weight(threshold.toFloat()).height(18.dp).background(ThresholdColor))
            if (top > 0) Box(Modifier.weight(top.toFloat()).height(18.dp).background(AboveColor))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Key(SportColors.swim.copy(alpha = 0.45f), stringResource(R.string.css_zone_easy, 100 - high))
            if (threshold > 0) Key(ThresholdColor, stringResource(R.string.css_zone_value, "B2", threshold))
            if (top > 0) Key(AboveColor, stringResource(R.string.css_zone_value, "C1–C3", top))
        }
        Text(
            stringResource(R.string.css_why_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        val more = listOfNotNull(
            check.fasterSessions.takeIf { it > 0 }?.let { stringResource(R.string.css_why_faster, it) },
            check.avgHr?.let { stringResource(R.string.css_avg_hr, it) },
        )
        if (more.isNotEmpty()) {
            Text(more.joinToString(" "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun PaceStat(label: String, secPer100: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(swimPace(secPer100), style = numberStyle(28.sp), color = color)
            Text(stringResource(R.string.swim_per_100), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun Key(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What a threshold too slow distorts: the zones, load and form, the coach's paces. */
@Composable
private fun EffectsCard(check: CssCheck) {
    val now = SwimAnalysis.zoneCode(check.usualPaceSecPer100.toDouble(), check.currentSecPer100)
    val then = SwimAnalysis.zoneCode(check.usualPaceSecPer100.toDouble(), check.suggestion.paceSecPer100)
    TriCard {
        CardHeader(stringResource(R.string.css_effects_title))
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (now != then) Effect(stringResource(R.string.css_effect_zones), stringResource(R.string.css_effect_zones_body, now, then))
            Effect(stringResource(R.string.css_effect_load), stringResource(R.string.css_effect_load_body))
            Effect(stringResource(R.string.css_effect_coach), stringResource(R.string.css_effect_coach_body))
        }
    }
}

@Composable
private fun Effect(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The 400 + 200 test: asked of the swim coach, or worked out here from two times the athlete already has. */
@Composable
private fun TestCard(swimMode: SportMode, onAskTest: (String) -> Unit, onUse: (Int) -> Unit) {
    var haveTimes by rememberSaveable { mutableStateOf(false) }
    var long by rememberSaveable { mutableStateOf("") }
    var short by rememberSaveable { mutableStateOf("") }
    var asked by rememberSaveable { mutableStateOf(false) }
    val coached = swimMode == SportMode.AI
    val request = stringResource(if (coached) R.string.css_test_request else R.string.css_test_request_own)
    TriCard {
        CardHeader(stringResource(R.string.css_test_title), trailing = stringResource(R.string.css_test_best))
        Text(
            stringResource(if (coached) R.string.css_test_body else R.string.css_test_body_own),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            stringResource(if (coached) R.string.css_test_ask else R.string.css_test_ask_own),
            onClick = { onAskTest(request) },
            compact = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { haveTimes = !haveTimes },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.css_have_times), style = MaterialTheme.typography.labelLarge, color = TriColors.accentText, modifier = Modifier.weight(1f))
            Icon(
                TriIcons.ChevronDown,
                contentDescription = null,
                tint = TriColors.accentText,
                modifier = Modifier.size(20.dp).rotate(if (haveTimes) 180f else 0f),
            )
        }
        AnimatedVisibility(visible = haveTimes) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val seconds400 = clockSeconds(long)
                val seconds200 = clockSeconds(short)
                val css = if (seconds400 != null && seconds200 != null) SwimThreshold.fromTest(seconds400, seconds200) else null
                val numbers = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TriTextField(
                        value = long,
                        onValueChange = { long = it.take(6); asked = false },
                        label = stringResource(R.string.css_time_400),
                        placeholder = "6:45",
                        isError = asked && seconds400 == null,
                        keyboardOptions = numbers,
                        modifier = Modifier.weight(1f),
                    )
                    TriTextField(
                        value = short,
                        onValueChange = { short = it.take(6); asked = false },
                        label = stringResource(R.string.css_time_200),
                        placeholder = "3:12",
                        isError = asked && seconds200 == null,
                        keyboardOptions = numbers.copy(imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(stringResource(R.string.css_time_hint), fontSize = 12.sp, color = TriColors.textDim)
                when {
                    css != null -> {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(swimPace(css), style = numberStyle(28.sp), color = SportColors.swim)
                            Text(stringResource(R.string.css_from_test), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        PrimaryButton(stringResource(R.string.swim_css_use, swimPace(css)), onClick = { onUse(css) }, compact = true, modifier = Modifier.fillMaxWidth())
                    }
                    asked -> Text(stringResource(R.string.css_time_error), fontSize = 13.sp, color = TriColors.danger)
                    else -> SecondaryButton(stringResource(R.string.css_compute), onClick = { asked = true }, compact = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** «6:45», «6.45» or «6'45»: minutes and seconds as the athlete types them, in seconds. */
internal fun clockSeconds(text: String): Int? {
    val parts = text.trim().split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
    if (parts.size != 2) return null
    val minutes = parts[0].toIntOrNull() ?: return null
    val seconds = parts[1].toIntOrNull()?.takeIf { it < 60 } ?: return null
    return (minutes * 60 + seconds).takeIf { it > 0 }
}

/** The cautious estimate, the zones it would give, and one tap to take it. */
@Composable
private fun EstimateCard(check: CssCheck, onUse: (Int) -> Unit) {
    val estimate = check.suggestion.paceSecPer100
    val pace = swimPace(estimate)
    val usualZone = SwimAnalysis.zoneOf(check.usualPaceSecPer100.toDouble(), estimate)
    val profile = AthleteProfile(cssSecPer100m = estimate)
    TriCard {
        CardHeader(stringResource(if (check.suggestion.source == CssSource.BEST_EFFORTS) R.string.css_estimate_title_best else R.string.css_estimate_title))
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pace, style = numberStyle(32.sp), color = SportColors.swim)
            Text(stringResource(R.string.swim_per_100), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            stringResource(if (check.suggestion.source == CssSource.BEST_EFFORTS) R.string.css_estimate_best else R.string.css_estimate_usual),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            stringResource(R.string.css_zones_with, pace),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
        Zones.SWIM.forEachIndexed { index, zone ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TriColors.border))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(zone, style = numberStyle(14.sp), modifier = Modifier.width(36.dp))
                Text(
                    Zones.label(zone) + if (index + 1 == usualZone) " · " + stringResource(R.string.css_zone_usual) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (index + 1 == usualZone) SportColors.swim else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Zones.target(Sport.SWIM, zone, profile).removeSuffix(" /100 m"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PrimaryButton(stringResource(R.string.swim_css_use, pace), onClick = { onUse(estimate) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}
