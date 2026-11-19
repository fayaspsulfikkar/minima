package com.minima.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.minima.domain.model.AppOpenDelay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [SettingsRepositoryImpl] verifying Preferences DataStore operations.
 *
 * Uses [PreferenceDataStoreFactory] with a [TemporaryFolder] so each test executes
 * with a completely clean, isolated DataStore file on JVM without Android device dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("test_${System.nanoTime()}.preferences_pb") }
        )
        repository = SettingsRepositoryImpl(testDataStore)
    }

    @Test
    fun initialTimestamp_returnsDefaultZero() = runTest(testDispatcher) {
        val initialValue = repository.getLastSyncTimestamp()
        assertEquals(0L, initialValue)
    }

    @Test
    fun writeTimestamp_persistsCorrectValue() = runTest(testDispatcher) {
        val expectedTimestamp = 1_700_000_123_456L
        repository.setLastSyncTimestamp(expectedTimestamp)

        val retrievedValue = repository.getLastSyncTimestamp()
        assertEquals(expectedTimestamp, retrievedValue)
    }

    @Test
    fun readTimestampAfterWriting_returnsUpdatedValue() = runTest(testDispatcher) {
        val timestamp1 = 1_000L
        val timestamp2 = 2_000L

        repository.setLastSyncTimestamp(timestamp1)
        assertEquals(timestamp1, repository.getLastSyncTimestamp())

        repository.setLastSyncTimestamp(timestamp2)
        assertEquals(timestamp2, repository.getLastSyncTimestamp())
    }

    @Test
    fun flow_emitsUpdatedValueAfterWrite() = runTest(testDispatcher) {
        // Initial emission should be 0L
        assertEquals(0L, repository.lastSyncTimestamp.first())

        val newTimestamp = 1_732_000_000_000L
        repository.setLastSyncTimestamp(newTimestamp)

        // Flow should reflect the updated value
        assertEquals(newTimestamp, repository.lastSyncTimestamp.first())
    }

    @Test
    fun initialAppOpenDelay_returnsDefaultOff() = runTest(testDispatcher) {
        val initialDelay = repository.getAppOpenDelay()
        assertEquals(AppOpenDelay.OFF, initialDelay)
    }

    @Test
    fun writeAppOpenDelay_persistsCorrectValue() = runTest(testDispatcher) {
        repository.setAppOpenDelay(AppOpenDelay.THREE_SECONDS)
        assertEquals(AppOpenDelay.THREE_SECONDS, repository.getAppOpenDelay())

        repository.setAppOpenDelay(AppOpenDelay.FIVE_SECONDS)
        assertEquals(AppOpenDelay.FIVE_SECONDS, repository.getAppOpenDelay())

        repository.setAppOpenDelay(AppOpenDelay.OFF)
        assertEquals(AppOpenDelay.OFF, repository.getAppOpenDelay())
    }

    @Test
    fun flow_emitsUpdatedDelayAfterWrite() = runTest(testDispatcher) {
        assertEquals(AppOpenDelay.OFF, repository.appOpenDelay.first())

        repository.setAppOpenDelay(AppOpenDelay.FIVE_SECONDS)
        assertEquals(AppOpenDelay.FIVE_SECONDS, repository.appOpenDelay.first())
    }
}

