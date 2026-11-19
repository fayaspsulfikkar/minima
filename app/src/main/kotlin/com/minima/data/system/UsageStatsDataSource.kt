package com.minima.data.system

import com.minima.domain.model.DailyUsage

/**
 * Interface abstracting Android's [android.app.usage.UsageStatsManager]
 * and [android.app.AppOpsManager] operations.
 */
interface UsageStatsDataSource {

    /**
     * Checks whether PACKAGE_USAGE_STATS permission is granted via AppOpsManager.
     */
    fun hasUsagePermission(): Boolean

    /**
     * Queries today's usage statistics for the device's local calendar day.
     *
     * @return [DailyUsage.Loaded] with total foreground milliseconds,
     *         [DailyUsage.PermissionDenied] if permission is missing, or
     *         [DailyUsage.Unavailable] if data is empty or cannot be queried.
     */
    fun getTodayUsage(): DailyUsage

    /**
     * Queries usage statistics for an arbitrary time range [startTimeMs, endTimeMs).
     *
     * @param startTimeMs Inclusive start epoch millisecond.
     * @param endTimeMs Exclusive end epoch millisecond.
     * @return [DailyUsage] result for the given range.
     */
    fun queryUsageForRange(startTimeMs: Long, endTimeMs: Long): DailyUsage

    /**
     * Dispatches an Intent to open Android's Usage Access Settings screen.
     */
    fun openUsageAccessSettings()
}

