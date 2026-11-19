package com.minima.di

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.minima.data.system.PackageDataSource
import com.minima.data.system.PackageDataSourceImpl
import com.minima.data.system.UsageStatsDataSource
import com.minima.data.system.UsageStatsDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds system data sources to their interfaces
 * and provides system-level service singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {

    @Binds
    @Singleton
    abstract fun bindPackageDataSource(
        impl: PackageDataSourceImpl
    ): PackageDataSource

    @Binds
    @Singleton
    abstract fun bindUsageStatsDataSource(
        impl: UsageStatsDataSourceImpl
    ): UsageStatsDataSource

    companion object {
        @Provides
        @Singleton
        fun providePackageManager(
            @ApplicationContext context: Context
        ): PackageManager {
            return context.packageManager
        }

        @Provides
        @Singleton
        fun provideUsageStatsManager(
            @ApplicationContext context: Context
        ): UsageStatsManager? {
            return context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        }
    }
}

