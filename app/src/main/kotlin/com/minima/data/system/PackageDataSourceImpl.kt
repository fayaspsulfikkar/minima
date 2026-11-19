package com.minima.data.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.minima.domain.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Concrete implementation of [PackageDataSource] using Android's [PackageManager].
 */
class PackageDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PackageDataSource {

    private val packageManager: PackageManager = context.packageManager

    override fun getInstalledApps(): List<AppInfo> {
        // Query all activities registered for ACTION_MAIN with CATEGORY_LAUNCHER
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList = packageManager.queryIntentActivities(launcherIntent, 0)
        val currentPackageName = context.packageName

        return resolveInfoList
            .asSequence()
            .mapNotNull { resolveInfo ->
                val pkgName = resolveInfo.activityInfo.packageName
                // Filter out MINIMA itself so the launcher does not list itself
                if (pkgName == currentPackageName) return@mapNotNull null

                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                if (label.isNotEmpty()) {
                    AppInfo(packageName = pkgName, label = label)
                } else null
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    override fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            // Catches ActivityNotFoundException, SecurityException, etc.
            false
        }
    }
}
