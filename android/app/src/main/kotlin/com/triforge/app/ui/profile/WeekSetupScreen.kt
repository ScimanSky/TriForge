package com.triforge.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triforge.app.R
import com.triforge.app.ui.common.LoadingBox
import com.triforge.app.ui.common.NoticeLine
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.ScreenTopBar
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.TriChip
import com.triforge.app.ui.common.TriTextField
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.Activity
import com.triforge.domain.AthleteProfile
import com.triforge.domain.ProfileText
import com.triforge.domain.RaceGoal
import com.triforge.domain.Sport
import com.triforge.domain.TrainingStats
import com.triforge.domain.coach.GymSlot
import com.triforge.domain.coach.RecentTraining
import com.triforge.domain.coach.SportMode
import com.triforge.domain.coach.SportSlot
import com.triforge.domain.coach.TrainingAvailability
import com.triforge.domain.coach.TriRegia
import com.triforge.domain.coach.WeekBudget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * "Giorni, orari e gara" (board 8b): the race and its distance, the weak
 * sport, the weekly hours and how the regia splits them, the preferred rest
 * day, the gym and, for each sport, who looks after it (its AI coach, the
 * athlete's own trainer, nobody for now) with the days and usual time. The
 * regia of the coach starts from here.
 */
@Composable
fun WeekSetupScreen(
    viewModel: WeekSetupViewModel,
    activities: List<Activity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(title = stringResource(R.string.week_setup_title), onBack = onBack)
        if (!state.loaded) {
            LoadingBox()
            return@Column
        }
        WeekSetupForm(state, activities, onSave = { profile, availability -> viewModel.save(profile, availability, onBack) })
    }
}

