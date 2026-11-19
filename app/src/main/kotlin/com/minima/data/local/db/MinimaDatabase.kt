package com.minima.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.minima.data.local.db.dao.DailyUsageRecordDao
import com.minima.data.local.db.dao.LaunchAttemptDao
import com.minima.data.local.db.entity.DailyUsageRecordEntity
import com.minima.data.local.db.entity.LaunchAttemptEntity

/**
 * MINIMA's single Room database.
 *
 * Database name : "minima.db"
 * Version       : 1
 * Entities      : LaunchAttemptEntity, DailyUsageRecordEntity
 *
 * exportSchema = false — schema export is not required for a personal-use project
 * and avoids a build warning about a missing schema export directory.
 *
 * Instantiation is handled externally (manually in Phase 4.3, via Hilt in Phase 4.4).
 * This class must remain free of Hilt annotations until Stage 4.4.
 */
@Database(
    entities = [
        LaunchAttemptEntity::class,
        DailyUsageRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MinimaDatabase : RoomDatabase() {

    abstract fun launchAttemptDao(): LaunchAttemptDao

    abstract fun dailyUsageRecordDao(): DailyUsageRecordDao
}

