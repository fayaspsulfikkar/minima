package com.minima.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.minima.data.local.datastore.MinimaPreferencesKeys
import com.minima.domain.model.AppOpenDelay
import com.minima.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/**
 * Implementation of [SettingsRepository] backed by Android Jetpack Preferences DataStore.
 *
 * Reads values asynchronously as a cold Flow with proper error handling for IOExceptions.
 * Writes values safely using the atomic [DataStore.edit] transaction.
 */
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val lastSyncTimestamp: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[MinimaPreferencesKeys.KEY_LAST_SYNC_TIMESTAMP] ?: 0L
        }

    override suspend fun getLastSyncTimestamp(): Long {
        return lastSyncTimestamp.first()
    }

    override suspend fun setLastSyncTimestamp(timestampMs: Long) {
        dataStore.edit { preferences ->
            preferences[MinimaPreferencesKeys.KEY_LAST_SYNC_TIMESTAMP] = timestampMs
        }
    }

    override val appOpenDelay: Flow<AppOpenDelay> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val seconds = preferences[MinimaPreferencesKeys.KEY_APP_OPEN_DELAY_SECONDS] ?: 0
            AppOpenDelay.fromSeconds(seconds)
        }

    override suspend fun getAppOpenDelay(): AppOpenDelay {
        return appOpenDelay.first()
    }

    override suspend fun setAppOpenDelay(delay: AppOpenDelay) {
        dataStore.edit { preferences ->
            preferences[MinimaPreferencesKeys.KEY_APP_OPEN_DELAY_SECONDS] = delay.seconds
        }
    }
}
