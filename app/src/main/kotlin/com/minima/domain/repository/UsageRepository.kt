package com.minima.domain.repository

import com.minima.domain.model.DailyUsage

/**
 * Repository interface for querying device usage statistics and managing permission.
 */
interface UsageRepository {

    /**
     * Checks whether Usage Access permission is granted.
     */
    fun hasUsagePermission(): Boolean

    /**
     * Retrieves today's total foreground device usage.
     */
    fun getTodayUsage(): DailyUsage

    /**
     * Queries device usage statistics for a specific time range.
     */
    fun queryUsageForRange(startTimeMs: Long, endTimeMs: Long): DailyUsage

    /**
     * Launches the system settings screen where the user can grant Usage Access.
     */
    fun openUsageAccessSettings()
}

