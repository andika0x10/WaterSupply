package com.andika.watersupply

import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.andika.watersupply.data.*
import com.andika.watersupply.reminders.ReminderController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import java.io.FileInputStream
import org.junit.Assert.*
import org.junit.Test

class ReminderControllerTest {
    @Test fun deliveryAndTargetSuppression() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repo = WaterRepository(context, Room.inMemoryDatabaseBuilder(context, WaterDatabase::class.java).build())
        val original = repo.preferences.first()
        val controller = ReminderController(context, repo)
        val notifications = context.getSystemService(NotificationManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").use {
                FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
            }
            repo.savePreferences(Preferences(enabled = true, start = 0, end = 1439))
            controller.reschedule()
            assertTrue(controller.notificationsAllowed())
            controller.notifyIfNeeded()
            awaitNotification(notifications, true)
            repo.setTarget(1); repo.record(1)
            controller.reschedule()
            controller.notifyIfNeeded()
            awaitNotification(notifications, false)
            // Correcting the record below target allows reminders again.
            repo.dao.onDate(java.time.LocalDate.now().toString()).forEach { repo.delete(it.id) }
            controller.reschedule(); controller.notifyIfNeeded()
            awaitNotification(notifications, true)
        } finally {
            notifications.cancel(1)
            repo.savePreferences(original)
            repo.database.close()
            context.waterApp.refresh()
        }
    }
    private suspend fun awaitNotification(manager: NotificationManager, expected: Boolean) {
        repeat(40) {
            if (manager.activeNotifications.any { it.id == 1 } == expected) return
            delay(50)
        }
        assertEquals(expected, manager.activeNotifications.any { it.id == 1 })
    }
}
