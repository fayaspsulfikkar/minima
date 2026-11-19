package com.minima.domain.model

/**
 * Represents an installed launchable application.
 *
 * In accordance with MINIMA's monochrome design philosophy,
 * no colorful icons, badges, or graphical assets are stored or rendered.
 *
 * @property packageName Unique Android package identifier (e.g., "com.android.settings")
 * @property label Human-readable application title (e.g., "Settings")
 */
data class AppInfo(
    val packageName: String,
    val label: String
)

