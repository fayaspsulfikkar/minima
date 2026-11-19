package com.minima.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minima.data.local.db.dao.DailyUsageRecordDao
import com.minima.data.local.db.entity.DailyUsageRecordEntity
import com.minima.domain.model.ActiveCountdown
import com.minima.domain.model.AppInfo
import com.minima.domain.model.AppOpenDelay
import com.minima.domain.model.AppUsageItem
import com.minima.domain.model.DailyUsage
import com.minima.domain.model.RepeatOpenAlert
import com.minima.domain.repository.SettingsRepository
import com.minima.domain.usecase.DetectRepeatedOpeningUseCase
import com.minima.domain.usecase.GetInstalledAppsUseCase
import com.minima.domain.usecase.GetTodayUsageUseCase
import com.minima.domain.usecase.LaunchAppUseCase
import com.minima.domain.usecase.OpenUsageAccessSettingsUseCase
import com.minima.domain.usecase.RecordLaunchAttemptUseCase
import com.minima.domain.usecase.SyncUsageDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Immutable UI state for MINIMA Home Screen.
 *
 * @param currentTime Formatted time string (e.g., "14:05")
 * @param currentDate Formatted day and date string (e.g., "Wednesday, 18 November")
 * @param screenTimeDisplay Formatted string representation of screen time (e.g. "4h 32m")
 * @param usageState Structured device usage state (PermissionDenied, Unavailable, or Loaded)
 * @param appName Minimal branding text ("MINIMA")
 * @param installedApps Complete list of installed launchable applications paired with daily usage insights
 * @param searchQuery Current user query in the search bar
 * @param filteredApps Applications matching the current search query
 * @param errorMessage Transient error notification if an app fails to open
 * @param appOpenDelay User-configured intentional delay before an app opens
 * @param activeCountdown Active countdown state if an app opening is currently paused
 * @param repeatOpenAlert Awareness alert when an application has been opened repeatedly
 */
data class HomeUiState(
    val currentTime: String = "",
    val currentDate: String = "",
    val screenTimeDisplay: String = "0h 00m",
    val usageState: DailyUsage = DailyUsage.Unavailable,
    val appName: String = "MINIMA",
    val installedApps: List<AppUsageItem> = emptyList(),
    val searchQuery: String = "",
    val filteredApps: List<AppUsageItem> = emptyList(),
    val errorMessage: String? = null,
    val appOpenDelay: AppOpenDelay = AppOpenDelay.OFF,
    val activeCountdown: ActiveCountdown? = null,
    val repeatOpenAlert: RepeatOpenAlert? = null
)

