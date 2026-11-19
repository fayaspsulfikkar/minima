package com.minima.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minima.data.local.db.entity.LaunchAttemptEntity

/**
 * Data Access Object for the launch_attempts table.
 *
 * All functions are suspend functions so callers must invoke them from a coroutine.
 * Room guarantees that suspend functions run on a background dispatcher automatically.
 */
@Dao
interface LaunchAttemptDao {

    /**
     * Record one app-launch attempt.
     * Returns the new row's auto-generated id.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: LaunchAttemptEntity): Long

    /**
     * Retrieve all attempts for a specific app within a time window.
     * Useful for "how many times did I open this app today?"
     *
     * @param startMs  inclusive lower bound (epoch ms)
     * @param endMs    exclusive upper bound (epoch ms)
     */
    @Query(
        "SELECT * FROM launch_attempts " +
        "WHERE packageName = :packageName " +
        "AND attemptedAt >= :startMs " +
        "AND attemptedAt < :endMs"
    )
    suspend fun getByPackageAndRange(
        packageName: String,
        startMs: Long,
        endMs: Long
    ): List<LaunchAttemptEntity>

    /**
     * Retrieve all attempts across all apps within a time window.
     * Useful for "which apps did I open today?"
     *
     * @param startMs  inclusive lower bound (epoch ms)
     * @param endMs    exclusive upper bound (epoch ms)
     */
    @Query(
        "SELECT * FROM launch_attempts " +
        "WHERE attemptedAt >= :startMs " +
        "AND attemptedAt < :endMs"
    )
    suspend fun getByRange(startMs: Long, endMs: Long): List<LaunchAttemptEntity>

    /**
     * Count how many times a specific app was launched within a time window.
     * Returns 0 when no matching rows exist.
     */
    @Query(
        "SELECT COUNT(*) FROM launch_attempts " +
        "WHERE packageName = :packageName " +
        "AND attemptedAt >= :startMs " +
        "AND attemptedAt < :endMs"
    )
    suspend fun countForPackageInRange(
        packageName: String,
        startMs: Long,
        endMs: Long
    ): Int

    /**
     * Retrieve the most recent launch attempt across all apps.
     * Returns null if no launch attempts have been recorded yet.
     */
    @Query("SELECT * FROM launch_attempts ORDER BY attemptedAt DESC LIMIT 1")
    suspend fun getLatestLaunch(): LaunchAttemptEntity?
}

