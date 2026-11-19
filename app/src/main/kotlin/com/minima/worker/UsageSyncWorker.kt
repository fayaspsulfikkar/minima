package com.minima.worker

import android.content.Context
import androidx.core.os.UserManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.minima.domain.usecase.SyncResult
import com.minima.domain.usecase.SyncUsageDataUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that synchronizes usage statistics and reconciles missed historical days.
 *
 * Configured with Hilt's [@HiltWorker] for dependency injection.
 * Verifies Direct Boot / device unlocked state via [UserManagerCompat.isUserUnlocked] before
 * attempting Room database or DataStore operations.
 */
@HiltWorker
class UsageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncUsageDataUseCase: SyncUsageDataUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Direct Boot / Device Credential Storage check:
        // When device is locked after reboot, encrypted storage / Room database may not be decrypted yet.
        if (!UserManagerCompat.isUserUnlocked(applicationContext)) {
            return Result.retry()
        }

        return when (syncUsageDataUseCase(force = true)) {
            is SyncResult.Success, is SyncResult.SkippedInterval -> Result.success()
            is SyncResult.Failure -> {
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}

