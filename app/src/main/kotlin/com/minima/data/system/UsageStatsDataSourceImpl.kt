package com.minima.data.system

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.minima.domain.model.DailyUsage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Concrete implementation of [UsageStatsDataSource] using Android system services.
 */
class UsageStatsDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UsageStatsDataSource {

    override fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun getTodayUsage(): DailyUsage {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zoneId)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val now = System.currentTimeMillis()
        return queryUsageForRange(startOfDay, now)
    }

    override fun queryUsageForRange(startTimeMs: Long, endTimeMs: Long): DailyUsage {
        if (!hasUsagePermission()) {
            return DailyUsage.PermissionDenied
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return DailyUsage.Unavailable

        return try {
            if (startTimeMs >= endTimeMs) {
                return DailyUsage.Loaded(0L)
            }

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTimeMs,
                endTimeMs
            )

            if (stats.isNullOrEmpty()) {
                DailyUsage.Unavailable
            } else {
                val packageUsageMap = stats
                    .filter { it.totalTimeInForeground > 0 }
                    .groupBy { it.packageName }
                    .mapValues { entry -> entry.value.sumOf { it.totalTimeInForeground } }
                val totalForegroundMs = stats.sumOf { it.totalTimeInForeground }
                DailyUsage.Loaded(
                    totalForegroundMs = totalForegroundMs,
                    packageUsage = packageUsageMap
                )
            }
        } catch (_: SecurityException) {
            DailyUsage.PermissionDenied
        } catch (_: Exception) {
            DailyUsage.Unavailable
        }
    }

    override fun openUsageAccessSettings() {
        try {
            // First attempt: direct package URI to open MINIMA's specific usage access toggle
            val packageUri = Uri.parse("package:${context.packageName}")
            val packageIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(packageIntent)
        } catch (_: Exception) {
            // Fallback: open generic Usage Access settings list
            try {
                val genericIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericIntent)
            } catch (_: Exception) {
                // Secondary fallback: open system application settings
                try {
                    val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                } catch (_: Exception) {
                    // Fail silently if device has no settings handler
                }
            }
        }
    }
}
