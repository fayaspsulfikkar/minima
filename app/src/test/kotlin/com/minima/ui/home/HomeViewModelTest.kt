package com.minima.ui.home

import com.minima.data.local.db.dao.DailyUsageRecordDao
import com.minima.data.local.db.entity.DailyUsageRecordEntity
import com.minima.data.local.db.entity.LaunchAttemptEntity
import com.minima.domain.model.AppInfo
import com.minima.domain.model.AppOpenDelay
import com.minima.domain.model.DailyUsage
import com.minima.domain.repository.AppRepository
import com.minima.domain.repository.LaunchAttemptRepository
import com.minima.domain.repository.SettingsRepository
import com.minima.domain.repository.UsageRepository
import com.minima.domain.usecase.DetectRepeatedOpeningUseCase
import com.minima.domain.usecase.GetInstalledAppsUseCase
import com.minima.domain.usecase.GetTodayUsageUseCase
import com.minima.domain.usecase.LaunchAppUseCase
import com.minima.domain.usecase.OpenUsageAccessSettingsUseCase
import com.minima.domain.usecase.RecordLaunchAttemptUseCase
import com.minima.domain.usecase.SyncUsageDataUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for [HomeViewModel] verifying Phase 5 per-app usage insights,
 * reactive Room observation, search, launch recording, and synchronization.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val sampleApps = listOf(
        AppInfo(packageName = "com.android.calculator2", label = "Calculator"),
        AppInfo(packageName = "com.android.calendar", label = "Calendar"),
        AppInfo(packageName = "com.android.camera", label = "Camera"),
        AppInfo(packageName = "com.google.android.gm", label = "Gmail"),
        AppInfo(packageName = "com.android.settings", label = "Settings")
    )

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeAppRepository: FakeAppRepository
    private lateinit var fakeUsageRepository: FakeUsageRepository
    private lateinit var fakeLaunchAttemptRepository: FakeLaunchAttemptRepository
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeDailyUsageRecordDao: FakeDailyUsageRecordDao

    private lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase
    private lateinit var launchAppUseCase: LaunchAppUseCase
    private lateinit var getTodayUsageUseCase: GetTodayUsageUseCase
    private lateinit var openUsageAccessSettingsUseCase: OpenUsageAccessSettingsUseCase
    private lateinit var recordLaunchAttemptUseCase: RecordLaunchAttemptUseCase
    private lateinit var syncUsageDataUseCase: SyncUsageDataUseCase
    private lateinit var detectRepeatedOpeningUseCase: DetectRepeatedOpeningUseCase

    private lateinit var viewModel: HomeViewModel

    private val todayDate = LocalDate.now(ZoneId.systemDefault()).toString()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeAppRepository = FakeAppRepository(sampleApps)
        fakeUsageRepository = FakeUsageRepository(currentUsage = DailyUsage.Loaded(0L))
        fakeLaunchAttemptRepository = FakeLaunchAttemptRepository()
        fakeSettingsRepository = FakeSettingsRepository()
        fakeDailyUsageRecordDao = FakeDailyUsageRecordDao()

        getInstalledAppsUseCase = GetInstalledAppsUseCase(fakeAppRepository)
        launchAppUseCase = LaunchAppUseCase(fakeAppRepository)
        getTodayUsageUseCase = GetTodayUsageUseCase(fakeUsageRepository)
        openUsageAccessSettingsUseCase = OpenUsageAccessSettingsUseCase(fakeUsageRepository)
        recordLaunchAttemptUseCase = RecordLaunchAttemptUseCase(fakeLaunchAttemptRepository)
        syncUsageDataUseCase = SyncUsageDataUseCase(
            settingsRepository = fakeSettingsRepository,
            launchAttemptRepository = fakeLaunchAttemptRepository,
            dailyUsageRecordDao = fakeDailyUsageRecordDao,
            usageRepository = fakeUsageRepository
        )
        detectRepeatedOpeningUseCase = DetectRepeatedOpeningUseCase(
            launchAttemptRepository = fakeLaunchAttemptRepository,
            appRepository = fakeAppRepository
        )

        viewModel = HomeViewModel(
            getInstalledAppsUseCase = getInstalledAppsUseCase,
            launchAppUseCase = launchAppUseCase,
            getTodayUsageUseCase = getTodayUsageUseCase,
            openUsageAccessSettingsUseCase = openUsageAccessSettingsUseCase,
            recordLaunchAttemptUseCase = recordLaunchAttemptUseCase,
            syncUsageDataUseCase = syncUsageDataUseCase,
            dailyUsageRecordDao = fakeDailyUsageRecordDao,
            settingsRepository = fakeSettingsRepository,
            detectRepeatedOpeningUseCase = detectRepeatedOpeningUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasValidClockAndDefaults() {
        val state = viewModel.uiState.value

        assertEquals("MINIMA", state.appName)
        assertEquals("0h 00m", state.screenTimeDisplay)
        assertEquals(DailyUsage.Loaded(0L), state.usageState)
        assertTrue("Current time should not be empty", state.currentTime.isNotEmpty())
        assertTrue("Time should match HH:mm pattern", state.currentTime.matches(Regex("\\d{2}:\\d{2}")))
        assertTrue("Current date should not be empty", state.currentDate.isNotEmpty())
        assertEquals(5, state.installedApps.size)
        assertEquals(5, state.filteredApps.size)
        assertEquals("", state.searchQuery)
        assertNull(state.errorMessage)
    }

    @Test
    fun onSearchQueryChanged_filtersAppsCorrectly() {
        viewModel.onSearchQueryChanged("calc")

        val state = viewModel.uiState.value
        assertEquals("calc", state.searchQuery)
        assertEquals(1, state.filteredApps.size)
        assertEquals("com.android.calculator2", state.filteredApps.first().packageName)
    }

    @Test
    fun onSearchQueryChanged_caseInsensitive() {
        viewModel.onSearchQueryChanged("GMAIL")

        val state = viewModel.uiState.value
        assertEquals(1, state.filteredApps.size)
        assertEquals("Gmail", state.filteredApps.first().label)
    }

    @Test
    fun onSearchQueryChanged_emptyQuery_showsAllApps() {
        viewModel.onSearchQueryChanged("calc")
        assertEquals(1, viewModel.uiState.value.filteredApps.size)

        viewModel.onSearchQueryChanged("")
        assertEquals(5, viewModel.uiState.value.filteredApps.size)
    }

    @Test
    fun clearSearchQuery_resetsQueryAndList() {
        viewModel.onSearchQueryChanged("camera")
        assertEquals(1, viewModel.uiState.value.filteredApps.size)

        viewModel.clearSearchQuery()
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(5, viewModel.uiState.value.filteredApps.size)
    }

    @Test
    fun launchApp_success_returnsTrueAndNoErrorMessage() {
        val success = viewModel.launchApp("com.android.calendar")

        assertTrue(success)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun launchApp_failure_returnsFalseAndSetsErrorMessage() {
        val success = viewModel.launchApp("com.nonexistent.app")

        assertFalse(success)
        assertEquals("Unable to open application", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun launchApp_whenRecordingFails_stillLaunchesApp() {
        fakeLaunchAttemptRepository.shouldFail = true

        val success = viewModel.launchApp("com.android.camera")

        assertTrue("App must still launch successfully even if recording fails", success)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun clearErrorMessage_clearsActiveError() {
        viewModel.launchApp("com.nonexistent.app")
        assertEquals("Unable to open application", viewModel.uiState.value.errorMessage)

        viewModel.clearErrorMessage()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun refreshInstalledApps_updatesListFromRepository() {
        val updatedApps = sampleApps + AppInfo("com.new.app", "New App")
        fakeAppRepository.apps = updatedApps

        viewModel.refreshInstalledApps()

        assertEquals(6, viewModel.uiState.value.installedApps.size)
        assertEquals(6, viewModel.uiState.value.filteredApps.size)
    }

    @Test
    fun refreshCurrentTime_updatesTimeAndDate() {
        viewModel.refreshCurrentTime()

        val state = viewModel.uiState.value
        assertTrue(state.currentTime.isNotEmpty())
        assertTrue(state.currentDate.isNotEmpty())
    }

    @Test
    fun refreshUsageStats_updatesLoadedUsage() {
        fakeUsageRepository.currentUsage = DailyUsage.Loaded(7_200_000L) // 2 hours

        viewModel.refreshUsageStats()

        val state = viewModel.uiState.value
        assertEquals(DailyUsage.Loaded(7_200_000L), state.usageState)
        assertEquals("2h 00m", state.screenTimeDisplay)
    }

    @Test
    fun refreshUsageStats_permissionDenied_displaysPlaceholder() {
        fakeUsageRepository.currentUsage = DailyUsage.PermissionDenied

        viewModel.refreshUsageStats()

        val state = viewModel.uiState.value
        assertEquals(DailyUsage.PermissionDenied, state.usageState)
        assertEquals("--h --m", state.screenTimeDisplay)
    }

    @Test
    fun refreshUsageStats_unavailable_displaysPlaceholder() {
        fakeUsageRepository.currentUsage = DailyUsage.Unavailable

        viewModel.refreshUsageStats()

        val state = viewModel.uiState.value
        assertEquals(DailyUsage.Unavailable, state.usageState)
        assertEquals("--h --m", state.screenTimeDisplay)
    }

    @Test
    fun formatUsageDuration_variousInputs_formatsAccurately() {
        assertEquals("0h 00m", HomeViewModel.formatUsageDuration(0L))
        assertEquals("0h 00m", HomeViewModel.formatUsageDuration(-500L))
        assertEquals("0h 00m", HomeViewModel.formatUsageDuration(45_000L))
        assertEquals("0h 05m", HomeViewModel.formatUsageDuration(300_000L))
        assertEquals("1h 00m", HomeViewModel.formatUsageDuration(3_600_000L))
        assertEquals("1h 30m", HomeViewModel.formatUsageDuration(5_400_000L))
        assertEquals("2h 15m", HomeViewModel.formatUsageDuration(8_100_000L))
        assertEquals("10h 00m", HomeViewModel.formatUsageDuration(36_000_000L))
    }

    @Test
    fun formatAppUsageDisplay_variousInputs_formatsAccurately() {
        // No record in Room -> placeholder dash
        assertEquals("—", HomeViewModel.formatAppUsageDisplay(0L, 0, hasRecord = false))

        // Record exists, 0 duration, 1 open
        assertEquals("0m · 1 open", HomeViewModel.formatAppUsageDisplay(0L, 1, hasRecord = true))

        // Record exists, <1 min duration, 3 opens
        assertEquals("<1m · 3 opens", HomeViewModel.formatAppUsageDisplay(45_000L, 3, hasRecord = true))

        // Record exists, 45 min duration, 5 opens
        assertEquals("45m · 5 opens", HomeViewModel.formatAppUsageDisplay(2_700_000L, 5, hasRecord = true))

        // Record exists, 1h 24m duration, 1 open
        assertEquals("1h 24m · 1 open", HomeViewModel.formatAppUsageDisplay(5_040_000L, 1, hasRecord = true))

        // Record exists, 2h 00m duration, 0 opens (e.g. opened externally)
        assertEquals("2h 00m · 0 opens", HomeViewModel.formatAppUsageDisplay(7_200_000L, 0, hasRecord = true))
    }

    @Test
    fun perAppUsage_whenNoRecord_displaysPlaceholder() {
        val state = viewModel.uiState.value
        val calculator = state.installedApps.first { it.packageName == "com.android.calculator2" }

        assertEquals("—", calculator.usageDisplay)
        assertFalse(calculator.hasUsageRecord)
        assertEquals(0L, calculator.totalForegroundMs)
        assertEquals(0, calculator.launchCount)
    }

    @Test
    fun perAppUsage_whenRoomUpdates_uiStateUpdatesAutomatically() = runTest {
        // Simulate Room database receiving sync records
        fakeDailyUsageRecordDao.upsert(
            DailyUsageRecordEntity(
                packageName = "com.google.android.gm",
                date = todayDate,
                totalForegroundMs = 3_600_000L, // 1h
                launchCount = 4
            )
        )

        val state = viewModel.uiState.value
        val gmail = state.installedApps.first { it.packageName == "com.google.android.gm" }

        assertTrue(gmail.hasUsageRecord)
        assertEquals(3_600_000L, gmail.totalForegroundMs)
        assertEquals(4, gmail.launchCount)
        assertEquals("1h 00m · 4 opens", gmail.usageDisplay)
    }

    @Test
    fun openUsageAccessSettings_delegatesToUseCase() {
        viewModel.openUsageAccessSettings()
        assertTrue(fakeUsageRepository.settingsOpened)
    }

    // --- Phase 6: Gentle App-Opening Delay Tests ---

    @Test
    fun launchApp_whenDelayDisabled_launchesImmediatelyWithoutCountdown() = runTest(testDispatcher) {
        viewModel.setAppOpenDelay(AppOpenDelay.OFF)

        val success = viewModel.launchApp("com.android.calendar")

        assertTrue(success)
        assertNull(viewModel.uiState.value.activeCountdown)
        val attempts = fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar")
        assertEquals(1, attempts)
    }

    @Test
    fun launchApp_whenThreeSecondDelay_countsDownAndLaunchesAfterThreeSeconds() = runTest(testDispatcher) {
        viewModel.setAppOpenDelay(AppOpenDelay.THREE_SECONDS)

        val initialSuccess = viewModel.launchApp("com.android.calendar")
        assertTrue(initialSuccess)

        // Initial state of countdown is 3 seconds
        val initialCountdown = viewModel.uiState.value.activeCountdown
        assertNotNull(initialCountdown)
        assertEquals(3, initialCountdown?.remainingSeconds)
        assertEquals(0, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar"))

        // Tick 1s -> 2s
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(2, viewModel.uiState.value.activeCountdown?.remainingSeconds)
        assertEquals(0, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar"))

        // Tick 1s -> 1s
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(1, viewModel.uiState.value.activeCountdown?.remainingSeconds)
        assertEquals(0, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar"))

        // Tick 1s -> completion
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()

        // Countdown is cleared and attempt is recorded
        assertNull(viewModel.uiState.value.activeCountdown)
        val attempts = fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar")
        assertEquals(1, attempts)
    }

    @Test
    fun launchApp_whenFiveSecondDelay_countsDownAndLaunchesAfterFiveSeconds() = runTest(testDispatcher) {
        viewModel.setAppOpenDelay(AppOpenDelay.FIVE_SECONDS)

        viewModel.launchApp("com.android.calendar")

        assertEquals(5, viewModel.uiState.value.activeCountdown?.remainingSeconds)

        // Advance 4 seconds
        testScheduler.advanceTimeBy(4000L)
        testScheduler.runCurrent()
        assertEquals(1, viewModel.uiState.value.activeCountdown?.remainingSeconds)
        assertEquals(0, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar"))

        // Advance final second
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertNull(viewModel.uiState.value.activeCountdown)
        val attempts = fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar")
        assertEquals(1, attempts)
    }

    @Test
    fun launchApp_whenCancelledDuringCountdown_doesNotLaunchAndDoesNotRecord() = runTest(testDispatcher) {
        viewModel.setAppOpenDelay(AppOpenDelay.THREE_SECONDS)

        viewModel.launchApp("com.android.calendar")
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(2, viewModel.uiState.value.activeCountdown?.remainingSeconds)

        // User cancels
        viewModel.cancelCountdown()
        assertNull(viewModel.uiState.value.activeCountdown)

        // Advance time past remaining duration
        testScheduler.advanceTimeBy(5000L)
        testScheduler.runCurrent()

        // Must not record any attempt
        val attempts = fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar")
        assertEquals(0, attempts)
    }

    @Test
    fun launchApp_recordsAttemptExactlyOnce_notRepeatedlyDuringCountdown() = runTest(testDispatcher) {
        viewModel.setAppOpenDelay(AppOpenDelay.THREE_SECONDS)

        viewModel.launchApp("com.android.calendar")

        // 3 -> 2
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(0, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar"))

        // 2 -> 1
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(0, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar"))

        // 1 -> 0 (complete)
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()

        // Exactly once
        val attempts = fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar")
        assertEquals(1, attempts)
    }

    @Test
    fun launchApp_rapidRepeatedTaps_debouncesAndLaunchesOnlyOnce() = runTest(testDispatcher) {
        viewModel.setAppOpenDelay(AppOpenDelay.OFF)

        var mockTime = 1000L
        viewModel.currentTimeMillisProvider = { mockTime }

        val tap1 = viewModel.launchApp("com.android.calendar")
        assertTrue(tap1)

        // Rapid tap at +200ms (<800ms)
        mockTime = 1200L
        val tap2 = viewModel.launchApp("com.android.calendar")
        assertFalse(tap2)

        // Another rapid tap at +400ms
        mockTime = 1400L
        val tap3 = viewModel.launchApp("com.android.camera")
        assertFalse(tap3)

        assertEquals(1, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar"))
        assertEquals(0, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.camera"))
    }

    @Test
    fun launchApp_rapidRepeatedTapsDuringCountdown_ignored() = runTest(testDispatcher) {
        viewModel.setAppOpenDelay(AppOpenDelay.THREE_SECONDS)

        var mockTime = 1000L
        viewModel.currentTimeMillisProvider = { mockTime }

        val tap1 = viewModel.launchApp("com.android.calendar")
        assertTrue(tap1)

        // Even after debounce time, countdown is active so tap is rejected
        mockTime = 2500L
        val tap2 = viewModel.launchApp("com.android.camera")
        assertFalse(tap2)

        testScheduler.advanceTimeBy(3000L)
        testScheduler.runCurrent()

        assertEquals(1, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar"))
        assertEquals(0, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.camera"))
    }

    @Test
    fun countdown_lifecycleCancellationOnPause_cancelsCountdownAndPreventsLaunch() = runTest(testDispatcher) {
        viewModel.setAppOpenDelay(AppOpenDelay.FIVE_SECONDS)

        viewModel.launchApp("com.android.calendar")
        testScheduler.advanceTimeBy(2000L)
        testScheduler.runCurrent()
        assertEquals(3, viewModel.uiState.value.activeCountdown?.remainingSeconds)

        // Activity onPause calls cancelCountdown
        viewModel.cancelCountdown()
        assertNull(viewModel.uiState.value.activeCountdown)

        testScheduler.advanceTimeBy(10000L)
        testScheduler.runCurrent()
        assertEquals(0, fakeLaunchAttemptRepository.getAttemptsForPackageToday("com.android.calendar"))
    }

    @Test
    fun setAppOpenDelay_updatesUiStateAndPersistsSetting() = runTest(testDispatcher) {
        viewModel.setAppOpenDelay(AppOpenDelay.FIVE_SECONDS)
        assertEquals(AppOpenDelay.FIVE_SECONDS, viewModel.uiState.value.appOpenDelay)
        assertEquals(AppOpenDelay.FIVE_SECONDS, fakeSettingsRepository.getAppOpenDelay())
    }

    @Test
    fun repeatAlert_whenDismissed_remainsHiddenAcrossOnResumeCalls() = runTest(testDispatcher) {
        val now = 1_700_000_000_000L
        viewModel.currentTimeMillisProvider = { now }

        // 3 launches for Calculator within 5 minutes
        fakeLaunchAttemptRepository.attempts.addAll(
            listOf(
                LaunchAttemptEntity(1, "com.android.calculator2", now - 4 * 60 * 1000L),
                LaunchAttemptEntity(2, "com.android.calculator2", now - 2 * 60 * 1000L),
                LaunchAttemptEntity(3, "com.android.calculator2", now - 30 * 1000L)
            )
        )

        viewModel.checkRepeatOpenAlert()
        testScheduler.runCurrent()

        val alert = viewModel.uiState.value.repeatOpenAlert
        assertNotNull("Alert should be active after 3 launches in 10m", alert)
        assertEquals("com.android.calculator2", alert?.packageName)
        assertEquals(3, alert?.attemptCount)

        // User dismisses the alert
        viewModel.dismissRepeatAlert()
        testScheduler.runCurrent()
        assertNull("Alert should be cleared after dismissal", viewModel.uiState.value.repeatOpenAlert)

        // onResume triggers re-check: alert must remain hidden for the current session!
        viewModel.checkRepeatOpenAlert()
        testScheduler.runCurrent()
        assertNull("Dismissed alert must remain hidden on subsequent onResume checks", viewModel.uiState.value.repeatOpenAlert)
    }

    @Test
    fun repeatAlert_whenNewLaunchOccursAfterDismissal_showsUpdatedAlert() = runTest(testDispatcher) {
        var currentTime = 1_700_000_000_000L
        viewModel.currentTimeMillisProvider = { currentTime }

        // 3 launches for Calculator
        fakeLaunchAttemptRepository.attempts.addAll(
            listOf(
                LaunchAttemptEntity(1, "com.android.calculator2", currentTime - 4 * 60 * 1000L),
                LaunchAttemptEntity(2, "com.android.calculator2", currentTime - 2 * 60 * 1000L),
                LaunchAttemptEntity(3, "com.android.calculator2", currentTime - 30 * 1000L)
            )
        )

        viewModel.checkRepeatOpenAlert()
        testScheduler.runCurrent()
        assertNotNull(viewModel.uiState.value.repeatOpenAlert)

        // Dismiss alert
        viewModel.dismissRepeatAlert()
        testScheduler.runCurrent()
        assertNull(viewModel.uiState.value.repeatOpenAlert)

        // User opens Calculator a 4th time 1 minute later
        currentTime += 60 * 1000L
        viewModel.currentTimeMillisProvider = { currentTime }
        fakeLaunchAttemptRepository.attempts.add(
            LaunchAttemptEntity(4, "com.android.calculator2", currentTime)
        )

        // Now rechecking must display the updated alert with count = 4
        viewModel.checkRepeatOpenAlert()
        testScheduler.runCurrent()

        val updatedAlert = viewModel.uiState.value.repeatOpenAlert
        assertNotNull("New launch must trigger updated alert even after previous dismissal", updatedAlert)
        assertEquals(4, updatedAlert?.attemptCount)
    }

    // --- Test Doubles ---

    private class FakeAppRepository(
        var apps: List<AppInfo>
    ) : AppRepository {
        var shouldFailLaunch: Boolean = false

        override fun getInstalledApps(): List<AppInfo> = apps

        override fun launchApp(packageName: String): Boolean {
            if (shouldFailLaunch) return false
            return apps.any { it.packageName == packageName }
        }
    }

    private class FakeUsageRepository(
        var currentUsage: DailyUsage = DailyUsage.Loaded(0L)
    ) : UsageRepository {
        var settingsOpened: Boolean = false

        override fun hasUsagePermission(): Boolean = currentUsage !is DailyUsage.PermissionDenied

        override fun getTodayUsage(): DailyUsage = currentUsage

        override fun queryUsageForRange(startTimeMs: Long, endTimeMs: Long): DailyUsage = currentUsage

        override fun openUsageAccessSettings() {
            settingsOpened = true
        }
    }

    private class FakeLaunchAttemptRepository : LaunchAttemptRepository {
        val attempts = mutableListOf<LaunchAttemptEntity>()
        var shouldFail = false

        override suspend fun recordAttempt(packageName: String, timestampMs: Long): Result<Long> {
            return if (shouldFail) {
                Result.failure(RuntimeException("Simulated disk error"))
            } else {
                val entity = LaunchAttemptEntity(id = attempts.size + 1L, packageName = packageName, attemptedAt = timestampMs)
                attempts.add(entity)
                Result.success(entity.id)
            }
        }

        override suspend fun getAttemptsForToday(): List<LaunchAttemptEntity> = attempts

        override suspend fun getAttemptsForPackageToday(packageName: String): Int {
            return attempts.count { it.packageName == packageName }
        }

        override suspend fun getAttemptsForRange(startMs: Long, endMs: Long): List<LaunchAttemptEntity> {
            return attempts.filter { it.attemptedAt in startMs..<endMs }
        }

        override suspend fun getAttemptsForPackageInRange(packageName: String, startMs: Long, endMs: Long): Int {
            return attempts.count { it.packageName == packageName && it.attemptedAt >= startMs && it.attemptedAt < endMs }
        }

        override suspend fun getLatestLaunch(): LaunchAttemptEntity? {
            return attempts.maxByOrNull { it.attemptedAt }
        }
    }

    private class FakeSettingsRepository(
        private var timestamp: Long = 0L,
        private var delay: AppOpenDelay = AppOpenDelay.OFF
    ) : SettingsRepository {
        private val syncFlow = MutableStateFlow(timestamp)
        private val delayFlow = MutableStateFlow(delay)

        override val lastSyncTimestamp: Flow<Long> = syncFlow
        override suspend fun getLastSyncTimestamp(): Long = timestamp
        override suspend fun setLastSyncTimestamp(timestampMs: Long) {
            timestamp = timestampMs
            syncFlow.value = timestampMs
        }

        override val appOpenDelay: Flow<AppOpenDelay> = delayFlow
        override suspend fun getAppOpenDelay(): AppOpenDelay = delay
        override suspend fun setAppOpenDelay(delay: AppOpenDelay) {
            this.delay = delay
            delayFlow.value = delay
        }
    }

    private class FakeDailyUsageRecordDao : DailyUsageRecordDao {
        val records = mutableMapOf<Pair<String, String>, DailyUsageRecordEntity>()
        private val recordsFlow = MutableStateFlow<List<DailyUsageRecordEntity>>(emptyList())

        override suspend fun upsert(record: DailyUsageRecordEntity) {
            records[Pair(record.packageName, record.date)] = record
            recordsFlow.value = records.values.toList()
        }

        override suspend fun upsertAll(records: List<DailyUsageRecordEntity>) {
            records.forEach { r ->
                this.records[Pair(r.packageName, r.date)] = r
            }
            recordsFlow.value = this.records.values.toList()
        }

        override suspend fun getRecord(packageName: String, date: String): DailyUsageRecordEntity? {
            return records[Pair(packageName, date)]
        }

        override suspend fun getRecordsForDate(date: String): List<DailyUsageRecordEntity> {
            return records.values.filter { it.date == date }
        }

        override fun getRecordsForDateFlow(date: String): Flow<List<DailyUsageRecordEntity>> {
            return recordsFlow
        }
    }
}
