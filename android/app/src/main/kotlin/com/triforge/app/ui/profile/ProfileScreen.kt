package com.triforge.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.ui.TrainingState
import com.triforge.app.ui.TrainingViewModel
import com.triforge.app.ui.account.AccountViewModel
import com.triforge.app.ui.common.Avatar
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.IconBox
import com.triforge.app.ui.common.KeyValueRow
import com.triforge.app.ui.common.LegendDot
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.ProgressBar
import com.triforge.app.ui.common.ScreenTopBar
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.StatGrid
import com.triforge.app.ui.common.TopBarIcon
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.labelRes
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.AthleteProfile
import com.triforge.domain.Formatters
import com.triforge.domain.ProfileStats
import com.triforge.domain.ProfileSummary
import com.triforge.domain.ProfileText
import com.triforge.domain.Sport
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val raceDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALY)

/**
 * The athlete's page: who they are, what they train for, how the last months
 * went and their thresholds. Read-only; changes go through the edit page, and
 * account matters through the settings behind the gear.
 */
@Composable
fun ProfileScreen(
    account: AccountViewModel,
    profile: ProfileViewModel,
    training: TrainingState,
    onEdit: () -> Unit,
    onOpenWeekSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val accountState by account.state.collectAsStateWithLifecycle()
    val profileState by profile.state.collectAsStateWithLifecycle()
    val stored = profileState.stored
    val name = accountState.account.displayName.ifBlank { profileState.name }
    val activities = (training as? TrainingState.Loaded)?.activities.orEmpty()
    val weeks = TrainingViewModel.WINDOW_WEEKS.toInt()
    val summary = remember(activities, today) {
        ProfileStats.summary(activities, weeks, today, ZoneId.systemDefault())
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.profile_title), onBack = null) {
            TopBarIcon(TriIcons.Settings, stringResource(R.string.settings_title), onOpenSettings)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hero(
                avatar = accountState.avatar,
                name = name,
                bio = accountState.account.bio,
                profile = stored,
                garminConnected = profileState.garmin.connected,
                onEdit = onEdit,
            )
            RaceCard(stored, today)
            WeekSetupCard(onOpenWeekSetup)
            if (summary.sessions > 0) {
                TrainingCard(summary)
            } else {
                EmptyTrainingCard(onOpenSources)
            }
            ThresholdsCard(stored, onEdit)
            BodyCard(stored)
        }
    }
}

