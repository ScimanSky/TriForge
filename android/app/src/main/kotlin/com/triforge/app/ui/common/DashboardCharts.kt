package com.triforge.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.ui.theme.Inter
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.domain.DayTraining
import com.triforge.domain.FitnessPoint
import com.triforge.domain.Sport
import com.triforge.domain.TrainingStats
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

/** Small grey text for chart axes. */
private val axisStyle = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = Color(0xFF9AA3AF))

/** Width reserved on the right of a chart for the axis labels. */
private val AxisGutter = 30.dp

/** Rounds a chart's top value up to a step that reads well: 10, 25, 50, 100... */
internal fun niceCeiling(value: Float): Float {
    if (value <= 0f) return 10f
    val steps = listOf(10f, 20f, 25f, 50f, 75f, 100f, 150f, 200f, 250f, 300f, 400f, 500f)
    return steps.firstOrNull { it >= value } ?: (ceil(value / 100f) * 100f)
}

private fun DrawScope.axisLabel(measurer: TextMeasurer, text: String, x: Float, y: Float) {
    val layout = measurer.measure(text, axisStyle)
    drawText(layout, topLeft = Offset(x, y - layout.size.height / 2f))
}

/**
 * Fitness, acute load and the load of each day, on one 0-based scale (all
 * three are TSS-equivalents per day, so one axis is honest). Daily load is
 * thin grey bars: they show the rhythm of hard and easy days.
 */
@Composable
fun LoadChart(
    points: List<FitnessPoint>,
    fitnessColor: Color,
    fatigueColor: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val grid = TriColors.border
    val barColor = Color(0xFF9AA3AF).copy(alpha = 0.22f)
    val top = niceCeiling(points.maxOf { max(max(it.fitness, it.fatigue), it.dailyLoad).toFloat() })

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .semantics { contentDescription = description },
    ) {
        val gutter = AxisGutter.toPx()
        val plotW = size.width - gutter
        val padTop = 6.dp.toPx()
        val plotH = size.height - padTop
        fun y(v: Double) = padTop + plotH * (1f - (v.toFloat() / top).coerceIn(0f, 1f))
        val step = plotW / points.size
        fun x(i: Int) = step * i + step / 2

        listOf(0f, top / 2, top).forEach { tick ->
            val ty = padTop + plotH * (1f - tick / top)
            drawLine(grid, Offset(0f, ty), Offset(plotW, ty), strokeWidth = 1.dp.toPx())
            axisLabel(measurer, tick.toInt().toString(), plotW + 6.dp.toPx(), ty)
        }

        val barW = (step * 0.35f).coerceAtLeast(1.5.dp.toPx())
        points.forEachIndexed { i, p ->
            if (p.dailyLoad <= 0) return@forEachIndexed
            val by = y(p.dailyLoad)
            drawRoundRect(
                barColor,
                topLeft = Offset(x(i) - barW / 2, by),
                size = Size(barW, padTop + plotH - by),
                cornerRadius = CornerRadius(barW / 2),
            )
        }

        val fitness = points.mapIndexed { i, p -> Offset(x(i), y(p.fitness)) }
        val area = Path().apply {
            moveTo(fitness.first().x, padTop + plotH)
            fitness.forEach { lineTo(it.x, it.y) }
            lineTo(fitness.last().x, padTop + plotH)
            close()
        }
        drawPath(area, fitnessColor.copy(alpha = 0.12f))
        drawPolyline(fitness, fitnessColor)
        val fatigue = points.mapIndexed { i, p -> Offset(x(i), y(p.fatigue)) }
        drawPolyline(fatigue, fatigueColor)

        drawCircle(TriColors.surface, radius = 6.dp.toPx(), center = fitness.last())
        drawCircle(fitnessColor, radius = 4.dp.toPx(), center = fitness.last())
        drawCircle(TriColors.surface, radius = 6.dp.toPx(), center = fatigue.last())
        drawCircle(fatigueColor, radius = 4.dp.toPx(), center = fatigue.last())
    }
}

private fun DrawScope.drawPolyline(points: List<Offset>, color: Color) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Tint of the productive form band, also used in its legend. */
val FormBandColor = Color(0xFF5BD69A).copy(alpha = 0.14f)

/** Form scale shown: from −50 % to +30 % of fitness. */
private const val FORM_MIN = -0.5f
private const val FORM_MAX = 0.3f

/**
 * Form day by day as bars around zero, on a fixed scale so a day at −20 %
 * always looks the same. The productive band (−30 % to −10 %) is shaded:
 * bars inside it are building fitness, bars below its floor are overload.
 */