@Composable
private fun WeekSetupForm(
    state: WeekSetupState,
    activities: List<Activity>,
    onSave: (AthleteProfile, TrainingAvailability) -> Unit,
) {
    val stored = state.profile
    var goal by rememberSaveable { mutableStateOf(stored.goal?.key) }
    var raceName by rememberSaveable { mutableStateOf(stored.raceName) }
    var raceDate by rememberSaveable { mutableStateOf(ProfileText.formatDate(stored.raceDate)) }
    var hours by rememberSaveable { mutableStateOf(stored.weeklyHours ?: DEFAULT_HOURS) }
    var draft by remember { mutableStateOf(state.availability) }
    val times = remember {
        (TriRegia.SPORTS + Sport.STRENGTH).associateWith { sport ->
            val time = if (sport == Sport.STRENGTH) state.availability.gym.time else state.availability.slot(sport)?.time
            mutableStateOf(time?.toString().orEmpty())
        }
    }
    var showErrors by remember { mutableStateOf(false) }

    val goalValue = RaceGoal.entries.firstOrNull { it.key == goal }
    val dateValue = ProfileText.parseDate(raceDate)
    val dateError = raceDate.isNotBlank() && dateValue == null
    // Only what the athlete can see can be wrong: a paused sport and a gym switched off show no time.
    val timed = TriRegia.SPORTS.filter { draft.isActive(it) } + listOfNotNull(Sport.STRENGTH.takeIf { draft.gym.enabled })
    val timeErrors = timed.filter { times.getValue(it).value.isNotBlank() && parseTime(times.getValue(it).value) == null }.toSet()
    val zone = remember { ZoneId.systemDefault() }
    val weekStart = remember { TrainingStats.weekStart(LocalDate.now(zone)) }
    val recent = remember(activities) { RecentTraining.of(activities, weekStart, zone) }
    // The split the regia would make today with what is on the page, saved or not.
    val budget = TriRegia.budget(stored.copy(goal = goalValue, raceDate = dateValue, weeklyHours = hours), draft, recent, weekStart)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.week_setup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        TriCard {
            Text(stringResource(R.string.week_setup_race), style = MaterialTheme.typography.titleMedium)
            TriTextField(
                value = raceName,
                onValueChange = { raceName = it.take(80) },
                label = stringResource(R.string.week_setup_race_name),
                placeholder = stringResource(R.string.week_setup_race_name_hint),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            TriTextField(
                value = raceDate,
                onValueChange = { raceDate = it.take(10) },
                label = stringResource(R.string.week_setup_race_date),
                placeholder = stringResource(R.string.week_setup_race_date_hint),
                isError = showErrors && dateError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Label(stringResource(R.string.week_setup_distance))
            Chips {
                RaceGoal.entries.filter { it != RaceGoal.FITNESS }.forEach { item ->
                    TriChip(stringResource(item.labelRes()), selected = goal == item.key, onClick = { goal = item.key })
                }
            }
            Label(stringResource(R.string.week_setup_weak))
            Chips {
                TriRegia.SPORTS.forEach { sport ->
                    when (draft.mode(sport)) {
                        SportMode.AI -> TriChip(sportName(sport), selected = draft.weakSport == sport, onClick = { draft = draft.copy(weakSport = sport) })
                        SportMode.OWN -> OffChip(stringResource(R.string.week_setup_weak_own, sportName(sport)))
                        SportMode.PAUSED -> OffChip(stringResource(R.string.week_setup_weak_paused, sportName(sport)))
                    }
                }
                TriChip(stringResource(R.string.week_setup_weak_data), selected = draft.weakSport == null, onClick = { draft = draft.copy(weakSport = null) })
            }
            Note(
                when {
                    draft.weakSport != null || draft.coachedSports.size < 2 -> stringResource(R.string.week_setup_weak_note)
                    budget.weakSport != null -> stringResource(R.string.week_setup_weak_from_data, sportName(budget.weakSport!!).lowercase())
                    else -> stringResource(R.string.week_setup_weak_no_data)
                },
            )
        }

        TriCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.week_setup_hours), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Stepper(
                    value = stringResource(R.string.week_setup_hours_value, hoursText(hours)),
                    onMinus = { hours = (hours - HOURS_STEP).coerceAtLeast(MIN_HOURS) },
                    onPlus = { hours = (hours + HOURS_STEP).coerceAtMost(MAX_HOURS) },
                    minusLabel = stringResource(R.string.week_setup_hours_less),
                    plusLabel = stringResource(R.string.week_setup_hours_more),
                )
            }
            Note(stringResource(R.string.week_setup_hours_note), top = 6.dp)
            Label(stringResource(R.string.week_setup_rest_day))
            DayRow(
                selected = setOfNotNull(draft.restDay),
                color = MaterialTheme.colorScheme.primary,
                onToggle = { day -> draft = draft.copy(restDay = if (draft.restDay == day) null else day) },
            )
            HorizontalDivider(color = TriColors.border, modifier = Modifier.padding(top = 16.dp))
            Text(
                stringResource(R.string.week_setup_split),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            SplitBar(budget)
            Note(stringResource(R.string.week_setup_split_note), top = 12.dp)
        }

        TriRegia.SPORTS.forEach { sport ->
            val slot = draft.slot(sport) ?: SportSlot()
            val time = times.getValue(sport)
            val mode = draft.mode(sport)
            TriCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (mode == SportMode.PAUSED) SportColors.dim(sport) else SportColors.of(sport)))
                    Text(
                        sportName(sport),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (mode == SportMode.PAUSED) TriColors.textDim else MaterialTheme.colorScheme.onSurface,
                    )
                }
                ModeRow(
                    mode = mode,
                    label = stringResource(R.string.week_setup_mode_label, sportName(sport).lowercase()),
                    onSelect = { draft = draft.withMode(sport, it) },
                )
                if (mode == SportMode.PAUSED) {
                    Note(stringResource(R.string.week_setup_paused_note, sportName(sport)), top = 10.dp)
                } else {
                    if (mode == SportMode.OWN) Note(stringResource(R.string.week_setup_own_note, sportName(sport)), top = 10.dp)
                    Label(stringResource(if (mode == SportMode.OWN) R.string.week_setup_own_days else R.string.week_setup_days))
                    DayRow(
                        selected = slot.days,
                        color = SportColors.of(sport),
                        // With the trainer the days go in the calendar as they are, so never on the rest day.
                        disabled = if (mode == SportMode.OWN) setOfNotNull(draft.restDay) else emptySet(),
                        onToggle = { day ->
                            val days = if (day in slot.days) slot.days - day else slot.days + day
                            draft = draft.withSlot(sport, slot.copy(days = days))
                        },
                    )
                    if (slot.days.isEmpty()) {
                        Note(stringResource(if (mode == SportMode.OWN) R.string.week_setup_own_no_days else R.string.week_setup_any_day), top = 6.dp)
                    }
                    TriTextField(
                        value = time.value,
                        onValueChange = { time.value = it.take(5) },
                        label = stringResource(R.string.week_setup_time),
                        placeholder = stringResource(R.string.week_setup_time_hint),
                        isError = showErrors && sport in timeErrors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    when (sport.takeIf { mode == SportMode.AI || it == Sport.SWIM }) {
                        Sport.SWIM -> {
                            Label(stringResource(R.string.week_setup_pool))
                            Chips {
                                TrainingAvailability.POOL_LENGTHS.forEach { length ->
                                    TriChip(stringResource(R.string.week_setup_pool_value, length), selected = draft.poolLengthM == length, onClick = { draft = draft.copy(poolLengthM = length) })
                                }
                            }
                        }
                        Sport.BIKE -> {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                                Text(stringResource(R.string.week_setup_longest_ride), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Stepper(
                                    value = draft.longestRideMin?.let(::rideText) ?: stringResource(R.string.week_setup_longest_ride_free),
                                    onMinus = {
                                        val current = draft.longestRideMin ?: return@Stepper
                                        draft = draft.copy(longestRideMin = (current - RIDE_STEP).takeIf { it >= MIN_RIDE })
                                    },
                                    onPlus = {
                                        val next = draft.longestRideMin?.plus(RIDE_STEP) ?: DEFAULT_RIDE
                                        draft = draft.copy(longestRideMin = next.coerceAtMost(TrainingAvailability.LONGEST_RIDE_MIN.last))
                                    },
                                    minusLabel = stringResource(R.string.week_setup_ride_less),
                                    plusLabel = stringResource(R.string.week_setup_ride_more),
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .heightIn(min = 52.dp)
                                    .toggleable(value = draft.indoorTrainer, role = Role.Switch, onValueChange = { draft = draft.copy(indoorTrainer = it) }),
                            ) {
                                Text(stringResource(R.string.week_setup_trainer), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = draft.indoorTrainer,
                                    onCheckedChange = null,
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        uncheckedTrackColor = TriColors.track,
                                        uncheckedBorderColor = TriColors.borderStrong,
                                    ),
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }
            // The gym comes after the swim, as on the board: what the athlete does now first.
            if (sport == Sport.SWIM) {
                GymCard(
                    gym = draft.gym,
                    restDay = draft.restDay,
                    time = times.getValue(Sport.STRENGTH),
                    timeError = showErrors && Sport.STRENGTH in timeErrors,
                    onChange = { draft = draft.copy(gym = it) },
                )
            }
        }

        TriCard(padding = 14.dp) {
            Text(
                thresholdsText(stored),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        NoticeLine(
            state.notice ?: if (showErrors && (dateError || timeErrors.isNotEmpty())) Notice(R.string.week_setup_error, isError = true) else null,
        )
        PrimaryButton(
            text = stringResource(if (state.saving) R.string.profile_saving else R.string.profile_save),
            enabled = !state.saving,
            onClick = {
                if (dateError || timeErrors.isNotEmpty()) {
                    showErrors = true
                    return@PrimaryButton
                }
                val availability = TriRegia.SPORTS.fold(draft) { acc, sport ->
                    val slot = acc.slot(sport) ?: SportSlot()
                    acc.withSlot(sport, slot.copy(time = parseTime(times.getValue(sport).value)))
                }.let { it.copy(gym = it.gym.copy(time = parseTime(times.getValue(Sport.STRENGTH).value))) }
                val profile = stored.copy(
                    goal = goalValue,
                    raceName = raceName.trim(),
                    raceDate = dateValue,
                    weeklyHours = hours,
                )
                onSave(profile, availability)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val DEFAULT_HOURS = 6.0
private const val MIN_HOURS = 2.0
private const val MAX_HOURS = 30.0
private const val HOURS_STEP = 0.5
private const val DEFAULT_RIDE = 180
private const val MIN_RIDE = 60
private const val RIDE_STEP = 30

private fun parseTime(text: String): LocalTime? {
    val clean = text.trim().replace('.', ':')
    if (clean.isEmpty()) return null
    val padded = if (clean.length == 4 && clean[1] == ':') "0$clean" else clean
    return try {
        LocalTime.parse(padded)
    } catch (error: DateTimeParseException) {
        null
    }
}

private fun hoursText(hours: Double): String =
    if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString().replace('.', ',')

private fun rideText(minutes: Int): String = "${minutes / 60}h" + "%02d".format(minutes % 60)

@Composable
private fun sportName(sport: Sport): String = stringResource(
    when (sport) {
        Sport.SWIM -> R.string.week_setup_swim
        Sport.BIKE -> R.string.week_setup_bike
        Sport.STRENGTH -> R.string.week_setup_gym
        else -> R.string.week_setup_run
    },
)

@Composable
private fun thresholdsText(profile: AthleteProfile): String {
    val parts = listOfNotNull(
        profile.cssSecPer100m?.let { stringResource(R.string.week_setup_css, ProfileText.formatPace(it)) },
        profile.ftpWatts?.let { stringResource(R.string.week_setup_ftp, it) },
        profile.thresholdPaceSecPerKm?.let { stringResource(R.string.week_setup_run_pace, ProfileText.formatPace(it)) },
    )
    return if (parts.isEmpty()) stringResource(R.string.week_setup_thresholds_none)
    else stringResource(R.string.week_setup_thresholds, parts.joinToString(" · "))
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Note(text: String, top: androidx.compose.ui.unit.Dp = 8.dp) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top),
    )
}

/** A choice that is not there to take: the sport is on hold or with the athlete's trainer. */
@Composable
private fun OffChip(label: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .dashedBorder(TriColors.borderStrong, 10.dp)
            .clip(shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** A dashed outline, for what is shown but cannot be chosen. */
private fun Modifier.dashedBorder(color: Color, radius: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    val width = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(width / 2, width / 2),
        size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** Who looks after a sport: its AI coach, the athlete's trainer, or nobody for now. */
@Composable
private fun ModeRow(mode: SportMode, label: String, onSelect: (SportMode) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .selectableGroup()
            .semantics { contentDescription = label },
    ) {
        listOf(
            SportMode.AI to R.string.week_setup_mode_ai,
            SportMode.OWN to R.string.week_setup_mode_own,
            SportMode.PAUSED to R.string.week_setup_mode_paused,
        ).forEach { (item, text) ->
            val on = item == mode
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else TriColors.borderStrong, shape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(item) })
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    stringResource(text),
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) TriColors.accentText else TriColors.textDim,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** The gym: the athlete's own programme, on their own days. */
@Composable
private fun GymCard(
    gym: GymSlot,
    restDay: DayOfWeek?,
    time: androidx.compose.runtime.MutableState<String>,
    timeError: Boolean,
    onChange: (GymSlot) -> Unit,
) {
    val color = SportColors.strength
    TriCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .toggleable(value = gym.enabled, role = Role.Switch, onValueChange = { onChange(gym.copy(enabled = it)) }),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (gym.enabled) color else SportColors.dim(Sport.STRENGTH)))
            Text(stringResource(R.string.week_setup_gym), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.week_setup_gym_on), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(
                checked = gym.enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedTrackColor = TriColors.track,
                    uncheckedBorderColor = TriColors.borderStrong,
                ),
            )
        }
        if (!gym.enabled) {
            Note(stringResource(R.string.week_setup_gym_off_note), top = 6.dp)
        } else {
            Label(stringResource(R.string.week_setup_gym_days))
            DayRow(
                selected = gym.days,
                color = color,
                disabled = setOfNotNull(restDay),
                onToggle = { day -> onChange(gym.copy(days = if (day in gym.days) gym.days - day else gym.days + day)) },
            )
            if (gym.days.isEmpty()) Note(stringResource(R.string.week_setup_gym_no_days), top = 6.dp)
            TriTextField(
                value = time.value,
                onValueChange = { time.value = it.take(5) },
                label = stringResource(R.string.week_setup_time),
                placeholder = stringResource(R.string.week_setup_time_hint),
                isError = timeError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                Text(stringResource(R.string.week_setup_gym_minutes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Stepper(
                    value = stringResource(R.string.week_setup_gym_minutes_value, gym.minutes),
                    onMinus = { onChange(gym.copy(minutes = (gym.minutes - GymSlot.STEP_MINUTES).coerceAtLeast(GymSlot.MINUTES.first))) },
                    onPlus = { onChange(gym.copy(minutes = (gym.minutes + GymSlot.STEP_MINUTES).coerceAtMost(GymSlot.MINUTES.last))) },
                    minusLabel = stringResource(R.string.week_setup_gym_less),
                    plusLabel = stringResource(R.string.week_setup_gym_more),
                )
            }
            Note(stringResource(R.string.week_setup_gym_note), top = 12.dp)
        }
    }
}

/**
 * How the regia splits the week's hours: the athlete's own sessions first
 * (the gym, the sports with their trainer), then the sports of the AI
 * coaches, and what nobody can use yet.
 */
@Composable
private fun SplitBar(budget: WeekBudget) {
    val parts = buildList {
        if (budget.gymMinutes > 0) add(Triple(stringResource(R.string.week_setup_gym), budget.gymMinutes, SportColors.strength))
        TriRegia.SPORTS.forEach { sport ->
            val own = budget.ownMinutes[sport] ?: 0
            val coached = budget.sports[sport]?.minutes ?: 0
            if (own > 0) add(Triple(stringResource(R.string.week_setup_split_own, sportName(sport)), own, SportColors.of(sport)))
            else if (coached > 0) add(Triple(sportName(sport), coached, SportColors.of(sport)))
        }
        val free = budget.availableMinutes - budget.fixedMinutes - budget.totalMinutes
        if (free >= 5) add(Triple(stringResource(R.string.week_setup_split_free), free, TriColors.borderStrong))
    }
    if (parts.isEmpty()) return
    val description = parts.joinToString(", ") { (name, minutes, _) -> "$name ${rideText(minutes)}" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        parts.forEach { (_, minutes, color) ->
            Box(Modifier.weight(minutes.coerceAtLeast(1).toFloat()).fillMaxHeight().background(color))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp).clearAndSetSemantics { }) {
        parts.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { (name, minutes, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Text(name, fontSize = 12.sp, color = TriColors.textDim, modifier = Modifier.weight(1f))
                        Text(rideText(minutes), style = numberStyle(13.sp))
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** Seven day buttons, Monday first; the selected ones take [color]. */
@Composable
private fun DayRow(selected: Set<DayOfWeek>, color: Color, onToggle: (DayOfWeek) -> Unit, disabled: Set<DayOfWeek> = emptySet()) {
    val letters = stringResource(R.string.week_setup_day_letters)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEachIndexed { index, day ->
            val name = TriRegia.dayName(day)
            val letter = letters.getOrElse(index) { ' ' }.toString()
            val shape = RoundedCornerShape(12.dp)
            val on = day in selected && day !in disabled
            if (day in disabled) {
                // The rest day: shown, never chosen.
                val rest = stringResource(R.string.week_setup_day_rest, name)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .dashedBorder(TriColors.borderStrong, 12.dp)
                        .clearAndSetSemantics { contentDescription = rest },
                ) {
                    Text(letter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(shape)
                        .background(if (on) lerp(MaterialTheme.colorScheme.background, color, 0.22f) else Color.Transparent)
                        .border(1.dp, if (on) color else TriColors.borderStrong, shape)
                        .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(day) })
                        .semantics { contentDescription = name },
                ) {
                    Text(
                        letter,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) lerp(color, Color.White, 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusLabel: String, plusLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", minusLabel, onMinus)
        Text(value, style = numberStyle(20.sp), modifier = Modifier.padding(horizontal = 6.dp).width(64.dp), textAlign = TextAlign.Center)
        StepButton("+", plusLabel, onPlus)
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .border(1.dp, TriColors.borderStrong, CircleShape)
            .semantics { contentDescription = label },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
