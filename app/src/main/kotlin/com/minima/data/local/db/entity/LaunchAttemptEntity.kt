package com.minima.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single app-launch attempt initiated through MINIMA.
 *
 * Every time the user taps an application in the launcher, one row is inserted here.
 * The table is designed for future reporting: "how many times did I open Instagram today?"
 *
 * Indexes:
 * - Composite (packageName, attemptedAt): fast per-app time-range queries
 * - Single (attemptedAt): fast all-app time-range queries
 */
@Entity(
    tableName = "launch_attempts",
    indices = [
        Index(value = ["packageName", "attemptedAt"]),
        Index(value = ["attemptedAt"])
    ]
)
data class LaunchAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val attemptedAt: Long   // epoch milliseconds
)

