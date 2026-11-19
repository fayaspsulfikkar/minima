package com.minima.data.local.db.entity

import androidx.room.Entity

/**
 * Represents the aggregated foreground usage for one application on one calendar day.
 *
 * The composite primary key (packageName + date) guarantees at most one record per
 * app per day — upsert replaces the row when usage is refreshed throughout the day.
 *
 * date format: ISO 8601 date string — "YYYY-MM-DD" (e.g. "2026-11-19")
 */
@Entity(
    tableName = "daily_usage_records",
    primaryKeys = ["packageName", "date"]
)
data class DailyUsageRecordEntity(
    val packageName: String,
    val date: String,               // ISO format: YYYY-MM-DD
    val totalForegroundMs: Long,    // total foreground time in milliseconds
    val launchCount: Int            // number of times brought to foreground
)

