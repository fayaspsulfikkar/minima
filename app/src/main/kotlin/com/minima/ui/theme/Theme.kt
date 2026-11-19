package com.minima.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Strict monochrome color scheme for MINIMA.
 * All surface, background, and text colors are explicitly constrained to black, white, and gray.
 */
private val MonochromeColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    primaryContainer = GreySubtle,
    onPrimaryContainer = PureWhite,
    secondary = GreyLight,
    onSecondary = PureBlack,
    secondaryContainer = GreyDark,
    onSecondaryContainer = PureWhite,
    background = PureBlack,
    onBackground = PureWhite,
    surface = PureBlack,
    onSurface = PureWhite,
    surfaceVariant = GreySubtle,
    onSurfaceVariant = GreyLight,
    outline = GreyDark
)

/**
 * MinimaTheme applies the monochrome visual identity to all child Composables.
 */
@Composable
fun MinimaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MonochromeColorScheme,
        typography = MinimaTypography,
        content = content
    )
}

