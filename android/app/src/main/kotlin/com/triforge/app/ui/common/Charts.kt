package com.triforge.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.domain.Sport
import com.triforge.domain.TrainingStats
import com.triforge.domain.WeekSummary
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weekLabel = DateTimeFormatter.ofPattern("d/M", Locale.ITALY)

/**
 * Weekly training time as stacked bars, one segment per discipline.
 * [description] is what a screen reader says in place of the drawing.
 */
@Composable
fun StackedWeekBars(trend: List<WeekSummary>, description: String, modifier: Modifier = Modifier) {
    val sports = TrainingStats.triathlonSports + Sport.OTHER
    val peak = (trend.maxOfOrNull { it.durationSec } ?: 0L).coerceAtLeast(1L)

    Column(modifier = modifier.semantics { contentDescription = description }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            trend.forEach { week ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // Empty space above the bar, then one segment per sport.
                    val idle = (peak - week.durationSec).toFloat()
                    if (idle > 0f) Box(Modifier.weight(idle))
                    if (week.durationSec > 0) {
                        Column(
                            Modifier
                                .weight(week.durationSec.toFloat())
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp)),
                        ) {
                            sports.forEach { sport ->
                                val seconds = week.of(sport).durationSec.toFloat()
                                if (seconds > 0f) {
                                    Box(
                                        Modifier
                                            .weight(seconds)
                                            .fillMaxWidth()
                                            .background(SportColors.of(sport)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        BarLabels(trend.map { weekLabel.format(it.weekStart) }, Modifier.padding(top = 6.dp))
    }
}

/**
 * One bar per week of a single sport. The last bar, the current week, is in
 * the sport's full color and the earlier ones muted, so the eye lands on now.
 */
@Composable
fun VolumeBars(
    values: List<Float>,
    bottomLabels: List<String>,
    color: Color,
    dimColor: Color,
    description: String,
    modifier: Modifier = Modifier,
    topLabels: List<String>? = null,
) {
    val peak = (values.maxOrNull() ?: 0f).takeIf { it > 0f } ?: 1f

    Column(modifier = modifier.semantics { contentDescription = description }) {
        if (topLabels != null) BarLabels(topLabels, Modifier.padding(bottom = 4.dp), strong = true)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                // A week with any training keeps a visible stub of a bar.
                val fraction = if (value > 0f) (value / peak).coerceIn(0.04f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (fraction > 0f) fraction else 0.02f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    fraction == 0f -> TriColors.track
                                    index == values.lastIndex -> color
                                    else -> dimColor
                                },
                            ),
                    )
                }
            }
        }
        BarLabels(bottomLabels, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BarLabels(labels: List<String>, modifier: Modifier = Modifier, strong: Boolean = false) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (strong) TriColors.textDim else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** One line of a [LineChart]. A null value is a gap, such as a week without training. */
data class LineSeries(
    val values: List<Float?>,
    val color: Color,
    val dashed: Boolean = false,
    val filled: Boolean = false,
)

/**
 * Lines over evenly spaced points, all on one vertical scale. With [invert]
 * smaller values are drawn higher, which is how a pace reads: faster is up.
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    description: String,
    modifier: Modifier = Modifier,
    invert: Boolean = false,
) {
    val all = series.flatMap { it.values.filterNotNull() }
    if (all.isEmpty()) return
    val lowest = all.min()
    val span = (all.max() - lowest).takeIf { it > 0f } ?: 1f
    val gridColor = TriColors.border

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = description },
    ) {
        val inset = 8.dp.toPx()
        val plotHeight = size.height - inset * 2
        val plotWidth = size.width - inset * 2

        for (line in 0..2) {
            val y = inset + plotHeight * line / 2f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        for (item in series) {
            val count = item.values.size
            val points = item.values.mapIndexedNotNull { index, value ->
                if (value == null) return@mapIndexedNotNull null
                val t = (value - lowest) / span
                val x = if (count > 1) inset + plotWidth * index / (count - 1) else size.width / 2f
                val y = inset + plotHeight * (if (invert) t else 1f - t)
                Offset(x, y)
            }
            if (points.isEmpty()) continue

            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                if (item.filled) {
                    val area = Path().apply {
                        moveTo(points.first().x, size.height)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, size.height)
                        close()
                    }
                    drawPath(area, item.color.copy(alpha = 0.12f))
                }
                drawPath(
                    path = path,
                    color = item.color,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (item.dashed) {
                            PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                        } else {
                            null
                        },
                    ),
                )
            }
            // The latest point is the one the athlete is asking about.
            drawCircle(item.color, radius = 4.dp.toPx(), center = points.last())
        }
    }
}
