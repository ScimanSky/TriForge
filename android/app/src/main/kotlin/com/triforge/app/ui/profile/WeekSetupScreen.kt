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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.triforge.domain.coach.RecentTraining
import com.triforge.domain.coach.SportSlot
import com.triforge.domain.coach.TrainingAvailability
import com.triforge.domain.coach.TriRegia
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * "Giorni, orari e gara" (board 8): the race and its distance, the weak
 * sport, the weekly hours, the preferred rest day and, for each sport, the
 * days and usual time. The regia of the coach starts from here.
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
        TriRegia.SPORTS.associateWith { sport -> mutableStateOf(state.availability.slot(sport)?.time?.toString().orEmpty()) }
    }
    var showErrors by remember { mutableStateOf(false) }

    val goalValue = RaceGoal.entries.firstOrNull { it.key == goal }
    val dateValue = ProfileText.parseDate(raceDate)
    val dateError = raceDate.isNotBlank() && dateValue == null
    val timeErrors = TriRegia.SPORTS.filter { times.getValue(it).value.isNotBlank() && parseTime(times.getValue(it).value) == null }.toSet()
    val zone = remember { ZoneId.systemDefault() }
    val recent = remember(activities) { RecentTraining.of(activities, TrainingStats.weekStart(LocalDate.now(zone)), zone) }
    val weak = draft.weakSport ?: TriRegia.weakFromData(recent, goalValue)
    val shares = TriRegia.shares(goalValue, weak)

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
                    TriChip(sportName(sport), selected = draft.weakSport == sport, onClick = { draft = draft.copy(weakSport = sport) })
                }
                TriChip(stringResource(R.string.week_setup_weak_data), selected = draft.weakSport == null, onClick = { draft = draft.copy(weakSport = null) })
            }
            HorizontalDivider(color = TriColors.border, modifier = Modifier.padding(top = 16.dp))
            Text(
                stringResource(R.string.week_setup_shares),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            ShareBar(shares)
            if (draft.weakSport == null) {
                Text(
                    weak?.let { stringResource(R.string.week_setup_weak_from_data, sportName(it).lowercase()) }
                        ?: stringResource(R.string.week_setup_weak_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
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
            Label(stringResource(R.string.week_setup_rest_day))
            DayRow(
                selected = setOfNotNull(draft.restDay),
                color = MaterialTheme.colorScheme.primary,
                onToggle = { day -> draft = draft.copy(restDay = if (draft.restDay == day) null else day) },
            )
        }

        TriRegia.SPORTS.forEach { sport ->
            val slot = draft.slot(sport) ?: SportSlot()
            val time = times.getValue(sport)
            TriCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(SportColors.of(sport)))
                    Text(sportName(sport), style = MaterialTheme.typography.titleMedium)
                }
                Label(stringResource(R.string.week_setup_days))
                DayRow(
                    selected = slot.days,
                    color = SportColors.of(sport),
                    onToggle = { day ->
                        val days = if (day in slot.days) slot.days - day else slot.days + day
                        draft = draft.withSlot(sport, slot.copy(days = days))
                    },
                )
                if (slot.days.isEmpty()) {
                    Text(
                        stringResource(R.string.week_setup_any_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
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
                when (sport) {
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
                }
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

/** The three sports' shares of the week, as one bar. */
@Composable
private fun ShareBar(shares: Map<Sport, Double>) {
    val percent = TriRegia.SPORTS.associateWith { ((shares[it] ?: 0.0) * 100).roundToInt() }
    val description = TriRegia.SPORTS.joinToString(", ") { "${sportName(it)} ${percent.getValue(it)}%" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TriRegia.SPORTS.forEach { sport ->
            val weight = percent.getValue(sport).coerceAtLeast(1).toFloat()
            Box(Modifier.weight(weight).fillMaxHeight().background(SportColors.of(sport)))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
        TriRegia.SPORTS.forEach { sport ->
            Text("${sportName(sport)} ${percent.getValue(sport)}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Seven day buttons, Monday first; the selected ones take [color]. */
@Composable
private fun DayRow(selected: Set<DayOfWeek>, color: Color, onToggle: (DayOfWeek) -> Unit) {
    val letters = stringResource(R.string.week_setup_day_letters)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEachIndexed { index, day ->
            val on = day in selected
            val name = TriRegia.dayName(day)
            val shape = RoundedCornerShape(12.dp)
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
                    letters.getOrElse(index) { ' ' }.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) lerp(color, Color.White, 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
