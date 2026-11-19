package com.triforge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.triforge.domain.Sport

// Colors from the approved design prototype. The app is dark only.
private val TriForgeColorScheme = darkColorScheme(
    primary = Color(0xFFFF5B2E),
    onPrimary = Color(0xFF0E1013),
    primaryContainer = Color(0xFF3A1D14),
    onPrimaryContainer = Color(0xFFFFB59C),
    secondary = Color(0xFFFFB59C),
    onSecondary = Color(0xFF0E1013),
    tertiary = Color(0xFF7FE3B0),
    onTertiary = Color(0xFF0E1013),
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF171A1F),
    onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF20242B),
    onSurfaceVariant = Color(0xFF9AA3AF),
    surfaceContainerLowest = Color(0xFF0E1013),
    surfaceContainerLow = Color(0xFF14171B),
    surfaceContainer = Color(0xFF171A1F),
    surfaceContainerHigh = Color(0xFF1C2026),
    surfaceContainerHighest = Color(0xFF20242B),
    outline = Color(0xFF3A414C),
    outlineVariant = Color(0xFF23272E),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF0E1013),
)

/** Prototype colors that have no slot in the Material color scheme. */
object TriColors {
    val border = Color(0xFF23272E)
    val borderStrong = Color(0xFF3A414C)
    val navBar = Color(0xFF14171B)
    val track = Color(0xFF23272E)
    val avatar = Color(0xFF2A2F37)
    val textDim = Color(0xFFAEB6C2)
    val accentText = Color(0xFFFFB59C)
    val good = Color(0xFF5BD69A)
    val warn = Color(0xFFFFB59C)

    // Load-recovery balance, from normal to persistent signs.
    val caution = Color(0xFFFFD23F)
    val alert = Color(0xFFFF8A4C)
    val danger = Color(0xFFFF5C64)
    val iconBox = Color(0xFF1F2A33)
}

object SportColors {
    val swim = Color(0xFF4CC2FF)
    val bike = Color(0xFFFFD23F)
    val run = Color(0xFFB78CFF)
    val other = Color(0xFF9AA3AF)

    fun of(sport: Sport): Color = when (sport) {
        Sport.SWIM -> swim
        Sport.BIKE -> bike
        Sport.RUN -> run
        Sport.OTHER -> other
    }

    /** Muted version, for the bars that are not the current week. */
    fun dim(sport: Sport): Color = when (sport) {
        Sport.SWIM -> Color(0xFF1B4A63)
        Sport.BIKE -> Color(0xFF5C5226)
        Sport.RUN -> Color(0xFF3E3260)
        Sport.OTHER -> Color(0xFF3A404A)
    }

    /** Background of the square behind a sport icon. */
    fun box(sport: Sport): Color = when (sport) {
        Sport.SWIM -> Color(0xFF13303F)
        Sport.BIKE -> Color(0xFF3A3214)
        Sport.RUN -> Color(0xFF2A2240)
        Sport.OTHER -> Color(0xFF23272E)
    }
}

@Composable
fun TriForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TriForgeColorScheme,
        typography = TriForgeTypography,
        content = content,
    )
}
