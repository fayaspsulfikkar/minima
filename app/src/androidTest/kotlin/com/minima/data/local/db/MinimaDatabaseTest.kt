package com.minima.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minima.data.local.db.entity.DailyUsageRecordEntity
import com.minima.data.local.db.entity.LaunchAttemptEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for MinimaDatabase.
 *
 * These tests run on a real Android device (or emulator) because Room uses
 * Android's SQLite implementation — the JVM does not include it.
 *
 * We use an in-memory database so each test starts completely fresh with no
 * leftover data, and no file is written to device storage.
 *
 * Run with:  ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MinimaDatabaseTest {

    private lateinit var db: MinimaDatabase

    // ── Timestamps used across tests ─────────────────────────────────────────
    private val DAY_START = 1_700_000_000_000L   // arbitrary epoch ms (day boundary)
    private val DAY_END   = DAY_START + 86_400_000L
    private val T1        = DAY_START + 1_000L
    private val T2        = DAY_START + 2_000L
    private val T3        = DAY_START + 3_000L
    private val TOMORROW  = DAY_END + 1_000L     // outside the test window

    private val DATE      = "2026-11-19"
    private val OTHER_DATE = "2026-11-20"

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MinimaDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ── LaunchAttemptDao ─────────────────────────────────────────────────────

    @Test
    fun insertLaunchAttempt_canBeRetrieved() = runTest {
        val dao = db.launchAttemptDao()

        dao.insert(LaunchAttemptEntity(packageName = "com.example.app", attemptedAt = T1))

        val results = dao.getByRange(DAY_START, DAY_END)
        assertEquals(1, results.size)
        assertEquals("com.example.app", results[0].packageName)
        assertEquals(T1, results[0].attemptedAt)
    }

    @Test
    fun getByPackageAndRange_filtersCorrectly() = runTest {
        val dao = db.launchAttemptDao()

        dao.insert(LaunchAttemptEntity(packageName = "com.app.a", attemptedAt = T1))
        dao.insert(LaunchAttemptEntity(packageName = "com.app.b", attemptedAt = T2))

        val resultsA = dao.getByPackageAndRange("com.app.a", DAY_START, DAY_END)
        val resultsB = dao.getByPackageAndRange("com.app.b", DAY_START, DAY_END)

        assertEquals(1, resultsA.size)
        assertEquals("com.app.a", resultsA[0].packageName)

        assertEquals(1, resultsB.size)
        assertEquals("com.app.b", resultsB[0].packageName)
    }

    @Test
    fun getByRange_excludesOutsideWindow() = runTest {
        val dao = db.launchAttemptDao()

        dao.insert(LaunchAttemptEntity(packageName = "com.example.app", attemptedAt = T1))
        dao.insert(LaunchAttemptEntity(packageName = "com.example.app", attemptedAt = TOMORROW))

        val results = dao.getByRange(DAY_START, DAY_END)
        assertEquals(1, results.size)
        assertEquals(T1, results[0].attemptedAt)
    }

    @Test
    fun countForPackageInRange_returnsCorrectCount() = runTest {
        val dao = db.launchAttemptDao()

        dao.insert(LaunchAttemptEntity(packageName = "com.example.app", attemptedAt = T1))
        dao.insert(LaunchAttemptEntity(packageName = "com.example.app", attemptedAt = T2))
        dao.insert(LaunchAttemptEntity(packageName = "com.example.app", attemptedAt = T3))
        // Different package — should not be counted
        dao.insert(LaunchAttemptEntity(packageName = "com.other.app", attemptedAt = T1))

        val count = dao.countForPackageInRange("com.example.app", DAY_START, DAY_END)
        assertEquals(3, count)
    }

    @Test
    fun countForPackageInRange_returnsZeroWhenEmpty() = runTest {
        val count = db.launchAttemptDao()
            .countForPackageInRange("com.nonexistent.app", DAY_START, DAY_END)
        assertEquals(0, count)
    }

    // ── DailyUsageRecordDao ──────────────────────────────────────────────────

    @Test
    fun upsertDailyUsageRecord_insertsNewRecord() = runTest {
        val dao = db.dailyUsageRecordDao()

        dao.upsert(
            DailyUsageRecordEntity(
                packageName = "com.example.app",
                date = DATE,
                totalForegroundMs = 3_600_000L,
                launchCount = 5
            )
        )

        val record = dao.getRecord("com.example.app", DATE)
        assertNotNull(record)
        assertEquals(3_600_000L, record!!.totalForegroundMs)
        assertEquals(5, record.launchCount)
    }

    @Test
    fun upsertDailyUsageRecord_updatesExistingRecord() = runTest {
        val dao = db.dailyUsageRecordDao()

        dao.upsert(
            DailyUsageRecordEntity(
                packageName = "com.example.app",
                date = DATE,
                totalForegroundMs = 1_000L,
                launchCount = 1
            )
        )
        // Upsert again with new values
        dao.upsert(
            DailyUsageRecordEntity(
                packageName = "com.example.app",
                date = DATE,
                totalForegroundMs = 7_200_000L,
                launchCount = 10
            )
        )

        val record = dao.getRecord("com.example.app", DATE)
        assertNotNull(record)
        assertEquals(7_200_000L, record!!.totalForegroundMs)
        assertEquals(10, record.launchCount)
    }

    @Test
    fun upsertDailyUsageRecord_noDuplicates() = runTest {
        val dao = db.dailyUsageRecordDao()

        dao.upsert(DailyUsageRecordEntity("com.example.app", DATE, 1_000L, 1))
        dao.upsert(DailyUsageRecordEntity("com.example.app", DATE, 2_000L, 2))

        val records = dao.getRecordsForDate(DATE)
        // The composite PK ensures only one row exists per (packageName, date)
        assertEquals(1, records.size)
    }

    @Test
    fun upsertAll_insertsMultipleRecords() = runTest {
        val dao = db.dailyUsageRecordDao()

        dao.upsertAll(
            listOf(
                DailyUsageRecordEntity("com.app.a", DATE, 5_000L, 3),
                DailyUsageRecordEntity("com.app.b", DATE, 3_000L, 2),
                DailyUsageRecordEntity("com.app.c", DATE, 1_000L, 1)
            )
        )

        val records = dao.getRecordsForDate(DATE)
        assertEquals(3, records.size)
    }

    @Test
    fun getRecordsForDate_isolatesDateCorrectly() = runTest {
        val dao = db.dailyUsageRecordDao()

        dao.upsert(DailyUsageRecordEntity("com.example.app", DATE, 1_000L, 1))
        dao.upsert(DailyUsageRecordEntity("com.example.app", OTHER_DATE, 2_000L, 2))

        val records = dao.getRecordsForDate(DATE)
        assertEquals(1, records.size)
        assertEquals(DATE, records[0].date)
    }

    @Test
    fun getRecordsForDateFlow_emitsCorrectData() = runTest {
        val dao = db.dailyUsageRecordDao()

        dao.upsertAll(
            listOf(
                DailyUsageRecordEntity("com.app.a", DATE, 500L, 1),
                DailyUsageRecordEntity("com.app.b", DATE, 9_000L, 5),
                DailyUsageRecordEntity("com.app.c", DATE, 3_000L, 2)
            )
        )

        val records = dao.getRecordsForDateFlow(DATE).first()
        assertEquals(3, records.size)
        // Ordered by totalForegroundMs DESC: b, c, a
        assertEquals("com.app.b", records[0].packageName)
        assertEquals("com.app.c", records[1].packageName)
        assertEquals("com.app.a", records[2].packageName)
    }

    @Test
    fun getRecord_returnsNullForMissingEntry() = runTest {
        val record = db.dailyUsageRecordDao().getRecord("com.nonexistent.app", DATE)
        assertNull(record)
    }
}

