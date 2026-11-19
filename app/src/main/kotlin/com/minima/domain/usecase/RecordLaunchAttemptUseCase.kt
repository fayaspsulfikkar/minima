package com.minima.domain.usecase

import com.minima.domain.repository.LaunchAttemptRepository
import javax.inject.Inject

/**
 * Use case to persist an application launch attempt initiated through MINIMA.
 */
class RecordLaunchAttemptUseCase @Inject constructor(
    private val repository: LaunchAttemptRepository
) {
    suspend operator fun invoke(
        packageName: String,
        timestampMs: Long = System.currentTimeMillis()
    ): Result<Long> {
        return repository.recordAttempt(packageName, timestampMs)
    }
}

