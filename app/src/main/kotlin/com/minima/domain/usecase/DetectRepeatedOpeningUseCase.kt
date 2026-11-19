package com.minima.domain.usecase

import com.minima.domain.model.RepeatOpenAlert
import com.minima.domain.repository.AppRepository
import com.minima.domain.repository.LaunchAttemptRepository
import javax.inject.Inject

/**
 * Use case to detect whether the user has repeatedly opened the same application
 * within a rolling time window (default 10 minutes, threshold >= 3 successful opens).
 *
 * Source of truth: Successful app launches recorded in Room's launch_attempts table.
 * Cancelled countdowns or aborted opens are never recorded and never counted.
 */
class DetectRepeatedOpeningUseCase @Inject constructor(
    private val launchAttemptRepository: LaunchAttemptRepository,
    private val appRepository: AppRepository
) {

    companion object {
        const val WINDOW_MILLIS = 10 * 60 * 1000L // 10 minutes
        const val THRESHOLD_COUNT = 3
    }

    /**
     * Evaluates whether the most recently launched application has been opened
     * [THRESHOLD_COUNT] or more times in the last [WINDOW_MILLIS].
     *
     * @param currentTimeMs Current timestamp in epoch milliseconds.
     * @return [RepeatOpenAlert] if threshold met, or null otherwise.
     */
    suspend operator fun invoke(currentTimeMs: Long = System.currentTimeMillis()): RepeatOpenAlert? {
        // 1. Identify the most recent launch attempt across all apps
        val latestLaunch = launchAttemptRepository.getLatestLaunch() ?: return null

        // 2. If the latest launch happened outside the 10-minute window, no recent repeat open can exist
        if ((currentTimeMs - latestLaunch.attemptedAt) > WINDOW_MILLIS) {
            return null
        }

        // 3. Count successful launches for this specific package in the rolling 10-minute window
        val windowStartMs = currentTimeMs - WINDOW_MILLIS
        val windowEndMs = currentTimeMs + 1000L // 1-second margin for execution delay
        val count = launchAttemptRepository.getAttemptsForPackageInRange(
            packageName = latestLaunch.packageName,
            startMs = windowStartMs,
            endMs = windowEndMs
        )

        // 4. Return alert if threshold (>= 3) is reached
        if (count >= THRESHOLD_COUNT) {
            val appLabel = appRepository.getInstalledApps()
                .find { it.packageName == latestLaunch.packageName }
                ?.label ?: latestLaunch.packageName

            return RepeatOpenAlert(
                packageName = latestLaunch.packageName,
                appLabel = appLabel,
                attemptCount = count,
                windowMinutes = (WINDOW_MILLIS / (60 * 1000)).toInt()
            )
        }

        return null
    }
}

