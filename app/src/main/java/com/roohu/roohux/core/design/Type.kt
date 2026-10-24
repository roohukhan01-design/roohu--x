package com.roohu.roohux.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.roohu.roohux.R

/** Poppins (SIL Open Font License 1.1), bundled in res/font. Latin glyphs; other scripts fall back to the system font. */
val Poppins = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light),
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) =
    TextStyle(fontFamily = Poppins, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em)

/** Hierarchy: display/headline = brand moments, title = screen & media titles, body = content, label = buttons & metadata. */
val RoohuXTypography = Typography(
    displayLarge = style(44, 52, FontWeight.Bold, -0.02f),
    displayMedium = style(36, 44, FontWeight.Bold, -0.02f),
    displaySmall = style(30, 38, FontWeight.Bold, -0.01f),
    headlineLarge = style(28, 36, FontWeight.Bold, -0.01f),
    headlineMedium = style(24, 32, FontWeight.Bold),
    headlineSmall = style(20, 28, FontWeight.Medium),
    titleLarge = style(18, 26, FontWeight.Medium),
    titleMedium = style(16, 24, FontWeight.Medium, 0.005f),
    titleSmall = style(14, 20, FontWeight.Medium, 0.01f),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 22, FontWeight.Normal),
    bodySmall = style(12, 18, FontWeight.Normal, 0.01f),
    labelLarge = style(14, 20, FontWeight.Medium, 0.02f),
    labelMedium = style(12, 16, FontWeight.Medium, 0.03f),
    labelSmall = style(11, 16, FontWeight.Medium, 0.04f),
)
