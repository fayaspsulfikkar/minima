package com.minima

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.minima.worker.WorkManagerScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * MinimaApp represents the base Application class for the MINIMA launcher.
 *
 * Annotated with [@HiltAndroidApp] to trigger Hilt's code generation.
 * Implements [Configuration.Provider] to provide [HiltWorkerFactory] on-demand to WorkManager.
 */
@HiltAndroidApp
class MinimaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        WorkManagerScheduler.schedulePeriodicSync(this)
    }
}
