package com.triforge.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.triforge.app.R
import com.triforge.domain.Activity
import com.triforge.domain.OtherKind
import com.triforge.domain.Sport
import java.util.Locale

@StringRes
fun Sport.labelRes(): Int = when (this) {
    Sport.SWIM -> R.string.sport_swim
    Sport.BIKE -> R.string.sport_bike
    Sport.RUN -> R.string.sport_run
    Sport.STRENGTH -> R.string.sport_strength
    Sport.OTHER -> R.string.sport_other
}

/** What an activity outside the three sports and the gym was. */
@StringRes
fun OtherKind.labelRes(): Int = when (this) {
    OtherKind.WALK -> R.string.kind_walk
    OtherKind.HIKE -> R.string.kind_hike
    OtherKind.MOBILITY -> R.string.kind_mobility
    OtherKind.CARDIO -> R.string.kind_cardio
    OtherKind.GENERIC -> R.string.kind_generic
}

/** The name of an activity when it has none of its own: the sport, or what kind of other activity it was. */
@StringRes
fun Activity.kindLabelRes(): Int = if (sport == Sport.OTHER) kind.labelRes() else sport.labelRes()

/** "1 seduta" or "4 sedute": the app's two forms of a count. */
@Composable
fun countLabel(count: Int, @StringRes one: Int, @StringRes many: Int): String =
    if (count == 1) stringResource(one) else stringResource(many, count)

/** A whole number with the Italian thousands point: 17.280. */
fun wholeNumber(value: Int): String = "%,d".format(Locale.ITALY, value)
