package com.triforge.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.R
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.Activity
import com.triforge.domain.ActivitySource
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALY)

/** "Lun 17 nov", in the phone's time zone. */
fun Activity.dayLabel(): String =
    dayFormat.format(startTime.atZone(ZoneId.systemDefault())).replaceFirstChar { it.titlecase(Locale.ITALY) }

@Composable
fun Activity.sourceLabel(): String = stringResource(
    if (source == ActivitySource.GARMIN) R.string.source_garmin else R.string.source_health_connect,
)

/**
 * An activity in a list: sport, title and day on the left, the headline
 * number on the right. With [load], the session's load in the app's scale,
 * the line under the number says what it counted for instead of the pace.
 */
@Composable
fun ActivityRow(activity: Activity, onClick: () -> Unit, modifier: Modifier = Modifier, load: Int? = null) {
    val day = remember(activity.startTime) { activity.dayLabel() }
    val hasDistance = activity.distanceMeters > 0
    val headline = if (hasDistance) {
        Formatters.distance(activity.sport, activity.distanceMeters)
    } else {
        Formatters.duration(activity.durationSec)
    }
    val detail = listOfNotNull(
        Formatters.duration(activity.durationSec).takeIf { hasDistance },
        activity.sets?.takeIf { activity.sport == Sport.STRENGTH }?.let { stringResource(R.string.activity_sets, it) },
        if (load != null) stringResource(R.string.activity_load, load) else Formatters.pace(activity.sport, activity.distanceMeters, activity.paceSec),
    ).joinToString(" · ")

    ListRow(modifier = modifier, onClick = onClick) {
        SportIconBox(activity.sport)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = activity.name.ifBlank { stringResource(activity.kindLabelRes()) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$day · ${activity.sourceLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = headline, style = numberStyle(17.sp), maxLines = 1)
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
