package com.minima.domain.repository

import com.minima.domain.model.AppOpenDelay
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing application-level preferences and settings.
 */
interface SettingsRepository {

    /**
     * Reactive stream of the last sync timestamp in epoch milliseconds.
     * Emits 0L if no sync has occurred yet.
     */
    val lastSyncTimestamp: Flow<Long>

    /**
     * Retrieves the current last sync timestamp.
     * Returns 0L if no sync has occurred yet.
     */
    suspend fun getLastSyncTimestamp(): Long

    /**
     * Updates the last sync timestamp.
     *
     * @param timestampMs epoch milliseconds of the sync completion.
     */
    suspend fun setLastSyncTimestamp(timestampMs: Long)

    /**
     * Reactive stream of the user-configured app-opening delay.
     * Emits [AppOpenDelay.OFF] by default.
     */
    val appOpenDelay: Flow<AppOpenDelay>

    /**
     * Retrieves the current app-opening delay configuration.
     */
    suspend fun getAppOpenDelay(): AppOpenDelay

    /**
     * Persists the user's selected app-opening delay setting.
     *
     * @param delay The desired [AppOpenDelay].
     */
    suspend fun setAppOpenDelay(delay: AppOpenDelay)
}

