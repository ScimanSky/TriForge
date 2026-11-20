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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.common.BackButton
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.PrimaryButton
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
import com.triforge.domain.SwimGearMarks
import com.triforge.domain.SwimRepeat
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

/**
 * Board 5e, the card of the swim detail: the question while nobody has
 * answered it, then what the athlete said, with the pace with equipment and
 * the athlete's own side by side.
 */
@Composable
internal fun SwimGearCard(activity: Activity, onEdit: () -> Unit, onNone: () -> Unit) {
    val marks = activity.gear
    val use = remember(activity.id, activity.laps, marks) { SwimAnalysis.gearUse(activity) }
    TriCard {
        when {
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

/** The tag of a repeat with equipment, in the table of the detail and in the list of the page. */
@Composable
internal fun GearTag(gear: Set<SwimGear>, modifier: Modifier = Modifier) {
    Text(
        gearTag(gear),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = GearColor,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(GearContainer).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

// What the page keeps while it is open, as text, so that it survives a rotation: "fins+paddles" and "3:fins+paddles;4:fins".
private fun encode(gear: Set<SwimGear>): String = gear.sortedBy { it.ordinal }.joinToString("+") { it.key }

private fun decode(text: String): Set<SwimGear> = text.split('+').mapNotNull { SwimGear.of(it) }.toSet()

private fun encode(marks: Map<Int, Set<SwimGear>>): String =
    marks.filterValues { it.isNotEmpty() }.toSortedMap().entries.joinToString(";") { (number, gear) -> "$number:${encode(gear)}" }

private fun decodeMarks(text: String): Map<Int, Set<SwimGear>> = text.split(';').mapNotNull { entry ->
    val number = entry.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
    decode(entry.substringAfter(':', "")).takeIf { it.isNotEmpty() }?.let { number to it }
}.toMap()

/**
 * Board 5e, the page where the athlete says what a swim was swum with:
 * the equipment on top, then the repeats to touch. Each touch gives a
 * repeat the equipment chosen above, or takes it off when it has it
 * already, so that different repeats can have different equipment. A swim
 * without laps can only be marked as a whole.
 */
@Composable
internal fun SwimGearPage(
    activity: Activity,
    onSave: (SwimGearMarks) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repeats = remember(activity.id, activity.laps) {
        SwimAnalysis.repeats(activity).filter { it.distanceMeters > 0 && it.durationSec > 0 }
    }
    val stored = activity.gear
    // What was marked on most repeats is what the athlete most likely goes on with.
    val startChoice = remember(activity.id) {
        stored?.session?.takeIf { it.isNotEmpty() }
            ?: stored?.repeats?.values?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key
            ?: emptySet()
    }
    val startMarks = remember(activity.id) {
        if (stored == null) emptyMap() else repeats.associate { it.number to stored.of(it.number) }.filterValues { it.isNotEmpty() }
    }
    var choiceText by rememberSaveable(activity.id) { mutableStateOf(encode(startChoice)) }
    var marksText by rememberSaveable(activity.id) { mutableStateOf(encode(startMarks)) }
    val choice = decode(choiceText)
    val marks = decodeMarks(marksText)

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
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TriCard {
                CardHeader(stringResource(R.string.gear_what))
                Row(
                    modifier = Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SwimGear.entries.forEach { gear ->
                        GearChip(stringResource(gear.labelRes()), selected = gear in choice) { on ->
                            choiceText = encode(if (on) choice + gear else choice - gear)
                        }
                    }
                }
                if (repeats.isEmpty()) Caption(stringResource(R.string.gear_whole_swim))
            }
            if (repeats.isNotEmpty()) {
                TriCard {
                    val meters = repeats.filter { it.number in marks }.sumOf { it.distanceMeters }
                    CardHeader(
                        stringResource(R.string.gear_where),
                        trailing = stringResource(R.string.gear_where_count, marks.size, repeats.size, Formatters.distance(Sport.SWIM, meters)),
                    )
                    Caption(stringResource(if (choice.isEmpty()) R.string.gear_where_choose else R.string.gear_where_body))
                    Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            stringResource(R.string.gear_all),
                            onClick = { marksText = encode(repeats.associate { it.number to choice }) },
                            modifier = Modifier.weight(1f),
                            enabled = choice.isNotEmpty(),
                            compact = true,
                        )
                        SecondaryButton(
                            stringResource(R.string.gear_clear),
                            onClick = { marksText = "" },
                            modifier = Modifier.weight(1f),
                            enabled = marks.isNotEmpty(),
                            compact = true,
                        )
                    }
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeats.forEach { repeat ->
                            val own = marks[repeat.number].orEmpty()
                            RepeatPick(repeat, own, enabled = choice.isNotEmpty() || own.isNotEmpty()) {
                                // The same equipment again takes it off; another one takes its place.
                                marksText = encode(if (own == choice || choice.isEmpty()) marks - repeat.number else marks + (repeat.number to choice))
                            }
                        }
                    }
                }
            }
            PrimaryButton(
                stringResource(R.string.gear_save),
                onClick = {
                    onSave(if (repeats.isEmpty()) SwimGearMarks(session = choice) else SwimGearMarks(repeats = marks))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            QuietButton(stringResource(R.string.gear_none_long), { onSave(SwimGearMarks.NONE) }, Modifier.fillMaxWidth())
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
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected) Icon(TriIcons.Check, contentDescription = null, tint = GearColor, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) GearColor else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** One repeat to touch: its number and distance, its pace, and the equipment it has. */
@Composable
private fun RepeatPick(repeat: SwimRepeat, gear: Set<SwimGear>, enabled: Boolean, onToggle: () -> Unit) {
    val marked = gear.isNotEmpty()
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (marked) GearRow else PlainRow)
            .then(if (marked) Modifier.border(1.dp, GearOutline, shape) else Modifier)
            .toggleable(value = marked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val box = RoundedCornerShape(7.dp)
        Box(
            Modifier
                .size(22.dp)
                .clip(box)
                .background(if (marked) SportColors.swim else Color.Transparent)
                .then(if (marked) Modifier else Modifier.border(2.dp, Color(0xFF5E6672), box)),
            contentAlignment = Alignment.Center,
        ) {
            if (marked) Icon(TriIcons.Check, contentDescription = null, tint = Color(0xFF0E1013), modifier = Modifier.size(14.dp))
        }
        Text("${repeat.number} · ${repeat.distanceMeters.roundToInt()} m", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        Text(pace(repeat.paceSecPer100), style = numberStyle(14.sp), color = if (marked) MaterialTheme.colorScheme.onSurface else TriColors.textDim)
        if (marked) GearTag(gear)
    }
}
