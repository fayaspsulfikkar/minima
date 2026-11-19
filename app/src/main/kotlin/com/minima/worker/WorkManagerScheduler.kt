package com.minima.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Utility to schedule and manage MINIMA's periodic background backup synchronization.
 */
object WorkManagerScheduler {

    const val WORK_NAME = "MINIMA_USAGE_SYNC_WORK"
    const val SYNC_INTERVAL_HOURS = 24L

    /**
     * Enqueues a periodic backup sync worker running once every 24 hours.
     * Uses [ExistingPeriodicWorkPolicy.KEEP] to prevent resetting the interval on app restarts.
     */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<UsageSyncWorker>(
            SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }
}

