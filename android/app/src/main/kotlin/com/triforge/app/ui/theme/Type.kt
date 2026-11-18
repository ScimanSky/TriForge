package com.triforge.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.triforge.app.R

// Both are variable fonts: one file serves every weight.
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

private fun inter(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

private fun grotesk(size: Int, line: Int, weight: FontWeight = FontWeight.Bold) =
    TextStyle(fontFamily = SpaceGrotesk, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** Space Grotesk for titles and numbers, Inter for everything else. */
val TriForgeTypography = Typography(
    displayLarge = grotesk(48, 52),
    displayMedium = grotesk(44, 48),
    displaySmall = grotesk(40, 42),
    headlineLarge = grotesk(32, 38),
    headlineMedium = grotesk(28, 34),
    headlineSmall = grotesk(26, 32),
    titleLarge = grotesk(22, 28),
    titleMedium = inter(16, 22, FontWeight.SemiBold),
    titleSmall = inter(15, 20, FontWeight.SemiBold),
    bodyLarge = inter(16, 24),
    bodyMedium = inter(14, 20),
    bodySmall = inter(12, 16),
    labelLarge = inter(14, 20, FontWeight.SemiBold),
    labelMedium = inter(12, 16, FontWeight.Medium),
    labelSmall = inter(10, 14, FontWeight.Medium),
)

/** Style for a figure: Space Grotesk bold, whose wide digits read well mid-effort. */
fun numberStyle(size: TextUnit): TextStyle =
    TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = size * 1.15f)
