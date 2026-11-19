package com.minima.di

import com.minima.data.repository.AppRepositoryImpl
import com.minima.data.repository.LaunchAttemptRepositoryImpl
import com.minima.data.repository.SettingsRepositoryImpl
import com.minima.data.repository.UsageRepositoryImpl
import com.minima.domain.repository.AppRepository
import com.minima.domain.repository.LaunchAttemptRepository
import com.minima.domain.repository.SettingsRepository
import com.minima.domain.repository.UsageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds concrete repository implementations to domain interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppRepository(
        impl: AppRepositoryImpl
    ): AppRepository

    @Binds
    @Singleton
    abstract fun bindUsageRepository(
        impl: UsageRepositoryImpl
    ): UsageRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindLaunchAttemptRepository(
        impl: LaunchAttemptRepositoryImpl
    ): LaunchAttemptRepository
}
