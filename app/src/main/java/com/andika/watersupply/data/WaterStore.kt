package com.andika.watersupply.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.flow.*
import java.time.*

fun glasses(units: Int): String = if (units % 2 == 0) "${units / 2}" else "${units / 2},5"

@Entity(tableName = "drinks")
data class Drink(@PrimaryKey(autoGenerate = true) val id: Long = 0, val timestamp: Long,
    val localDate: String, val units: Int)

@Entity(tableName = "targets")
data class DailyTarget(@PrimaryKey val date: String, val units: Int)

@Dao
interface WaterDao {
    @Query("SELECT * FROM drinks ORDER BY timestamp DESC") fun observeDrinks(): Flow<List<Drink>>
    @Query("SELECT * FROM targets ORDER BY date") fun observeTargets(): Flow<List<DailyTarget>>
    @Query("SELECT * FROM drinks WHERE localDate = :date") suspend fun onDate(date: String): List<Drink>
    @Query("SELECT units FROM targets WHERE date <= :date ORDER BY date DESC LIMIT 1") suspend fun target(date: String): Int?
    @Insert suspend fun insert(drink: Drink): Long
    @Update suspend fun update(drink: Drink)
    @Query("DELETE FROM drinks WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM drinks WHERE id = (SELECT id FROM drinks WHERE localDate = :date ORDER BY timestamp DESC, id DESC LIMIT 1)")
    suspend fun deleteLatestOnDate(date: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setTarget(target: DailyTarget)
}

@Database(entities = [Drink::class, DailyTarget::class], version = 1, exportSchema = true)
abstract class WaterDatabase : RoomDatabase() { abstract fun waterDao(): WaterDao }

enum class ReminderMode { INTERVAL, CUSTOM }
data class Preferences(val enabled: Boolean = false, val mode: ReminderMode = ReminderMode.INTERVAL,
    val interval: Int = 60, val start: Int = 480, val end: Int = 1260,
    val times: List<Int> = listOf(540, 750, 960))

private val Context.waterPreferences by preferencesDataStore("water_preferences")
class WaterRepository(context: Context, val database: WaterDatabase = Room.databaseBuilder(
    context.applicationContext, WaterDatabase::class.java, "water.db").build()) {
    private val store = context.applicationContext.waterPreferences
    val dao = database.waterDao()
    val drinks = dao.observeDrinks()
    val targets = dao.observeTargets()
    val preferences = store.data.map { p ->
        Preferences(p[booleanPreferencesKey("enabled")] ?: false,
            runCatching { ReminderMode.valueOf(p[stringPreferencesKey("mode")] ?: "INTERVAL") }.getOrDefault(ReminderMode.INTERVAL),
            p[intPreferencesKey("interval")] ?: 60, p[intPreferencesKey("start")] ?: 480,
            p[intPreferencesKey("end")] ?: 1260,
            p[stringSetPreferencesKey("times")]?.mapNotNull(String::toIntOrNull)?.sorted() ?: listOf(540, 750, 960))
    }

    suspend fun savePreferences(p: Preferences) {
        require(p.interval in 15..240 && p.interval % 15 == 0)
        require(p.start in 0..1439 && p.end in 0..1439 && p.start < p.end)
        require((p.mode != ReminderMode.CUSTOM || p.times.isNotEmpty()) && p.times.all { it in 0..1439 })
        store.edit {
            it[booleanPreferencesKey("enabled")] = p.enabled
            it[stringPreferencesKey("mode")] = p.mode.name
            it[intPreferencesKey("interval")] = p.interval
            it[intPreferencesKey("start")] = p.start
            it[intPreferencesKey("end")] = p.end
            it[stringSetPreferencesKey("times")] = p.times.map(Int::toString).toSet()
        }
    }

    suspend fun record(units: Int, timestamp: Long = System.currentTimeMillis(), id: Long = 0): Long {
        require(units == 1 || units == 2)
        require(timestamp <= System.currentTimeMillis()) { "Waktu catatan tidak boleh di masa depan." }
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val drink = Drink(id, timestamp, date, units)
        return database.withTransaction {
            if (id == 0L) dao.insert(drink) else { dao.update(drink); id }
        }
    }
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun undoLatestToday(): Boolean = dao.deleteLatestOnDate(LocalDate.now().toString()) > 0
    suspend fun setTarget(units: Int, date: LocalDate = LocalDate.now()) {
        require(units > 0)
        dao.setTarget(DailyTarget(date.toString(), units))
    }
    suspend fun today(): Pair<Int, Int> {
        val date = LocalDate.now().toString()
        return database.withTransaction { dao.onDate(date).sumOf { it.units } to (dao.target(date) ?: 16) }
    }
}

fun targetFor(targets: List<DailyTarget>, date: LocalDate): Int =
    targets.lastOrNull { it.date <= date.toString() }?.units ?: 16
