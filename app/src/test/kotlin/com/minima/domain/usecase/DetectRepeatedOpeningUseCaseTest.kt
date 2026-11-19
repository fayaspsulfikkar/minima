package com.minima.domain.usecase

import com.minima.data.local.db.entity.LaunchAttemptEntity
import com.minima.domain.model.AppInfo
import com.minima.domain.model.RepeatOpenAlert
import com.minima.domain.repository.AppRepository
import com.minima.domain.repository.LaunchAttemptRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DetectRepeatedOpeningUseCase] verifying repeat-open detection threshold
 * (>= 3 successful launches in rolling 10m window) and latest app identification.
 */
class DetectRepeatedOpeningUseCaseTest {

    private lateinit var fakeLaunchAttemptRepository: FakeLaunchAttemptRepository
    private lateinit var fakeAppRepository: FakeAppRepository
    private lateinit var useCase: DetectRepeatedOpeningUseCase

    private val nowMs = 1_700_000_000_000L

    @Before
    fun setUp() {
        fakeLaunchAttemptRepository = FakeLaunchAttemptRepository()
        fakeAppRepository = FakeAppRepository(
            listOf(
                AppInfo("com.social.app", "Social App"),
                AppInfo("com.work.app", "Work App")
            )
        )
        useCase = DetectRepeatedOpeningUseCase(
            launchAttemptRepository = fakeLaunchAttemptRepository,
            appRepository = fakeAppRepository
        )
    }

    @Test
    fun detectRepeatedOpening_whenNoLaunches_returnsNull() = runTest {
        val alert = useCase(currentTimeMs = nowMs)
        assertNull(alert)
    }

    @Test
    fun detectRepeatedOpening_whenUnderThreshold_returnsNull() = runTest {
        // 2 launches within 10 minutes (threshold is 3)
        fakeLaunchAttemptRepository.attempts.addAll(
            listOf(
                LaunchAttemptEntity(1, "com.social.app", nowMs - 5 * 60 * 1000L),
                LaunchAttemptEntity(2, "com.social.app", nowMs - 1 * 60 * 1000L)
            )
        )

        val alert = useCase(currentTimeMs = nowMs)
        assertNull(alert)
    }

    @Test
    fun detectRepeatedOpening_whenThreeLaunchesInTenMinutes_returnsAlert() = runTest {
        // 3 launches within 10 minutes
        fakeLaunchAttemptRepository.attempts.addAll(
            listOf(
                LaunchAttemptEntity(1, "com.social.app", nowMs - 8 * 60 * 1000L),
                LaunchAttemptEntity(2, "com.social.app", nowMs - 4 * 60 * 1000L),
                LaunchAttemptEntity(3, "com.social.app", nowMs - 30 * 1000L)
            )
        )

        val alert = useCase(currentTimeMs = nowMs)
        assertNotNull(alert)
        assertEquals(
            RepeatOpenAlert(
                packageName = "com.social.app",
                appLabel = "Social App",
                attemptCount = 3,
                windowMinutes = 10
            ),
            alert
        )
    }

    @Test
    fun detectRepeatedOpening_whenLaunchesOutsideTenMinutes_ignored() = runTest {
        // 2 launches older than 10 minutes, 2 launches within 10 minutes
        fakeLaunchAttemptRepository.attempts.addAll(
            listOf(
                LaunchAttemptEntity(1, "com.social.app", nowMs - 15 * 60 * 1000L),
                LaunchAttemptEntity(2, "com.social.app", nowMs - 11 * 60 * 1000L),
                LaunchAttemptEntity(3, "com.social.app", nowMs - 6 * 60 * 1000L),
                LaunchAttemptEntity(4, "com.social.app", nowMs - 1 * 60 * 1000L)
            )
        )

        val alert = useCase(currentTimeMs = nowMs)
        assertNull("Launches older than 10 minutes must not count towards the 3-attempt threshold", alert)
    }

    @Test
    fun detectRepeatedOpening_whenLatestLaunchOutsideTenMinutes_returnsNull() = runTest {
        // 3 launches, but all occurred 12 minutes ago
        fakeLaunchAttemptRepository.attempts.addAll(
            listOf(
                LaunchAttemptEntity(1, "com.social.app", nowMs - 15 * 60 * 1000L),
                LaunchAttemptEntity(2, "com.social.app", nowMs - 13 * 60 * 1000L),
                LaunchAttemptEntity(3, "com.social.app", nowMs - 11 * 60 * 1000L)
            )
        )

        val alert = useCase(currentTimeMs = nowMs)
        assertNull("Must return null when latest launch was > 10m ago", alert)
    }

    @Test
    fun detectRepeatedOpening_checksCandidateAppFromLatestLaunchOnly() = runTest {
        // Social App had 5 launches 20 minutes ago.
        // Work App had 1 launch 1 minute ago.
        fakeLaunchAttemptRepository.attempts.addAll(
            listOf(
                LaunchAttemptEntity(1, "com.social.app", nowMs - 25 * 60 * 1000L),
                LaunchAttemptEntity(2, "com.social.app", nowMs - 24 * 60 * 1000L),
                LaunchAttemptEntity(3, "com.social.app", nowMs - 23 * 60 * 1000L),
                LaunchAttemptEntity(4, "com.work.app", nowMs - 1 * 60 * 1000L)
            )
        )

        val alert = useCase(currentTimeMs = nowMs)
        assertNull("Latest launch is Work App (1 launch), so no alert should be triggered", alert)
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

    private class FakeAppRepository(private val apps: List<AppInfo>) : AppRepository {
        override fun getInstalledApps(): List<AppInfo> = apps
        override fun launchApp(packageName: String): Boolean = true
    }
}

