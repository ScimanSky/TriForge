package com.triforge.app.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.KeyValueRow
import com.triforge.app.ui.common.SecondaryButton
import com.triforge.app.ui.common.StatCell
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.common.VolumeBars
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.CssConfidence
import com.triforge.domain.CssEstimate
import com.triforge.domain.Formatters
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val effortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ITALY)
private val weekLabel = DateTimeFormatter.ofPattern("d/M", Locale.ITALY)

private fun pace(seconds: Double): String = Formatters.minutesAndSeconds(seconds)

private fun twoDecimals(value: Double): String = "%.2f".format(Locale.ITALY, value)

/**
 * The CSS the app reads from the athlete's best repeats, next to the one in
 * the profile, with what the difference means and a button to adopt it.
 * Hidden when the two agree. It is an estimate: the button is a quiet one,
 * and there is none when a longer swim contradicts it.
 */
@Composable
internal fun CssEstimateCard(estimate: CssEstimate, profileCss: Int?, onUse: (Int) -> Unit) {
    if (profileCss != null && abs(profileCss - estimate.paceSecPer100) < 1) return
    val css = pace(estimate.paceSecPer100.toDouble())

    TriCard {
        CardHeader(
            title = stringResource(R.string.swim_css_title),
            trailing = stringResource(
                if (estimate.confidence == CssConfidence.LOW) R.string.swim_css_confidence_low else R.string.swim_css_confidence_medium,
            ),
        )
        Row(
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(css, style = numberStyle(32.sp), color = SportColors.swim)
            Text(
                stringResource(R.string.swim_per_100),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(bottom = 6.dp),
            )
            Text(
                text = profileCss?.let { stringResource(R.string.swim_css_in_profile, pace(it.toDouble())) }
                    ?: stringResource(R.string.swim_css_no_profile),
                style = MaterialTheme.typography.bodySmall,
                color = TriColors.textDim,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Column(modifier = Modifier.padding(top = 8.dp)) {
            estimate.efforts.forEach { effort ->
                KeyValueRow(
                    stringResource(R.string.swim_css_best, effort.distanceMeters),
                    "${Formatters.clock(effort.seconds.roundToInt().toLong())} · " +
                        effortDate.format(effort.date.atZone(ZoneId.systemDefault())),
                )
            }
        }
        Text(
            text = stringResource(R.string.swim_css_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        cssDifference(estimate, profileCss)?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        // An estimate, not a test: the athlete chooses, and the button does not push.
        if (estimate.confidence != CssConfidence.LOW) {
            SecondaryButton(
                text = stringResource(R.string.swim_css_use, css),
                onClick = { onUse(estimate.paceSecPer100) },
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            )
        }
    }
}

/** What the estimate means next to the threshold of the profile, in a sentence or two. Null without a profile value. */
@Composable
private fun cssDifference(estimate: CssEstimate, profileCss: Int?): String? = when {
    estimate.confidence == CssConfidence.LOW -> stringResource(R.string.swim_css_low)
    profileCss == null -> null
    estimate.paceSecPer100 > profileCss -> stringResource(R.string.swim_css_slower, pace(profileCss.toDouble()))
    else -> stringResource(R.string.swim_css_faster, pace(profileCss.toDouble()))
}

/** Metres per stroke week by week, freestyle in the pool. */
@Composable
internal fun StrokeLengthCard(weeks: List<Pair<LocalDate, Double?>>, strokeRate: Int?) {
    val known = weeks.mapNotNull { it.second }
    if (known.size < 2) return
    val floor = (known.min() - 0.1).coerceAtLeast(0.0)
    val change = known.last() - known.first()

    TriCard {
        CardHeader(
            title = stringResource(R.string.swim_dps_title),
            trailing = stringResource(R.string.swim_dps_scope),
        )
        VolumeBars(
            values = weeks.map { (_, value) -> value?.let { (it - floor).toFloat() } ?: 0f },
            topLabels = weeks.map { (_, value) -> value?.let(::twoDecimals).orEmpty() },
            bottomLabels = weeks.map { weekLabel.format(it.first) },
            color = SportColors.swim,
            description = stringResource(
                R.string.swim_dps_chart,
                weeks.size,
                twoDecimals(known.first()),
                twoDecimals(known.last()),
            ),
            modifier = Modifier.padding(top = 14.dp),
        )
        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCell(
                value = (if (change >= 0) "+" else "−") + stringResource(R.string.unit_m_value, twoDecimals(abs(change))),
                label = stringResource(R.string.swim_dps_change, weeks.size),
                valueColor = if (change >= 0) TriColors.good else TriColors.warn,
                modifier = Modifier.weight(1f),
            )
            StatCell(
                value = strokeRate?.toString() ?: stringResource(R.string.value_none),
                label = stringResource(R.string.swim_efficiency_rate),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.swim_dps_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
