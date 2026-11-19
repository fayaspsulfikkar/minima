package com.minima.domain.usecase

import com.minima.domain.model.DailyUsage
import com.minima.domain.repository.UsageRepository
import javax.inject.Inject

/**
 * Use case to retrieve today's foreground device usage.
 */
class GetTodayUsageUseCase @Inject constructor(
    private val repository: UsageRepository
) {
    operator fun invoke(): DailyUsage = repository.getTodayUsage()
}