@Composable
fun FormBars(points: List<FitnessPoint>, description: String, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val band = FormBandColor
    val zero = TriColors.borderStrong
    val past = Color(0xFF9AA3AF).copy(alpha = 0.55f)
    val now = Color(0xFFF2F4F7)
    val over = TriColors.danger

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics { contentDescription = description },
    ) {
        val gutter = AxisGutter.toPx()
        val plotW = size.width - gutter
        fun y(v: Float) = size.height * (FORM_MAX - v.coerceIn(FORM_MIN, FORM_MAX)) / (FORM_MAX - FORM_MIN)
        val step = plotW / points.size

        val bandTop = y(-0.10f)
        val bandBottom = y(-0.30f)
        drawRect(band, topLeft = Offset(0f, bandTop), size = Size(plotW, bandBottom - bandTop))

        drawLine(zero, Offset(0f, y(0f)), Offset(plotW, y(0f)), strokeWidth = 1.dp.toPx())
        axisLabel(measurer, "0", plotW + 6.dp.toPx(), y(0f))
        axisLabel(measurer, "−30%", plotW + 6.dp.toPx(), bandBottom)

        val barW = (step * 0.55f).coerceAtLeast(2.dp.toPx())
        points.forEachIndexed { i, p ->
            val v = p.formPct.toFloat()
            val cx = step * i + step / 2
            val y0 = y(0f)
            val y1 = y(v)
            val color = when {
                v < -0.30f -> over
                i == points.lastIndex -> now
                else -> past
            }
            val topY = minOf(y0, y1)
            val h = max(kotlin.math.abs(y1 - y0), 1.5.dp.toPx())
            drawRoundRect(color, Offset(cx - barW / 2, topY), Size(barW, h), CornerRadius(barW / 3))
        }
    }
}

/** Date labels under a day-by-day chart: first day, middle, and "oggi". */
@Composable
fun DayAxis(first: String, middle: String, last: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(end = AxisGutter), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(first, middle, last).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One measure against two references, the bullet graph: the fill is what
 * is done, the solid tick what the athlete usually has by today, the hollow
 * tick a whole usual week.
 */
@Composable
fun BulletBar(
    done: Double,
    usualToDate: Double?,
    usualWeek: Double?,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val tickColor = Color(0xFFF2F4F7)
    val track = TriColors.track
    val scale = max(max((usualWeek ?: 0.0) * 1.15, done * 1.05), 1.0)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .semantics { contentDescription = description },
    ) {
        val barH = 8.dp.toPx()
        val top = (size.height - barH) / 2
        val r = CornerRadius(barH / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, barH), r)
        if (done > 0) {
            val w = (size.width * (done / scale)).toFloat().coerceAtLeast(barH)
            drawRoundRect(color, Offset(0f, top), Size(w, barH), r)
        }
        usualWeek?.takeIf { it > 0 }?.let {
            val x = (size.width * (it / scale)).toFloat()
            drawRoundRect(
                tickColor.copy(alpha = 0.7f),
                Offset(x - 1.5.dp.toPx(), 1.dp.toPx()),
                Size(3.dp.toPx(), size.height - 2.dp.toPx()),
                CornerRadius(1.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        usualToDate?.takeIf { it > 0 }?.let {
            val x = (size.width * (it / scale)).toFloat()
            drawRoundRect(
                tickColor,
                Offset(x - 1.dp.toPx(), 0f),
                Size(2.dp.toPx(), size.height),
                CornerRadius(1.dp.toPx()),
            )
        }
    }
}

/**
 * Monday to Sunday: one column per day with a stacked bar of minutes per
 * sport, so "what did I do and when" reads without a list. Today is
 * outlined, the days to come are empty.
 */
@Composable
fun WeekStrip(
    days: List<DayTraining>,
    today: LocalDate,
    dayLetters: List<String>,
    minutesLabel: (Long) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val sports = TrainingStats.triathlonSports + Sport.OTHER
    // A 90-minute day fills the column unless a longer one sets the scale.
    val peak = max(days.maxOf { it.totalSec }, 90 * 60L).toFloat()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEachIndexed { index, day ->
            val isToday = day.date == today
            val future = day.date.isAfter(today)
            val shape = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .then(
                        if (isToday) Modifier.border(1.dp, TriColors.borderStrong, shape) else Modifier,
                    )
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = dayLetters[index],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(44.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (day.totalSec == 0L) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (future) TriColors.border else TriColors.track),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(day.totalSec / peak),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            // Swim at the bottom, as in race order read upwards.
                            sports.reversed().forEach { sport ->
                                val sec = day.secondsBySport[sport] ?: 0L
                                if (sec > 0) {
                                    Box(
                                        Modifier
                                            .weight(sec.toFloat())
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(SportColors.of(sport)),
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = if (day.totalSec > 0) minutesLabel(day.totalSec) else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A sport's coloured dot, so a name never relies on coloured text. */
@Composable
fun SportDot(sport: Sport, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(10.dp)
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(SportColors.of(sport)),
    )
}
