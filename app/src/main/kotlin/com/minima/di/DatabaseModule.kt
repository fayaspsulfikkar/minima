package com.minima.di

import android.content.Context
import androidx.room.Room
import com.minima.data.local.db.MinimaDatabase
import com.minima.data.local.db.dao.DailyUsageRecordDao
import com.minima.data.local.db.dao.LaunchAttemptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides Room database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMinimaDatabase(
        @ApplicationContext context: Context
    ): MinimaDatabase {
        return Room.databaseBuilder(
            context,
            MinimaDatabase::class.java,
            "minima.db"
        ).build()
    }

    @Provides
    fun provideLaunchAttemptDao(database: MinimaDatabase): LaunchAttemptDao {
        return database.launchAttemptDao()
    }

    @Provides
    fun provideDailyUsageRecordDao(database: MinimaDatabase): DailyUsageRecordDao {
        return database.dailyUsageRecordDao()
    }
}

