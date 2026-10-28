package com.andika.watersupply

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.updateAll
import androidx.test.platform.app.InstrumentationRegistry
import com.andika.watersupply.data.glasses
import com.andika.watersupply.widget.RetryWidget
import com.andika.watersupply.widget.WaterWidget
import com.andika.watersupply.widget.WaterWidgetReceiver
import com.andika.watersupply.widget.WidgetDataSource
import com.andika.watersupply.widget.WidgetProgress
import androidx.glance.action.actionParametersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Exercises the real receiver, WorkManager session, RemoteViews and PendingIntent actions. */
class WidgetHostTest {
    @Test fun initialDataActionsMultipleInstancesAndExpiredSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, WaterWidgetReceiver::class.java)
        val host = AppWidgetHost(context, 4242)
        val repository = context.waterApp.repository
        val originalIds = repository.drinks.first().map { it.id }.toSet()
        val (before, target) = repository.today()
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
        }
        val user = shell("am get-current-user").toString(Charsets.UTF_8).trim().toInt()
        var grantedForTest = false
        try {
            instrumentation.runOnMainSync { host.startListening() }
            val views = (1..2).map {
                val id = host.allocateAppWidgetId()
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 276)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 276)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(276f, 110f)))
                }
                if (!manager.bindAppWidgetIdIfAllowed(id, provider, options)) {
                    shell("appwidget grantbind --package ${context.packageName} --user $user")
                    grantedForTest = true
                    assertTrue("Test host must be allowed to bind", manager.bindAppWidgetIdIfAllowed(id, provider, options))
                }
                lateinit var view: AppWidgetHostView
                instrumentation.runOnMainSync { view = host.createView(context, id, manager.getAppWidgetInfo(id)) }
                id to view
            }
            suspend fun awaitProgress(total: Int) {
                val expected = "${glasses(total)} / ${glasses(target)}"
                repeat(300) {
                    var ready = false
                    instrumentation.runOnMainSync {
                        ready = views.all { (_, view) -> descendants(view).filterIsInstance<TextView>().any { it.text.toString() == expected } }
                    }
                    if (ready) return
                    delay(100)
                }
                var actual = ""
                instrumentation.runOnMainSync {
                    actual = views.joinToString { (_, view) -> descendants(view).filterIsInstance<TextView>().joinToString { it.text } }
                }
                fail("Expected $expected on both hosts, got: $actual")
            }
            fun click(description: String) = instrumentation.runOnMainSync {
                val action = descendants(views.first().second).single {
                    it.contentDescription?.toString() == description
                }
                val ancestors = generateSequence(action) { it.parent as? View }.takeWhile { it != views.first().second }.toList()
                val clickable = (descendants(action) + ancestors).firstOrNull { it.isClickable }
                assertNotNull("$description must have a click target: " + descendants(views.first().second)
                    .joinToString { "${it.javaClass.simpleName}(${it.contentDescription}, click=${it.isClickable})" }, clickable)
                assertTrue(clickable!!.performClick())
            }
            awaitProgress(before)
            click("Tambah 1 gelas")
            awaitProgress(before + 2)
            click("Tambah setengah gelas")
            awaitProgress(before + 3)
            click("Urungkan catatan terakhir hari ini")
            awaitProgress(before + 2)
            click("Urungkan catatan terakhir hari ini")
            awaitProgress(before)
            // Retry must also work while provideGlance is still running.
            RetryWidget().onAction(context, AppWidgetId(views.first().first), actionParametersOf())
            awaitProgress(before)
            // Glance ends an idle session ~45 seconds after the first composition.
            delay(60_000)
            val available = AtomicBoolean(false)
            val source = object : WidgetDataSource {
                override suspend fun read(context: Context): WidgetProgress {
                    check(available.get()) { "Simulated database read failure" }
                    val (total, goal) = repository.today()
                    return WidgetProgress(total, goal, total > 0)
                }
                override fun observe(context: Context) = flow { emit(read(context)) }
            }
            // Starting a new managed session with an unavailable source must publish an error.
            WaterWidget(source).update(context, AppWidgetId(views.first().first))
            var failed = false
            repeat(300) {
                if (!failed) {
                    instrumentation.runOnMainSync {
                        failed = descendants(views.first().second).filterIsInstance<TextView>()
                            .any { it.text.toString() == "Catatan belum bisa dimuat" }
                    }
                    if (!failed) delay(100)
                }
            }
            assertTrue("Expired session must restart and expose a recoverable error", failed)
            repository.record(1)
            WaterWidget().updateAll(context)
            available.set(true)
            click("Coba lagi")
            awaitProgress(before + 1)
        } finally {
            instrumentation.runOnMainSync { host.stopListening(); host.deleteHost() }
            repository.drinks.first().filter { it.id !in originalIds }.forEach { repository.delete(it.id) }
            if (grantedForTest) shell("appwidget revokebind --package ${context.packageName} --user $user")
            context.waterApp.refresh()
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
