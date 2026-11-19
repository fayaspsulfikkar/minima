package com.minima.domain.usecase

import com.minima.domain.repository.UsageRepository
import javax.inject.Inject

/**
 * Use case to open Android's Usage Access system settings screen.
 */
class OpenUsageAccessSettingsUseCase @Inject constructor(
    private val repository: UsageRepository
) {
    operator fun invoke() = repository.openUsageAccessSettings()
}
