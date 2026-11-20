package com.triforge.app.ui.today

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.RecoveryState
import com.triforge.app.ui.TrainingState
import com.triforge.app.ui.coach.KeyboardBack
import com.triforge.app.ui.coach.TodayUi
import com.triforge.app.ui.common.ActivityRow
import com.triforge.app.ui.common.BulletBar
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.Avatar
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.DayAxis
import com.triforge.app.ui.common.FormBandColor
import com.triforge.app.ui.common.FormBars
import com.triforge.app.ui.common.KeyValueRow
import com.triforge.app.ui.common.LegendDot
import com.triforge.app.ui.common.LoadChart
import com.triforge.app.ui.common.LoadingBox
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.SportDot
import com.triforge.app.ui.common.StackedWeekBars
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.WeekStrip
import com.triforge.app.ui.common.labelRes
import com.triforge.app.ui.detail.SwimGearPage
import com.triforge.app.ui.recovery.LinkRow
import com.triforge.app.ui.recovery.RecoveryCard
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.Activity
import com.triforge.domain.AthleteProfile
import com.triforge.domain.FitnessTrend
import com.triforge.domain.FormZone
import com.triforge.domain.Formatters
import com.triforge.domain.LoadQuality
import com.triforge.domain.SessionLoad
import com.triforge.domain.Sport
import com.triforge.domain.SportWeekGlance
import com.triforge.domain.SwimAnalysis
import com.triforge.domain.SwimGearMarks
import com.triforge.domain.TrainingStats
import com.triforge.domain.WeekGlance
import com.triforge.domain.WeekGlanceModel
import com.triforge.domain.coach.SportMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Sessions listed under "Ultime sessioni". */
private const val RECENT_COUNT = 3

/** Weeks in the collapsed volume chart. */
private const val VOLUME_WEEKS = 12

/** Past weeks needed before "usual" is shown: one week is an anecdote. */
private const val MIN_REFERENCE_WEEKS = 2

/** Days of load data before fitness and form mean anything. */
private const val MIN_LOAD_DAYS = 14

private val headerDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALY)
private val axisDate = DateTimeFormatter.ofPattern("d MMM", Locale.ITALY)

@Composable
fun TodayScreen(
    state: TrainingState,
    recovery: RecoveryState,
    name: String,
    onOpenProfile: () -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenRecovery: () -> Unit,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: ByteArray? = null,
    onConnectSource: () -> Unit = onOpenProfile,
    /** What the coaches have for today: «Oggi tocca a» and the swim threshold to check. */
    advice: TodayUi = TodayUi(),
    profile: AthleteProfile = AthleteProfile(),
    swimMode: SportMode = SportMode.AI,
    onOpenCoach: () -> Unit = {},
    onOpenNutri: () -> Unit = {},
    onUseCss: (Int) -> Unit = {},
    onAskSwimTest: (String) -> Unit = {},
    onSnoozeCss: () -> Unit = {},
    /** What the athlete says a swim was swum with: its id, and the equipment or none. */
    onSwimGear: (String, SwimGearMarks) -> Unit = { _, _ -> },
) {
    // Board 5e: the page of a swim's equipment opens over Oggi, from the question about the latest swim.
    var gearFor by rememberSaveable { mutableStateOf<String?>(null) }
    val gearSwim = gearFor?.let { id -> (state as? TrainingState.Loaded)?.activities?.firstOrNull { it.id == id } }
    if (gearSwim != null) {
        BackHandler { gearFor = null }
        SwimGearPage(
            activity = gearSwim,
            onSave = {
                onSwimGear(gearSwim.id, it)
                gearFor = null
            },
            onBack = { gearFor = null },
            modifier = modifier,
        )
        return
    }
    // Board 4f: the page of the swim threshold opens over Oggi and closes by itself once the threshold fits.
    var threshold by rememberSaveable { mutableStateOf(false) }
    val check = advice.cssCheck
    // Once the threshold fits there is no page to come back to, now or when a check comes up again.
    LaunchedEffect(check == null) { if (check == null) threshold = false }
    if (threshold && check != null) {
        BackHandler { threshold = false }
        KeyboardBack()
        SwimThresholdPage(
            check = check,
            swimMode = swimMode,
            onBack = { threshold = false },
            onAskTest = {
                threshold = false
                onAskSwimTest(it)
            },
            onUse = onUseCss,
            onSnooze = onSnoozeCss,
            modifier = modifier,
        )
        return
    }
    when (state) {
        TrainingState.Loading -> LoadingBox(modifier)

        TrainingState.Failed -> CenteredMessage(stringResource(R.string.load_error), modifier = modifier)

        is TrainingState.Loaded -> Dashboard(
            activities = state.activities,
            recovery = recovery,
            name = name,
            avatar = avatar,
            onOpenProfile = onOpenProfile,
            onConnectSource = onConnectSource,
            onOpenActivity = onOpenActivity,
            onOpenStats = onOpenStats,
            onOpenRecovery = onOpenRecovery,
            onCheckIn = onCheckIn,
            modifier = modifier,
            advice = advice,
            profile = profile,
            onOpenCoach = onOpenCoach,
            onOpenNutri = onOpenNutri,
            onVerifyCss = { threshold = true },
            onMarkGear = { gearFor = it },
            onNoGear = { onSwimGear(it, SwimGearMarks.NONE) },
        )
    }
}

