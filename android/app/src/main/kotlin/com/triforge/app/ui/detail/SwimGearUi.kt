package com.triforge.app.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.common.BackButton
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.PrimaryButton
import com.triforge.app.ui.common.SectionLabel
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.Activity
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.SwimAnalysis
import com.triforge.domain.SwimGear
import com.triforge.domain.SwimGearGuess
import com.triforge.domain.SwimGearMarks
import com.triforge.domain.SwimRepeat
import com.triforge.domain.SwimSeries
import com.triforge.domain.SwimSets
import kotlin.math.roundToInt

/** The colours of what was swum with equipment (board 5e): the swim's blue, lightened, on its dark container. */
internal val GearColor = Color(0xFFB9E4FA)
internal val GearContainer = Color(0xFF10303F)
private val GearRow = Color(0xFF122733)
private val GearOutline = Color(0xFF2E86B3)
private val PlainRow = Color(0xFF1D2127)

@StringRes
internal fun SwimGear.labelRes(): Int = when (this) {
    SwimGear.FINS -> R.string.gear_fins
    SwimGear.PULL_BUOY -> R.string.gear_pull_buoy
    SwimGear.PADDLES -> R.string.gear_paddles
}

/** «pinne e palette»: the equipment in a sentence. */
@Composable
internal fun gearNames(gear: Set<SwimGear>): String {
    val names = gear.sortedBy { it.ordinal }.map { stringResource(it.labelRes()).lowercase() }
    return if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString(", ") + " " + stringResource(R.string.gear_and) + " " + names.last()
}

/** «pinne · palette», «pull · palette»: the equipment on a tag, short enough for a column of the table. */
@Composable
internal fun gearTag(gear: Set<SwimGear>): String = gear.sortedBy { it.ordinal }.map {
    stringResource(
        when (it) {
            SwimGear.FINS -> R.string.gear_fins_tag
            SwimGear.PULL_BUOY -> R.string.gear_pull_buoy_tag
            SwimGear.PADDLES -> R.string.gear_paddles_tag
        },
    )
}.joinToString(" · ")

private fun pace(seconds: Double): String = Formatters.minutesAndSeconds(seconds)

/** The guess worth putting in front of the athlete: repeats that look swum with equipment, not only a doubt about the stroke. */
internal fun proposal(activity: Activity): SwimGearGuess? = SwimSets.gearGuess(activity)?.takeIf { it.likely.isNotEmpty() }

/** «12 ripetute sembrano con attrezzatura». */
@Composable
internal fun gearGuessTitle(guess: SwimGearGuess): String =
    if (guess.likely.size == 1) stringResource(R.string.gear_guess_title_one) else stringResource(R.string.gear_guess_title, guess.likely.size)

/** Why the app thinks so, with the numbers it measured against, and what the answer changes. */
@Composable
internal fun gearGuessWhy(guess: SwimGearGuess): String =
    stringResource(R.string.gear_guess_why, pace(guess.usualPaceSecPer100), guess.usualStrokesPerLength.roundToInt()) +
        " " + stringResource(R.string.gear_guess_effect)

/**
 * Board 5e, the card of the swim detail: the question while nobody has
 * answered it, then what the athlete said, with the pace with equipment and
 * the athlete's own side by side.
 */
