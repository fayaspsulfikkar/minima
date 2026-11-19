package com.minima.domain.usecase

import com.minima.domain.model.AppInfo
import com.minima.domain.model.DailyUsage
import com.minima.domain.repository.AppRepository
import com.minima.domain.repository.UsageRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for domain use cases.
 */
class UseCasesTest {

    @Test
    fun getInstalledAppsUseCase_delegatesToRepository() {
        val expected = listOf(AppInfo("com.test", "Test App"))
        val fakeRepo = object : AppRepository {
            override fun getInstalledApps(): List<AppInfo> = expected
            override fun launchApp(packageName: String): Boolean = true
        }

        val useCase = GetInstalledAppsUseCase(fakeRepo)
        val result = useCase()

        assertEquals(expected, result)
    }

    @Test
    fun launchAppUseCase_delegatesToRepository() {
        var launchedPackage: String? = null
        val fakeRepo = object : AppRepository {
            override fun getInstalledApps(): List<AppInfo> = emptyList()
            override fun launchApp(packageName: String): Boolean {
                launchedPackage = packageName
                return true
            }
        }

        val useCase = LaunchAppUseCase(fakeRepo)
        val success = useCase("com.test.app")

        assertEquals(true, success)
        assertEquals("com.test.app", launchedPackage)
    }

    @Test
    fun getTodayUsageUseCase_delegatesToRepository() {
        val expected = DailyUsage.Loaded(3_600_000L)
        val fakeRepo = object : UsageRepository {
            override fun hasUsagePermission(): Boolean = true
            override fun getTodayUsage(): DailyUsage = expected
            override fun openUsageAccessSettings() {}
        }

        val useCase = GetTodayUsageUseCase(fakeRepo)
        val result = useCase()

        assertEquals(expected, result)
    }

    @Test
    fun openUsageAccessSettingsUseCase_delegatesToRepository() {
        var settingsOpened = false
        val fakeRepo = object : UsageRepository {
            override fun hasUsagePermission(): Boolean = false
            override fun getTodayUsage(): DailyUsage = DailyUsage.PermissionDenied
            override fun openUsageAccessSettings() {
                settingsOpened = true
            }
        }

        val useCase = OpenUsageAccessSettingsUseCase(fakeRepo)
        useCase()

        assertTrue(settingsOpened)
    }
}
