package com.minima.data.repository

import com.minima.data.local.db.dao.LaunchAttemptDao
import com.minima.data.local.db.entity.LaunchAttemptEntity
import com.minima.domain.repository.LaunchAttemptRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Concrete implementation of [LaunchAttemptRepository] backed by [LaunchAttemptDao].
 */
class LaunchAttemptRepositoryImpl @Inject constructor(
    private val launchAttemptDao: LaunchAttemptDao
) : LaunchAttemptRepository {

    override suspend fun recordAttempt(
        packageName: String,
        timestampMs: Long
    ): Result<Long> {
        return try {
            val id = launchAttemptDao.insert(
                LaunchAttemptEntity(
                    packageName = packageName,
                    attemptedAt = timestampMs
                )
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAttemptsForToday(): List<LaunchAttemptEntity> {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zoneId)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val endOfDay = startOfDay + 86_400_000L
        return getAttemptsForRange(startOfDay, endOfDay)
    }

    override suspend fun getAttemptsForPackageToday(packageName: String): Int {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zoneId)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val endOfDay = startOfDay + 86_400_000L
        return getAttemptsForPackageInRange(packageName, startOfDay, endOfDay)
    }

    override suspend fun getAttemptsForRange(startMs: Long, endMs: Long): List<LaunchAttemptEntity> {
        return launchAttemptDao.getByRange(startMs, endMs)
    }

    override suspend fun getAttemptsForPackageInRange(packageName: String, startMs: Long, endMs: Long): Int {
        return launchAttemptDao.countForPackageInRange(packageName, startMs, endMs)
    }

    override suspend fun getLatestLaunch(): LaunchAttemptEntity? {
        return launchAttemptDao.getLatestLaunch()
    }
}