/**
 * The Today tab answers, top to bottom: how am I today, is my fitness
 * building safely, am I on track this week, what did I just do. Every number
 * comes with what it is compared to.
 */
@Composable
private fun Dashboard(
    activities: List<Activity>,
    recovery: RecoveryState,
    name: String,
    avatar: ByteArray?,
    onOpenProfile: () -> Unit,
    onConnectSource: () -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenRecovery: () -> Unit,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    advice: TodayUi = TodayUi(),
    profile: AthleteProfile = AthleteProfile(),
    onOpenCoach: () -> Unit = {},
    onOpenNutri: () -> Unit = {},
    onVerifyCss: () -> Unit = {},
    onMarkGear: (String) -> Unit = {},
    onNoGear: (String) -> Unit = {},
) {
    val zone = ZoneId.systemDefault()
    val gearSwim = remember(activities, today) { SwimAnalysis.gearToAsk(activities, today, zone) }
    val glance = remember(activities, today) { WeekGlanceModel.compute(activities, today, zone) }
    val volume = remember(activities, today) { TrainingStats.weeklyTrend(activities, VOLUME_WEEKS, today, zone) }
    val loaded = recovery as? RecoveryState.Loaded
    val trend = remember(loaded) { loaded?.let { FitnessTrend.from(it.report.history) } }
    val loads = loaded?.loads.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { Header(today, name, avatar, onOpenProfile) }

        if (activities.isEmpty()) {
            item(key = "empty") { EmptyCard(onConnectSource) }
            return@LazyColumn
        }

        // What to do comes first: the numbers below are its reasons.
        advice.card?.let { card ->
            item(key = "advice") { TodayAdviceCard(card, profile, onOpenCoach, onOpenNutri) }
        }
        // Board 5e: asked once about the latest pool swim, while it is fresh in the memory.
        gearSwim?.let { swim ->
            item(key = "gear") {
                val days = ChronoUnit.DAYS.between(swim.startTime.atZone(zone).toLocalDate(), today)
                SwimGearQuestionCard(swim, days, onYes = { onMarkGear(swim.id) }, onNo = { onNoGear(swim.id) })
            }
        }
        val check = advice.cssCheck
        if (check != null && !advice.cssSnoozed) {
            item(key = "css") { CssNoticeCard(check, onVerifyCss) }
        }

        if (recovery is RecoveryState.Loading) {
            // The two cards take a couple of seconds to compute: their place is held, so the week does not jump down when they arrive.
            item(key = "recovery") { PendingCard(stringResource(R.string.recovery_title), RECOVERY_CARD_HEIGHT) }
            item(key = "load") { PendingCard(stringResource(R.string.load_card_title), LOAD_CARD_HEIGHT) }
        } else {
            item(key = "recovery") { RecoveryCard(recovery, onOpen = onOpenRecovery, onCheckIn = onCheckIn) }
        }
        if (trend != null && trend.points.isNotEmpty()) {
            item(key = "load") {
                val firstDay = activities.minOf { it.startTime.atZone(zone).toLocalDate() }
                LoadCard(
                    trend,
                    loads.values,
                    estimate = firstDay.isAfter(today.minusDays(42)),
                    onOpen = onOpenRecovery,
                    onVerifyCss = onVerifyCss.takeIf { check != null },
                )
            }
        }
        item(key = "week") {
            val weekLoads = loads.values.filter {
                !it.activity.startTime.atZone(zone).toLocalDate().isBefore(glance.weekStart)
            }
            WeekCard(glance, weekLoad = if (loads.isEmpty()) null else weekLoads.sumOf { it.tss }, onOpenStats = onOpenStats)
        }

        item(key = "recent-title") { SectionLabel(stringResource(R.string.today_recent_sessions)) }
        items(activities.take(RECENT_COUNT), key = { it.id }) { activity ->
            ActivityRow(activity, onClick = { onOpenActivity(activity.id) })
        }

        item(key = "volume") {
            val average = volume.dropLast(1).filter { it.durationSec > 0 }.map { it.durationSec }.average()
            CollapsibleCard(
                title = stringResource(R.string.today_volume_title, VOLUME_WEEKS),
                summary = if (average.isNaN()) null else stringResource(R.string.today_volume_summary, Formatters.duration(average.toLong())),
            ) {
                StackedWeekBars(
                    trend = volume,
                    description = stringResource(R.string.today_volume_chart, VOLUME_WEEKS),
                )
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Swim, bike and run always; the gym and the rest when the weeks hold any.
                    TrainingStats.allSports.filter { sport ->
                        sport in TrainingStats.triathlonSports || volume.any { it.of(sport).sessions > 0 }
                    }.forEach { sport ->
                        LegendDot(SportColors.of(sport), stringResource(sport.labelRes()))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(onConnectSource: () -> Unit) {
    TriCard {
        Text(
            text = stringResource(R.string.today_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.today_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.today_connect_source),
            onClick = onConnectSource,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** About as tall as the cards of «Carico e recupero» and of the form once they are computed. */
private val RECOVERY_CARD_HEIGHT = 250.dp
private val LOAD_CARD_HEIGHT = 440.dp

/** A card still being computed: its title and its room, so that what is below stays where it is. */
@Composable
private fun PendingCard(title: String, height: Dp) {
    TriCard(modifier = Modifier.heightIn(min = height)) {
        CardHeader(title = title)
        Text(
            text = stringResource(R.string.today_pending),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Compact: the date and the avatar. The space goes to the data. */
@Composable
private fun Header(today: LocalDate, name: String, avatar: ByteArray?, onOpenProfile: () -> Unit) {
    val date = remember(today) { headerDate.format(today).replaceFirstChar { it.titlecase(Locale.ITALY) } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.nav_today),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Avatar(
            jpeg = avatar,
            name = name,
            size = 40.dp,
            contentDescription = stringResource(R.string.nav_profile),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    onClickLabel = stringResource(R.string.nav_profile),
                    role = Role.Button,
                    onClick = onOpenProfile,
                ),
        )
    }
}

/** "−21 %", with the typographic minus. */
private fun percent(value: Double): String {
    val rounded = (value * 100).roundToInt()
    return when {
        rounded < 0 -> "−${-rounded}%"
        rounded > 0 -> "+$rounded%"
        else -> "0%"
    }
}

private fun signedOne(value: Double): String {
    val text = "%.1f".format(Locale.ITALY, abs(value))
    return when {
        value <= -0.05 -> "−$text"
        value >= 0.05 -> "+$text"
        else -> "0"
    }
}

private fun FormZone.labelRes(): Int = when (this) {
    FormZone.DETRAINING -> R.string.form_zone_detraining
    FormZone.FRESH -> R.string.form_zone_fresh
    FormZone.NEUTRAL -> R.string.form_zone_neutral
    FormZone.PRODUCTIVE -> R.string.form_zone_productive
    FormZone.OVERLOAD -> R.string.form_zone_overload
}

/**
 * Fitness, acute load and form, all from the same TSS-equivalent load the
 * recovery balance uses. One panel for the loads (one unit, one axis), one
 * for form on a fixed scale with the productive band.
 */
@Composable
private fun LoadCard(
    trend: FitnessTrend,
    loads: Collection<SessionLoad>,
    estimate: Boolean,
    onOpen: () -> Unit,
    /** Set while the swim threshold is in doubt: the card says its numbers may be too high. */
    onVerifyCss: (() -> Unit)? = null,
) {
    val points = trend.points
    val latest = points.last()
    val fitnessColor = MaterialTheme.colorScheme.primary
    val fatigueColor = TriColors.textDim
    val loadDays = points.count { it.dailyLoad > 0 }
    var details by rememberSaveable { mutableStateOf(false) }

    TriCard(onClick = onOpen) {
        CardHeader(
            title = stringResource(R.string.load_card_title),
            trailing = if (onVerifyCss != null) stringResource(R.string.recovery_confidence_low) else stringResource(R.string.today_form_period, points.size),
        )
        if (loadDays < MIN_LOAD_DAYS / 2 && points.size >= MIN_LOAD_DAYS) {
            Text(
                text = stringResource(R.string.load_card_learning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val ramp = trend.ramp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatBlock(
                value = (if (estimate) "~" else "") + latest.fitness.roundToInt(),
                label = stringResource(R.string.today_fitness),
                note = ramp?.let { stringResource(R.string.load_ramp_note, signedOne(it)) },
                marker = fitnessColor,
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                value = latest.fatigue.roundToInt().toString(),
                label = stringResource(R.string.load_acute),
                note = stringResource(R.string.load_acute_note),
                marker = fatigueColor,
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                value = percent(latest.formPct),
                label = stringResource(R.string.today_form),
                note = stringResource(latest.zone.labelRes()),
                modifier = Modifier.weight(1f),
            )
        }
        if (onVerifyCss != null) {
            Text(
                text = stringResource(R.string.css_form_doubt),
                style = MaterialTheme.typography.bodySmall,
                color = TriColors.accentText,
            )
            LinkRow(stringResource(R.string.css_verify), onVerifyCss, Modifier.padding(bottom = 6.dp))
        }
        LoadChart(
            points = points,
            fitnessColor = fitnessColor,
            fatigueColor = fatigueColor,
            description = stringResource(
                R.string.load_chart_description,
                points.size,
                latest.fitness.roundToInt(),
                latest.fatigue.roundToInt(),
            ),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LegendDot(fitnessColor, stringResource(R.string.today_fitness))
            LegendDot(fatigueColor, stringResource(R.string.load_acute))
            LegendDot(Color(0xFF9AA3AF).copy(alpha = 0.5f), stringResource(R.string.load_daily))
        }
        Text(
            text = stringResource(R.string.load_form_panel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        FormBars(
            points = points,
            description = stringResource(
                R.string.load_form_description,
                percent(latest.formPct),
                stringResource(latest.zone.labelRes()),
            ),
        )
        DayAxis(
            first = axisDate.format(points.first().date),
            middle = axisDate.format(points[points.size / 2].date),
            last = stringResource(R.string.axis_today),
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The long label gives way, the short one stays whole.
            LegendSwatch(FormBandColor.copy(alpha = 0.6f), stringResource(R.string.form_legend_productive), Modifier.weight(1f, fill = false))
            LegendSwatch(TriColors.danger, stringResource(R.string.form_zone_overload))
        }

        // Details on demand: what the numbers mean and how good the load data is.
        ExpandRow(
            text = stringResource(R.string.load_details),
            expanded = details,
            onToggle = { details = !details },
        )
        AnimatedVisibility(visible = details) {
            Column {
                val monotony = latestMonotony(trend)
                KeyValueRow(
                    stringResource(R.string.load_week_tss),
                    trend.loadOfLast(7).roundToInt().toString(),
                )
                if (monotony != null) {
                    KeyValueRow(stringResource(R.string.recovery_load_monotony), "%.1f".format(Locale.ITALY, monotony))
                }
                val recent = loads.filter { !it.activity.startTime.isBefore(java.time.Instant.now().minusSeconds(28L * 86_400)) }
                val total = recent.sumOf { it.tss }
                if (total > 0) {
                    val measured = recent.filter { it.quality != LoadQuality.LOW }.sumOf { it.tss } / total
                    KeyValueRow(stringResource(R.string.load_quality), percent(measured).removePrefix("+"))
                    if (measured < 0.5) {
                        Text(
                            text = stringResource(R.string.load_quality_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = TriColors.accentText,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.load_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(width = 12.dp, height = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        // A legend is one line: a word cut in two («Sovraccar-ico») reads as a mistake.
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Foster monotony of the last seven days: mean over standard deviation of daily load. */
private fun latestMonotony(trend: FitnessTrend): Double? {
    val week = trend.points.takeLast(7).map { it.dailyLoad }
    if (week.size < 7 || week.sum() <= 0) return null
    val mean = week.average()
    val sd = kotlin.math.sqrt(week.sumOf { (it - mean) * (it - mean) } / (week.size - 1))
    return (mean / maxOf(sd, 1.0)).coerceAtMost(4.0)
}

@Composable
private fun ExpandRow(text: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(min = 44.dp)
            .clickable(
                onClickLabel = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                role = Role.Button,
                onClick = onToggle,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = TriIcons.ChevronDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (expanded) 180f else 0f),
        )
    }
}

/** A headline figure: a small colour key, the value, its name and one line of context. */
@Composable
private fun StatBlock(
    value: String,
    label: String,
    note: String?,
    modifier: Modifier = Modifier,
    marker: Color? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (marker != null) {
                Box(
                    Modifier
                        .width(10.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(marker),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(text = value, style = numberStyle(24.sp), maxLines = 1)
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * This week per discipline, compared fairly: against what the athlete
 * usually has done by the same weekday, with a whole usual week as a
 * second reference.
 */
@Composable
private fun WeekCard(glance: WeekGlance, weekLoad: Double?, onOpenStats: () -> Unit) {
    val letters = listOf("L", "M", "M", "G", "V", "S", "D")
    val weekday = glance.today.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ITALY)
    val comparable = glance.referenceWeeks >= MIN_REFERENCE_WEEKS
    val shown = glance.sports.filter { it.done > 0 || (it.usualWeek ?: 0.0) > 0 }

    TriCard {
        CardHeader(
            title = stringResource(R.string.today_this_week),
            trailing = listOfNotNull(
                Formatters.duration(glance.durationSec),
                stringResource(R.string.week_sessions, glance.sessions),
                weekLoad?.let { stringResource(R.string.week_tss, it.roundToInt()) },
            ).joinToString(" · "),
        )
        WeekStrip(
            days = glance.days,
            today = glance.today,
            dayLetters = letters,
            minutesLabel = { Formatters.duration(it) },
            description = stringResource(
                R.string.week_strip_description,
                glance.days.count { it.totalSec > 0 },
                Formatters.duration(glance.durationSec),
            ),
            modifier = Modifier.padding(top = 12.dp),
        )
        if (comparable && glance.usualDurationToDateSec != null) {
            Text(
                text = stringResource(
                    R.string.week_total_vs_usual,
                    Formatters.duration(glance.usualDurationToDateSec!!),
                    weekday,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        shown.forEach { SportRow(it, comparable, weekday) }
        if (comparable) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TickKey(filled = true, label = stringResource(R.string.week_key_to_date, weekday))
                TickKey(filled = false, label = stringResource(R.string.week_key_whole))
            }
        } else {
            Text(
                text = stringResource(R.string.week_no_reference),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LinkRow(text = stringResource(R.string.today_sport_stats), onClick = onOpenStats)
    }
}

@Composable
private fun TickKey(filled: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (filled) MaterialTheme.colorScheme.onSurface else TriColors.borderStrong),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SportRow(row: SportWeekGlance, comparable: Boolean, weekday: String) {
    val sport = row.sport
    fun text(value: Double) =
        if (row.byTime) Formatters.duration(value.toLong()) else Formatters.distance(sport, value)

    val usualToDate = row.usualToDate.takeIf { comparable }
    val usualWeek = row.usualWeek.takeIf { comparable }
    val delta = usualToDate?.let { usual -> deltaText(row.done, usual) }

    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SportDot(sport)
            Text(
                text = stringResource(sport.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (delta != null) {
                Text(
                    text = delta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = text(row.done), style = numberStyle(17.sp))
        }
        BulletBar(
            done = row.done,
            usualToDate = usualToDate,
            usualWeek = usualWeek,
            color = SportColors.of(sport),
            description = stringResource(
                R.string.week_sport_description,
                stringResource(sport.labelRes()),
                text(row.done),
                usualToDate?.let(::text) ?: "—",
                usualWeek?.let(::text) ?: "—",
            ),
        )
        if (usualToDate != null && usualWeek != null) {
            Text(
                text = stringResource(R.string.week_sport_context, weekday, text(usualToDate), text(usualWeek)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "↑ 23 %" against the usual, or "≈ solito" inside the noise. Neutral ink: more is not "good". */
@Composable
private fun deltaText(done: Double, usual: Double): String? {
    if (usual <= 0) return if (done > 0) stringResource(R.string.week_delta_new) else null
    val change = (done - usual) / usual
    return when {
        abs(change) < 0.05 -> stringResource(R.string.week_delta_same)
        change > 0 -> "↑ ${(change * 100).roundToInt()}%"
        else -> "↓ ${(-change * 100).roundToInt()}%"
    }
}
