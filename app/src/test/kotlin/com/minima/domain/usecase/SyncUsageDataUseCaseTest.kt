package com.minima.domain.usecase

import com.minima.data.local.db.dao.DailyUsageRecordDao
import com.minima.data.local.db.entity.DailyUsageRecordEntity
import com.minima.data.local.db.entity.LaunchAttemptEntity
import com.minima.domain.model.AppOpenDelay
import com.minima.domain.model.DailyUsage
import com.minima.domain.repository.LaunchAttemptRepository
import com.minima.domain.repository.SettingsRepository
import com.minima.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Unit tests verifying [SyncUsageDataUseCase] data integrity, multi-day historical reconciliation,
 * and date-boundary synchronization rules.
 */
class SyncUsageDataUseCaseTest {

    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeLaunchAttemptRepository: FakeLaunchAttemptRepository
    private lateinit var fakeDailyUsageRecordDao: FakeDailyUsageRecordDao
    private lateinit var fakeUsageRepository: FakeUsageRepository
    private lateinit var useCase: SyncUsageDataUseCase

    private val testZone = ZoneOffset.UTC
    // Reference timestamp: 2023-11-15T12:00:00Z
    private val nowMs = 1_700_050_800_000L
    private val todayDate = Instant.ofEpochMilli(nowMs).atZone(testZone).toLocalDate().toString()

    @Before
    fun setUp() {
        fakeSettingsRepository = FakeSettingsRepository()
        fakeLaunchAttemptRepository = FakeLaunchAttemptRepository()
        fakeDailyUsageRecordDao = FakeDailyUsageRecordDao()
        fakeUsageRepository = FakeUsageRepository()

        useCase = SyncUsageDataUseCase(
            settingsRepository = fakeSettingsRepository,
            launchAttemptRepository = fakeLaunchAttemptRepository,
            dailyUsageRecordDao = fakeDailyUsageRecordDao,
            usageRepository = fakeUsageRepository
        )
    }

    @Test
    fun syncUsage_within15MinuteInterval_isSkipped() = runTest {
        // Last sync was 10 minutes ago
        val tenMinutesAgo = nowMs - (10 * 60 * 1000L)
        fakeSettingsRepository.setLastSyncTimestamp(tenMinutesAgo)

        val result = useCase(currentTimeMs = nowMs, zoneId = testZone)

        assertEquals(SyncResult.SkippedInterval, result)
        assertEquals(tenMinutesAgo, fakeSettingsRepository.getLastSyncTimestamp())
        assertTrue("No records should be upserted when skipped", fakeDailyUsageRecordDao.records.isEmpty())
    }

    @Test
    fun syncUsage_after15MinuteInterval_runsSuccessfully() = runTest {
        // Last sync was 16 minutes ago
        val sixteenMinutesAgo = nowMs - (16 * 60 * 1000L)
        fakeSettingsRepository.setLastSyncTimestamp(sixteenMinutesAgo)

        val usage = DailyUsage.Loaded(
            totalForegroundMs = 3_600_000L,
            packageUsage = mapOf("com.example.app" to 3_600_000L)
        )
        fakeUsageRepository.currentUsage = usage

        val result = useCase(currentUsage = usage, currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)
        assertEquals(nowMs, fakeSettingsRepository.getLastSyncTimestamp())
    }

    @Test
    fun syncUsage_aggregatesLaunchCountsCorrectly() = runTest {
        // 3 attempts for App A, 1 attempt for App B today
        fakeLaunchAttemptRepository.attempts.addAll(
            listOf(
                LaunchAttemptEntity(id = 1, packageName = "com.app.a", attemptedAt = nowMs - 5000),
                LaunchAttemptEntity(id = 2, packageName = "com.app.a", attemptedAt = nowMs - 3000),
                LaunchAttemptEntity(id = 3, packageName = "com.app.a", attemptedAt = nowMs - 1000),
                LaunchAttemptEntity(id = 4, packageName = "com.app.b", attemptedAt = nowMs - 500)
            )
        )

        val usage = DailyUsage.Loaded(
            totalForegroundMs = 1_000L,
            packageUsage = mapOf("com.app.a" to 1_000L)
        )

        val result = useCase(currentUsage = usage, currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)
        val recordA = fakeDailyUsageRecordDao.getRecord("com.app.a", todayDate)
        val recordB = fakeDailyUsageRecordDao.getRecord("com.app.b", todayDate)

        assertEquals(3, recordA?.launchCount)
        assertEquals(1_000L, recordA?.totalForegroundMs)

        assertEquals(1, recordB?.launchCount)
        assertEquals(0L, recordB?.totalForegroundMs) // Valid usage query -> 0L for app with no foreground
    }

