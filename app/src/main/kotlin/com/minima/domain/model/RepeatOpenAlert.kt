package com.minima.domain.model

/**
 * Represents an awareness alert indicating that a specific application
 * has been opened repeatedly within a recent rolling window.
 *
 * @param packageName Unique package name of the repeatedly launched application.
 * @param appLabel Display label of the application.
 * @param attemptCount Number of successful launches within the window.
 * @param windowMinutes Duration of the rolling window in minutes (default 10).
 */
data class RepeatOpenAlert(
    val packageName: String,
    val appLabel: String,
    val attemptCount: Int,
    val windowMinutes: Int = 10
)

