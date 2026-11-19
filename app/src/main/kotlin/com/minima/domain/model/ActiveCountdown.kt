package com.minima.domain.model

/**
 * Represents the active state of an intentional app-opening countdown.
 *
 * @param packageName Target application package name.
 * @param appLabel User-facing display name of the application.
 * @param remainingSeconds Number of seconds remaining before the app opens automatically.
 * @param totalSeconds Initial total duration configured for this countdown.
 */
data class ActiveCountdown(
    val packageName: String,
    val appLabel: String,
    val remainingSeconds: Int,
    val totalSeconds: Int
)

