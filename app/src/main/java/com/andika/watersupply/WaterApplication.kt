package com.andika.watersupply

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.andika.watersupply.data.WaterRepository
import com.andika.watersupply.reminders.ReminderController
import com.andika.watersupply.widget.WaterWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WaterApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
        android.util.Log.e("WaterSupply", "Background refresh failed", error)
    })
    val repository by lazy { WaterRepository(this) }
    val reminders by lazy { ReminderController(this, repository) }
    private val refreshMutex = Mutex()
    override fun onCreate() { super.onCreate(); scope.launch { refresh() } }
    suspend fun refresh() = refreshMutex.withLock {
        try {
            reminders.reschedule()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.e("WaterSupply", "Reminder scheduling failed", error)
        }
        WaterWidget().updateAll(this)
    }
}
val Context.waterApp: WaterApplication get() = applicationContext as WaterApplication
