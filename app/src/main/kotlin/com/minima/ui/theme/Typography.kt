package com.minima.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography definition for MINIMA.
 *
 * In a monochrome launcher with no colorful icons, typography is the primary
 * medium of hierarchy, readability, and intentional design.
 */
val MinimaTypography = Typography(
    // Large digital clock
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 68.sp,
        lineHeight = 72.sp,
        letterSpacing = (-1.5).sp,
        color = PureWhite
    ),
    // Section headers or prominent dates
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp,
        color = PureWhite
    ),
    // Body text for application names and main content
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
        color = GreyLight
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        color = GreyMedium
    ),
    // Small tracked uppercase labels (e.g., "SCREEN TIME TODAY", "MINIMA")
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.5.sp,
        color = GreyMedium
    )
)

