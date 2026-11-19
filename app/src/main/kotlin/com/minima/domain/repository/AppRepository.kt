package com.minima.domain.repository

import com.minima.domain.model.AppInfo

/**
 * Repository interface defining installed applications operations.
 */
interface AppRepository {

    /**
     * Retrieves all installed launchable applications.
     */
    fun getInstalledApps(): List<AppInfo>

    /**
     * Launches an application by package name.
     * @return true if successful, false if uninstalled or unlaunchable.
     */
    fun launchApp(packageName: String): Boolean
}