    @Test
    fun syncUsage_combinesForegroundAndLaunchAttempts() = runTest {
        // App A has only launch attempts
        fakeLaunchAttemptRepository.attempts.add(
            LaunchAttemptEntity(id = 1, packageName = "com.app.a", attemptedAt = nowMs)
        )

        // App B has only foreground stats
        val usage = DailyUsage.Loaded(
            totalForegroundMs = 5_000_000L,
            packageUsage = mapOf("com.app.b" to 5_000_000L)
        )

        val result = useCase(currentUsage = usage, currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)
        val recordA = fakeDailyUsageRecordDao.getRecord("com.app.a", todayDate)
        val recordB = fakeDailyUsageRecordDao.getRecord("com.app.b", todayDate)

        // App A: 1 launch, 0 foreground
        assertEquals(1, recordA?.launchCount)
        assertEquals(0L, recordA?.totalForegroundMs)

        // App B: 0 launches, 5_000_000 foreground
        assertEquals(0, recordB?.launchCount)
        assertEquals(5_000_000L, recordB?.totalForegroundMs)
    }

    @Test
    fun syncUsage_whenUsageUnavailable_preservesStoredForegroundTime() = runTest {
        // Pre-existing stored record with positive foreground time
        fakeDailyUsageRecordDao.upsert(
            DailyUsageRecordEntity(
                packageName = "com.example.app",
                date = todayDate,
                totalForegroundMs = 7_200_000L,
                launchCount = 2
            )
        )

        // New launch attempts today
        fakeLaunchAttemptRepository.attempts.addAll(
            listOf(
                LaunchAttemptEntity(id = 1, packageName = "com.example.app", attemptedAt = nowMs - 100),
                LaunchAttemptEntity(id = 2, packageName = "com.example.app", attemptedAt = nowMs - 50),
                LaunchAttemptEntity(id = 3, packageName = "com.example.app", attemptedAt = nowMs)
            )
        )

        // Usage data is currently Unavailable
        val result = useCase(currentUsage = DailyUsage.Unavailable, currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertFalse("Timestamp must not update when usage is Unavailable", success.timestampUpdated)

        val record = fakeDailyUsageRecordDao.getRecord("com.example.app", todayDate)
        // Must preserve previously stored 7_200_000L foreground time!
        assertEquals(7_200_000L, record?.totalForegroundMs)
        // Must update launch count to 3
        assertEquals(3, record?.launchCount)
    }

    @Test
    fun syncUsage_whenPermissionDenied_doesNotOverwriteStoredUsage() = runTest {
        // Pre-existing stored record with positive foreground time
        fakeDailyUsageRecordDao.upsert(
            DailyUsageRecordEntity(
                packageName = "com.example.app",
                date = todayDate,
                totalForegroundMs = 10_000_000L,
                launchCount = 1
            )
        )

        fakeLaunchAttemptRepository.attempts.add(
            LaunchAttemptEntity(id = 1, packageName = "com.example.app", attemptedAt = nowMs)
        )

        // Permission denied
        val result = useCase(currentUsage = DailyUsage.PermissionDenied, currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertFalse("Timestamp must not update when permission is denied", success.timestampUpdated)

        val record = fakeDailyUsageRecordDao.getRecord("com.example.app", todayDate)
        assertEquals(10_000_000L, record?.totalForegroundMs)
    }

    @Test
    fun syncUsage_whenSuccessful_updatesTimestamp() = runTest {
        val usage = DailyUsage.Loaded(
            totalForegroundMs = 1_000L,
            packageUsage = mapOf("com.example.app" to 1_000L)
        )

        assertEquals(0L, fakeSettingsRepository.getLastSyncTimestamp())

        val result = useCase(currentUsage = usage, currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertTrue(success.timestampUpdated)
        assertEquals(nowMs, fakeSettingsRepository.getLastSyncTimestamp())
    }

    @Test
    fun syncUsage_whenFails_doesNotUpdateTimestamp() = runTest {
        fakeDailyUsageRecordDao.shouldFail = true

        val usage = DailyUsage.Loaded(
            totalForegroundMs = 1_000L,
            packageUsage = mapOf("com.example.app" to 1_000L)
        )

        val result = useCase(currentUsage = usage, currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Failure)
        assertEquals(0L, fakeSettingsRepository.getLastSyncTimestamp())
    }

    @Test
    fun recordLaunchAttempt_insertsSuccessfully() = runTest {
        val recordUseCase = RecordLaunchAttemptUseCase(fakeLaunchAttemptRepository)
        val result = recordUseCase("com.test.app", nowMs)

        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrNull())
        assertEquals(1, fakeLaunchAttemptRepository.attempts.size)
        assertEquals("com.test.app", fakeLaunchAttemptRepository.attempts[0].packageName)
    }

    // --- Phase 7 Dedicated Unit Tests ---

    @Test
    fun sync_whenLastSyncTimestampMissing_syncsTodayOnlyWithoutHistoricalBackfill() = runTest {
        // lastSyncTimestamp is 0L (missing / first sync)
        fakeSettingsRepository.setLastSyncTimestamp(0L)

        val todayUsage = DailyUsage.Loaded(
            totalForegroundMs = 2_000_000L,
            packageUsage = mapOf("com.app.today" to 2_000_000L)
        )
        fakeUsageRepository.currentUsage = todayUsage

        val result = useCase(currentUsage = todayUsage, currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)
        // Today's record exists
        val todayRecord = fakeDailyUsageRecordDao.getRecord("com.app.today", todayDate)
        assertEquals(2_000_000L, todayRecord?.totalForegroundMs)

        // No historical backfill records were created
        val allDates = fakeDailyUsageRecordDao.records.values.map { it.date }.toSet()
        assertEquals(setOf(todayDate), allDates)
        assertEquals(nowMs, fakeSettingsRepository.getLastSyncTimestamp())
    }

    @Test
    fun sync_whenHistoricalUsageUnavailable_preservesExistingPositiveRecords() = runTest {
        val todayLocalDate = Instant.ofEpochMilli(nowMs).atZone(testZone).toLocalDate()
        val yesterdayLocalDate = todayLocalDate.minusDays(1)
        val yesterdayDate = yesterdayLocalDate.toString()
        val yesterdayStartMs = yesterdayLocalDate.atStartOfDay(testZone).toInstant().toEpochMilli()
        val yesterdayEndMs = yesterdayLocalDate.plusDays(1).atStartOfDay(testZone).toInstant().toEpochMilli()

        // Last sync was yesterday mid-day
        fakeSettingsRepository.setLastSyncTimestamp(yesterdayStartMs + 10_000L)

        // Existing positive record stored yesterday
        fakeDailyUsageRecordDao.upsert(
            DailyUsageRecordEntity(
                packageName = "com.app.history",
                date = yesterdayDate,
                totalForegroundMs = 8_500_000L,
                launchCount = 3
            )
        )

        // Historical query returns Unavailable
        fakeUsageRepository.rangeUsageMap[Pair(yesterdayStartMs, yesterdayEndMs)] = DailyUsage.Unavailable
        fakeUsageRepository.currentUsage = DailyUsage.Loaded(1_000L, mapOf("com.app.today" to 1_000L))

        val result = useCase(currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)
        val yesterdayRecord = fakeDailyUsageRecordDao.getRecord("com.app.history", yesterdayDate)
        // Must preserve existing positive 8_500_000L foreground time
        assertEquals(8_500_000L, yesterdayRecord?.totalForegroundMs)
        assertEquals(3, yesterdayRecord?.launchCount)
    }

    @Test
    fun sync_whenHistoricalUsageLowerThanStored_preservesHigherStoredRecord() = runTest {
        val todayLocalDate = Instant.ofEpochMilli(nowMs).atZone(testZone).toLocalDate()
        val yesterdayLocalDate = todayLocalDate.minusDays(1)
        val yesterdayDate = yesterdayLocalDate.toString()
        val yesterdayStartMs = yesterdayLocalDate.atStartOfDay(testZone).toInstant().toEpochMilli()
        val yesterdayEndMs = yesterdayLocalDate.plusDays(1).atStartOfDay(testZone).toInstant().toEpochMilli()

        fakeSettingsRepository.setLastSyncTimestamp(yesterdayStartMs + 5_000L)

        // Pre-existing stored record had 5_000_000L
        fakeDailyUsageRecordDao.upsert(
            DailyUsageRecordEntity(
                packageName = "com.app.history",
                date = yesterdayDate,
                totalForegroundMs = 5_000_000L,
                launchCount = 4
            )
        )

        // Historical query returns lower usage (e.g. 2_000_000L due to system anomaly)
        fakeUsageRepository.rangeUsageMap[Pair(yesterdayStartMs, yesterdayEndMs)] = DailyUsage.Loaded(
            totalForegroundMs = 2_000_000L,
            packageUsage = mapOf("com.app.history" to 2_000_000L)
        )
        fakeUsageRepository.currentUsage = DailyUsage.Loaded(0L)

        val result = useCase(currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)
        val yesterdayRecord = fakeDailyUsageRecordDao.getRecord("com.app.history", yesterdayDate)
        // Must enforce max(queried, stored) -> 5_000_000L
        assertEquals(5_000_000L, yesterdayRecord?.totalForegroundMs)
    }

    @Test
    fun sync_whenMoreThanSevenDaysBetweenSyncs_capsLookbackToSevenDays() = runTest {
        val todayLocalDate = Instant.ofEpochMilli(nowMs).atZone(testZone).toLocalDate()
        // Last sync was 30 days ago
        val thirtyDaysAgoMs = nowMs - (30L * 24 * 60 * 60 * 1000L)
        fakeSettingsRepository.setLastSyncTimestamp(thirtyDaysAgoMs)

        fakeUsageRepository.currentUsage = DailyUsage.Loaded(0L)

        val result = useCase(currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)

        // Verified queried date ranges
        val queriedRanges = fakeUsageRepository.queriedRanges
        for ((startMs, _) in queriedRanges) {
            val queriedLocalDate = Instant.ofEpochMilli(startMs).atZone(testZone).toLocalDate()
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(queriedLocalDate, todayLocalDate)
            assertTrue(
                "Queried date $queriedLocalDate was $daysBetween days ago; must not exceed 7 days lookback",
                daysBetween <= 7
            )
        }
    }

    @Test
    fun sync_dateBoundaryHandling_isolatesMidnightCorrectly() = runTest {
        val todayLocalDate = Instant.ofEpochMilli(nowMs).atZone(testZone).toLocalDate()
        val yesterdayLocalDate = todayLocalDate.minusDays(1)
        val yesterdayDate = yesterdayLocalDate.toString()
        val yesterdayStartMs = yesterdayLocalDate.atStartOfDay(testZone).toInstant().toEpochMilli()
        val todayStartMs = todayLocalDate.atStartOfDay(testZone).toInstant().toEpochMilli()

        // Last sync was yesterday morning
        fakeSettingsRepository.setLastSyncTimestamp(yesterdayStartMs + 1000L)

        // Launch at 23:59:59 yesterday (1 second before midnight)
        val justBeforeMidnight = todayStartMs - 1000L
        // Launch at 00:00:01 today (1 second after midnight)
        val justAfterMidnight = todayStartMs + 1000L

        fakeLaunchAttemptRepository.attempts.addAll(
            listOf(
                LaunchAttemptEntity(1, "com.app.boundary", justBeforeMidnight),
                LaunchAttemptEntity(2, "com.app.boundary", justAfterMidnight)
            )
        )

        fakeUsageRepository.currentUsage = DailyUsage.Loaded(
            totalForegroundMs = 1_000L,
            packageUsage = mapOf("com.app.boundary" to 1_000L)
        )

        val result = useCase(currentTimeMs = nowMs, zoneId = testZone)

        assertTrue(result is SyncResult.Success)
        val yesterdayRecord = fakeDailyUsageRecordDao.getRecord("com.app.boundary", yesterdayDate)
        val todayRecord = fakeDailyUsageRecordDao.getRecord("com.app.boundary", todayDate)

        // Exactly 1 launch belongs to yesterday
        assertEquals(1, yesterdayRecord?.launchCount)
        // Exactly 1 launch belongs to today
        assertEquals(1, todayRecord?.launchCount)
    }

    // --- Test Doubles ---

    private class FakeLaunchAttemptRepository : LaunchAttemptRepository {
        val attempts = mutableListOf<LaunchAttemptEntity>()

        override suspend fun recordAttempt(packageName: String, timestampMs: Long): Result<Long> {
            val entity = LaunchAttemptEntity(id = attempts.size + 1L, packageName = packageName, attemptedAt = timestampMs)
            attempts.add(entity)
            return Result.success(entity.id)
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

    private class FakeSettingsRepository : SettingsRepository {
        private var timestamp: Long = 0L
        private var delay: AppOpenDelay = AppOpenDelay.OFF

        override val lastSyncTimestamp: Flow<Long> = flowOf(timestamp)
        override suspend fun getLastSyncTimestamp(): Long = timestamp
        override suspend fun setLastSyncTimestamp(timestampMs: Long) {
            timestamp = timestampMs
        }

        override val appOpenDelay: Flow<AppOpenDelay> = flowOf(delay)
        override suspend fun getAppOpenDelay(): AppOpenDelay = delay
        override suspend fun setAppOpenDelay(delay: AppOpenDelay) {
            this.delay = delay
        }
    }

    private class FakeDailyUsageRecordDao : DailyUsageRecordDao {
        val records = mutableMapOf<Pair<String, String>, DailyUsageRecordEntity>()
        var shouldFail: Boolean = false

        override suspend fun upsert(record: DailyUsageRecordEntity) {
            if (shouldFail) throw RuntimeException("Simulated SQLite write error")
            records[Pair(record.packageName, record.date)] = record
        }

        override suspend fun upsertAll(records: List<DailyUsageRecordEntity>) {
            if (shouldFail) throw RuntimeException("Simulated SQLite write error")
            records.forEach { upsert(it) }
        }

        override suspend fun getRecord(packageName: String, date: String): DailyUsageRecordEntity? {
            return records[Pair(packageName, date)]
        }

        override suspend fun getRecordsForDate(date: String): List<DailyUsageRecordEntity> {
            return records.values.filter { it.date == date }
        }

        override fun getRecordsForDateFlow(date: String): Flow<List<DailyUsageRecordEntity>> {
            return flowOf(records.values.filter { it.date == date }.sortedByDescending { it.totalForegroundMs })
        }
    }

    private class FakeUsageRepository : UsageRepository {
        var currentUsage: DailyUsage = DailyUsage.Unavailable
        val rangeUsageMap = mutableMapOf<Pair<Long, Long>, DailyUsage>()
        val queriedRanges = mutableListOf<Pair<Long, Long>>()

        override fun hasUsagePermission(): Boolean = currentUsage !is DailyUsage.PermissionDenied
        override fun getTodayUsage(): DailyUsage = currentUsage
        override fun queryUsageForRange(startTimeMs: Long, endTimeMs: Long): DailyUsage {
            queriedRanges.add(Pair(startTimeMs, endTimeMs))
            return rangeUsageMap[Pair(startTimeMs, endTimeMs)] ?: currentUsage
        }
        override fun openUsageAccessSettings() {}
    }
}
