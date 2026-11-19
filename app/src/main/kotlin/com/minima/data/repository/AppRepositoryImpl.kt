package com.minima.data.repository

import com.minima.data.system.PackageDataSource
import com.minima.domain.model.AppInfo
import com.minima.domain.repository.AppRepository
import javax.inject.Inject

/**
 * Implementation of [AppRepository] delegating to [PackageDataSource].
 */
class AppRepositoryImpl @Inject constructor(
    private val packageDataSource: PackageDataSource
) : AppRepository {

    override fun getInstalledApps(): List<AppInfo> {
        return packageDataSource.getInstalledApps()
    }

    override fun launchApp(packageName: String): Boolean {
        return packageDataSource.launchApp(packageName)
    }
}
