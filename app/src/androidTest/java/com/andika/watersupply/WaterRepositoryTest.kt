package com.andika.watersupply

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.andika.watersupply.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import com.andika.watersupply.widget.WidgetProgress
import com.andika.watersupply.widget.observeWidgetProgress
import org.junit.*
import org.junit.Assert.*
import java.time.*

class WaterRepositoryTest {
    private lateinit var repo: WaterRepository
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repo = WaterRepository(context, Room.inMemoryDatabaseBuilder(context, WaterDatabase::class.java).build())
    }
    @After fun close() { repo.database.close() }
    @Test fun widgetObservesTargetChangesAndSwitchesDay(): Unit = runBlocking {
        val today = LocalDate.now()
        val dates = MutableStateFlow(today)
        val latest = MutableStateFlow<WidgetProgress?>(null)
        val collector = launch { observeWidgetProgress(repo.dao, dates).collect { latest.value = it } }
        suspend fun expect(value: WidgetProgress) = withTimeout(5_000) { latest.first { it == value } }
        try {
            expect(WidgetProgress(0, 16, false))
            repo.record(1)
            expect(WidgetProgress(1, 16, true))
            repo.setTarget(20)
            expect(WidgetProgress(1, 20, true))
            dates.value = today.plusDays(1)
            expect(WidgetProgress(0, 20, false))
            repo.setTarget(12, today.plusDays(1))
            expect(WidgetProgress(0, 12, false))
            dates.value = today
            expect(WidgetProgress(1, 20, true))
        } finally { collector.cancelAndJoin() }
    }
    @Test fun undoUsesTimestampThenIdAndNeverTouchesYesterday() = runBlocking {
        val now = System.currentTimeMillis()
        val yesterday = LocalDate.now().minusDays(1).atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val old = repo.record(2, yesterday)
        assertFalse(repo.undoLatestToday())
        repo.record(2, now)
        val second = repo.record(1, now)
        // Timestamp takes priority over insertion order.
        repo.record(2, now - 1, second)
        assertTrue(repo.undoLatestToday())
        assertEquals(listOf(second), repo.dao.onDate(LocalDate.now().toString()).map { it.id })
        assertTrue(repo.undoLatestToday())
        assertFalse(repo.undoLatestToday())
        assertEquals(0, repo.today().first)
        assertEquals(old, repo.dao.onDate(LocalDate.now().minusDays(1).toString()).single().id)
    }
    @Test fun undoBreaksTimestampTiesById() = runBlocking {
        val timestamp = System.currentTimeMillis()
        val first = repo.record(2, timestamp)
        repo.record(1, timestamp)
        assertTrue(repo.undoLatestToday())
        assertEquals(first, repo.dao.onDate(LocalDate.now().toString()).single().id)
        assertEquals(2, repo.today().first)
    }
    @Test fun concurrentUndoRemovesExactlyOneEntryPerSuccessfulCall() = runBlocking {
        repeat(12) { repo.record(if (it % 2 == 0) 1 else 2) }
        val results = coroutineScope {
            (1..20).map { async(Dispatchers.IO) { repo.undoLatestToday() } }.awaitAll()
        }
        assertEquals(12, results.count { it })
        assertEquals(0, repo.today().first)
    }
    @Test fun concurrentWidgetAndAppWritesAreNotLost() = runBlocking {
        val ids = coroutineScope { (1..20).map { async(Dispatchers.IO) { repo.record(if (it % 2 == 0) 2 else 1) } }.awaitAll() }
        assertEquals(20, ids.distinct().size)
        assertEquals(30, repo.today().first)
        repo.delete(ids.first())
        assertEquals(29, repo.today().first)
    }
    @Test fun editMovesRecordToPreviousDateAndFutureIsRejected() = runBlocking {
        val id = repo.record(2)
        val yesterday = LocalDate.now().minusDays(1)
        val timestamp = yesterday.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        repo.record(1, timestamp, id)
        assertEquals(0, repo.today().first)
        assertEquals(1, repo.dao.onDate(yesterday.toString()).single().units)
        try { repo.record(2, System.currentTimeMillis() + 60_000); fail("Future record accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun historicalTargetsArePreserved() = runBlocking {
        val today = LocalDate.now()
        repo.setTarget(12, today.minusDays(2)); repo.setTarget(20, today)
        assertEquals(12, repo.dao.target(today.minusDays(1).toString()))
        assertEquals(20, repo.today().second)
    }
    @Test fun widgetSummaryUsesSelectedDateAndApplicableTarget() = runBlocking {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        repo.record(2, yesterday.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        repo.setTarget(12, yesterday)
        repo.setTarget(20, today)
        assertEquals(0, repo.dao.observeTotal(today.toString()).first())
        assertEquals(2, repo.dao.observeTotal(yesterday.toString()).first())
        assertEquals(12, repo.dao.observeTarget(yesterday.toString()).first())
        assertEquals(20, repo.dao.observeTarget(today.toString()).first())
        val id = repo.record(1)
        assertEquals(1, repo.dao.observeTotal(today.toString()).first())
        repo.delete(id)
        assertEquals(0, repo.dao.observeTotal(today.toString()).first())
    }
    @Test fun recordsSurviveDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        try {
            val first = WaterRepository(context, Room.databaseBuilder(context, WaterDatabase::class.java, name).build())
            first.record(1); first.setTarget(19); first.database.close()
            val second = WaterRepository(context, Room.databaseBuilder(context, WaterDatabase::class.java, name).build())
            assertEquals(1 to 19, second.today())
            second.database.close()
        } finally { context.deleteDatabase(name) }
    }
}
