package com.minima.domain.model

/**
 * Represents an installed application paired with its daily usage metrics.
 *
 * Designed for MINIMA's minimal usage-awareness dashboard:
 * displays the application label alongside its daily foreground duration
 * and launcher-initiated opening counts.
 *
 * @property packageName Unique Android package identifier.
 * @property label Human-readable application title.
 * @property totalForegroundMs Milliseconds spent in foreground today.
 * @property launchCount Number of times opened through MINIMA today.
 * @property hasUsageRecord True if Room contains an entry for this app today.
 * @property usageDisplay Human-readable usage text (e.g., "1h 24m · 3 opens", or "—" if no record).
 */
data class AppUsageItem(
    val packageName: String,
    val label: String,
    val totalForegroundMs: Long = 0L,
    val launchCount: Int = 0,
    val hasUsageRecord: Boolean = false,
    val usageDisplay: String = "—"
)

