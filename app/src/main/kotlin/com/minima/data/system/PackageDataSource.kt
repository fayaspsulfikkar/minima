package com.minima.data.system

import com.minima.domain.model.AppInfo

/**
 * Interface abstracting Android's [android.content.pm.PackageManager] operations.
 *
 * This allows pure JVM unit testing by supplying a mock or fake data source.
 */
interface PackageDataSource {

    /**
     * Retrieves all installed user-launchable applications sorted alphabetically by name.
     */
    fun getInstalledApps(): List<AppInfo>

    /**
     * Attempts to start an application using its launch intent.
     *
     * @param packageName Package identifier to launch.
     * @return true if the launch intent was successfully dispatched, false otherwise.
     */
    fun launchApp(packageName: String): Boolean
}