@Composable
internal fun SwimGearCard(activity: Activity, onEdit: () -> Unit, onNone: () -> Unit) {
    val marks = activity.gear
    val use = remember(activity.id, activity.laps, marks) { SwimAnalysis.gearUse(activity) }
    val guess = remember(activity.id, activity.laps, marks) { if (marks == null) proposal(activity) else null }
    TriCard {
        when {
            marks == null && guess != null -> {
                // Board 5f: the app says what it saw, the athlete confirms.
                CardHeader(stringResource(R.string.gear_title), trailing = stringResource(R.string.gear_to_confirm))
                Text(gearGuessTitle(guess), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                Caption(gearGuessWhy(guess))
                Row(
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SecondaryButton(stringResource(R.string.gear_check), onEdit, modifier = Modifier.weight(1f), compact = true)
                    QuietButton(stringResource(R.string.gear_question_no), onNone)
                }
            }

            marks == null -> {
                CardHeader(stringResource(R.string.gear_title), trailing = stringResource(R.string.gear_to_mark))
                Caption(stringResource(R.string.gear_ask_body))
                Row(
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SecondaryButton(stringResource(R.string.gear_mark), onEdit, modifier = Modifier.weight(1f), compact = true)
                    QuietButton(stringResource(R.string.gear_none_short), onNone)
                }
            }

            use == null -> {
                CardHeader(stringResource(R.string.gear_title), trailing = stringResource(R.string.gear_none_label))
                Row(
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.gear_none_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    QuietButton(stringResource(R.string.gear_edit), onEdit)
                }
            }

            else -> {
                CardHeader(stringResource(R.string.gear_title), trailing = gearNames(use.gear))
                val withPace = use.paceSecPer100?.let(::pace).orEmpty()
                val withMeters = Formatters.distance(Sport.SWIM, use.meters)
                val ownPace = use.ownPaceSecPer100?.let(::pace)
                val ownMeters = Formatters.distance(Sport.SWIM, use.ownMeters)
                val description = if (ownPace == null) null else stringResource(R.string.gear_tile_description, withPace, withMeters, ownPace, ownMeters)
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PaceTile(stringResource(R.string.gear_with), withPace, withMeters, assisted = true, modifier = Modifier.weight(1f))
                    if (ownPace != null) {
                        PaceTile(stringResource(R.string.gear_own), ownPace, ownMeters, assisted = false, modifier = Modifier.weight(1f))
                    }
                }
                Caption(stringResource(if (use.whole) R.string.gear_whole_caption else R.string.gear_partial_caption))
                SecondaryButton(stringResource(R.string.gear_edit), onEdit, modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), compact = true)
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

/** An action that asks for less attention than the button next to it. */
@Composable
internal fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, color = TriColors.textDim, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PaceTile(label: String, pace: String, meters: String, assisted: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (assisted) GearRow else PlainRow)
            .then(if (assisted) Modifier.border(1.dp, GearOutline, shape) else Modifier)
            .padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (assisted) GearColor else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(pace, style = numberStyle(22.sp), modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.gear_tile_sub, meters),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// What the page keeps while it is open, as text, so that it survives a rotation: "fins+paddles" and "3:fins+paddles;4:".
private fun encode(gear: Set<SwimGear>): String = gear.sortedBy { it.ordinal }.joinToString("+") { it.key }

private fun decode(text: String): Set<SwimGear> = text.split('+').mapNotNull { SwimGear.of(it) }.toSet()

// A repeat can be ticked before its equipment is chosen: "4:" keeps it.
private fun encode(marks: Map<Int, Set<SwimGear>>): String =
    marks.toSortedMap().entries.joinToString(";") { (number, gear) -> "$number:${encode(gear)}" }

private fun decodeMarks(text: String): Map<Int, Set<SwimGear>> = text.split(';').mapNotNull { entry ->
    if (':' !in entry) return@mapNotNull null
    entry.substringBefore(':').toIntOrNull()?.let { it to decode(entry.substringAfter(':')) }
}.toMap()

/**
 * What the app thinks of a series: nothing, that it looks swum with
 * equipment, that it may have been (the watch gave it another stroke), or
 * that it lies between series that look it.
 */
private enum class Look { PLAIN, LIKELY, MAYBE, BETWEEN }

/**
 * Board 5f, the page where the athlete says what a swim was swum with, by
 * series as in SwimForge: «8 × 50 m» is one tick and its equipment, and a
 * series opens to take one repeat out. On a swim nobody has answered
 * about, the series that look swum with equipment come ticked already,
 * with the equipment used last ([lastGear]): nothing is saved until the
 * athlete says so. «Tutta la nuotata» marks the whole swim, and is all
 * there is for a swim without laps.
 */
@Composable
internal fun SwimGearPage(
    activity: Activity,
    onSave: (SwimGearMarks) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    lastGear: Set<SwimGear> = emptySet(),
) {
    val pool = activity.poolLengthMeters
    val repeats = remember(activity.id, activity.laps) {
        SwimAnalysis.repeats(activity).filter { it.distanceMeters > 0 && it.durationSec > 0 }
    }
    val stored = activity.gear
    val guess = remember(activity.id, activity.laps) { if (stored == null) SwimSets.gearGuess(repeats, pool) else null }
    fun look(repeat: SwimRepeat): Look = when {
        guess == null -> Look.PLAIN
        repeat.number in guess.likely -> Look.LIKELY
        repeat.number in guess.maybe -> Look.MAYBE
        repeat.number in guess.between -> Look.BETWEEN
        else -> Look.PLAIN
    }
    // What was marked, or what the app guessed, stays in series of its own: one tick takes it all.
    val series = remember(activity.id, activity.laps) {
        SwimSets.series(repeats, pool) { stored?.repeats?.get(it.number).orEmpty() to look(it) }
    }
    val proposed = guess != null && guess.likely.isNotEmpty()

    val startMarks = remember(activity.id) {
        when {
            stored != null -> repeats.mapNotNull { r -> stored.repeats[r.number]?.takeIf { it.isNotEmpty() }?.let { r.number to it } }.toMap()
            guess != null -> guess.likely.associateWith { lastGear }
            else -> emptyMap()
        }
    }
    val startWhole = remember(activity.id) { stored?.session.orEmpty() }
    var marksText by rememberSaveable(activity.id) { mutableStateOf(encode(startMarks)) }
    var wholeText by rememberSaveable(activity.id) { mutableStateOf(encode(startWhole)) }
    var whole by rememberSaveable(activity.id) { mutableStateOf(repeats.isEmpty() || startWhole.isNotEmpty()) }
    // What a series ticked now starts with: the equipment chosen last on the page, then the one used last.
    var recentText by rememberSaveable(activity.id) { mutableStateOf(encode(lastGear)) }
    var openText by rememberSaveable(activity.id) { mutableStateOf("") }
    val marks = decodeMarks(marksText)
    val wholeGear = decode(wholeText)
    val recent = decode(recentText)
    val open = openText.split(',').mapNotNull { it.toIntOrNull() }.toSet()

    fun gearOf(one: SwimSeries): Set<SwimGear> =
        one.numbers.mapNotNull { marks[it] }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()

    fun tick(one: SwimSeries) {
        val all = one.numbers.all { it in marks }
        val start = gearOf(one).ifEmpty { recent }
        marksText = encode(if (all) marks - one.numbers.toSet() else marks + one.numbers.associateWith { marks[it] ?: start })
    }

    fun tick(one: SwimSeries, number: Int) {
        marksText = encode(if (number in marks) marks - number else marks + (number to gearOf(one).ifEmpty { recent }))
    }

    fun choose(one: SwimSeries, gear: SwimGear, on: Boolean) {
        val chosen = if (on) gearOf(one) + gear else gearOf(one) - gear
        marksText = encode(marks + one.numbers.filter { it in marks }.associateWith { chosen })
        if (chosen.isNotEmpty()) recentText = encode(chosen)
    }

    val ready = if (whole) wholeGear.isNotEmpty() else marks.isNotEmpty() && marks.values.all { it.isNotEmpty() }
    val markedMeters = if (whole) activity.distanceMeters else repeats.filter { it.number in marks }.sumOf { it.distanceMeters }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BackButton(onBack)
            Text(stringResource(R.string.gear_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (proposed) {
                Text(
                    stringResource(R.string.gear_page_proposed, pace(guess.usualPaceSecPer100), guess.usualStrokesPerLength.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            TriCard {
                if (repeats.isEmpty()) {
                    CardHeader(stringResource(R.string.gear_what))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(value = whole, role = Role.Switch, onValueChange = { whole = it }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.gear_whole_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.gear_whole_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // The row takes the touch: the switch itself stays passive.
                        Switch(
                            checked = whole,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = SportColors.swim,
                                checkedThumbColor = Color(0xFF0E1013),
                                uncheckedTrackColor = TriColors.track,
                                uncheckedBorderColor = TriColors.borderStrong,
                            ),
                        )
                    }
                }
                if (whole) {
                    GearChips(wholeGear, Modifier.padding(top = 12.dp)) { gear, on -> wholeText = encode(if (on) wholeGear + gear else wholeGear - gear) }
                    if (repeats.isEmpty()) Caption(stringResource(R.string.gear_whole_swim))
                }
            }
            if (!whole) {
                @Composable
                fun group(label: String?, vararg kinds: Look) {
                    val rows = series.filter { look(it.repeats.first()) in kinds }
                    if (rows.isEmpty()) return
                    if (label != null) SectionLabel(label)
                    rows.forEach { one ->
                        val first = one.numbers.first()
                        SeriesPick(
                            series = one,
                            marks = marks,
                            gear = gearOf(one),
                            open = first in open,
                            note = when (look(one.repeats.first())) {
                                Look.MAYBE -> maybeNote(one)
                                Look.BETWEEN -> stringResource(if (one.count == 1) R.string.gear_between_note_one else R.string.gear_between_note)
                                else -> null
                            },
                            onTick = { tick(one) },
                            onGear = { gear, on -> choose(one, gear, on) },
                            onOpen = { openText = (if (first in open) open - first else open + first).joinToString(",") },
                            onRepeat = { tick(one, it) },
                        )
                    }
                }
                val maybe = series.count { look(it.repeats.first()).let { kind -> kind == Look.MAYBE || kind == Look.BETWEEN } }
                group(stringResource(R.string.gear_section_likely), Look.LIKELY)
                group(stringResource(if (maybe == 1) R.string.gear_section_maybe_one else R.string.gear_section_maybe), Look.MAYBE, Look.BETWEEN)
                group(stringResource(if (guess == null) R.string.gear_section_all else R.string.gear_section_rest), Look.PLAIN)
            }
            QuietButton(stringResource(R.string.gear_none_long), { onSave(SwimGearMarks.NONE) }, Modifier.fillMaxWidth())
        }
        // What is about to be saved stays in sight while the list scrolls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                when {
                    whole && wholeGear.isEmpty() -> stringResource(R.string.gear_footer_choose_whole)
                    whole -> stringResource(R.string.gear_footer_whole, Formatters.distance(Sport.SWIM, markedMeters))
                    marks.isEmpty() -> stringResource(R.string.gear_footer_none)
                    !ready -> stringResource(R.string.gear_footer_choose)
                    marks.size == 1 -> stringResource(R.string.gear_footer_one, Formatters.distance(Sport.SWIM, markedMeters))
                    else -> stringResource(R.string.gear_footer, marks.size, Formatters.distance(Sport.SWIM, markedMeters))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (!ready && (whole || marks.isNotEmpty())) GearColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                stringResource(R.string.gear_save),
                onClick = { onSave(if (whole) SwimGearMarks(session = wholeGear) else SwimGearMarks(repeats = marks)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = ready,
            )
        }
    }
}

/** Why a series is only a maybe: the watch gave it another stroke, and with that stroke nobody uses fins. */
@Composable
private fun maybeNote(series: SwimSeries): String {
    val stroke = stringResource(
        when (series.otherStroke) {
            "breast" -> R.string.gear_stroke_breast
            "back" -> R.string.gear_stroke_back
            "fly" -> R.string.gear_stroke_fly
            else -> R.string.gear_stroke_other
        },
    )
    return stringResource(if (series.count == 1) R.string.gear_maybe_note_one else R.string.gear_maybe_note, stroke)
}

/** The three pieces of equipment to switch on and off. */
@Composable
private fun GearChips(chosen: Set<SwimGear>, modifier: Modifier = Modifier, onToggle: (SwimGear, Boolean) -> Unit) {
    Row(modifier = modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SwimGear.entries.forEach { gear ->
            GearChip(stringResource(gear.labelRes()), selected = gear in chosen) { on -> onToggle(gear, on) }
        }
    }
}

@Composable
private fun GearChip(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) GearContainer else Color.Transparent)
            .border(1.dp, if (selected) SportColors.swim else TriColors.borderStrong, shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onToggle)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected) Icon(TriIcons.Check, contentDescription = null, tint = GearColor, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) GearColor else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** The tick of a series or of a repeat: full, a dash when only some of its repeats are ticked, or empty. */
@Composable
private fun TickBox(state: ToggleableState) {
    val on = state != ToggleableState.Off
    val box = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .size(22.dp)
            .clip(box)
            .background(if (on) SportColors.swim else Color.Transparent)
            .then(if (on) Modifier else Modifier.border(2.dp, Color(0xFF5E6672), box)),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ToggleableState.On -> Icon(TriIcons.Check, contentDescription = null, tint = Color(0xFF0E1013), modifier = Modifier.size(14.dp))
            ToggleableState.Indeterminate -> Box(Modifier.size(width = 10.dp, height = 2.dp).background(Color(0xFF0E1013)))
            ToggleableState.Off -> Unit
        }
    }
}

/**
 * One series to tick: «8 × 50 m», where it is in the swim, its pace and
 * strokes. Ticked, it shows its equipment; with more than one repeat it
 * opens, to take out the one swum without.
 */
@Composable
private fun SeriesPick(
    series: SwimSeries,
    marks: Map<Int, Set<SwimGear>>,
    gear: Set<SwimGear>,
    open: Boolean,
    note: String?,
    onTick: () -> Unit,
    onGear: (SwimGear, Boolean) -> Unit,
    onOpen: () -> Unit,
    onRepeat: (Int) -> Unit,
) {
    val ticked = series.numbers.count { it in marks }
    val state = when (ticked) {
        0 -> ToggleableState.Off
        series.count -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val shape = RoundedCornerShape(16.dp)
    val title = stringResource(R.string.gear_series_title, series.count, series.distanceMeters.roundToInt())
    val where = if (series.count == 1) {
        stringResource(R.string.gear_series_repeat, series.numbers.first())
    } else {
        stringResource(R.string.gear_series_repeats, series.numbers.first(), series.numbers.last())
    }
    val numbers = listOfNotNull(
        where,
        stringResource(R.string.gear_series_pace, pace(series.paceSecPer100)),
        series.strokesPerLength?.let { stringResource(R.string.gear_series_strokes, it.roundToInt()) },
    ).joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (ticked > 0) GearRow else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (ticked > 0) GearOutline else TriColors.border, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .triStateToggleable(state = state, role = Role.Checkbox, onClick = onTick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TickBox(state)
            Column(Modifier.weight(1f)) {
                Text(
                    if (state == ToggleableState.Indeterminate) title + " · " + stringResource(R.string.gear_series_part, ticked, series.count) else title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(numbers, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (ticked > 0) GearChips(gear, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), onGear)
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            )
        }
        if (series.count > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = open, role = Role.Button, onValueChange = { onOpen() })
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (open) stringResource(R.string.gear_series_close) else stringResource(R.string.gear_series_open, series.count),
                    style = MaterialTheme.typography.labelLarge,
                    color = TriColors.textDim,
                )
                Icon(
                    TriIcons.ChevronDown,
                    contentDescription = null,
                    tint = TriColors.textDim,
                    modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
                )
            }
            if (open) {
                series.repeats.forEach { repeat ->
                    RepeatPick(repeat, series.poolLengthMeters, ticked = repeat.number in marks) { onRepeat(repeat.number) }
                }
            }
        }
    }
}

/** One repeat of an open series: its number, its time and its strokes. */
@Composable
private fun RepeatPick(repeat: SwimRepeat, poolLengthMeters: Double?, ticked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = ticked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TickBox(if (ticked) ToggleableState.On else ToggleableState.Off)
        Text(
            stringResource(R.string.gear_repeat, repeat.number),
            style = MaterialTheme.typography.bodyMedium,
            color = if (ticked) MaterialTheme.colorScheme.onSurface else TriColors.textDim,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            listOfNotNull(
                Formatters.clock(repeat.durationSec.roundToInt().toLong()),
                SwimSets.strokesPerLength(repeat, poolLengthMeters)?.let { stringResource(R.string.gear_repeat_strokes, it.roundToInt()) },
            ).joinToString(" · "),
            style = numberStyle(14.sp),
            color = TriColors.textDim,
            maxLines = 1,
        )
    }
}
