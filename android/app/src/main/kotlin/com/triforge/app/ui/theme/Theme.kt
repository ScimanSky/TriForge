package com.triforge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.triforge.domain.Sport

// Colors from the design prototype. The app is dark only, like the prototype.
private val TriForgeColors = darkColorScheme(
    primary = Color(0xFFFF5B2E),
    onPrimary = Color(0xFF0E1013),
    primaryContainer = Color(0xFF3A1D14),
    onPrimaryContainer = Color(0xFFFFD9CC),
    secondary = Color(0xFF7FE3B0),
    onSecondary = Color(0xFF0E1013),
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
    outline = Color(0xFF3A404A),
    outlineVariant = Color(0xFF2A2F37),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF0E1013),
)

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
}

@Composable
fun TriForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TriForgeColors, content = content)
}
