package com.minima.domain.usecase

import com.minima.data.local.db.dao.DailyUsageRecordDao
import com.minima.data.local.db.entity.DailyUsageRecordEntity
import com.minima.domain.model.DailyUsage
import com.minima.domain.repository.LaunchAttemptRepository
import com.minima.domain.repository.SettingsRepository
import com.minima.domain.repository.UsageRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Result of a usage synchronization attempt.
 */
sealed interface SyncResult {
    /** Skipped because less than [SyncUsageDataUseCase.MIN_SYNC_INTERVAL_MS] has elapsed. */
    data object SkippedInterval : SyncResult

    /** Synchronization completed successfully. */
    data class Success(val recordsCount: Int, val timestampUpdated: Boolean) : SyncResult

    /** An unexpected exception occurred during synchronization. */
    data class Failure(val error: Throwable) : SyncResult
}

/**
 * Use case to synchronize daily foreground usage and launch counts into Room.
 *
 * Implements strict data integrity rules:
 * - Minimum sync interval: 15 minutes (bypassable with `force = true` for WorkManager).
 * - Multi-day historical reconciliation when crossing date boundaries (capped to max 7 days).
 * - First-sync behaviour: if lastSyncTimestamp is 0L, synchronizes today only without historical backfill.
 * - Unavailable or permission-denied usage data never overwrites stored foreground time with zero.
 * - If historical usage is lower than an existing stored record, preserves the higher stored record.
 * - Launch counts update independently of usage stats availability.
 * - Combines packages from both usage queries and recorded launch attempts.
 * - Updates [SettingsRepository.setLastSyncTimestamp] only on successful valid usage synchronization.
 */
class SyncUsageDataUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val launchAttemptRepository: LaunchAttemptRepository,
    private val dailyUsageRecordDao: DailyUsageRecordDao,
    private val usageRepository: UsageRepository
) {

    companion object {
        const val MIN_SYNC_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
        const val MAX_HISTORICAL_LOOKBACK_DAYS = 7L
    }

    suspend operator fun invoke(
        currentUsage: DailyUsage? = null,
        currentTimeMs: Long = System.currentTimeMillis(),
        force: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SyncResult {
        return try {
            // 1. Check whether synchronization is due (15-minute throttle unless forced)
            val lastSync = settingsRepository.getLastSyncTimestamp()
            if (!force && lastSync > 0L && (currentTimeMs - lastSync) < MIN_SYNC_INTERVAL_MS) {
                return SyncResult.SkippedInterval
            }

            // 2. Establish local date boundary
            val todayLocalDate = Instant.ofEpochMilli(currentTimeMs).atZone(zoneId).toLocalDate()
            val todayDate = todayLocalDate.toString() // "YYYY-MM-DD"

            // 3. Reconcile historical days if crossing midnight / multiple days missed
            if (lastSync > 0L) {
                val lastSyncLocalDate = Instant.ofEpochMilli(lastSync).atZone(zoneId).toLocalDate()
                if (lastSyncLocalDate.isBefore(todayLocalDate)) {
                    val maxLookbackDate = todayLocalDate.minusDays(MAX_HISTORICAL_LOOKBACK_DAYS)
                    val effectiveStartDate = if (lastSyncLocalDate.isBefore(maxLookbackDate)) {
                        maxLookbackDate
                    } else {
                        lastSyncLocalDate
                    }

                    var targetDate = effectiveStartDate
                    while (targetDate.isBefore(todayLocalDate)) {
                        syncHistoricalDay(targetDate, zoneId)
                        targetDate = targetDate.plusDays(1)
                    }
                }
            }

            // 4. Retrieve today's launch attempts from Room and aggregate counts
            val startOfTodayMs = todayLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfTodayMs = todayLocalDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val launchAttempts = launchAttemptRepository.getAttemptsForRange(startOfTodayMs, endOfTodayMs)
            val launchCounts = launchAttempts.groupingBy { it.packageName }.eachCount()

            // 5. Retrieve existing stored records for today to preserve stored foreground time
            val existingRecords = dailyUsageRecordDao.getRecordsForDate(todayDate).associateBy { it.packageName }

            // 6. Evaluate usage state (use passed Phase 3 usage result or query once)
            val usage = currentUsage ?: usageRepository.getTodayUsage()

            when (usage) {
                is DailyUsage.Loaded -> {
                    val packageUsage = usage.packageUsage
                    val allPackages = (launchCounts.keys + packageUsage.keys + existingRecords.keys).toSet()

                    val recordsToUpsert = allPackages.map { pkg ->
                        val launchCount = launchCounts[pkg] ?: existingRecords[pkg]?.launchCount ?: 0
                        val queriedForegroundMs = packageUsage[pkg] ?: 0L
                        val existingForegroundMs = existingRecords[pkg]?.totalForegroundMs ?: 0L
                        // Enforce non-decreasing integrity: max of queried and existing
                        val foregroundMs = maxOf(queriedForegroundMs, existingForegroundMs)

                        DailyUsageRecordEntity(
                            packageName = pkg,
                            date = todayDate,
                            totalForegroundMs = foregroundMs,
                            launchCount = launchCount
                        )
                    }

                    if (recordsToUpsert.isNotEmpty()) {
                        dailyUsageRecordDao.upsertAll(recordsToUpsert)
                    }

                    // Update timestamp only after successful valid usage synchronization
                    settingsRepository.setLastSyncTimestamp(currentTimeMs)
                    SyncResult.Success(recordsCount = recordsToUpsert.size, timestampUpdated = true)
                }

                is DailyUsage.PermissionDenied, DailyUsage.Unavailable -> {
                    // Usage data is missing, denied, or temporarily unavailable
                    // Rule: Never interpret unavailable usage as zero; preserve existing foreground time
                    // Rule: Launch counts must still be updated independently
                    val allPackages = (launchCounts.keys + existingRecords.keys).toSet()
                    val recordsToUpsert = allPackages.map { pkg ->
                        val preservedForegroundMs = existingRecords[pkg]?.totalForegroundMs ?: 0L
                        val launchCount = launchCounts[pkg] ?: existingRecords[pkg]?.launchCount ?: 0
                        DailyUsageRecordEntity(
                            packageName = pkg,
                            date = todayDate,
                            totalForegroundMs = preservedForegroundMs,
                            launchCount = launchCount
                        )
                    }

                    if (recordsToUpsert.isNotEmpty()) {
                        dailyUsageRecordDao.upsertAll(recordsToUpsert)
                    }

                    // Timestamp is NOT updated because valid usage was not synchronized
                    SyncResult.Success(recordsCount = recordsToUpsert.size, timestampUpdated = false)
                }
            }
        } catch (e: Exception) {
            SyncResult.Failure(e)
        }
    }

    private suspend fun syncHistoricalDay(date: LocalDate, zoneId: ZoneId) {
        val dateStr = date.toString()
        val dayStartMs = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEndMs = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val dayLaunches = launchAttemptRepository.getAttemptsForRange(dayStartMs, dayEndMs)
            .groupingBy { it.packageName }.eachCount()

        val existingRecords = dailyUsageRecordDao.getRecordsForDate(dateStr).associateBy { it.packageName }

        val usage = usageRepository.queryUsageForRange(dayStartMs, dayEndMs)

        when (usage) {
            is DailyUsage.Loaded -> {
                val packageUsage = usage.packageUsage
                val allPackages = (dayLaunches.keys + packageUsage.keys + existingRecords.keys).toSet()

                val recordsToUpsert = allPackages.map { pkg ->
                    val launchCount = dayLaunches[pkg] ?: existingRecords[pkg]?.launchCount ?: 0
                    val queriedForegroundMs = packageUsage[pkg] ?: 0L
                    val existingForegroundMs = existingRecords[pkg]?.totalForegroundMs ?: 0L
                    val foregroundMs = maxOf(queriedForegroundMs, existingForegroundMs)

                    DailyUsageRecordEntity(
                        packageName = pkg,
                        date = dateStr,
                        totalForegroundMs = foregroundMs,
                        launchCount = launchCount
                    )
                }

                if (recordsToUpsert.isNotEmpty()) {
                    dailyUsageRecordDao.upsertAll(recordsToUpsert)
                }
            }

            is DailyUsage.PermissionDenied, DailyUsage.Unavailable -> {
                val allPackages = (dayLaunches.keys + existingRecords.keys).toSet()
                val recordsToUpsert = allPackages.map { pkg ->
                    val preservedForegroundMs = existingRecords[pkg]?.totalForegroundMs ?: 0L
                    val launchCount = dayLaunches[pkg] ?: existingRecords[pkg]?.launchCount ?: 0
                    DailyUsageRecordEntity(
                        packageName = pkg,
                        date = dateStr,
                        totalForegroundMs = preservedForegroundMs,
                        launchCount = launchCount
                    )
                }

                if (recordsToUpsert.isNotEmpty()) {
                    dailyUsageRecordDao.upsertAll(recordsToUpsert)
                }
            }
        }
    }
}