@Composable
private fun Hero(
    avatar: ByteArray?,
    name: String,
    bio: String,
    profile: AthleteProfile,
    garminConnected: Boolean,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(
            jpeg = avatar,
            name = name,
            size = 112.dp,
            ring = true,
            contentDescription = stringResource(R.string.profile_photo),
        )
        Text(
            text = name.ifBlank { stringResource(R.string.profile_no_name) },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (bio.isNotBlank()) {
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyMedium,
                color = TriColors.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        val badges = buildList {
            profile.goal?.let { add(TriIcons.Flag to stringResource(it.labelRes())) }
            profile.weeklyHours?.let {
                add(TriIcons.Activity to stringResource(R.string.profile_badge_hours, ProfileText.formatDecimal(it)))
            }
            if (garminConnected) add(TriIcons.Watch to stringResource(R.string.source_garmin))
        }
        if (badges.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                badges.forEach { (icon, label) -> Badge(icon, label) }
            }
        }
        SecondaryButton(
            text = stringResource(R.string.profile_edit),
            onClick = onEdit,
            compact = true,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun Badge(icon: ImageVector, label: String) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, TriColors.border, shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TriColors.accentText, modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

/** The goal race and the days left to it, when the athlete has one. */
@Composable
private fun RaceCard(profile: AthleteProfile, today: LocalDate) {
    if (profile.raceName.isBlank() && profile.raceDate == null) return
    val days = ProfileStats.daysToRace(profile.raceDate, today)

    TriCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBox(background = MaterialTheme.colorScheme.primaryContainer) {
                Icon(TriIcons.Flag, contentDescription = null, tint = TriColors.accentText, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.profile_race_card),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = profile.raceName.ifBlank { stringResource(R.string.profile_race_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                )
                profile.raceDate?.let {
                    Text(
                        text = raceDateFormat.format(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (days != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = days.toString(), style = numberStyle(30.sp), color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(if (days == 1L) R.string.profile_race_day else R.string.profile_race_days),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The way into "Giorni, orari e gara", which the coach's regia starts from. */
@Composable
private fun WeekSetupCard(onOpen: () -> Unit) {
    TriCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(R.string.week_setup_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.week_setup_card_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(TriIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** Totals of the loaded weeks, and how the time splits across the three sports. */
@Composable
private fun TrainingCard(summary: ProfileSummary) {
    // Swim, bike and run always; the gym and the other activities when the weeks hold any.
    val shares = summary.bySport.map { it.sport to summary.timeShare(it.sport) }
    val shareText = shares.map { (sport, share) -> "${stringResource(sport.labelRes())} ${(share * 100).roundToInt()}%" }.joinToString(", ")

    TriCard {
        CardHeader(
            title = stringResource(R.string.profile_training_title),
            trailing = stringResource(R.string.period_last_weeks, summary.weeks),
        )
        StatGrid(
            stats = listOf(
                (summary.durationSec / 3600.0).roundToInt().toString() to stringResource(R.string.unit_hours),
                summary.sessions.toString() to stringResource(R.string.profile_sessions),
                (summary.distanceMeters / 1000.0).roundToInt().toString() to stringResource(R.string.unit_km),
            ),
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.profile_split_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        )
        SplitBar(
            shares = shares.map { (sport, share) -> SportColors.of(sport) to share },
            description = stringResource(R.string.profile_split_chart, shareText),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            shares.forEach { (sport, share) ->
                LegendDot(
                    color = SportColors.of(sport),
                    label = "${stringResource(sport.labelRes())} ${(share * 100).roundToInt()}%",
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_active_weeks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.profile_active_weeks_value, summary.activeWeeks, summary.weeks),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        ProgressBar(
            fraction = summary.activeWeeks.toFloat() / summary.weeks,
            color = TriColors.good,
        )
    }
}

/** One bar split in proportion, the way a race splits into its legs. */
@Composable
private fun SplitBar(shares: List<Pair<Color, Float>>, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(TriColors.track)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        shares.filter { it.second > 0f }.forEach { (color, share) ->
            Box(
                Modifier
                    .weight(share)
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}

@Composable
private fun EmptyTrainingCard(onOpenSources: () -> Unit) {
    TriCard {
        CardHeader(title = stringResource(R.string.profile_training_title))
        Text(
            text = stringResource(R.string.profile_training_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
        )
        PrimaryButton(text = stringResource(R.string.today_connect_source), onClick = onOpenSources, compact = true)
    }
}

/** CSS, FTP and threshold pace side by side, each in its sport's color. */
@Composable
private fun ThresholdsCard(profile: AthleteProfile, onEdit: () -> Unit) {
    val none = stringResource(R.string.value_none)
    TriCard {
        CardHeader(title = stringResource(R.string.profile_thresholds))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThresholdTile(
                color = SportColors.swim,
                label = stringResource(R.string.profile_tile_css),
                value = profile.cssSecPer100m?.let { ProfileText.formatPace(it) } ?: none,
                unit = stringResource(R.string.unit_min_100m),
                modifier = Modifier.weight(1f),
            )
            ThresholdTile(
                color = SportColors.bike,
                label = stringResource(R.string.profile_tile_ftp),
                value = profile.ftpWatts?.toString() ?: none,
                unit = stringResource(R.string.unit_watt),
                modifier = Modifier.weight(1f),
            )
            ThresholdTile(
                color = SportColors.run,
                label = stringResource(R.string.profile_tile_pace),
                value = profile.thresholdPaceSecPerKm?.let { ProfileText.formatPace(it) } ?: none,
                unit = stringResource(R.string.unit_min_km),
                modifier = Modifier.weight(1f),
            )
        }
        val missing = profile.cssSecPer100m == null || profile.ftpWatts == null || profile.thresholdPaceSecPerKm == null
        if (missing) {
            Text(
                text = stringResource(R.string.profile_thresholds_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            SecondaryButton(
                text = stringResource(R.string.profile_thresholds_set),
                onClick = onEdit,
                compact = true,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun ThresholdTile(color: Color, label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(TriColors.iconBox.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .width(22.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(text = value, style = numberStyle(22.sp))
        Text(text = unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Weight, height, max heart rate and weekly time: dense, so folded away. */
@Composable
private fun BodyCard(profile: AthleteProfile) {
    val none = stringResource(R.string.value_none)
    val kg = stringResource(R.string.unit_kg)
    val cm = stringResource(R.string.unit_cm)
    val summary = listOfNotNull(
        profile.weightKg?.let { "${ProfileText.formatDecimal(it)} $kg" },
        profile.heightCm?.let { "$it $cm" },
    ).joinToString(" · ").ifEmpty { null }

    CollapsibleCard(title = stringResource(R.string.profile_body_card), summary = summary) {
        KeyValueRow(stringResource(R.string.profile_weight), profile.weightKg?.let { "${ProfileText.formatDecimal(it)} $kg" } ?: none)
        KeyValueRow(stringResource(R.string.profile_height), profile.heightCm?.let { "$it $cm" } ?: none)
        KeyValueRow(
            stringResource(R.string.profile_max_hr),
            profile.maxHr?.let { stringResource(R.string.unit_bpm_value, it) } ?: none,
        )
        KeyValueRow(
            stringResource(R.string.profile_weekly_hours),
            profile.weeklyHours?.let { "${ProfileText.formatDecimal(it)} ${stringResource(R.string.unit_h)}" } ?: none,
        )
    }
}
