package com.minima.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Strict monochrome color palette for MINIMA.
 *
 * MINIMA's design philosophy strictly forbids colorful elements, saturated accents,
 * and decorative gradients. The visual hierarchy is achieved entirely through
 * brightness contrast (white, greys, and black).
 */
val PureBlack = Color(0xFF000000)
val PureWhite = Color(0xFFFFFFFF)

// Grayscale tones for secondary information and subtle borders
val GreyLight = Color(0xFFE0E0E0)   // High emphasis secondary text
val GreyMedium = Color(0xFF8E8E93)  // Medium emphasis labels (e.g. captions, placeholders)
val GreyDark = Color(0xFF2C2C2E)    // Subtle separators or container outlines
val GreySubtle = Color(0xFF1C1C1E)  // Dark card or section background if needed