/**
 * HomeViewModel coordinates the home screen dashboard, clock ticker,
 * live device usage statistics, per-app usage insights, intentional delay, and application launcher.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val launchAppUseCase: LaunchAppUseCase,
    private val getTodayUsageUseCase: GetTodayUsageUseCase,
    private val openUsageAccessSettingsUseCase: OpenUsageAccessSettingsUseCase,
    private val recordLaunchAttemptUseCase: RecordLaunchAttemptUseCase,
    private val syncUsageDataUseCase: SyncUsageDataUseCase,
    private val dailyUsageRecordDao: DailyUsageRecordDao,
    private val settingsRepository: SettingsRepository,
    private val detectRepeatedOpeningUseCase: DetectRepeatedOpeningUseCase
) : ViewModel() {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var rawInstalledApps: List<AppInfo> = emptyList()
    private var persistedRecords: Map<String, DailyUsageRecordEntity> = emptyMap()

    private var tickerJob: Job? = null
    private var recordsObserverJob: Job? = null
    private var countdownJob: Job? = null
    private var observedDate: String? = null
    private var lastLaunchTimeMs: Long = 0L

    // In-memory session tracking for dismissed repeat-open alerts
    private var dismissedPackage: String? = null
    private var dismissedLaunchCount: Int = 0

    internal var currentTimeMillisProvider: () -> Long = { System.currentTimeMillis() }

    init {
        observeAppOpenDelay()
        startObservingPersistedRecords()
        refreshInstalledApps()
        refreshUsageStats()
        checkRepeatOpenAlert()
    }

    private fun observeAppOpenDelay() {
        viewModelScope.launch {
            try {
                settingsRepository.appOpenDelay.collect { delay ->
                    _uiState.update { it.copy(appOpenDelay = delay) }
                }
            } catch (_: Exception) {
                // Fall back gracefully to default setting
            }
        }
    }

    /**
     * Persists and applies a new intentional app-opening delay.
     */
    fun setAppOpenDelay(delay: AppOpenDelay) {
        _uiState.update { it.copy(appOpenDelay = delay) }
        viewModelScope.launch {
            try {
                settingsRepository.setAppOpenDelay(delay)
            } catch (_: Exception) {
                // Settings persistence failure must not crash the launcher
            }
        }
    }

    private fun getTodayDate(): String {
        return LocalDate.now(ZoneId.systemDefault()).toString()
    }

    internal fun createInitialState(): HomeUiState {
        val now = LocalDateTime.now()
        val apps = getInstalledAppsUseCase()
        rawInstalledApps = apps
        val mappedApps = apps.map { app ->
            AppUsageItem(
                packageName = app.packageName,
                label = app.label,
                usageDisplay = "—"
            )
        }
        val initialUsage = getTodayUsageUseCase()
        val initialDisplay = when (initialUsage) {
            is DailyUsage.Loaded -> formatUsageDuration(initialUsage.totalForegroundMs)
            is DailyUsage.PermissionDenied, DailyUsage.Unavailable -> "--h --m"
        }

        return HomeUiState(
            currentTime = now.format(timeFormatter),
            currentDate = now.format(dateFormatter),
            screenTimeDisplay = initialDisplay,
            usageState = initialUsage,
            appName = "MINIMA",
            installedApps = mappedApps,
            searchQuery = "",
            filteredApps = mappedApps,
            errorMessage = null
        )
    }

    /**
     * Reactively observes today's persisted usage records from Room.
     * When records are synchronized or updated, the UI automatically receives the latest data.
     */
    fun startObservingPersistedRecords(todayDate: String = getTodayDate()) {
        if (observedDate == todayDate && recordsObserverJob?.isActive == true) return
        observedDate = todayDate
        recordsObserverJob?.cancel()
        recordsObserverJob = viewModelScope.launch {
            try {
                dailyUsageRecordDao.getRecordsForDateFlow(todayDate).collect { records ->
                    persistedRecords = records.associateBy { it.packageName }
                    updateUiAppsList()
                }
            } catch (_: Exception) {
                // Database observation error should never crash the launcher
            }
        }
    }

    /**
     * Pairs raw installed applications with Room's persisted usage metrics
     * and updates the UI state.
     */
    private fun updateUiAppsList() {
        val mapped = rawInstalledApps.map { app ->
            val record = persistedRecords[app.packageName]
            val hasRecord = record != null
            val foregroundMs = record?.totalForegroundMs ?: 0L
            val launches = record?.launchCount ?: 0
            AppUsageItem(
                packageName = app.packageName,
                label = app.label,
                totalForegroundMs = foregroundMs,
                launchCount = launches,
                hasUsageRecord = hasRecord,
                usageDisplay = formatAppUsageDisplay(foregroundMs, launches, hasRecord)
            )
        }

        _uiState.update { currentState ->
            val filtered = filterApps(mapped, currentState.searchQuery)
            currentState.copy(
                installedApps = mapped,
                filteredApps = filtered
            )
        }
    }

    /**
     * Starts the 1-second ticker job that keeps the home screen clock updated.
     */
    fun startClockTicker(scope: CoroutineScope = viewModelScope) {
        if (tickerJob?.isActive == true) return

        tickerJob = scope.launch {
            while (isActive) {
                refreshCurrentTime()
                delay(1000L)
            }
        }
    }

    /**
     * Stops the clock ticker job to save battery when the home screen is not visible.
     */
    fun stopClockTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /**
     * Manually refreshes the time and date in the UI state.
     */
    fun refreshCurrentTime() {
        val now = LocalDateTime.now()
        val formattedTime = now.format(timeFormatter)
        val formattedDate = now.format(dateFormatter)

        _uiState.update { currentState ->
            currentState.copy(
                currentTime = formattedTime,
                currentDate = formattedDate
            )
        }
    }

    /**
     * Refreshes today's usage statistics from UsageStatsManager
     * and triggers background synchronization if due.
     */
    fun refreshUsageStats() {
        val today = getTodayDate()
        startObservingPersistedRecords(today)

        val usage = getTodayUsageUseCase()
        val display = when (usage) {
            is DailyUsage.Loaded -> formatUsageDuration(usage.totalForegroundMs)
            is DailyUsage.PermissionDenied, DailyUsage.Unavailable -> "--h --m"
        }

        _uiState.update { currentState ->
            currentState.copy(
                usageState = usage,
                screenTimeDisplay = display
            )
        }

        // Trigger synchronization asynchronously with the retrieved usage result
        syncUsageIfDue(usage)
    }

    /**
     * Triggers usage and launch synchronization in the background if the 15-minute interval has elapsed.
     */
    fun syncUsageIfDue(currentUsage: DailyUsage? = null) {
        viewModelScope.launch {
            try {
                val usage = currentUsage ?: getTodayUsageUseCase()
                syncUsageDataUseCase(usage)
            } catch (_: Exception) {
                // Synchronization failures must never crash MINIMA or interrupt UI
            }
        }
    }

    /**
     * Opens Android's system Settings screen to grant Usage Access.
     */
    fun openUsageAccessSettings() {
        openUsageAccessSettingsUseCase()
    }

    /**
     * Evaluates whether the user has repeatedly opened an application recently,
     * respecting session-scoped dismissal.
     */
    fun checkRepeatOpenAlert() {
        viewModelScope.launch {
            try {
                val alert = detectRepeatedOpeningUseCase(currentTimeMillisProvider())
                if (alert != null) {
                    val isDismissed = alert.packageName == dismissedPackage && alert.attemptCount <= dismissedLaunchCount
                    if (!isDismissed) {
                        _uiState.update { it.copy(repeatOpenAlert = alert) }
                    } else {
                        _uiState.update { it.copy(repeatOpenAlert = null) }
                    }
                } else {
                    _uiState.update { it.copy(repeatOpenAlert = null) }
                }
            } catch (_: Exception) {
                // Repeat-open detection failures must never crash MINIMA
            }
        }
    }

    /**
     * Dismisses the active repeat-open alert for the current MINIMA session.
     * The alert remains hidden across onResume calls unless a new launch occurs
     * for this app or another app reaches the threshold.
     */
    fun dismissRepeatAlert() {
        val currentAlert = _uiState.value.repeatOpenAlert
        if (currentAlert != null) {
            dismissedPackage = currentAlert.packageName
            dismissedLaunchCount = currentAlert.attemptCount
        }
        _uiState.update { it.copy(repeatOpenAlert = null) }
    }

    /**
     * Refreshes the list of installed applications from PackageManager.
     */
    fun refreshInstalledApps() {
        rawInstalledApps = getInstalledAppsUseCase()
        updateUiAppsList()
    }

    /**
     * Updates the search filter query and recalculates matching applications.
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { currentState ->
            val filtered = filterApps(currentState.installedApps, query)
            currentState.copy(
                searchQuery = query,
                filteredApps = filtered
            )
        }
    }

    /**
     * Clears the current search query and resets the filtered list to all installed apps.
     */
    fun clearSearchQuery() {
        onSearchQueryChanged("")
    }

    /**
     * Initiates opening of an application.
     *
     * Respects the configured [AppOpenDelay]:
     * - If [AppOpenDelay.OFF], executes immediate launch and records attempt.
     * - If delay is enabled, starts a lifecycle-aware countdown. Launch and recording
     *   occur exactly once upon countdown completion. If cancelled, neither launch nor
     *   recording occurs.
     *
     * Debounces rapid repeated taps to prevent duplicate launches.
     */
    fun launchApp(packageName: String): Boolean {
        val currentTime = currentTimeMillisProvider()

        // 1. Debounce rapid repeated taps within 800ms
        if (currentTime - lastLaunchTimeMs < 800L) {
            return false
        }

        // 2. Reject if a countdown is already actively ticking
        if (countdownJob?.isActive == true || _uiState.value.activeCountdown != null) {
            return false
        }

        val delay = _uiState.value.appOpenDelay
        if (delay == AppOpenDelay.OFF || delay.seconds <= 0) {
            lastLaunchTimeMs = currentTime
            return executeLaunch(packageName)
        }

        val app = rawInstalledApps.find { it.packageName == packageName }
        val appLabel = app?.label ?: packageName

        startCountdown(packageName, appLabel, delay.seconds)
        return true
    }

    /**
     * Starts an intentional countdown before opening an application.
     */
    private fun startCountdown(packageName: String, appLabel: String, totalSeconds: Int) {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                activeCountdown = ActiveCountdown(
                    packageName = packageName,
                    appLabel = appLabel,
                    remainingSeconds = totalSeconds,
                    totalSeconds = totalSeconds
                )
            )
        }

        countdownJob = viewModelScope.launch {
            try {
                for (remaining in totalSeconds downTo 1) {
                    _uiState.update {
                        it.copy(
                            activeCountdown = ActiveCountdown(
                                packageName = packageName,
                                appLabel = appLabel,
                                remainingSeconds = remaining,
                                totalSeconds = totalSeconds
                            )
                        )
                    }
                    delay(1000L)
                }

                // Countdown finished: clear countdown, update launch timestamp, record attempt, and launch
                _uiState.update { it.copy(activeCountdown = null) }
                countdownJob = null
                lastLaunchTimeMs = currentTimeMillisProvider()
                executeLaunch(packageName)
            } catch (e: CancellationException) {
                _uiState.update { it.copy(activeCountdown = null) }
                countdownJob = null
                throw e
            }
        }
    }

    /**
     * Cancels any active countdown without launching the application or recording a launch attempt.
     */
    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update { it.copy(activeCountdown = null) }
    }

    /**
     * Executes the actual app launch and records the launch attempt in Room.
     */
    private fun executeLaunch(packageName: String): Boolean {
        // 1. Asynchronously record the launch attempt
        viewModelScope.launch {
            try {
                recordLaunchAttemptUseCase(packageName)
            } catch (_: Exception) {
                // Recording failure must never prevent app launching or crash
            }
        }

        // 2. Launch application immediately
        val success = launchAppUseCase(packageName)
        if (!success) {
            _uiState.update { it.copy(errorMessage = "Unable to open application") }
            refreshInstalledApps()
        }
        return success
    }

    /**
     * Clears any active error message.
     */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Filters a list of applications based on a search query.
     */
    private fun filterApps(apps: List<AppUsageItem>, query: String): List<AppUsageItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return apps
        return apps.filter { it.label.contains(trimmed, ignoreCase = true) }
    }

    override fun onCleared() {
        super.onCleared()
        stopClockTicker()
        cancelCountdown()
        recordsObserverJob?.cancel()
    }

    companion object {
        /**
         * Formats milliseconds into human-readable hours and minutes ("Xh YYm").
         */
        fun formatUsageDuration(totalMs: Long): String {
            if (totalMs <= 0L) return "0h 00m"
            val totalMinutes = totalMs / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return String.format(Locale.US, "%dh %02dm", hours, minutes)
        }

        /**
         * Formats per-app foreground duration and launch count for display on the home screen.
         * e.g.: "1h 24m · 3 opens", "45m · 1 open", "0m · 2 opens", or "—" if no record exists.
         */
        fun formatAppUsageDisplay(foregroundMs: Long, launchCount: Int, hasRecord: Boolean): String {
            if (!hasRecord) return "—"

            val durationStr = if (foregroundMs >= 60_000L) {
                val totalMinutes = foregroundMs / (1000 * 60)
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                if (hours > 0) {
                    String.format(Locale.US, "%dh %02dm", hours, minutes)
                } else {
                    "${minutes}m"
                }
            } else if (foregroundMs > 0L) {
                "<1m"
            } else {
                "0m"
            }

            val launchStr = when (launchCount) {
                1 -> "1 open"
                else -> "$launchCount opens"
            }

            return "$durationStr · $launchStr"
        }
    }
}
