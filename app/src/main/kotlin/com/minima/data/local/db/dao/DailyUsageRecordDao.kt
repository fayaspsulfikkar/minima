package com.minima.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.minima.data.local.db.entity.DailyUsageRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the daily_usage_records table.
 *
 * Uses @Upsert (Room 2.5+): inserts the row if the primary key is new,
 * or replaces the row if the primary key already exists.
 * This is the correct strategy for refreshing daily usage throughout the day.
 */
@Dao
interface DailyUsageRecordDao {

    /**
     * Insert or replace a single daily usage record.
     */
    @Upsert
    suspend fun upsert(record: DailyUsageRecordEntity)

    /**
     * Insert or replace a batch of daily usage records in one transaction.
     * Efficient when synchronising full-day data from UsageStatsManager.
     */
    @Upsert
    suspend fun upsertAll(records: List<DailyUsageRecordEntity>)

    /**
     * Retrieve the usage record for a specific app on a specific date.
     * Returns null if no record exists yet.
     */
    @Query(
        "SELECT * FROM daily_usage_records " +
        "WHERE packageName = :packageName " +
        "AND date = :date"
    )
    suspend fun getRecord(packageName: String, date: String): DailyUsageRecordEntity?

    /**
     * Retrieve all usage records for a given date as a one-shot list.
     */
    @Query("SELECT * FROM daily_usage_records WHERE date = :date")
    suspend fun getRecordsForDate(date: String): List<DailyUsageRecordEntity>

    /**
     * Observe all usage records for a given date as a reactive Flow.
     * Emits a new list whenever the underlying data changes.
     * Records are ordered by total foreground time descending (heaviest use first).
     */
    @Query(
        "SELECT * FROM daily_usage_records " +
        "WHERE date = :date " +
        "ORDER BY totalForegroundMs DESC"
    )
    fun getRecordsForDateFlow(date: String): Flow<List<DailyUsageRecordEntity>>
}

