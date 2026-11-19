package com.minima.domain.repository

import com.minima.data.local.db.entity.LaunchAttemptEntity

/**
 * Repository interface for recording and querying user app-launch attempts.
 */
interface LaunchAttemptRepository {

    /**
     * Records an app-launch attempt.
     *
     * @param packageName Package identifier of the launched app.
     * @param timestampMs Timestamp of the launch attempt in epoch milliseconds.
     * @return [Result.success] with the inserted row id, or [Result.failure] if an error occurred.
     */
    suspend fun recordAttempt(
        packageName: String,
        timestampMs: Long = System.currentTimeMillis()
    ): Result<Long>

    /**
     * Retrieves all launch attempts recorded today (from midnight to end-of-day in local zone).
     */
    suspend fun getAttemptsForToday(): List<LaunchAttemptEntity>

    /**
     * Counts launch attempts for a specific package today.
     */
    suspend fun getAttemptsForPackageToday(packageName: String): Int

    /**
     * Retrieves all launch attempts recorded within a specific time window.
     */
    suspend fun getAttemptsForRange(startMs: Long, endMs: Long): List<LaunchAttemptEntity>

    /**
     * Counts launch attempts for a specific package within a given time range.
     */
    suspend fun getAttemptsForPackageInRange(packageName: String, startMs: Long, endMs: Long): Int

    /**
     * Retrieves the most recently recorded launch attempt across all apps, or null if none exist.
     */
    suspend fun getLatestLaunch(): LaunchAttemptEntity?
}

