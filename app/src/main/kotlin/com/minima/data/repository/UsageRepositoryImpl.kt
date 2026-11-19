package com.minima.data.repository

import com.minima.data.system.UsageStatsDataSource
import com.minima.domain.model.DailyUsage
import com.minima.domain.repository.UsageRepository
import javax.inject.Inject

/**
 * Concrete implementation of [UsageRepository] delegating to [UsageStatsDataSource].
 */
class UsageRepositoryImpl @Inject constructor(
    private val dataSource: UsageStatsDataSource
) : UsageRepository {

    override fun hasUsagePermission(): Boolean = dataSource.hasUsagePermission()

    override fun getTodayUsage(): DailyUsage = dataSource.getTodayUsage()

    override fun queryUsageForRange(startTimeMs: Long, endTimeMs: Long): DailyUsage =
        dataSource.queryUsageForRange(startTimeMs, endTimeMs)

    override fun openUsageAccessSettings() = dataSource.openUsageAccessSettings()
}
