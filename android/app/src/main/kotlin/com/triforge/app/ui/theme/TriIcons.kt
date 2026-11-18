package com.triforge.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.triforge.domain.Sport

/**
 * The prototype's line icons, on a 24 by 24 grid with a 2 px round stroke.
 * They take their color from the tint of the Icon that shows them.
 */
object TriIcons {

    val Home by lazy { stroke("Home", "M3 11 L12 4 L21 11 M5 9.5 V20 H19 V9.5") }

    val Activity by lazy { stroke("Activity", "M3 12 H7 L10 4 L14 20 L17 12 H21") }

    val Person by lazy {
        stroke(
            "Person",
            "M8.5 8 a3.5 3.5 0 1 0 7 0 a3.5 3.5 0 1 0 -7 0 Z " +
                "M5 20 c0.8 -3.5 3.4 -5.5 7 -5.5 s6.2 2 7 5.5",
        )
    }

    val Swim by lazy {
        stroke(
            "Swim",
            "M2 15 c2 0 2 -1.5 4 -1.5 s2 1.5 4 1.5 s2 -1.5 4 -1.5 s2 1.5 4 1.5 s2 -1.5 4 -1.5 " +
                "M2 19.5 c2 0 2 -1.5 4 -1.5 s2 1.5 4 1.5 s2 -1.5 4 -1.5 s2 1.5 4 1.5 s2 -1.5 4 -1.5 " +
                "M8 10 l4 -5 l3 3",
        )
    }

    val Bike by lazy {
        stroke(
            "Bike",
            "M2 16.5 a3.5 3.5 0 1 0 7 0 a3.5 3.5 0 1 0 -7 0 Z " +
                "M15 16.5 a3.5 3.5 0 1 0 7 0 a3.5 3.5 0 1 0 -7 0 Z " +
                "M5.5 16.5 l4 -7 h6 l3 7 M15.5 9.5 l-3 7",
        )
    }

    val Run by lazy {
        stroke(
            "Run",
            "M12 4.5 a2 2 0 1 0 4 0 a2 2 0 1 0 -4 0 Z " +
                "M8 21 l3 -6 l3 2 v5 M6 12 l3 -4 l4 1 l3 4 h3",
        )
    }

    val Watch by lazy {
        stroke("Watch", "M7 7 h10 v10 h-10 Z M9 7 l1 -4 h4 l1 4 M9 17 l1 4 h4 l1 -4")
    }

    val Heart by lazy {
        stroke(
            "Heart",
            "M12 20 C5.5 14.5 3 11.5 3 8.5 a4.5 4.5 0 0 1 9 -1.5 a4.5 4.5 0 0 1 9 1.5 c0 3 -2.5 6 -9 11.5 Z",
        )
    }

    val Link by lazy { stroke("Link", "M9 15 l6 -6 M10 6 l2 -2 a4.2 4.2 0 0 1 6 6 l-2 2 M14 18 l-2 2 a4.2 4.2 0 0 1 -6 -6 l2 -2") }

    val ChevronDown by lazy { stroke("ChevronDown", "M6 9 l6 6 l6 -6") }

    val ChevronRight by lazy { stroke("ChevronRight", "M9 5 l7 7 l-7 7") }

    val Back by lazy { stroke("Back", "M15 5 l-7 7 l7 7") }

    fun of(sport: Sport): ImageVector = when (sport) {
        Sport.SWIM -> Swim
        Sport.BIKE -> Bike
        Sport.RUN -> Run
        Sport.OTHER -> Activity
    }

    private fun stroke(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
