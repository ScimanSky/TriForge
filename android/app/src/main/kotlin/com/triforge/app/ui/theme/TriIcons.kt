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

    val Settings by lazy {
        stroke(
            "Settings",
            "M9 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0 Z " +
                "M10.3 3 h3.4 l0.5 2.4 l1.6 0.9 l2.3 -0.8 l1.7 2.9 l-1.8 1.6 v1.9 l1.8 1.6 l-1.7 2.9 " +
                "l-2.3 -0.8 l-1.6 0.9 l-0.5 2.4 h-3.4 l-0.5 -2.4 l-1.6 -0.9 l-2.3 0.8 l-1.7 -2.9 " +
                "l1.8 -1.6 v-1.9 l-1.8 -1.6 l1.7 -2.9 l2.3 0.8 l1.6 -0.9 Z",
        )
    }

    val Camera by lazy {
        stroke("Camera", "M4 8 h3 l2 -3 h6 l2 3 h3 v11 h-16 Z M9 13 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0 Z")
    }

    val Shield by lazy {
        stroke("Shield", "M12 3 l8 3 v6 c0 4.5 -3.5 8 -8 9 c-4.5 -1 -8 -4.5 -8 -9 v-6 Z M9 12 l2 2 l4 -4")
    }

    val Download by lazy { stroke("Download", "M12 4 v11 M7 10 l5 5 l5 -5 M5 20 h14") }

    val Trash by lazy {
        stroke("Trash", "M4 7 h16 M9 7 V4 h6 v3 M6 7 l1 13 h10 l1 -13 M10 11 v6 M14 11 v6")
    }

    val Info by lazy {
        stroke("Info", "M3 12 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0 Z M12 11 v6 M12 7.5 v0.5")
    }

    val Document by lazy { stroke("Document", "M6 3 h8 l4 4 v14 h-12 Z M14 3 v4 h4 M9 12 h6 M9 16 h6") }

    val Key by lazy {
        stroke("Key", "M3 15 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0 Z M10 12 l9 -9 M16 6 l2 2 M13.5 8.5 l2 2")
    }

    val SignOut by lazy { stroke("SignOut", "M10 4 H5 v16 h5 M15 8 l4 4 l-4 4 M19 12 H9") }

    val Sparkle by lazy { stroke("Sparkle", "M12 3 l2 6 l6 2 l-6 2 l-2 6 l-2 -6 l-6 -2 l6 -2 Z") }

    val Check by lazy { stroke("Check", "M5 12 l5 5 l9 -10") }

    val Edit by lazy { stroke("Edit", "M4 20 h4 L19 9 l-4 -4 L4 16 Z M13 7 l4 4") }

    val Lock by lazy { stroke("Lock", "M6 11 h12 v9 h-12 Z M8 11 V8 a4 4 0 0 1 8 0 v3") }

    val Flag by lazy { stroke("Flag", "M5 21 V4 M5 4 h11 l-2 4 l2 4 h-11") }

    val Close by lazy { stroke("Close", "M6 6 l12 12 M18 6 l-12 12") }

    val Send by lazy { stroke("Send", "M5 12 h14 M13 6 l6 6 l-6 6") }

    /** The Coach tab: a speech bubble with a spark. */
    val Coach by lazy { stroke("Coach", "M4 5 h16 v11 h-11 l-5 4 Z M12 8 l1 2 l2 1 l-2 1 l-1 2 l-1 -2 l-2 -1 l2 -1 Z") }

    /** NutriAI's tab: a fruit with its leaf. */
    val Nutrition by lazy {
        stroke("Nutrition", "M12 8 c-2.5 -2.5 -8 -1.5 -8 3.5 S8 21 12 21 s8 -4.5 8 -9.5 s-5.5 -6 -8 -3.5 Z M12 8 c0 -2 1 -4 3.5 -5")
    }

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
