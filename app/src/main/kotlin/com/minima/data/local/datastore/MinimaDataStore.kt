package com.minima.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Top-level property delegate ensuring a single DataStore instance exists
 * per application context, as recommended by official Android guidance.
 *
 * File created on disk: files/datastore/minima_preferences.preferences_pb
 */
val Context.minimaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "minima_preferences"
)

/**
 * Preference keys for MINIMA.
 */
object MinimaPreferencesKeys {
    val KEY_LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
    val KEY_APP_OPEN_DELAY_SECONDS = intPreferencesKey("app_open_delay_seconds")
}

