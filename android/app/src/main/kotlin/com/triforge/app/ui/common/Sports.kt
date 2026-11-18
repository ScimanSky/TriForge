package com.triforge.app.ui.common

import androidx.annotation.StringRes
import com.triforge.app.R
import com.triforge.domain.Sport

@StringRes
fun Sport.labelRes(): Int = when (this) {
    Sport.SWIM -> R.string.sport_swim
    Sport.BIKE -> R.string.sport_bike
    Sport.RUN -> R.string.sport_run
    Sport.OTHER -> R.string.sport_other
}
