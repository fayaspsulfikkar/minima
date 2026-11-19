package com.minima.domain.model

/**
 * Represents the device's daily foreground usage state.
 */
sealed interface DailyUsage {

    /**
     * Usage access permission (PACKAGE_USAGE_STATS) has not been granted by the user.
     */
    data object PermissionDenied : DailyUsage

    /**
     * Permission is granted, but usage statistics are currently unavailable
     * (e.g. query returned empty/null due to write latency, OS batching, or Direct Boot).
     */
    data object Unavailable : DailyUsage

    /**
     * Successfully calculated daily foreground usage.
     *
     * @property totalForegroundMs Total milliseconds spent in foreground across applications today.
     *                             Can be 0L if device was genuinely not used.
     * @property packageUsage Map of package name to total foreground milliseconds for each app today.
     */
    data class Loaded(
        val totalForegroundMs: Long,
        val packageUsage: Map<String, Long> = emptyMap()
    ) : DailyUsage
}
